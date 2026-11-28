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
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 小组件 = 左右分栏。
 *
 *   左 76dp：栏目列表（点击切换），有到点未处理的栏目带同色小圆点
 *   右 剩余：选中栏目的待办在前，已完成 / 已忽略的沉在最后（灰色）
 *
 * 没有单独的「已完成」页 —— 处理过的事件就留在它所属栏目的列表底部，
 * 这样"今天这一栏还剩什么"是一眼可见的。
 *
 * 为什么不用横滑翻页：Android 15 起 RemoteViews 白名单移除了 HorizontalScrollView
 * （InflateException: Class not allowed to be inflated），而且桌面组件收不到 onTouch。
 * 两个 ListView 并排是允许的（并列，不是嵌套）。
 */
object WidgetUpdater {

    private const val TAG = "WidgetUpdater"
    private const val PREFS = "widget_state"
    private const val KEY_SELECTED_PREFIX = "sel_"
    private const val NOT_SET = 0L

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
        /** 当前栏目今天已处理（完成 + 忽略）的条数 */
        val handledCount: Int
    )

    suspend fun compute(context: Context, widgetId: Int): WidgetData {
        val repo = ReminderRepository.get(context)
        val groups = repo.groups()
        val events = repo.events()
        val done = repo.doneToday()
        val now = LocalDateTime.now()
        val today = now.toLocalDate()

        val blocks = DayPlan.build(groups, events, done, now)
        val dueTotal = blocks.sumOf { it.dueCount }
        val todoTotal = blocks.sumOf { it.pending.size }

        // 默认选中：第一个"有到点的栏目"，没有就第一个还有待办的，再没有就第一个栏目
        val defaultId = blocks.firstOrNull { it.dueCount > 0 }?.group?.id
            ?: blocks.firstOrNull { it.pending.isNotEmpty() }?.group?.id
            ?: blocks.firstOrNull()?.group?.id
            ?: 0L

        val saved = getSelectedGroup(context, widgetId)
        // 选中的栏目可能已被删除（旧版本还可能存过 -1 这种"已完成页"的值），兜底回默认
        val effectiveId = if (blocks.any { it.group.id == saved }) saved else defaultId

        // 今天已处理的记录，按栏目分组（完成 + 忽略都在这里）
        val handledByGroup = done
            .filter { it.dayKey == today.toString() }
            .groupBy { it.groupId }

        // ---- 左侧：只有栏目 ----
        val groupRows = blocks.map { b ->
            WidgetRow.GroupTab(
                groupId = b.group.id,
                name = b.group.name,
                color = b.group.color,
                dueCount = b.dueCount,
                selected = b.group.id == effectiveId,
                pendingCount = b.pending.size
            )
        }

        // ---- 右侧：待办 + 已处理 ----
        val contentRows = ArrayList<WidgetRow>(16)
        var selectedName = ""
        val block = blocks.firstOrNull { it.group.id == effectiveId }
        if (block != null) {
            selectedName = block.group.name

            // 待办：按今天的计划时刻，点一下=完成，点右侧⊘=忽略
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

            // 已处理：沉到最下面，按计划时刻排
            handledByGroup[effectiveId].orEmpty()
                .sortedBy { it.dueKey }
                .forEach { r ->
                    contentRows += WidgetRow.HandledRow(
                        recordId = r.id,
                        name = r.eventName,
                        timeText = r.dueKey.substringAfter(' ').take(5),
                        color = r.color,
                        ignored = r.ignored
                    )
                }
        }

        val handledCount = handledByGroup[effectiveId]?.size ?: 0
        return WidgetData(groupRows, contentRows, dueTotal, todoTotal, selectedName, handledCount)
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

        // 各品牌桌面的组件宽度差异很大（小屏 4 列可能只有 280dp，大屏能到 380dp），
        // 左侧栏目宽度按比例算，避免窄屏挤成两字换行 / 宽屏留白过多（API 31+ 才支持动态设宽）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val opts = mgr.getAppWidgetOptions(widgetId)
            val widthDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            if (widthDp > 0) {
                val leftDp = (widthDp * 0.24f).toInt().coerceIn(64, 96)
                views.setViewLayoutWidth(
                    R.id.w_group_list,
                    leftDp.toFloat(),
                    android.util.TypedValue.COMPLEX_UNIT_DIP
                )
            }
        }

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

        // 左侧 = 切换栏目；右侧 = 完成/忽略（靠 extras 里的 op 区分，template 只能有一个 action）
        views.setPendingIntentTemplate(
            R.id.w_group_list,
            template(context, WidgetActionReceiver.ACTION_SELECT_GROUP)
        )
        views.setPendingIntentTemplate(
            R.id.w_content_list,
            template(context, WidgetActionReceiver.ACTION_ITEM_CLICK)
        )
        views.setEmptyView(R.id.w_content_list, R.id.w_content_empty)

        // 顶部整条点击进 App
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
            val headerText = buildString {
                if (widgetData.dueTotal > 0) {
                    append("${widgetData.dueTotal} 项到点")
                } else {
                    append("今日待办 ${widgetData.todoTotal}")
                }
                if (widgetData.selectedName.isNotEmpty()) append(" · ${widgetData.selectedName}")
                if (widgetData.handledCount > 0) append("  ✓${widgetData.handledCount}")
            }
            views.setTextViewText(R.id.w_header_text, headerText)
            val idleColor = androidx.core.content.ContextCompat.getColor(context, R.color.w_header)
            views.setInt(
                R.id.w_header_text, "setTextColor",
                if (widgetData.dueTotal > 0) 0xFFFF6A00.toInt() else idleColor
            )
            views.setTextViewText(R.id.w_content_empty, "这一栏今天都处理完了 🎉")
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
