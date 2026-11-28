package com.remind.widget.data

import android.content.Context
import com.remind.widget.core.AlarmScheduler
import com.remind.widget.core.Schedule
import com.remind.widget.widget.WidgetUpdater
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 全部业务操作的唯一入口。
 * 任何写操作结束后都会统一做两件事：重排闹钟 + 刷新桌面小组件，
 * 避免"数据变了但界面/提醒没跟上"这类散落各处的同步 bug。
 */
class ReminderRepository private constructor(
    private val context: Context,
    private val db: AppDatabase
) {
    private val groupDao = db.groupDao()
    private val eventDao = db.eventDao()
    private val doneDao = db.doneDao()

    /** 首次灌数据的进程内互斥锁 */
    private val seedMutex = kotlinx.coroutines.sync.Mutex()

    // ---------- 观测 ----------
    val groupsFlow: Flow<List<Group>> = groupDao.observeAll()
    val eventsFlow: Flow<List<Event>> = eventDao.observeAll()

    fun eventsOfGroupFlow(groupId: Long): Flow<List<Event>> = eventDao.observeByGroup(groupId)

    fun doneTodayFlow(): Flow<List<DoneRecord>> =
        doneDao.observeByDay(LocalDate.now().toString())

    /** 全部历史记录（已完成列表页） */
    val allDoneFlow: Flow<List<DoneRecord>> = doneDao.observeAll()

    // ---------- 读 ----------
    suspend fun groups(): List<Group> = groupDao.all()

    suspend fun events(): List<Event> = eventDao.allEnabled()

    suspend fun eventsOfGroup(groupId: Long): List<Event> = eventDao.byGroup(groupId)

    suspend fun event(eventId: Long): Event? = eventDao.byId(eventId)

    suspend fun doneToday(): List<DoneRecord> = doneDao.byDay(LocalDate.now().toString())

    suspend fun recentDone(limit: Int = 100): List<DoneRecord> = doneDao.recent(limit)

    /** 今天这次是否已经做过 */
    suspend fun isDoneToday(event: Event): Boolean {
        val key = Schedule.todayDueKey(event) ?: return false
        return doneDao.dueKeysOf(event.id).contains(key)
    }

    // ---------- 栏目 ----------
    suspend fun createGroup(name: String, color: Long): Long {
        val order = groupDao.count()
        val id = groupDao.insert(Group(name = name, color = color, sortOrder = order))
        afterChange()
        return id
    }

    suspend fun updateGroup(group: Group) {
        groupDao.update(group)
        afterChange()
    }

    suspend fun deleteGroup(groupId: Long) {
        groupDao.byId(groupId)?.let { groupDao.delete(it) }
        afterChange()
    }

    // ---------- 事件 ----------
    suspend fun createEvent(event: Event): Long {
        val id = eventDao.insert(event)
        afterChange()
        return id
    }

    suspend fun updateEvent(event: Event) {
        eventDao.update(event)
        afterChange()
    }

    suspend fun deleteEvent(eventId: Long) {
        eventDao.byId(eventId)?.let { eventDao.delete(it) }
        AlarmScheduler.cancel(context, eventId)
        afterChange()
    }

    // ---------- 完成 / 撤销 ----------
    /**
     * 处理一次事件：完成（ignored=false）或忽略（ignored=true）。
     * 两者的效果相同——今天这一次不再出现在待办里，只是展示上区分。
     * 返回 true 表示这次真的写入了一条记录（重复点击返回 false）。
     * 一次性事件处理后自动停用，避免它继续占着闹钟。
     */
    suspend fun completeEvent(eventId: Long, ignored: Boolean = false): Boolean {
        val ev = eventDao.byId(eventId) ?: return false
        val group = groupDao.byId(ev.groupId) ?: return false
        val now = LocalDateTime.now()
        val dueKey = Schedule.todayDueKey(ev, now) ?: Schedule.dueKey(ev, now.toLocalDate())

        if (doneDao.dueKeysOf(eventId).contains(dueKey)) {
            // 已经处理过一次了（不管是完成还是忽略），不重复记账
            return false
        }

        doneDao.insert(
            DoneRecord(
                eventId = ev.id,
                groupId = group.id,
                eventName = ev.name,
                groupName = group.name,
                color = group.color,
                doneAt = System.currentTimeMillis(),
                dueKey = dueKey,
                dayKey = now.toLocalDate().toString(),
                ignored = ignored
            )
        )

        if (ev.type == EventType.ONCE) {
            eventDao.update(ev.copy(enabled = false))
        }

        afterChange()
        return true
    }

    /** 撤销某条完成记录（点错了的后悔药） */
    suspend fun undoComplete(recordId: Long) {
        doneDao.deleteById(recordId)
        afterChange()
    }

    /** 清理历史：保留最近 N 天 */
    suspend fun pruneHistory(keepDays: Int) {
        val before = LocalDate.now().minusDays(keepDays.toLong()).toString()
        doneDao.pruneBefore(before)
        afterChange()
    }

    // ---------- 闹钟回调 ----------
    /** 用户点"测试提醒"：拿第一个启用的事件排一个 10 秒后的闹钟 */
    suspend fun scheduleTestAlarm(): Boolean {
        val ev = eventDao.allEnabled().firstOrNull() ?: return false
        AlarmScheduler.scheduleTest(context, ev.id)
        return true
    }

    /** 闹钟响了：发通知 → 排下一次 → 刷新小组件 */
    suspend fun onAlarmFired(eventId: Long) {
        val ev = eventDao.byId(eventId) ?: return
        val group = groupDao.byId(ev.groupId) ?: return
        if (ev.enabled && group.notify) {
            com.remind.widget.core.Notifier.show(context, group, ev)
        }
        // 立刻排下一次（同一天内不重复响，所以从"现在"往后算即可）
        AlarmScheduler.schedule(context, ev)
        WidgetUpdater.refresh(context)
    }

    // ---------- 统一收尾 ----------
    private suspend fun afterChange() {
        AlarmScheduler.resync(context)
        WidgetUpdater.refresh(context)
    }

    // ---------- 首次种子数据 ----------
    /**
     * 首次启动灌一组示例，让用户一装上小组件就有东西可看、可点。
     * 只在完全空库时执行。
     */
    suspend fun seedIfEmpty() {
        // Application、小组件 onUpdate/onEnabled、开机广播都会调这个方法。
        // 只靠 count()==0 判断在并发下会插两遍（实测出现过栏目整体翻倍），
        // 所以用「进程内互斥 + 落盘标记」双保险。
        seedMutex.withLock { doSeed() }
    }

    private suspend fun doSeed() {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_SEEDED, false)) return
        if (groupDao.count() > 0) {
            prefs.edit().putBoolean(KEY_SEEDED, true).apply()
            return
        }
        val now = LocalDateTime.now()
        val today = now.toLocalDate()

        val medId = groupDao.insert(Group(name = "吃药", color = 0xFFFF6A00, sortOrder = 0))
        eventDao.insert(
            Event(groupId = medId, name = "维生素D", type = EventType.DAILY,
                hour = 13, minute = 0, sortOrder = 0)
        )
        eventDao.insert(
            Event(groupId = medId, name = "钙片", type = EventType.DAILY,
                hour = 19, minute = 0, sortOrder = 1)
        )

        val fitId = groupDao.insert(Group(name = "健身", color = 0xFF3D5AFE, sortOrder = 1))
        // 三个动作锚点错开各 3 天，正好演示"非锚点日不出现"
        eventDao.insert(
            Event(groupId = fitId, name = "练胸", type = EventType.INTERVAL,
                hour = 20, minute = 0, intervalDays = 3,
                anchorDate = today.minusDays(3).toString(), sortOrder = 0)
        )
        eventDao.insert(
            Event(groupId = fitId, name = "练肩", type = EventType.INTERVAL,
                hour = 20, minute = 0, intervalDays = 3,
                anchorDate = today.minusDays(2).toString(), sortOrder = 1)
        )
        eventDao.insert(
            Event(groupId = fitId, name = "练背", type = EventType.INTERVAL,
                hour = 20, minute = 0, intervalDays = 3,
                anchorDate = today.minusDays(1).toString(), sortOrder = 2)
        )

        val waterId = groupDao.insert(Group(name = "喝水", color = 0xFF14A3C7, sortOrder = 2))
        eventDao.insert(
            Event(groupId = waterId, name = "上午 500ml", type = EventType.DAILY,
                hour = 10, minute = 30, sortOrder = 0)
        )

        val workId = groupDao.insert(Group(name = "工作", color = 0xFF8E4EC6, sortOrder = 3))
        eventDao.insert(
            Event(groupId = workId, name = "写日报", type = EventType.DAILY,
                hour = 18, minute = 30, sortOrder = 0)
        )
        eventDao.insert(
            Event(groupId = workId, name = "周会", type = EventType.WEEKLY,
                hour = 10, minute = 0,
                weekdaysMask = 1 shl 1, // 周一
                sortOrder = 1)
        )

        val petId = groupDao.insert(Group(name = "宠物", color = 0xFFF5A623, sortOrder = 4))
        eventDao.insert(
            Event(groupId = petId, name = "喂猫", type = EventType.DAILY,
                hour = 8, minute = 0, sortOrder = 0)
        )

        prefs.edit().putBoolean(KEY_SEEDED, true).apply()
        android.util.Log.i("ReminderRepo", "示例数据已灌入：${groupDao.count()} 个栏目")
        afterChange()
    }

    companion object {
        private const val PREFS = "remind_prefs"
        private const val KEY_SEEDED = "seeded_v1"

        @Volatile
        private var instance: ReminderRepository? = null

        fun get(context: Context): ReminderRepository = instance ?: synchronized(this) {
            instance ?: ReminderRepository(
                context.applicationContext,
                AppDatabase.get(context.applicationContext)
            ).also { instance = it }
        }
    }
}
