package com.remind.widget.core

import com.remind.widget.data.Event
import com.remind.widget.data.EventType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 调度核心：一个事件"哪天该出现 / 下次什么时候 / 今天是否已到点"，全部集中在这里。
 *
 * 刻意保持成纯函数（不碰 Android API），这样能被单元测试直接覆盖，
 * 也保证原型 v0.2 里验证过的语义能一比一复现：
 *  - INTERVAL 是**固定锚点**：anchor 起第 N、2N、3N… 天各一次；
 *    提前做完不会把周期往前推（这是 YG 明确选的语义）。
 *  - WEEKLY 的 mask 用 bit0=周日 … bit6=周六，
 *    与 java.time 的 DayOfWeek.value（周一=1…周日=7）通过 `value % 7` 对齐。
 */
object Schedule {

    private val DUE_KEY_FMT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    /** 该事件在指定日期有没有一次计划触发；没有返回 null */
    fun triggerOn(event: Event, date: LocalDate): LocalDateTime? {
        if (!event.enabled) return null
        val at = date.atTime(event.hour, event.minute)
        return when (event.type) {
            EventType.ONCE ->
                if (event.onceDate == date.toString()) at else null

            EventType.DAILY -> at

            EventType.WEEKLY -> {
                val bit = date.dayOfWeek.value % 7
                if (event.weekdaysMask and (1 shl bit) != 0) at else null
            }

            EventType.INTERVAL -> {
                if (event.intervalDays <= 0) return null
                val anchor = runCatching { LocalDate.parse(event.anchorDate) }.getOrNull()
                    ?: return null
                val diff = ChronoUnit.DAYS.between(anchor, date)
                if (diff >= 0 && diff % event.intervalDays == 0L) at else null
            }
        }
    }

    /**
     * 下一次触发时刻（严格晚于 now）。
     * 一次性事件过点后返回 null —— 表示这个事件已经彻底结束。
     * 最多向前找 800 天（间隔型最长 365 天，留够余量）。
     */
    fun nextTrigger(event: Event, now: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        if (!event.enabled) return null

        if (event.type == EventType.ONCE) {
            val d = runCatching { LocalDate.parse(event.onceDate) }.getOrNull() ?: return null
            val t = d.atTime(event.hour, event.minute)
            return if (t.isAfter(now)) t else null
        }

        for (offset in 0..800) {
            val t = triggerOn(event, now.toLocalDate().plusDays(offset.toLong())) ?: continue
            if (t.isAfter(now)) return t
        }
        return null
    }

    /** 今天这一"次"是否已经到点（到点才允许在小组件里置顶高亮） */
    fun isDueNow(event: Event, now: LocalDateTime = LocalDateTime.now()): Boolean {
        val t = triggerOn(event, now.toLocalDate()) ?: return false
        return !t.isAfter(now)
    }

    /**
     * 这次触发对应的唯一键（yyyy-MM-dd HH:mm）。
     * 完成记录靠它去重：同一时刻点两次不会写两条。
     */
    fun dueKey(event: Event, date: LocalDate): String =
        date.atTime(event.hour, event.minute).format(DUE_KEY_FMT)

    /** 今天的 dueKey（事件今天不触发时返回 null） */
    fun todayDueKey(event: Event, now: LocalDateTime = LocalDateTime.now()): String? {
        val date = now.toLocalDate()
        if (triggerOn(event, date) == null) return null
        return dueKey(event, date)
    }

    /** 界面用的规则短文案：每天 / 每周一三五 / 每 3 天 / 仅一次 */
    fun ruleText(event: Event): String = when (event.type) {
        EventType.DAILY -> "每天"
        EventType.WEEKLY -> "每周" + weekdaysText(event.weekdaysMask)
        EventType.INTERVAL -> "每 ${event.intervalDays} 天"
        EventType.ONCE -> "仅一次"
    }

    fun weekdaysText(mask: Int): String {
        if (mask == 0) return "—"
        val names = arrayOf("日", "一", "二", "三", "四", "五", "六")
        val sb = StringBuilder()
        for (i in 0..6) if (mask and (1 shl i) != 0) sb.append(names[i])
        return sb.toString()
    }

    fun hhmm(event: Event): String =
        "%02d:%02d".format(event.hour, event.minute)

    /**
     * 事件相对"今天"的人类可读状态，用于编辑页与首页副标题。
     * 例：今天 13:00 ／ 明天 08:30 ／ 11/30 20:00 ／ 已结束
     */
    fun nextText(event: Event, now: LocalDateTime = LocalDateTime.now()): String {
        val next = nextTrigger(event, now) ?: return "已结束"
        val today = now.toLocalDate()
        val hm = "%02d:%02d".format(next.hour, next.minute)
        return when {
            next.toLocalDate() == today -> "今天 $hm"
            next.toLocalDate() == today.plusDays(1) -> "明天 $hm"
            else -> "%d/%d %s".format(next.monthValue, next.dayOfMonth, hm)
        }
    }
}
