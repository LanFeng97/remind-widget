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

            // ================= 数据 =================
            // 今日完成 / 历史记录的查看已移到首页标题旁的「已完成列表」入口
            item {
                CardSurface {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("数据", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text("${groups.size} 个栏目 · 今日 ${doneToday.size} 条", fontSize = 12.5.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    TipText("历史记录全部保留，在首页标题旁的「已完成列表」里查看（可按提醒时间 / 栏目 / 完成时间排序）。")
                }
            }

            // ================= 添加桌面小组件（醒目引导） =================
            // 一键添加（requestPinAppWidget）在 HyperOS 4.0 上是假接口：返回 true 但系统什么都不做。
            // 所以这里把「手动步骤」提到最前面直接展示，按钮只作为可选尝试，并按品牌给不同路径。
            item {
                val mgr = AppWidgetManager.getInstance(ctx)
                val widgetIds = remember(tick) {
                    runCatching {
                        mgr.getAppWidgetIds(
                            ComponentName(ctx, ReminderWidgetProvider::class.java)
                        )
                    }.getOrDefault(IntArray(0))
                }
                val brand = remember { brandHint() }
                val added = widgetIds.isNotEmpty()

                CardSurface {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("添加桌面小组件", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Pill(
                            text = if (added) "已添加 ${widgetIds.size} 个" else "未添加",
                            tint = if (added) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                            background = if (added)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        brand.name,
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(12.dp))
                    Text(
                        "按这几步手动添加（最可靠）",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))

                    brand.steps.forEachIndexed { i, step ->
                        Row(Modifier.padding(vertical = 1.5.dp)) {
                            Text(
                                "${i + 1}.",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(18.dp)
                            )
                            Text(
                                step,
                                fontSize = 12.5.sp,
                                lineHeight = 19.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (brand.note != null) {
                        Spacer(Modifier.height(9.dp))
                        Text(
                            brand.note,
                            fontSize = 11.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(13.dp))
                    PrimaryButton("尝试一键添加（部分桌面不支持）") { addWidgetToHome(ctx) }
                    Spacer(Modifier.height(7.dp))
                    SecondaryButton("打开桌面设置") {
                        runCatching {
                            ctx.startActivity(
                                Intent(Settings.ACTION_HOME_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                }
            }

            // ================= 小组件诊断 =================
            // 用来区分两种完全不同的病因：
            //   A. 系统压根没注册 provider（安装包/manifest 问题）
            //   B. 系统注册了但桌面不列出来（桌面侧过滤，只能改声明去迎合）
            item {
                val diag = remember(tick) {
                    runCatching {
                        val mgr = AppWidgetManager.getInstance(ctx)
                        val all = mgr.installedProviders
                        val mine = all.filter { it.provider.packageName == ctx.packageName }
                        WidgetDiag(
                            total = all.size,
                            mineCount = mine.size,
                            minW = mine.firstOrNull()?.minWidth ?: 0,
                            minH = mine.firstOrNull()?.minHeight ?: 0,
                            pinSupported = mgr.isRequestPinAppWidgetSupported
                        )
                    }.getOrNull()
                }
                CardSurface {
                    Text("小组件诊断", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    if (diag == null) {
                        TipText("读取失败，点下面刷新重试")
                    } else {
                        DiagLine("系统已注册的组件提供者", "${diag.total} 个")
                        DiagLine(
                            "本应用注册数",
                            "${diag.mineCount} 个",
                            warn = diag.mineCount == 0
                        )
                        if (diag.mineCount > 0) {
                            // AppWidgetProviderInfo.minWidth 的单位是 px，要除以 density 换回 dp
                            val d = ctx.resources.displayMetrics.density
                            DiagLine(
                                "声明尺寸",
                                "${(diag.minW / d).toInt()} × ${(diag.minH / d).toInt()} dp"
                            )
                        }
                        DiagLine("一键添加接口", if (diag.pinSupported) "支持" else "不支持")
                        Spacer(Modifier.height(7.dp))
                        TipText(
                            if (diag.mineCount == 0) {
                                "系统没有识别到本应用的组件，说明安装包里的 provider 声明有问题，请把这个诊断结果发给我。"
                            } else {
                                "系统已识别到组件。如果桌面列表里仍然看不到「提醒」，那是桌面侧的过滤，" +
                                    "请在小部件列表点右上角的筛选图标，切到「按应用」视图再看一次。"
                            }
                        )
                    }
                    Spacer(Modifier.height(9.dp))
                    SecondaryButton("刷新诊断") { tick++ }
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

/** 诊断结果 */
private data class WidgetDiag(
    val total: Int,
    val mineCount: Int,
    val minW: Int,
    val minH: Int,
    val pinSupported: Boolean
)

@Composable
private fun DiagLine(label: String, value: String, warn: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            color = if (warn) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

// ---------------- 小组件添加 ----------------

/**
 * 按品牌给出小部件入口路径。
 * 各家 ROM 的入口差异极大：小米把标准 Android 组件收进「安卓小部件」二级分类，
 * 其他品牌大多在长按桌面后的一级菜单里。猜错路径比不给路径更糟，所以这里分开写。
 */
private data class BrandHint(
    val name: String,
    val steps: List<String>,
    val note: String? = null
)

private fun brandHint(): BrandHint {
    val mfr = Build.MANUFACTURER.orEmpty()
    val model = Build.MODEL.orEmpty()
    val s = "$mfr ${Build.BRAND.orEmpty()}".lowercase()
    val label = "已识别：$mfr $model"

    return when {
        s.contains("xiaomi") || s.contains("redmi") || s.contains("poco") || isMiuiRom() ->
            BrandHint(
                name = "$label（HyperOS / MIUI）",
                steps = listOf(
                    "在桌面空白处【双指捏合】（像缩小一张照片那样）",
                    "点底部的【小部件】",
                    "点【全部应用】",
                    "一直往下滚到列表最底部，点【安卓小部件】",
                    "继续滚到最底，找到【提醒】，双击即可放到桌面"
                ),
                note = "小米把标准 Android 组件收在「安卓小部件」这个二级分类里；" +
                    "小部件面板顶部的搜索框只搜小米自家组件，搜「提醒」搜不到，必须从分类进去找。"
            )

        s.contains("samsung") -> BrandHint(
            name = "$label（One UI）",
            steps = listOf(
                "长按桌面空白处",
                "点底部【小组件】",
                "在列表里找到【提醒】",
                "按住拖到桌面"
            )
        )

        s.contains("oppo") || s.contains("oneplus") || s.contains("realme") ->
            BrandHint(
                name = "$label（ColorOS）",
                steps = listOf(
                    "长按桌面空白处",
                    "点【小部件】或【卡片】",
                    "在列表里找到【提醒】",
                    "点击或拖到桌面"
                )
            )

        s.contains("vivo") || s.contains("iqoo") -> BrandHint(
            name = "$label（OriginOS）",
            steps = listOf(
                "长按桌面空白处",
                "点【原子组件】或【小部件】",
                "找到【提醒】",
                "拖到桌面"
            )
        )

        s.contains("huawei") || s.contains("honor") -> BrandHint(
            name = "$label（HarmonyOS / MagicOS）",
            steps = listOf(
                "长按桌面空白处",
                "点【服务卡片】或【小组件】",
                "找到【提醒】",
                "添加"
            ),
            note = "部分鸿蒙机型对标准 Android 组件的支持有限。如果在所有分类里都找不到「提醒」，" +
                "那就是系统层面的限制，不是安装问题。"
        )

        else -> BrandHint(
            name = "$label",
            steps = listOf(
                "长按桌面空白处",
                "选择【小部件 / 小组件 / Widgets】",
                "在列表里找到【提醒】",
                "拖到桌面，或点一下直接添加"
            ),
            note = "不同品牌的入口名称略有差别，找不到时可以在小部件面板里切到「按应用」视图。"
        )
    }
}

/** 小米 ROM 判断：MIUI/HyperOS 会填 ro.miui.ui.version.name，标准 Android 不会 */
private fun isMiuiRom(): Boolean = try {
    val cls = Class.forName("android.os.SystemProperties")
    val getter = cls.getMethod("get", String::class.java)
    val version = getter.invoke(null, "ro.miui.ui.version.name") as? String
    version != null && version.isNotEmpty()
} catch (t: Throwable) {
    false
}

/**
 * 一键添加。小米 HyperOS 的桌面大多**不支持** requestPinAppWidget，
 * 这时必须给出明确文案，否则用户点完毫无反馈（这是 v1.0 的原始 bug）。
 */
private fun addWidgetToHome(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        toast(context, "这台手机系统太旧，不支持一键添加，请按上面的步骤手动添加")
        return
    }
    val mgr = AppWidgetManager.getInstance(context)
    if (!mgr.isRequestPinAppWidgetSupported) {
        toast(context, "这台手机的桌面不支持一键添加，请按上面的步骤手动添加")
        return
    }
    val ok = runCatching {
        mgr.requestPinAppWidget(
            ComponentName(context, ReminderWidgetProvider::class.java),
            null, null
        )
    }.getOrDefault(false)

    // 无论成败都给反馈：HyperOS 上「接口返回 true 但桌面什么都不弹」是常态，
    // 静默会让人以为按钮坏了（v1.0 的原始 bug）。
    toast(
        context,
        if (ok) "已发出添加请求。如果桌面没有弹确认框，说明这台手机的桌面忽略了它，请按上面的步骤手动添加"
        else "系统拒绝了添加请求，请按上面的步骤手动添加"
    )
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
