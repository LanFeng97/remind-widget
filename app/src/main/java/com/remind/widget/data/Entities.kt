package com.remind.widget.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 栏目：一组同类提醒（吃药、健身、喝水…）
 * 桌面小组件里每一个"层"对应一个 Group。
 */
@Entity(tableName = "groups")
data class Group(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** ARGB 颜色，Long 存避免 Room 对 Int 颜色的类型歧义 */
    val color: Long = 0xFFFF6A00,
    val sortOrder: Int = 0,
    /** 有事件到点时，该栏目在小组件里置顶并高亮 */
    val pinWhenDue: Boolean = true,
    /** 到点时是否同时发系统通知 */
    val notify: Boolean = true
)

/** 事件类型：一次性 / 每天 / 每周指定星期 / 间隔 N 天（固定锚点） */
enum class EventType { ONCE, DAILY, WEEKLY, INTERVAL }

/**
 * 事件：栏目下的一条具体提醒（维生素D、练胸…）
 *
 * 调度语义（与原型 v0.2 完全一致）：
 *  - ONCE     : 仅在 onceDate 的 hour:minute 触发一次
 *  - DAILY    : 每天 hour:minute
 *  - WEEKLY   : weekdaysMask 中选中的星期，hour:minute（bit0=周日 … bit6=周六）
 *  - INTERVAL : 从 anchorDate 起，第 N、2N、3N… 天触发（固定锚点，提前完成不顺延）
 */
@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = Group::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("groupId")]
)
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val name: String,
    val type: EventType,
    val hour: Int = 9,
    val minute: Int = 0,
    /** WEEKLY 用：bit0=周日, bit1=周一 … bit6=周六 */
    val weekdaysMask: Int = 0,
    /** INTERVAL 用：间隔天数，>=1 */
    val intervalDays: Int = 1,
    /** INTERVAL 用：锚点日 yyyy-MM-dd */
    val anchorDate: String = "",
    /** ONCE 用：触发日 yyyy-MM-dd */
    val onceDate: String = "",
    val enabled: Boolean = true,
    val sortOrder: Int = 0
)

/**
 * 完成记录：点一下小组件里的事件就往这里写一条。
 * eventName / groupName / color 是快照，事件被删掉后历史仍然可读。
 */
@Entity(tableName = "done_records", indices = [Index("dayKey"), Index("eventId")])
data class DoneRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: Long,
    val groupId: Long,
    val eventName: String,
    val groupName: String,
    val color: Long,
    /** 完成瞬间的时间戳 */
    val doneAt: Long,
    /** 这一次完成对应的是哪个计划触发时刻（yyyy-MM-dd HH:mm），用于判定"今天这次是否已做" */
    val dueKey: String,
    /** yyyy-MM-dd，方便按天查询 */
    val dayKey: String
)

/** 小组件一层里的一行：可能是"栏目头"，也可能是"事件行" */
sealed class WidgetRow {
    abstract val key: String

    data class Header(
        val groupId: Long,
        val name: String,
        val color: Long,
        val dueCount: Int,
        val totalCount: Int
    ) : WidgetRow() {
        override val key = "h-$groupId"
    }

    data class EventRow(
        val eventId: Long,
        val groupId: Long,
        val name: String,
        val timeText: String,
        val ruleText: String,
        val color: Long,
        val due: Boolean
    ) : WidgetRow() {
        override val key = "e-$eventId"
    }

    data class DoneRow(
        val recordId: Long,
        val name: String,
        val timeText: String,
        val color: Long
    ) : WidgetRow() {
        override val key = "d-$recordId"
    }

    /** 已完成页的标题行 */
    data class DoneHeader(val count: Int) : WidgetRow() {
        override val key = "dh"
    }

    /**
     * 左侧栏目的切换项（左右分栏布局）。
     * selected 由 SharedPreferences 里每个 widget 的选中栏目决定。
     */
    data class GroupTab(
        val groupId: Long,
        val name: String,
        val color: Long,
        val dueCount: Int,
        val selected: Boolean
    ) : WidgetRow() {
        override val key = "gt-$groupId"
    }

    /** 左侧的「已完成」入口，永远排在栏目下面 */
    data class DoneTab(
        val count: Int,
        val selected: Boolean
    ) : WidgetRow() {
        override val key = "gt-done"
    }
}
