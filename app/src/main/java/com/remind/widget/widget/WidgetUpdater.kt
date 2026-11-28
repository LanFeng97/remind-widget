package com.remind.widget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.RemoteViews
import com.remind.widget.R
import com.remind.widget.core.DayPlan
import com.remind.widget.core.Schedule
import com.remind.widget.data.ReminderRepository
import com.remind.widget.data.WidgetRow
import com.remind.widget.receiver.WidgetActionReceiver
import java.time.LocalDateTime

/**
 * 小组件 = 左右分栏。
 *
 *   左 76dp：栏目列表（点击切换），最后一项是「已完成」
 *   右 剩余：当前选中栏目的时间项 / 今天的完成记录
 *
 * 为什么不用横滑翻页：Android 15 起 RemoteViews 白名单移除了 HorizontalScrollView
 * （InflateException: Class not allowed to be inflated），而且桌面组件收不到 onTouch。
 * 两个 ListView 并排是允许的（并列，不是嵌套）。
 */
object WidgetUpdater {

    private const val TAG = "WidgetUpdater"
    private const val PREFS = "widget_state"
    private const val KEY_SELECTED_PREFIX = "sel_"

    /** 选中值：0 = 还没选过（用默认），-1 = 选中「已完成」，其它 = groupId */
    private const val NOT_SET = 0L
    private const val DONE_TAB = -1L

    // ---------------- 选中状态 ----------------

