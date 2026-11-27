package com.remind.widget.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.remind.widget.R
import com.remind.widget.data.WidgetRow
import com.remind.widget.receiver.WidgetActionReceiver
import kotlinx.coroutines.runBlocking

/**
 * 给两个 ListView 供数据的 Service。
 * onDataSetChanged() 在 Binder 线程执行，允许同步查库，所以这里用 runBlocking 是标准做法。
 */
class WidgetListService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        ListFactory(applicationContext, intent)

    companion object {
        const val EXTRA_WHICH = "which"
        const val WHICH_TODO = "todo"
        const val WHICH_DONE = "done"
    }

    private class ListFactory(
        private val context: Context,
        private val intent: Intent
    ) : RemoteViewsFactory {

        private var rows: List<WidgetRow> = emptyList()
        private val which: String = intent.getStringExtra(EXTRA_WHICH) ?: WHICH_TODO

        override fun onCreate() = Unit

        /** 列表加载中的占位视图，返回 null 表示不显示（数据来自本地库，没什么可等的） */
        override fun getLoadingView(): RemoteViews? = null

        override fun onDataSetChanged() {
            rows = runBlocking {
                val data = WidgetUpdater.compute(context)
                if (which == WHICH_DONE) data.doneRows else data.todoRows
            }
        }

        override fun onDestroy() {
            rows = emptyList()
        }

        override fun getCount(): Int = rows.size

        override fun getViewAt(position: Int): RemoteViews {
            val row = rows.getOrNull(position)
                ?: return RemoteViews(context.packageName, R.layout.widget_item_event)
            return when (row) {
                is WidgetRow.Header -> headerView(row)
                is WidgetRow.EventRow -> eventView(row)
                is WidgetRow.DoneHeader -> doneHeaderView(row)
                is WidgetRow.DoneRow -> doneRowView(row)
            }
        }

        /** 取颜色资源（深色模式由 values-night 自动换值） */
        private fun c(id: Int): Int = androidx.core.content.ContextCompat.getColor(context, id)

        private fun headerView(row: WidgetRow.Header): RemoteViews {
            val rv = RemoteViews(context.packageName, R.layout.widget_item_header)
            rv.setTextViewText(R.id.ih_name, row.name)
            rv.setTextViewText(
                R.id.ih_count,
                if (row.dueCount > 0) "${row.dueCount} 项到点" else "${row.totalCount} 项"
            )
            val due = row.dueCount > 0
            // 圆点用 ● 字符上色（setBackgroundColor 只能给方块，圆角在集合项里没有 tint 通道）
            rv.setTextColor(R.id.ih_dot, row.color.toInt())
            rv.setTextColor(
                R.id.ih_name,
                if (due) row.color.toInt() else c(R.color.w_text)
            )
            rv.setTextColor(
                R.id.ih_count,
                if (due) row.color.toInt() else c(R.color.w_text_sub)
            )
            // 点栏目名 → 打开 App 并定位到该栏目
            val fill = Intent().apply {
                putExtra(WidgetActionReceiver.EXTRA_GROUP_ID, row.groupId)
            }
            rv.setOnClickFillInIntent(R.id.ih_root, fill)
            return rv
        }

        private fun eventView(row: WidgetRow.EventRow): RemoteViews {
            val rv = RemoteViews(context.packageName, R.layout.widget_item_event)
            rv.setTextViewText(R.id.ie_time, row.timeText)
            rv.setTextViewText(R.id.ie_name, row.name)
            rv.setTextViewText(R.id.ie_rule, row.ruleText)
            rv.setTextColor(R.id.ie_name, c(R.color.w_text))
            rv.setTextColor(R.id.ie_time, if (row.due) row.color.toInt() else c(R.color.w_text_sub))
            rv.setTextColor(R.id.ie_dot, if (row.due) row.color.toInt() else c(R.color.w_dot_empty))
            rv.setTextViewText(R.id.ie_dot, if (row.due) "●" else "○")
            // 点击 → 划线样式由下一次刷新反映（立即记账，不做本地动画：RemoteViews 没有逐帧能力）
            val fill = Intent().apply {
                putExtra(WidgetActionReceiver.EXTRA_EVENT_ID, row.eventId)
            }
            rv.setOnClickFillInIntent(R.id.ie_root, fill)
            return rv
        }

        private fun doneHeaderView(row: WidgetRow.DoneHeader): RemoteViews {
            val rv = RemoteViews(context.packageName, R.layout.widget_item_header)
            rv.setTextViewText(R.id.ih_name, "今日已完成")
            rv.setTextViewText(R.id.ih_count, "${row.count} 项")
            rv.setTextColor(R.id.ih_name, c(R.color.w_text))
            rv.setTextColor(R.id.ih_count, c(R.color.w_text_sub))
            // 已完成页的标题不带栏目色点，避免和下面的 ✓ 行视觉打架
            rv.setViewVisibility(R.id.ih_dot, android.view.View.GONE)
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
            // 已完成行故意不做点击动作：这一页是"回看"，误触就撤销反而危险
            return rv
        }

        override fun getViewTypeCount(): Int = 4

        override fun getItemId(position: Int): Long =
            rows.getOrNull(position)?.key?.hashCode()?.toLong() ?: position.toLong()

        override fun hasStableIds(): Boolean = false
    }
}
