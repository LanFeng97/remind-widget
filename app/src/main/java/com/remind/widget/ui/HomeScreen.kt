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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remind.widget.core.DayPlan
import com.remind.widget.core.Schedule
import com.remind.widget.data.DoneRecord
import com.remind.widget.data.Event
import com.remind.widget.data.Group
import com.remind.widget.ui.theme.AccentOrange
import com.remind.widget.ui.theme.GroupPalette
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/**
 * 首页：栏目卡片列表。
 * 交互与桌面小组件保持一致 —— 点事件行即完成，点卡片其它区域进编辑。
 */
@Composable
fun HomeScreen(
    groups: List<Group>,
    events: List<Event>,
    doneToday: List<DoneRecord>,
    hasUpdate: Boolean,
    onOpenGroup: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDoneHistory: () -> Unit,
    onCreateGroup: (String, Long, () -> Unit) -> Unit,
    onComplete: (Long) -> Unit,
    onIgnore: (Long) -> Unit,
    onUndo: (Long) -> Unit
) {
    val now = rememberNow()
    val blocks = remember(groups, events, doneToday, now) {
        DayPlan.build(groups, events, doneToday, now)
    }
    val pendingTotal = blocks.sumOf { it.pending.size }
    val dueTotal = blocks.sumOf { it.dueCount }

    var showNewDialog by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {

            // ---------- 标题 ----------
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 10.dp, top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    // 标题 + 已完成列表入口（挨着标题放，不进设置）
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "提醒",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .clickable(onClick = onOpenDoneHistory)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "已完成列表",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        buildString {
                            val today = java.time.LocalDate.now()
                            val wd = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
                            append("${today.monthValue}月${today.dayOfMonth}日 ")
                            append(wd[today.dayOfWeek.value - 1])
                            append(" · ${groups.size} 个栏目 · $pendingTotal 条待办")
                            if (dueTotal > 0) append(" · $dueTotal 项到点")
                        },
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 齿轮 + 更新红点：发现新版本时在图标右上角点一个小圆点
                Box {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = if (hasUpdate) "设置（有新版本）" else "设置",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (hasUpdate) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 7.dp, end = 9.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(AccentOrange)
                        )
                    }
                }
            }

            if (groups.isEmpty()) {
                EmptyHint("还没有栏目，点右下角 + 新建一个")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    items(blocks, key = { it.group.id }) { block ->
                        GroupCard(
                            modifier = Modifier.animateItem(),
                            block = block,
                            allEvents = events.filter { it.groupId == block.group.id },
                            doneToday = doneToday.filter { it.groupId == block.group.id },
                            now = now,
                            onClick = { onOpenGroup(block.group.id) },
                            onComplete = onComplete,
                            onIgnore = onIgnore,
                            onUndo = onUndo
                        )
                    }
                }
            }
        }

        // ---------- 新建栏目 ----------
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 22.dp)
                .size(56.dp)
                .clip(CircleShape)
                .background(AccentOrange)
                .clickable { showNewDialog = true },
            contentAlignment = Alignment.Center
        ) {
            Text("+", fontSize = 28.sp, color = Color.White, fontWeight = FontWeight.Light)
        }
    }

    if (showNewDialog) {
        NewGroupDialog(
            onDismiss = { showNewDialog = false },
            onConfirm = { name, color ->
                showNewDialog = false
                onCreateGroup(name, color) { }
            }
        )
    }
}

