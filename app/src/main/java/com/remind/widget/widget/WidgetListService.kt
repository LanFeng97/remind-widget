package com.remind.widget.widget

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.remind.widget.R
import com.remind.widget.data.WidgetRow
import com.remind.widget.receiver.WidgetActionReceiver
import kotlinx.coroutines.runBlocking

/**
 * 给左右两个 ListView 供数据。
 *
 * · which=groups  → 左侧：栏目切换项 + 「已完成」
 * · which=content → 右侧：当前选中栏目的时间项，或今天的完成记录
 *
 * onDataSetChanged() 在 Binder 线程执行，允许同步查库，所以这里用 runBlocking 是标准做法。
 */
class WidgetListService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        ListFactory(applicationContext, intent)

    companion object {
        const val EXTRA_WHICH = "which"
        const val WHICH_GROUPS = "groups"
        const val WHICH_CONTENT = "content"
    }

    private class ListFactory(
        private val context: Context,
        private val intent: Intent
    ) : RemoteViewsFactory {

        private var rows: List<WidgetRow> = emptyList()
        private val which: String = intent.getStringExtra(EXTRA_WHICH) ?: WHICH_CONTENT
        private val widgetId: Int =
            intent.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, -1)

        override fun onCreate() = Unit

        override fun getLoadingView(): RemoteViews? = null

        override fun onDataSetChanged() {
            rows = runBlocking {
                val data = WidgetUpdater.compute(context, widgetId)
                if (which == WHICH_GROUPS) data.groupRows else data.contentRows
            }
        }

        override fun onDestroy() {
            rows = emptyList()
        }

        override fun getCount(): Int = rows.size

        override fun getViewAt(position: Int): RemoteViews {
            val row = rows.getOrNull(position)
                ?: return RemoteViews(context.packageName, R.layout.widget_item_event_simple)
            return when (row) {
                is WidgetRow.GroupTab -> groupTabView(row)
                is WidgetRow.DoneTab -> doneTabView(row)
                is WidgetRow.EventRow -> eventView(row)
                is WidgetRow.DoneRow -> doneRowView(row)
                is WidgetRow.DoneHeader -> headerView(
                    WidgetRow.Header(0, "今日已完成", 0xFF9AA0A8, 0, row.count)
                )
                is WidgetRow.Header -> headerView(row)
            }
        }

        private fun c(id: Int): Int = androidx.core.content.ContextCompat.getColor(context, id)

        // ---------------- 左侧：栏目切换项 ----------------

        private fun groupTabView(row: WidgetRow.GroupTab): RemoteViews {
            val rv = RemoteViews(context.packageName, R.layout.widget_item_group)
            rv.setTextViewText(R.id.ig_name, row.name)

            if (row.selected) {
                // 选中：左侧竖条用栏目色 + 名称加粗
                rv.setInt(R.id.ig_bar, "setBackgroundColor", row.color.toInt())
                rv.setTextColor(R.id.ig_name, row.color.toInt())
                rv.setTextViewTextSize(R.id.ig_name, android.util.TypedValue.COMPLEX_UNIT_SP, 12.5f)
            } else {
                rv.setInt(R.id.ig_bar, "setBackgroundColor", android.graphics.Color.TRANSPARENT)
                rv.setTextColor(R.id.ig_name, c(R.color.w_text_sub))
                rv.setTextViewTextSize(R.id.ig_name, android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
            }

            // 该栏目有到点未完成的项，右侧点一个小圆点
            if (row.dueCount > 0) {
                rv.setViewVisibility(R.id.ig_due, View.VISIBLE)
                rv.setTextColor(R.id.ig_due, row.color.toInt())
            } else {
                rv.setViewVisibility(R.id.ig_due, View.GONE)
            }

            val fill = Intent().apply {
                putExtra(WidgetActionReceiver.EXTRA_GROUP_ID, row.groupId)
                putExtra(WidgetActionReceiver.EXTRA_WIDGET_ID, widgetId)
            }
            rv.setOnClickFillInIntent(R.id.ig_root, fill)
            return rv
        }

        private fun doneTabView(row: WidgetRow.DoneTab): RemoteViews {
            val rv = RemoteViews(context.packageName, R.layout.widget_item_group)
            rv.setTextViewText(R.id.ig_name, "已完成")
            if (row.selected) {
                rv.setInt(R.id.ig_bar, "setBackgroundColor", c(R.color.w_text_sub))
                rv.setTextColor(R.id.ig_name, c(R.color.w_text))
                rv.setTextViewTextSize(R.id.ig_name, android.util.TypedValue.COMPLEX_UNIT_SP, 12.5f)
            } else {
                rv.setInt(R.id.ig_bar, "setBackgroundColor", android.graphics.Color.TRANSPARENT)
                rv.setTextColor(R.id.ig_name, c(R.color.w_text_sub))
                rv.setTextViewTextSize(R.id.ig_name, android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
            }
            rv.setViewVisibility(R.id.ig_due, View.GONE)

            val fill = Intent().apply {
                putExtra(WidgetActionReceiver.EXTRA_GROUP_ID, -1L)
                putExtra(WidgetActionReceiver.EXTRA_WIDGET_ID, widgetId)
            }
            rv.setOnClickFillInIntent(R.id.ig_root, fill)
            return rv
        }

        // ---------------- 右侧：事件 / 已完成 ----------------

        private fun eventView(row: WidgetRow.EventRow): RemoteViews {
            val rv = RemoteViews(context.packageName, R.layout.widget_item_event_simple)
            rv.setTextViewText(R.id.ie_time, row.timeText)
            rv.setTextViewText(R.id.ie_name, row.name)
            rv.setTextColor(R.id.ie_name, c(R.color.w_text))
            rv.setTextColor(R.id.ie_time, if (row.due) row.color.toInt() else c(R.color.w_text_sub))
            rv.setTextColor(R.id.ie_dot, if (row.due) row.color.toInt() else c(R.color.w_dot_empty))
            rv.setTextViewText(R.id.ie_dot, if (row.due) "●" else "○")

            val fill = Intent().apply {
                putExtra(WidgetActionReceiver.EXTRA_EVENT_ID, row.eventId)
            }
            rv.setOnClickFillInIntent(R.id.ie_root, fill)
            return rv
        }

        private fun doneRowView(row: WidgetRow.DoneRow): RemoteViews {
            val rv = RemoteViews(context.packageName, R.layout.widget_item_done)
            rv.setTextViewText(R.id.id_name, row.name)
            rv.setTextViewText(R.id.id_time, row.timeText)
            rv.setTextColor(R.id.id_check, row.color.toInt())
            // 删除线：RemoteViews 只能通过反射调用 setPaintFlags
            rv.setInt(
                R.id.id_name, "setPaintFlags",
                android.graphics.Paint.STRIKE_THRU_TEXT_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG
            )
            // 已完成行故意不做点击动作：这一页是回看，误触就撤销反而危险
            return rv
        }

        /** 兼容旧布局的栏目头（左右分栏后基本不再用到） */
        private fun headerView(row: WidgetRow.Header): RemoteViews {
            val rv = RemoteViews(context.packageName, R.layout.widget_item_header)
            rv.setTextViewText(R.id.ih_name, row.name)
            rv.setTextViewText(R.id.ih_count, "${row.totalCount} 项")
            rv.setTextColor(R.id.ih_name, c(R.color.w_text))
            rv.setTextColor(R.id.ih_count, c(R.color.w_text_sub))
            rv.setTextColor(R.id.ih_dot, row.color.toInt())
            return rv
        }

        override fun getViewTypeCount(): Int = 5

        override fun getItemId(position: Int): Long =
            rows.getOrNull(position)?.key?.hashCode()?.toLong() ?: position.toLong()

        override fun hasStableIds(): Boolean = false
    }
}
