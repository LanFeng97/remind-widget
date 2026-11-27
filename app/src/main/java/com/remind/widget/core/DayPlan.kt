package com.remind.widget.core

import com.remind.widget.data.DoneRecord
import com.remind.widget.data.Event
import com.remind.widget.data.Group
import java.time.LocalDateTime

/**
 * "今天该怎么办"的统一计算层 —— App 首页和桌面小组件都用它。
 *
 * 抽出来的原因：排序规则（到点优先 → 未完成优先 → 按下次触发）只要有一处实现漂移，
 * 用户就会看到"App 里顺序和桌面上不一样"，这种 bug 极难自证。
 */
object DayPlan {

    data class Block(
        val group: Group,
        /** 今天该做、且今天这次还没做掉的，已按时间排序 */
        val pending: List<Event>,
        /** 已到点的条数（栏目关闭"到点置顶"时为 0） */
        val dueCount: Int,
        /** 今天有事件但全部做完了 */
        val allDone: Boolean,
        /** 最近一次到期时刻，用于稳定排序 */
        val minNext: LocalDateTime
    ) {
        val totalCount: Int get() = pending.size
    }

    fun build(
        groups: List<Group>,
        events: List<Event>,
        doneToday: List<DoneRecord>,
        now: LocalDateTime = LocalDateTime.now()
    ): List<Block> {
        val today = now.toLocalDate()
        val doneKeys = doneToday.map { it.dueKey }.toHashSet()
        val byGroup = events.groupBy { it.groupId }

        return groups.map { g ->
            val evs = byGroup[g.id].orEmpty()
            val pending = evs
                .filter { ev ->
                    Schedule.triggerOn(ev, today) != null &&
                        !doneKeys.contains(Schedule.dueKey(ev, today))
                }
                .sortedWith(compareBy({ it.hour * 60 + it.minute }, { it.sortOrder }))

            val dueCount = pending.count { Schedule.isDueNow(it, now) }
            // 用"今天的计划时刻"排序，而不是 nextTrigger：
            // nextTrigger 对已过点的条目会返回明天，导致 8:00 漏做的反而排到 19:00 的正常项后面。
            val minSlot = pending
                .mapNotNull { Schedule.triggerOn(it, today) }
                .minOrNull() ?: LocalDateTime.MAX

            Block(
                group = g,
                pending = pending,
                dueCount = if (g.pinWhenDue) dueCount else 0,
                allDone = evs.isNotEmpty() && pending.isEmpty(),
                minNext = minSlot
            )
        }.sortedWith(
            compareByDescending<Block> { it.dueCount > 0 }
                .thenBy { it.allDone }
                .thenBy { it.minNext }
        )
    }
}
