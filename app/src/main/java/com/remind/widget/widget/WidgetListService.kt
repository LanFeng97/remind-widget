package com.remind.widget.widget

import android.content.Context
import android.content.Intent
import android.util.TypedValue
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
 * · which=groups  → 左侧：栏目切换项
 * · which=content → 右侧：待办（可点完成 / 点⊘忽略）+ 已处理（灰色沉底）
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
                ?: return RemoteViews(context.packageName, R.layout.widget_item_event_action)
            return when (row) {
                is WidgetRow.GroupTab -> groupTabView(row)
                is WidgetRow.EventRow -> eventView(row)
                is WidgetRow.HandledRow -> handledRowView(row)
                // 旧类型（DoneRow / DoneHeader / Header）：左右分栏后不再产生，兜底成空行避免崩溃
                else -> RemoteViews(context.packageName, R.layout.widget_item_handled)
            }
        }

        private fun c(id: Int): Int = androidx.core.content.ContextCompat.getColor(context, id)

        // ---------------- 左侧：栏目切换 ----------------

        private fun groupTabView(row: WidgetRow.GroupTab): RemoteViews {
            val rv = RemoteViews(context.packageName, R.layout.widget_item_group)
            rv.setTextViewText(R.id.ig_name, row.name)

            if (row.selected) {
                // 选中：左侧竖条用栏目色 + 名称放大一点
                rv.setInt(R.id.ig_bar, "setBackgroundColor", row.color.toInt())
                rv.setTextColor(R.id.ig_name, row.color.toInt())
                rv.setTextViewTextSize(R.id.ig_name, TypedValue.COMPLEX_UNIT_SP, 12.5f)
            } else {
                rv.setInt(R.id.ig_bar, "setBackgroundColor", android.graphics.Color.TRANSPARENT)
                rv.setTextColor(R.id.ig_name, c(R.color.w_text_sub))
                rv.setTextViewTextSize(R.id.ig_name, TypedValue.COMPLEX_UNIT_SP, 12f)
            }

            // 该栏目有到点未处理的项，名字右端点一个小圆点
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

        // ---------------- 右侧：待办 ----------------

        private fun eventView(row: WidgetRow.EventRow): RemoteViews {
            val rv = RemoteViews(context.packageName, R.layout.widget_item_event_action)
            rv.setTextViewText(R.id.ie_time, row.timeText)
            rv.setTextViewText(R.id.ie_name, row.name)
            rv.setTextColor(R.id.ie_name, c(R.color.w_text))
            rv.setTextColor(R.id.ie_time, if (row.due) row.color.toInt() else c(R.color.w_text_sub))
            rv.setTextColor(R.id.ie_ignore, if (row.due) row.color.toInt() else c(R.color.w_dot_empty))

            // 整行 = 完成
            val completeFill = Intent().apply {
                putExtra(WidgetActionReceiver.EXTRA_EVENT_ID, row.eventId)
                putExtra(WidgetActionReceiver.EXTRA_OP, WidgetActionReceiver.OP_COMPLETE)
            }
            rv.setOnClickFillInIntent(R.id.ie_root, completeFill)

            // 右侧 ⊘ = 忽略（子 View 的 fillInIntent 优先，点这里不会触发整行的完成）
            val ignoreFill = Intent().apply {
                putExtra(WidgetActionReceiver.EXTRA_EVENT_ID, row.eventId)
                putExtra(WidgetActionReceiver.EXTRA_OP, WidgetActionReceiver.OP_IGNORE)
            }
            rv.setOnClickFillInIntent(R.id.ie_ignore, ignoreFill)
            return rv
        }

        // ---------------- 右侧：已处理（完成 / 忽略） ----------------

        private fun handledRowView(row: WidgetRow.HandledRow): RemoteViews {
            val rv = RemoteViews(context.packageName, R.layout.widget_item_handled)
            rv.setTextViewText(R.id.ih_mark, if (row.ignored) "⊘" else "✓")
            rv.setTextViewText(R.id.ih_time, row.timeText)
            rv.setTextViewText(R.id.ih_name, row.name)

            // 完成的划删除线；忽略的不划（因为这件事并没有做）。
            // ListView 会复用 item view，两个分支都要显式设置 paintFlags，否则样式会串。
            val flags = if (row.ignored) {
                android.graphics.Paint.ANTI_ALIAS_FLAG
            } else {
                android.graphics.Paint.STRIKE_THRU_TEXT_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG
            }
            rv.setInt(R.id.ih_name, "setPaintFlags", flags)

            // 已处理行不做点击动作：撤销统一在 App 里，避免桌面误触
            return rv
        }

        override fun getViewTypeCount(): Int = 3

        override fun getItemId(position: Int): Long =
            rows.getOrNull(position)?.key?.hashCode()?.toLong() ?: position.toLong()

        override fun hasStableIds(): Boolean = false
    }
}
