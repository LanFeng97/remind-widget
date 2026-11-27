package com.remind.widget.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.remind.widget.data.Event
import com.remind.widget.data.EventType
import java.time.LocalDate

/**
 * 事件定时设置（底部抽屉）。
 * 四类重复方式与数据层 EventType 一一对应；
 * 时间/日期用系统原生选择器，避免 Compose 实验性组件在不同 ROM 上的排版差异。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditorSheet(
    groupId: Long,
    existing: Event?,
    sortOrder: Int,
    onDismiss: () -> Unit,
    onSave: (Event) -> Unit,
    onDelete: (Long) -> Unit
) {
    val ctx = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: EventType.DAILY) }
    var hour by remember { mutableIntStateOf(existing?.hour ?: 9) }
    var minute by remember { mutableIntStateOf(existing?.minute ?: 0) }
    var mask by remember { mutableIntStateOf(existing?.weekdaysMask ?: 0) }
    var days by remember { mutableIntStateOf(existing?.intervalDays?.coerceAtLeast(1) ?: 3) }
    var anchor by remember {
        mutableStateOf(existing?.anchorDate?.takeIf { it.isNotBlank() } ?: LocalDate.now().toString())
    }
    var onceDate by remember {
        mutableStateOf(existing?.onceDate?.takeIf { it.isNotBlank() } ?: LocalDate.now().toString())
    }

    fun pickTime() {
        TimePickerDialog(ctx, { _, h, m -> hour = h; minute = m }, hour, minute, true).show()
    }

    fun pickDate(current: String, onPick: (String) -> Unit) {
        val d = runCatching { LocalDate.parse(current) }.getOrDefault(LocalDate.now())
        DatePickerDialog(
            ctx,
            { _, y, mo, day -> onPick(LocalDate.of(y, mo + 1, day).toString()) },
            d.year, d.monthValue - 1, d.dayOfMonth
        ).show()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 30.dp)
        ) {
            Text(
                if (existing == null) "新增事件" else "编辑事件",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(16.dp))

            // ---------- 名称 ----------
            SectionLabel("事件名称")
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 13.dp)
            ) {
                if (name.isEmpty()) {
                    Text(
                        "例如：维生素D / 练胸",
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
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ---------- 重复方式 ----------
            Spacer(Modifier.height(16.dp))
            SectionLabel("重复方式")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TypeChip("仅一次", type == EventType.ONCE, Modifier.weight(1f)) { type = EventType.ONCE }
                TypeChip("每天", type == EventType.DAILY, Modifier.weight(1f)) { type = EventType.DAILY }
                TypeChip("每周", type == EventType.WEEKLY, Modifier.weight(1f)) { type = EventType.WEEKLY }
                TypeChip("间隔N天", type == EventType.INTERVAL, Modifier.weight(1f)) { type = EventType.INTERVAL }
            }

            // ---------- 时间 ----------
            Spacer(Modifier.height(16.dp))
            SectionLabel("触发时间")
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { pickTime() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "%02d:%02d".format(hour, minute),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // ---------- 类型相关 ----------
            when (type) {
                EventType.ONCE -> {
                    Spacer(Modifier.height(16.dp))
                    SectionLabel("触发日期")
                    ValueRow(onceDate) { pickDate(onceDate) { onceDate = it } }
                }

                EventType.WEEKLY -> {
                    Spacer(Modifier.height(16.dp))
                    SectionLabel("星期")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // 展示顺序：一 二 三 四 五 六 日（bit0 是周日）
                        listOf(1, 2, 3, 4, 5, 6, 0).forEach { w ->
                            val on = mask and (1 shl w) != 0
                            Box(
                                Modifier
                                    .size(38.dp, 34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (on) MaterialTheme.colorScheme.primary
                                        else Color.Transparent
                                    )
                                    .clickable { mask = if (on) mask and (1 shl w).inv() else mask or (1 shl w) }
                                    .padding(1.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    arrayOf("日", "一", "二", "三", "四", "五", "六")[w],
                                    fontSize = 12.5.sp,
                                    color = if (on) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                EventType.INTERVAL -> {
                    Spacer(Modifier.height(16.dp))
                    SectionLabel("间隔天数（从锚点日算起，逢 N、2N、3N… 天触发）")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(42.dp, 44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { days = (days - 1).coerceAtLeast(1) },
                            contentAlignment = Alignment.Center
                        ) { Text("−", fontSize = 18.sp) }
                        Text(
                            "$days 天",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(72.dp)
                        )
                        Box(
                            Modifier
                                .size(42.dp, 44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { days = (days + 1).coerceAtMost(365) },
                            contentAlignment = Alignment.Center
                        ) { Text("＋", fontSize = 18.sp) }
                    }
                    Spacer(Modifier.height(10.dp))
                    SectionLabel("锚点日")
                    ValueRow(anchor) { pickDate(anchor) { anchor = it } }
                }

                EventType.DAILY -> Unit
            }

            // ---------- 说明 ----------
            Spacer(Modifier.height(14.dp))
            Text(
                hintFor(type, hour, minute, days, anchor, mask),
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ---------- 底部按钮 ----------
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (existing != null) {
                    TextButton(onClick = { onDelete(existing.id) }) {
                        Text("删除事件", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable {
                            onSave(
                                Event(
                                    id = existing?.id ?: 0L,
                                    groupId = groupId,
                                    name = name.trim().ifBlank { "未命名事件" },
                                    type = type,
                                    hour = hour,
                                    minute = minute,
                                    weekdaysMask = if (mask == 0 && type == EventType.WEEKLY) {
                                        1 shl (LocalDate.now().dayOfWeek.value % 7)
                                    } else mask,
                                    intervalDays = days,
                                    anchorDate = anchor,
                                    onceDate = onceDate,
                                    enabled = true,
                                    sortOrder = existing?.sortOrder ?: sortOrder
                                )
                            )
                        }
                        .padding(horizontal = 22.dp, vertical = 11.dp)
                ) {
                    Text(
                        "保存",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 11.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 7.dp)
    )
}

@Composable
private fun TypeChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .height(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ValueRow(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        Text(text, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun hintFor(
    type: EventType,
    hour: Int,
    minute: Int,
    days: Int,
    anchor: String,
    mask: Int
): String = when (type) {
    EventType.ONCE -> "只在选定日期的 %02d:%02d 提醒一次，完成后该事件结束。".format(hour, minute)
    EventType.DAILY -> "每天 %02d:%02d 提醒；完成后今天的条目消失，明天照常出现。".format(hour, minute)
    EventType.WEEKLY -> {
        val names = arrayOf("日", "一", "二", "三", "四", "五", "六")
        val picked = (0..6).filter { mask and (1 shl it) != 0 }.joinToString("") { names[it] }
        if (picked.isEmpty()) "还没选星期，保存时会默认设为今天这一星期。"
        else "每周 $picked 的 %02d:%02d 提醒。".format(hour, minute)
    }
    EventType.INTERVAL ->
        "从锚点 $anchor 起，第 $days、${days * 2}、${days * 3}… 天各提醒一次。" +
            "这是固定锚点：提前做完不会把周期往前推。"
}
