package com.remind.widget.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.remind.widget.data.Event
import com.remind.widget.data.ReminderRepository
import com.remind.widget.receiver.AlarmReceiver
import java.time.ZoneId

/**
 * 精确闹钟调度。
 *
 * 为什么不用 WorkManager：WorkManager 的最小可靠粒度是分钟级且会被 Doze 推迟，
 * 吃药提醒差 15 分钟就失去意义了。这里用 setExactAndAllowWhileIdle。
 *
 * Android 12+ 需要 SCHEDULE_EXACT_ALARM 权限（用户可在系统设置里关掉），
 * 关掉时降级成 setAndAllowWhileIdle（不精确但仍会响），不会静默丢提醒。
 */
object AlarmScheduler {

    private const val TAG = "AlarmScheduler"
    private const val REQUEST_BASE = 1000

    private fun pendingIntent(context: Context, eventId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE
            putExtra(AlarmReceiver.EXTRA_EVENT_ID, eventId)
        }
        return PendingIntent.getBroadcast(
            context,
            (REQUEST_BASE + eventId).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 为单个事件排下一次。事件已结束（如一次性过点）则自动取消 */
    fun schedule(context: Context, event: Event) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context, event.id)

        val next = Schedule.nextTrigger(event)
        if (next == null) {
            am.cancel(pi)
            return
        }

        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { am.canScheduleExactAlarms() }.getOrDefault(false)
        } else {
            true
        }

        try {
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
            Log.d(TAG, "event=${event.id} ${event.name} -> $next (exact=$canExact)")
        } catch (e: SecurityException) {
            Log.w(TAG, "精确闹钟被拒，降级为非精确: ${e.message}")
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi) }
        }
    }

    fun cancel(context: Context, eventId: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pendingIntent(context, eventId))
    }

    /**
     * 用户自测入口：把某个事件的闹钟临时提前到 N 秒后，走一遍完整链路
     * （AlarmManager → AlarmReceiver → 通知 → 小组件刷新）。
     * 响过之后 onAlarmFired 会按正常规则重排，不会破坏原有节奏。
     */
    fun scheduleTest(context: Context, eventId: Long, delayMillis: Long = 10_000L) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context, eventId)
        val at = System.currentTimeMillis() + delayMillis
        try {
            val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching { am.canScheduleExactAlarms() }.getOrDefault(false)
            } else true
            if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            Log.i(TAG, "测试闹钟已排：event=$eventId ${delayMillis}ms 后")
        } catch (e: SecurityException) {
            Log.w(TAG, "测试闹钟被拒: ${e.message}")
        }
    }

    /**
     * 全量重排：数据一变就整体重建，比做增量 diff 简单也不会漏。
     * 事件数量是个位数到几十，成本可以忽略。
     */
    suspend fun resync(context: Context) {
        val events = ReminderRepository.get(context).events()
        events.forEach { schedule(context, it) }
    }
}
