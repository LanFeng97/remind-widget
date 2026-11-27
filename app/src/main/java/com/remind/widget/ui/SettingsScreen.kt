package com.remind.widget.ui

import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.remind.widget.BuildConfig
import com.remind.widget.core.Notifier
import com.remind.widget.core.UpdateChecker
import com.remind.widget.data.DoneRecord
import com.remind.widget.data.Group
import com.remind.widget.widget.ReminderWidgetProvider

/** 权限项的三态：能自动检测的给 OK/NEED，测不到的标"需手动确认"，不误导用户 */
private enum class PermStatus { OK, NEED, UNKNOWN }

/**
 * 设置 / 权限体检 / 版本与更新。
 *
 * 这里刻意区分了「能检测」和「测不到」：
 *  · 通知、精确闹钟、省电白名单都能通过 API 真实读取
 *  · 小米的"自启动"没有任何公开接口，第三方 App 读不到，
 *    所以那一项永远显示"需手动确认"而不是"未开启"——之前硬编码 false 会让人以为开了没用。
 */
@Composable
fun SettingsScreen(
    vm: AppViewModel,
    groups: List<Group>,
    doneToday: List<DoneRecord>,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    val updateState by vm.updateState.collectAsStateWithLifecycle()

    val notifyOn = remember(tick) { Notifier.canPost(ctx) }
    val exactOn = remember(tick) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
            }.getOrDefault(false)
        } else true
    }
    val batteryFree = remember(tick) {
        runCatching {
            ctx.getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(ctx.packageName)
        }.getOrDefault(false)
    }
    val canInstall = remember(tick) { UpdateChecker.hasInstallPermission(ctx) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().height(50.dp).padding(start = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text("设置", style = MaterialTheme.typography.titleLarge)
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            // ================= 版本与更新 =================
            item {
                CardSurface {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("提醒", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Pill(
                            text = when (updateState) {
                                is UpdateState.Available -> "有新版本"
                                is UpdateState.Failed -> "检查失败"
                                else -> "GitHub"
                            },
                            tint = if (updateState is UpdateState.Available) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            background = if (updateState is UpdateState.Available)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    }

                    Spacer(Modifier.height(11.dp))

                    when (val s = updateState) {
                        is UpdateState.Idle -> {
                            TipText("点下面的按钮检查 GitHub 上有没有新版本。")
                            Spacer(Modifier.height(9.dp))
                            PrimaryButton("检查更新") { vm.checkUpdate() }
                        }

                        is UpdateState.Checking -> {
                            TipText("正在连接 GitHub…")
                            Spacer(Modifier.height(9.dp))
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }

                        is UpdateState.UpToDate -> {
                            TipText("已是最新版本 ✓")
                            Spacer(Modifier.height(9.dp))
                            SecondaryButton("重新检查") { vm.checkUpdate() }
                        }

                        is UpdateState.Available -> {
                            Text(
                                "发现新版本 v${s.info.versionName}",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                s.info.notes.take(600),
                                fontSize = 11.5.sp,
                                lineHeight = 17.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (s.info.sizeBytes > 0) {
                                Spacer(Modifier.height(6.dp))
                                TipText("安装包 ${"%.1f".format(s.info.sizeBytes / 1024.0 / 1024.0)} MB")
                            }
                            Spacer(Modifier.height(11.dp))
                            PrimaryButton(if (canInstall) "下载并安装" else "下载更新") {
                                if (!canInstall) {
                                    // 没有安装未知应用的权限，先引导去开
                                    UpdateChecker.openInstallPermissionSettings(ctx)
                                    toast(ctx, "请先允许「提醒」安装未知应用，回来再点一次")
                                    tick++
                                } else {
                                    vm.downloadUpdate()
                                }
                            }
                        }

                        is UpdateState.Downloading -> {
                            TipText("正在下载 ${s.percent}%")
                            Spacer(Modifier.height(9.dp))
                            LinearProgressIndicator(
                                progress = { s.percent / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        is UpdateState.Downloaded -> {
                            TipText("下载完成，点下面开始安装")
                            Spacer(Modifier.height(9.dp))
                            PrimaryButton(
                                if (canInstall) "立即安装" else "去允许安装"
                            ) {
                                if (canInstall) vm.installDownloaded()
                                else {
                                    UpdateChecker.openInstallPermissionSettings(ctx)
                                    tick++
                                }
                            }
                        }

                        is UpdateState.Failed -> {
                            TipText("检查失败：${s.message}")
                            Spacer(Modifier.height(9.dp))
                            SecondaryButton("重试") { vm.checkUpdate() }
                        }
                    }
                }
            }

            // ================= 权限体检 =================
            item {
                CardSurface {
                    Text(
                        "提醒能不能准时响，全看下面几项",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))

                    PermissionRow(
                        title = "通知权限",
                        desc = "到点要弹通知，否则只能在桌面小组件里看到",
                        status = if (notifyOn) PermStatus.OK else PermStatus.NEED,
                        actionText = if (notifyOn) "设置" else "去开启"
                    ) {
                        runCatching {
                            ctx.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        }
                        tick++
                    }

                    Divider()

                    PermissionRow(
                        title = "精确闹钟",
                        desc = "关掉后系统会把 13:00 的提醒推到 13:15 之后",
                        status = if (exactOn) PermStatus.OK else PermStatus.NEED,
                        actionText = if (exactOn) "设置" else "去开启"
                    ) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            runCatching {
                                ctx.startActivity(
                                    Intent(
                                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        Uri.parse("package:${ctx.packageName}")
                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                        vm.resyncAlarms()
                        tick++
                    }

                    Divider()

                    PermissionRow(
                        title = "省电策略：无限制",
                        desc = "被系统判定为受限时，后台闹钟会被推迟甚至不触发",
                        status = if (batteryFree) PermStatus.OK else PermStatus.NEED,
                        actionText = if (batteryFree) "设置" else "去设置"
                    ) {
                        runCatching {
                            // 有 REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 权限，可以直接弹系统白名单确认框
                            ctx.startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${ctx.packageName}")
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                        tick++
                    }

                    Divider()

                    // 自启动在小米上是 AppOps 私有项，没有公开 API 可读，只能标注"需手动确认"
                    PermissionRow(
                        title = "自启动",
                        desc = "小米把自启动开关放在私有设置里，App 读不到状态；只要你在下面路径里允许过就行",
                        status = PermStatus.UNKNOWN,
                        actionText = "去设置"
                    ) {
                        openMiuiAutostart(ctx)
                        tick++
                    }

                    Spacer(Modifier.height(10.dp))
                    SecondaryButton("重新检测全部状态") { tick++ }
                }
            }

            // ================= 路径说明 =================
            item {
                CardSurface {
                    Text("HyperOS 建议的开启路径", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    listOf(
                        "设置 → 应用设置 → 应用管理 → 提醒 → 权限管理 → 允许通知",
                        "同一页面 → 省电策略 → 无限制",
                        "同一页面 → 自启动 → 打开",
                        "设置 → 应用设置 → 特殊权限 → 闹钟和提醒 → 允许",
                        "最近任务里下拉本应用卡片 → 加锁（防止一键清理掉）"
                    ).forEachIndexed { i, line ->
                        Text(
                            "${i + 1}. $line",
                            fontSize = 11.5.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ================= 今日完成 =================
            item {
                CardSurface {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("今日完成", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text("${doneToday.size} 条", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (doneToday.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        doneToday.sortedByDescending { it.doneAt }.take(8).forEach { rec ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("✓", fontSize = 11.5.sp, color = androidx.compose.ui.graphics.Color(rec.color))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "${rec.groupName} · ${rec.eventName}",
                                    fontSize = 12.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { vm.undo(rec.id) }) {
                                    Text("撤销", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            // ================= 数据 =================
            item {
                CardSurface {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("数据", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text("${groups.size} 个栏目", fontSize = 12.5.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    TipText("历史记录全部保留。桌面小组件的「已完成」页只显示当天条目。")
                }
            }

            // ================= 小组件入口 =================
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(15.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { addWidgetToHome(ctx) }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "把 4×2 小组件放到桌面",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            // 找不到组件时的手动引导——小米桌面经常不弹一键添加
            item {
                CardSurface {
                    Text("手动添加（一键添加没反应时用这个）", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(7.dp))
                    Text(
                        "长按桌面空白处 → 点「添加小部件 / 小组件」→ 在列表里找到「提醒」→ 长按拖到桌面。\n" +
                            "如果列表里没有「提醒」：先在本页面点过一次上面的橙色按钮，再回桌面重新打开小组件列表。",
                        fontSize = 11.5.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(9.dp))
                    SecondaryButton("打开系统桌面设置检查组件") {
                        runCatching {
                            ctx.startActivity(
                                Intent(Settings.ACTION_HOME_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                }
            }

            item {
                Box(
                    Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(15.dp))
                        .clickable { vm.resyncAlarms(); tick++ }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("重建全部闹钟", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                }
            }

            item {
                Box(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(15.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable {
                            vm.testAlarm { ok ->
                                if (ok) toast(ctx, "10 秒后应该会弹出提醒，先按 Home 回桌面等")
                                else toast(ctx, "还没有可用的事件，先去加一个")
                            }
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "10 秒后测试一次提醒",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "走完整的闹钟链路，验证通知与小组件刷新",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ---------------- 小组件添加 ----------------

/**
 * 一键添加。小米 HyperOS 的桌面大多数版本**不支持** requestPinAppWidget，
 * 这时必须给出明确文案，否则用户点完毫无反馈（v1.0 的 bug）。
 */
private fun addWidgetToHome(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        toast(context, "这台手机太旧，请手动长按桌面添加")
        return
    }
    val mgr = AppWidgetManager.getInstance(context)
    if (!mgr.isRequestPinAppWidgetSupported) {
        toast(context, "这台手机的桌面不支持一键添加，请长按桌面空白 → 小组件 → 找到「提醒」")
        return
    }
    val ok = runCatching {
        mgr.requestPinAppWidget(
            ComponentName(context, ReminderWidgetProvider::class.java),
            null, null
        )
    }.getOrDefault(false)

    if (ok) {
        toast(context, "已请求添加，请在系统弹窗里点确认")
    } else {
        toast(context, "系统拒绝了请求，请长按桌面空白 → 小组件 → 找到「提醒」")
    }
}

private fun openMiuiAutostart(context: Context) {
    val candidates = listOf(
        "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        "com.miui.securitycenter" to "com.miui.permcenter.permissions.PermissionsEditorActivity"
    )
    for ((pkg, cls) in candidates) {
        val ok = runCatching {
            context.startActivity(
                Intent().apply {
                    setClassName(pkg, cls)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            true
        }.getOrDefault(false)
        if (ok) return
    }
    // 小米上找不到就直接进应用详情页，用户能在那里找到权限入口
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}

// ---------------- 小组件 ----------------

@Composable
private fun Divider() {
    HorizontalDivider(
        Modifier.padding(vertical = 7.dp),
        color = MaterialTheme.colorScheme.outline
    )
}

@Composable
private fun TipText(text: String) {
    Text(
        text,
        fontSize = 11.5.sp,
        lineHeight = 17.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 12.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun toast(context: Context, message: String) {
    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
}

@Composable
private fun PermissionRow(
    title: String,
    desc: String,
    status: PermStatus,
    actionText: String,
    onAction: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(7.dp))
                Pill(
                    text = when (status) {
                        PermStatus.OK -> "已开启"
                        PermStatus.NEED -> "未开启"
                        PermStatus.UNKNOWN -> "需手动确认"
                    },
                    tint = when (status) {
                        PermStatus.OK -> MaterialTheme.colorScheme.primary
                        PermStatus.NEED -> MaterialTheme.colorScheme.error
                        PermStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    background = when (status) {
                        PermStatus.OK -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        PermStatus.NEED -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                        PermStatus.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                desc,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onAction) { Text(actionText, fontSize = 12.sp) }
    }
}