@Composable
private fun GroupCard(
    modifier: Modifier = Modifier,
    block: DayPlan.Block,
    allEvents: List<Event>,
    doneToday: List<DoneRecord>,
    now: LocalDateTime,
    onClick: () -> Unit,
    onComplete: (Long) -> Unit,
    onIgnore: (Long) -> Unit,
    onUndo: (Long) -> Unit
) {
    val group = block.group
    val color = Color(group.color)
    val due = block.dueCount > 0
    val idleCount = allEvents.count { Schedule.triggerOn(it, now.toLocalDate()) == null }
    val handled = doneToday.sortedBy { it.dueKey }

    CardSurface(
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ColorDot(color, 8)
            Spacer(Modifier.width(9.dp))
            Text(
                group.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.weight(1f))
            if (due) {
                Pill("${block.dueCount} 项到点", tint = color, background = color.copy(alpha = 0.13f))
            } else {
                Pill(
                    when {
                        block.pending.isNotEmpty() -> "${block.pending.size} 个待办"
                        handled.isNotEmpty() -> "今天已完成"
                        else -> "今天无安排"
                    }
                )
            }
        }

        // 待办：点整行=完成，点右侧 ⊘=忽略
        if (block.pending.isNotEmpty()) {
            Spacer(Modifier.height(9.dp))
            block.pending.take(5).forEach { ev ->
                EventLine(
                    event = ev,
                    now = now,
                    dotColor = color,
                    onComplete = { onComplete(ev.id) },
                    onIgnore = { onIgnore(ev.id) }
                )
            }
            if (block.pending.size > 5) {
                Spacer(Modifier.height(5.dp))
                Text(
                    "还有 ${block.pending.size - 5} 个事件",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 已处理（完成 / 忽略）沉在卡片底部，灰色 + 删除线 + 撤销
        if (handled.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(4.dp))
            handled.forEach { rec ->
                HandledLine(record = rec, onUndo = { onUndo(rec.id) })
            }
        }

        if (idleCount > 0) {
            Spacer(Modifier.height(7.dp))
            Text(
                buildString {
                    append("另有 $idleCount 个事件今天不触发")
                    allEvents.filter { Schedule.triggerOn(it, now.toLocalDate()) == null }
                        .take(2)
                        .forEach { append(" · ${it.name} ${Schedule.nextText(it, now)}") }
                },
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

/**
 * 一行待办：点整行 = 完成，点右侧 ⊘ = 忽略。
 * 不再做"划线 620ms 后消失"——完成后会原地变成下面的 [HandledLine]，看得见、可撤销。
 */
@Composable
private fun EventLine(
    event: Event,
    now: LocalDateTime,
    dotColor: Color,
    onComplete: () -> Unit,
    onIgnore: () -> Unit
) {
    val due = Schedule.isDueNow(event, now)

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onComplete)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            Schedule.hhmm(event),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (due) dotColor else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(40.dp)
        )
        Text(
            event.name.ifBlank { "未命名事件" },
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            Schedule.ruleText(event),
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // ⊘ 忽略：内层 clickable 会吃掉点击，不会误触发整行的"完成"
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .clickable(onClick = onIgnore),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "⊘",
                fontSize = 13.sp,
                color = if (due) dotColor
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

/** 一行已处理（完成 / 忽略）：灰色，完成划删除线，右侧 ↺ 可撤销 */
@Composable
private fun HandledLine(
    record: DoneRecord,
    onUndo: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (record.ignored) "⊘" else "✓",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (record.ignored) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            else Color(record.color)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            record.dueKey.substringAfter(' ').take(5),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.width(38.dp)
        )
        Text(
            record.eventName,
            fontSize = 13.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textDecoration = if (record.ignored) null else TextDecoration.LineThrough,
            modifier = Modifier.weight(1f)
        )
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

@Composable
private fun NewGroupDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Long) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(GroupPalette.first().toArgbLong()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建栏目", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    if (name.isEmpty()) {
                        Text(
                            "栏目名，例如「吃药」",
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
                Spacer(Modifier.height(14.dp))
                ColorSwatchRow(selected = color) { color = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim().ifBlank { "新栏目" }, color) },
                enabled = name.isNotBlank()
            ) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** Color → ARGB Long（数据层统一用 Long 存颜色） */
fun Color.toArgbLong(): Long = this.toArgb().toLong() and 0xFFFFFFFFL
