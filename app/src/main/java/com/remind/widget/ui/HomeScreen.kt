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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
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
    onOpenGroup: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onCreateGroup: (String, Long, () -> Unit) -> Unit,
    onComplete: (Long) -> Unit
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
                    Text(
                        "提醒",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
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
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = "设置",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                            now = now,
                            onClick = { onOpenGroup(block.group.id) },
                            onComplete = onComplete
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
    now: LocalDateTime,
    onClick: () -> Unit,
    onComplete: (Long) -> Unit
) {
    val group = block.group
    val color = Color(group.color)
    val due = block.dueCount > 0
    val idleCount = allEvents.count { Schedule.triggerOn(it, now.toLocalDate()) == null }

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
                        block.allDone -> "今天已完成"
                        else -> "今天无安排"
                    }
                )
            }
        }

        if (block.pending.isNotEmpty()) {
            Spacer(Modifier.height(9.dp))
            block.pending.take(3).forEach { ev ->
                EventLine(
                    event = ev,
                    now = now,
                    dotColor = color,
                    onComplete = { onComplete(ev.id) }
                )
            }
            if (block.pending.size > 3) {
                Spacer(Modifier.height(5.dp))
                Text(
                    "还有 ${block.pending.size - 3} 个事件",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (block.allDone) {
            Spacer(Modifier.height(7.dp))
            Text(
                "今天已全部完成",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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

/** 一行事件：点击 → 划线 620ms → 记账消失（与原型里定下来的节奏一致） */
@Composable
private fun EventLine(
    event: Event,
    now: LocalDateTime,
    dotColor: Color,
    onComplete: () -> Unit
) {
    var ticking by remember(event.id) { mutableStateOf(false) }
    val due = Schedule.isDueNow(event, now)

    if (ticking) {
        LaunchedEffect(Unit) {
            delay(620)
            onComplete()
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .clickable(enabled = !ticking) { ticking = true }
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
            color = if (ticking) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (ticking) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f)
        )
        Text(
            Schedule.ruleText(event),
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (ticking) "✓" else "○",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (ticking) dotColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        )
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
