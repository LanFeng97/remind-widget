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
 * 小组件的全部渲染与数据准备都在这里。
 *
 * 两条硬约束决定了实现方式：
 * 1. RemoteViews 里 **ScrollView 不能滚动**（Launcher 不把触摸事件转进去），
 *    纵向滚动只能用集合视图 → 这里用 ListView + RemoteViewsService。
 * 2. 集合视图之间不能嵌套，但可以放在 HorizontalScrollView 里并排 →
 *    待办页 / 已完成页做成两页横滑。
 *    API 31 起才能动态设置子视图宽度（setViewLayoutWidth），
 *    更低版本退回固定 300dp 并靠右上角切换按钮翻页。
 */
object WidgetUpdater {

    private const val TAG = "WidgetUpdater"
    private const val PREFS = "widget_state"
    private const val KEY_PAGE_PREFIX = "page_"

    // ---------------- 页面状态 ----------------
    fun getPage(context: Context, widgetId: Int): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_PAGE_PREFIX + widgetId, 0)

    fun setPage(context: Context, widgetId: Int, page: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_PAGE_PREFIX + widgetId, page.coerceIn(0, 1)).apply()
    }

    // ---------------- 数据 ----------------
    data class WidgetData(
        val todoRows: List<WidgetRow>,
        val doneRows: List<WidgetRow>,
        val dueTotal: Int,
        val todoTotal: Int
    )

    /**
     * 组装两页的行数据。
     * 排序规则统一走 DayPlan，保证 App 首页与桌面顺序一致。
     */
    suspend fun compute(context: Context): WidgetData {
        val repo = ReminderRepository.get(context)
        val groups = repo.groups()
        val events = repo.events()
        val done = repo.doneToday()

        val now = LocalDateTime.now()
        // 排序与"今天该出现什么"统一由 DayPlan 决定，保证 App 首页与桌面顺序完全一致
        val blocks = DayPlan.build(groups, events, done, now)

        val todoRows = ArrayList<WidgetRow>(64)
        var dueTotal = 0
        var todoTotal = 0

        for (b in blocks) {
            if (b.pending.isEmpty()) continue
            dueTotal += b.dueCount
            todoTotal += b.pending.size
            todoRows += WidgetRow.Header(
                groupId = b.group.id,
                name = b.group.name,
                color = b.group.color,
                dueCount = b.dueCount,
                totalCount = b.pending.size
            )
            b.pending.forEach { ev ->
                todoRows += WidgetRow.EventRow(
                    eventId = ev.id,
                    groupId = b.group.id,
                    name = ev.name,
                    timeText = Schedule.hhmm(ev),
                    ruleText = Schedule.ruleText(ev),
                    color = b.group.color,
                    due = Schedule.isDueNow(ev, now)
                )
            }
        }

        val doneRows = ArrayList<WidgetRow>(done.size + 1)
        doneRows += WidgetRow.DoneHeader(done.size)
        done.sortedByDescending { it.doneAt }.forEach { r ->
            doneRows += WidgetRow.DoneRow(
                recordId = r.id,
                name = r.eventName,
                timeText = java.time.Instant.ofEpochMilli(r.doneAt)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalTime()
                    .let { "%02d:%02d".format(it.hour, it.minute) },
                color = r.color
            )
        }

        return WidgetData(todoRows, doneRows, dueTotal, todoTotal)
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
        // 注意变量别叫 data：Intent.apply { data = ... } 会撞上它，Kotlin 优先解析外层局部变量
        val widgetData = runCatching { compute(context) }
            .onFailure { Log.e(TAG, "compute 失败", it) }
            .getOrNull()

        val views = RemoteViews(context.packageName, R.layout.widget_main)
        val page = getPage(context, widgetId)

        // 两页叠在同一个 FrameLayout 里，用 visibility 切页。
        // 不用 HorizontalScrollView：Android 15 的 RemoteViews 白名单已不允许它
        // （实测 InflateException: Class not allowed to be inflated）。
        views.setViewVisibility(
            R.id.w_page_todo,
            if (page == 0) android.view.View.VISIBLE else android.view.View.GONE
        )
        views.setViewVisibility(
            R.id.w_page_done,
            if (page == 1) android.view.View.VISIBLE else android.view.View.GONE
        )

        // 两个列表各自的适配器（data 字段必须不同，否则 PendingIntent/Intent 会被合并）
        val todoIntent = Intent(context, WidgetListService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            putExtra(WidgetListService.EXTRA_WHICH, WidgetListService.WHICH_TODO)
            data = Uri.parse("remindwidget://todo/$widgetId")
        }
        val doneIntent = Intent(context, WidgetListService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            putExtra(WidgetListService.EXTRA_WHICH, WidgetListService.WHICH_DONE)
            data = Uri.parse("remindwidget://done/$widgetId")
        }
        views.setRemoteAdapter(R.id.w_todo_list, todoIntent)
        views.setRemoteAdapter(R.id.w_done_list, doneIntent)

        // 集合视图的点击必须走 template + fillInIntent
        views.setPendingIntentTemplate(R.id.w_todo_list, template(context, WidgetActionReceiver.ACTION_COMPLETE))
        views.setPendingIntentTemplate(R.id.w_done_list, template(context, WidgetActionReceiver.ACTION_UNDO))

        // 空态
        views.setEmptyView(R.id.w_todo_list, R.id.w_todo_empty)
        views.setEmptyView(R.id.w_done_list, R.id.w_done_empty)

        // 右上角切换按钮（横滑在个别 Launcher 上不生效时的兜底入口）
        val nextPage = if (page == 0) 1 else 0
        val toggle = Intent(context, WidgetActionReceiver::class.java).apply {
            action = WidgetActionReceiver.ACTION_TOGGLE_PAGE
            putExtra(WidgetActionReceiver.EXTRA_WIDGET_ID, widgetId)
            putExtra(WidgetActionReceiver.EXTRA_PAGE, nextPage)
            data = Uri.parse("remindwidget://toggle/$widgetId/$nextPage")
        }
        views.setOnClickPendingIntent(
            R.id.w_page_switch,
            PendingIntent.getBroadcast(
                context, 90_000 + widgetId, toggle,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        views.setTextViewText(R.id.w_page_switch, if (page == 0) "已完成" else "待办")

        // 顶部整块点击 → 打开 App
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
            // 左上角文案跟着当前页走，否则切到已完成页还显示"几项到点"会自相矛盾
            val doneCount = widgetData.doneRows.count { it is WidgetRow.DoneRow }
            val headerText = if (page == 0) {
                if (widgetData.dueTotal > 0) "${widgetData.dueTotal} 项到点"
                else "今日待办 ${widgetData.todoTotal}"
            } else {
                "今天已完成 $doneCount 项"
            }
            views.setTextViewText(R.id.w_header_text, headerText)
            val idleColor = androidx.core.content.ContextCompat.getColor(context, R.color.w_header)
            views.setInt(
                R.id.w_header_text, "setTextColor",
                if (page == 0 && widgetData.dueTotal > 0) 0xFFFF6A00.toInt() else idleColor
            )
        }

        runCatching {
            mgr.notifyAppWidgetViewDataChanged(widgetId, R.id.w_todo_list)
            mgr.notifyAppWidgetViewDataChanged(widgetId, R.id.w_done_list)
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
