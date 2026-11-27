package com.remind.widget.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remind.widget.core.Schedule
import com.remind.widget.data.DoneRecord
import com.remind.widget.data.Event
import com.remind.widget.data.EventType
import com.remind.widget.data.Group
import java.time.LocalDate

/** 栏目编辑页：改名字/颜色、增删事件、开关到点行为 */
@Composable
fun EditScreen(
    vm: AppViewModel,
    group: Group,
    groupEvents: List<Event>,
    doneToday: List<DoneRecord>,
    onBack: () -> Unit,
    onDeleteGroup: () -> Unit
) {
    val now = rememberNow()
    var name by remember(group.id) { mutableStateOf(group.name) }
    var color by remember(group.id) { mutableStateOf(group.color) }
    var pin by remember(group.id) { mutableStateOf(group.pinWhenDue) }
    var notify by remember(group.id) { mutableStateOf(group.notify) }
    var sheetEvent by remember { mutableStateOf<Event?>(null) }
    var showSheet by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val groupDone = doneToday.filter { it.groupId == group.id }

    fun persist() {
        vm.updateGroup(
            group.copy(name = name.trim().ifBlank { "未命名" }, color = color, pinWhenDue = pin, notify = notify)
        )
    }

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
                .height(50.dp)
                .padding(start = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                "编辑「$name」",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { persist(); onBack() }) {
                Text("完成", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            // ---------- 名称 / 颜色 ----------
            item {
                CardSurface {
                    Text("栏目名称", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(14.dp))
                    Text("标识色", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(7.dp))
                    ColorSwatchRow(selected = color) { color = it; persist() }
                }
            }

            // ---------- 事件列表 ----------
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "栏目内的事件",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { sheetEvent = null; showSheet = true }) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(15.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("添加", fontSize = 12.5.sp)
                    }
                }
            }

            items(groupEvents.size, key = { groupEvents[it].id }) { index ->
                val ev = groupEvents[index]
                EventRowCard(
                    event = ev,
                    now = now,
                    color = Color(group.color),
                    onEdit = { sheetEvent = ev; showSheet = true },
                    onDelete = { vm.deleteEvent(ev.id) }
                )
            }

            item {
                AddDashedButton("＋ 添加一个事件") { sheetEvent = null; showSheet = true }
            }

            // ---------- 到点行为 ----------
            item {
                Text(
                    "到点行为",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
            }
            item {
                CardSurface {
                    ToggleRow(
                        title = "推到桌面小组件最上层",
                        subtitle = "到时间后该栏目自动置顶并高亮",
                        checked = pin,
                        onCheckedChange = { pin = it; persist() }
                    )
                    HorizontalDivider(
                        Modifier.padding(vertical = 6.dp),
                        color = MaterialTheme.colorScheme.outline
                    )
                    ToggleRow(
                        title = "同时发系统通知",
                        subtitle = "HyperOS 需在系统设置里允许通知与横幅",
                        checked = notify,
                        onCheckedChange = { notify = it; persist() }
                    )
                }
            }

            // ---------- 今日已完成 ----------
            if (groupDone.isNotEmpty()) {
                item {
                    Text(
                        "今日已完成 ${groupDone.size} 项",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }
                item {
                    CardSurface {
                        groupDone.forEach { rec ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("✓", fontSize = 12.sp, color = Color(rec.color))
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    rec.eventName,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { vm.undo(rec.id) }) {
                                    Text("撤销", fontSize = 11.5.sp)
                                }
                            }
                        }
                    }
                }
            }

            // ---------- 删除栏目 ----------
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .clickable { confirmDelete = true }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "删除这个栏目",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除栏目") },
            text = { Text("「$name」以及它下面的 ${groupEvents.size} 个事件会一起删除，今日完成记录保留。") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDeleteGroup() }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            }
        )
    }

    if (showSheet) {
        EventEditorSheet(
            groupId = group.id,
            existing = sheetEvent,
            sortOrder = groupEvents.size,
            onDismiss = { showSheet = false },
            onSave = { ev ->
                if (sheetEvent == null) vm.createEvent(ev) else vm.updateEvent(ev)
                showSheet = false
            },
            onDelete = { id -> vm.deleteEvent(id); showSheet = false }
        )
    }
}

/** 事件卡片：点一下进编辑，右侧删除 */
@Composable
private fun EventRowCard(
    event: Event,
    now: java.time.LocalDateTime,
    color: Color,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val todayOff = Schedule.triggerOn(event, now.toLocalDate()) == null
    CardSurface(modifier = Modifier.clickable(onClick = onEdit)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    event.name.ifBlank { "未命名事件" },
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(
                        Schedule.hhmm(event),
                        tint = color,
                        background = color.copy(alpha = 0.13f)
                    )
                    Spacer(Modifier.width(6.dp))
                    Pill(Schedule.ruleText(event))
                    Spacer(Modifier.width(6.dp))
                    if (todayOff) {
                        Pill("今天不触发")
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "下次 ${Schedule.nextText(event, now)}",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "删除事件",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}
