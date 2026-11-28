package com.remind.widget.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remind.widget.data.DoneRecord
import com.remind.widget.data.Group
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 已完成列表的三种排序 */
enum class DoneSort(val label: String) {
    BY_REMINDER("提醒时间"),
    BY_GROUP("栏目"),
    BY_DONE_TIME("完成时间")
}

/**
 * 已完成列表（一级页面，从首页标题旁的小按钮进入）。
 *
 * 三种排序：
 *  · 提醒时间 —— 全部混在一起，按"原本该在几点提醒"排，并按日期分组
 *  · 栏目     —— 按栏目分组（喝水 / 吃药 …），组内按完成时间排
 *  · 完成时间 —— 全部混在一起，按实际点掉的时刻倒序
 *
 * 「包括忽略事件」默认开：忽略也是当天做过的一次决定，默认让它可见更有信息量。
 */
@Composable
fun DoneHistoryScreen(
    groups: List<Group>,
    records: List<DoneRecord>,
    onBack: () -> Unit,
    onUndo: (Long) -> Unit
) {
    var sort by remember { mutableStateOf(DoneSort.BY_REMINDER) }
    var includeIgnored by remember { mutableStateOf(true) }

    val shown = remember(records, includeIgnored) {
        if (includeIgnored) records else records.filter { !it.ignored }
    }
    val groupNameOf = remember(groups) { groups.associate { it.id to it.name } }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // ---------- 顶栏 ----------
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 18.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Text("←", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.width(4.dp))
            Column {
                Text(
                    "已完成列表",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "共 ${shown.size} 条" + if (!includeIgnored) "（不含忽略）" else "",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ---------- 排序切换 ----------
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DoneSort.entries.forEach { option ->
                val active = option == sort
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        .clickable { sort = option }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        option.label,
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ---------- 包括忽略 ----------
        Row(
            Modifier
                .fillMaxWidth()
                // 整行可点：只让 Switch 本身能点的话，点文字没反应会让人以为开关坏了
                .clickable { includeIgnored = !includeIgnored }
                .padding(start = 18.dp, end = 14.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "包括忽略事件",
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.weight(1f))
            Switch(checked = includeIgnored, onCheckedChange = { includeIgnored = it })
        }

        // ---------- 清单 ----------
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (records.isEmpty()) "还没有任何记录" else "没有符合条件的事件",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (sort) {
                    DoneSort.BY_REMINDER -> {
                        // 按日分组，组内按提醒时间（dueKey 的字典序就是时间序）
                        shown.groupBy { it.dayKey }
                            .toSortedMap(compareByDescending { it })
                            .forEach { (day, list) ->
                                item(key = "h-$day") { DayHeader(dayLabel(day), list.size) }
                                items(list.sortedBy { it.dueKey }, key = { "r-${it.id}" }) { rec ->
                                    DoneRowCard(
                                        record = rec,
                                        groupName = groupNameOf[rec.groupId].orEmpty(),
                                        sort = sort,
                                        onUndo = { onUndo(rec.id) }
                                    )
                                }
                            }
                    }

                    DoneSort.BY_GROUP -> {
                        // 按栏目分组，组内按完成时间
                        shown.groupBy { it.groupId }
                            .entries
                            .sortedBy { e -> groupNameOf[e.key].orEmpty() }
                            .forEach { (gid, list) ->
                                item(key = "g-$gid") {
                                    GroupHeader(
                                        name = groupNameOf[gid] ?: "已删除的栏目",
                                        color = Color(list.first().color),
                                        count = list.size
                                    )
                                }
                                items(list.sortedBy { it.doneAt }, key = { "gr-${it.id}" }) { rec ->
                                    DoneRowCard(
                                        record = rec,
                                        groupName = "",
                                        sort = sort,
                                        onUndo = { onUndo(rec.id) }
                                    )
                                }
                            }
                    }

                    DoneSort.BY_DONE_TIME -> {
                        // 按完成日期分组，组内按完成时刻倒序
                        shown.groupBy {
                            Instant.ofEpochMilli(it.doneAt).atZone(ZoneId.systemDefault()).toLocalDate().toString()
                        }
                            .toSortedMap(compareByDescending { it })
                            .forEach { (day, list) ->
                                item(key = "d-$day") { DayHeader(dayLabel(day), list.size) }
                                items(list.sortedByDescending { it.doneAt }, key = { "t-${it.id}" }) { rec ->
                                    DoneRowCard(
                                        record = rec,
                                        groupName = groupNameOf[rec.groupId].orEmpty(),
                                        sort = sort,
                                        onUndo = { onUndo(rec.id) }
                                    )
                                }
                            }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(label: String, count: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "$count 条",
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun GroupHeader(name: String, color: Color, count: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            name,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "$count 条",
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

/**
 * 一条记录：
 *   [✓/⊘] [提醒 13:00] [事件名] [栏目] [完成 17:45] [↺]
 * 所有行都带"被完成/被忽略的时刻"。
 */
@Composable
private fun DoneRowCard(
    record: DoneRecord,
    groupName: String,
    sort: DoneSort,
    onUndo: () -> Unit
) {
    CardSurface(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (record.ignored) "⊘" else "✓",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (record.ignored) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                else Color(record.color)
            )
            Spacer(Modifier.width(8.dp))

            // 提醒时间
            Text(
                record.dueKey.substringAfter(' ').take(5),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(38.dp)
            )

            // 事件名（完成划删除线，忽略不划）
            Text(
                record.eventName,
                fontSize = 13.5.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textDecoration = if (record.ignored) null else TextDecoration.LineThrough,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // 栏目（按栏目排序时表头已经写了，行内就不重复）
            if (groupName.isNotEmpty() && sort != DoneSort.BY_GROUP) {
                Text(
                    groupName,
                    fontSize = 10.5.sp,
                    color = Color(record.color),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(7.dp))
            }

            // 完成 / 忽略时刻
            Text(
                hhmm(record.doneAt),
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
            )
            Spacer(Modifier.width(2.dp))

            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onUndo),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "↺",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/** 时间戳 → HH:mm */
private fun hhmm(epochMs: Long): String {
    val t = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalTime()
    return "%02d:%02d".format(t.hour, t.minute)
}

/** yyyy-MM-dd → 今天 / 昨天 / M月d日 */
private fun dayLabel(dayKey: String): String {
    val date = runCatching { LocalDate.parse(dayKey) }.getOrNull() ?: return dayKey
    val today = LocalDate.now()
    return when (date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> "${date.monthValue}月${date.dayOfMonth}日"
    }
}