    fun getSelectedGroup(context: Context, widgetId: Int): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_SELECTED_PREFIX + widgetId, NOT_SET)

    fun setSelectedGroup(context: Context, widgetId: Int, groupId: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_SELECTED_PREFIX + widgetId, groupId).apply()
    }

    fun resetWidget(context: Context, widgetId: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_SELECTED_PREFIX + widgetId).apply()
    }

    // ---------------- 数据 ----------------

    data class WidgetData(
        val groupRows: List<WidgetRow>,
        val contentRows: List<WidgetRow>,
        val dueTotal: Int,
        val todoTotal: Int,
        val selectedName: String,
        val selectedIsDone: Boolean
    )

    suspend fun compute(context: Context, widgetId: Int): WidgetData {
        val repo = ReminderRepository.get(context)
        val groups = repo.groups()
        val events = repo.events()
        val done = repo.doneToday()
        val now = LocalDateTime.now()

        val blocks = DayPlan.build(groups, events, done, now)
        val dueTotal = blocks.sumOf { it.dueCount }
        val todoTotal = blocks.sumOf { it.pending.size }

        // 默认选中：第一个"有到点的栏目"，没有就第一个还有待办的，再没有就第一个栏目
        val defaultId = blocks.firstOrNull { it.dueCount > 0 }?.group?.id
            ?: blocks.firstOrNull { it.pending.isNotEmpty() }?.group?.id
            ?: blocks.firstOrNull()?.group?.id
            ?: DONE_TAB

        val saved = getSelectedGroup(context, widgetId)
        val selectedId = if (saved == NOT_SET) defaultId else saved
        // 选中的栏目可能已被删除，兜底回默认
        val effectiveId = when {
            selectedId == DONE_TAB -> DONE_TAB
            blocks.any { it.group.id == selectedId } -> selectedId
            else -> defaultId
        }

        // ---- 左侧：栏目 + 已完成 ----
        val groupRows = ArrayList<WidgetRow>(blocks.size + 1)
        blocks.forEach { b ->
            groupRows += WidgetRow.GroupTab(
                groupId = b.group.id,
                name = b.group.name,
                color = b.group.color,
                dueCount = b.dueCount,
                selected = b.group.id == effectiveId
            )
        }
        groupRows += WidgetRow.DoneTab(done.size, effectiveId == DONE_TAB)

        // ---- 右侧：选中栏目的内容 ----
        val contentRows = ArrayList<WidgetRow>(16)
        var selectedName = ""
        val isDone = effectiveId == DONE_TAB

        if (isDone) {
            selectedName = "已完成"
            if (done.isNotEmpty()) {
                done.sortedByDescending { it.doneAt }.forEach { r ->
                    contentRows += WidgetRow.DoneRow(
                        recordId = r.id,
                        name = r.eventName,
                        timeText = java.time.Instant.ofEpochMilli(r.doneAt)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalTime()
                            .let { "%02d:%02d".format(it.hour, it.minute) },
                        color = r.color
                    )
                }
            }
        } else {
            val block = blocks.firstOrNull { it.group.id == effectiveId }
            if (block != null) {
                selectedName = block.group.name
                block.pending.forEach { ev ->
                    contentRows += WidgetRow.EventRow(
                        eventId = ev.id,
                        groupId = block.group.id,
                        name = ev.name,
                        timeText = Schedule.hhmm(ev),
                        ruleText = Schedule.ruleText(ev),
                        color = block.group.color,
                        due = Schedule.isDueNow(ev, now)
                    )
                }
            }
        }

        return WidgetData(groupRows, contentRows, dueTotal, todoTotal, selectedName, isDone)
    }

    // ---------------- 刷新 ----------------

    suspend fun refresh(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(
            android.content.ComponentName(context, ReminderWidgetProvider::class.java)
        )
        ids.forEach { runCatching { refreshWidget(context, it) } }
    }

    suspend fun refreshWidget(context: Context, widgetId: Int) {
        val mgr = AppWidgetManager.getInstance(context)
        val widgetData = runCatching { compute(context, widgetId) }
            .onFailure { Log.e(TAG, "compute 失败", it) }
            .getOrNull()

        val views = RemoteViews(context.packageName, R.layout.widget_main)

        // 两个列表各自的适配器（data 必须不同，否则 Intent 会被合并）
        val groupIntent = Intent(context, WidgetListService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            putExtra(WidgetListService.EXTRA_WHICH, WidgetListService.WHICH_GROUPS)
            data = Uri.parse("remindwidget://groups/$widgetId")
        }
        val contentIntent = Intent(context, WidgetListService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            putExtra(WidgetListService.EXTRA_WHICH, WidgetListService.WHICH_CONTENT)
            data = Uri.parse("remindwidget://content/$widgetId")
        }
        views.setRemoteAdapter(R.id.w_group_list, groupIntent)
        views.setRemoteAdapter(R.id.w_content_list, contentIntent)

        // 左侧点击 = 切换栏目；右侧点击 = 标记完成（撤销走 App 内）
        views.setPendingIntentTemplate(
            R.id.w_group_list,
            template(context, WidgetActionReceiver.ACTION_SELECT_GROUP)
        )
        views.setPendingIntentTemplate(
            R.id.w_content_list,
            template(context, WidgetActionReceiver.ACTION_COMPLETE)
        )
        views.setEmptyView(R.id.w_content_list, R.id.w_content_empty)

        // 顶部：整条点击进 App
        val open = Intent(context, WidgetActionReceiver::class.java).apply {
            action = WidgetActionReceiver.ACTION_OPEN_APP
            data = Uri.parse("remindwidget://open/$widgetId")
        }
        views.setOnClickPendingIntent(
            R.id.w_header,
            PendingIntent.getBroadcast(
                context, 91_000 + widgetId, open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        if (widgetData != null) {
            val headerText = when {
                widgetData.selectedIsDone -> "今天已完成 ${widgetData.contentRows.size} 项"
                widgetData.dueTotal > 0 -> "${widgetData.dueTotal} 项到点 · ${widgetData.selectedName}"
                else -> "今日待办 ${widgetData.todoTotal}"
            }
            views.setTextViewText(R.id.w_header_text, headerText)
            val idleColor = androidx.core.content.ContextCompat.getColor(context, R.color.w_header)
            views.setInt(
                R.id.w_header_text, "setTextColor",
                if (!widgetData.selectedIsDone && widgetData.dueTotal > 0) 0xFFFF6A00.toInt()
                else idleColor
            )
            // 空态文案：已完成页和待办页不一样
            views.setTextViewText(
                R.id.w_content_empty,
                if (widgetData.selectedIsDone) "今天还没有完成记录" else "这一栏今天都完成了 🎉"
            )
        }

        runCatching {
            mgr.notifyAppWidgetViewDataChanged(widgetId, R.id.w_group_list)
            mgr.notifyAppWidgetViewDataChanged(widgetId, R.id.w_content_list)
        }
        mgr.updateAppWidget(widgetId, views)
    }

    private fun template(context: Context, action: String): PendingIntent {
        val intent = Intent(context, WidgetActionReceiver::class.java).apply { this.action = action }
        return PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            intent,
            // 必须 MUTABLE，否则 fillInIntent 的 extras 填不进去
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }
}
