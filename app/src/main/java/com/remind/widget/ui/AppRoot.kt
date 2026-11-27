package com.remind.widget.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** 只有三个界面，用一个 sealed 状态切换就够，不引 Navigation 库 */
sealed interface Screen {
    data object Home : Screen
    data class Edit(val groupId: Long) : Screen
    data object Settings : Screen
}

@Composable
fun AppRoot(initialGroupId: Long = -1L) {
    val vm: AppViewModel = viewModel()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val events by vm.events.collectAsStateWithLifecycle()
    val doneToday by vm.doneToday.collectAsStateWithLifecycle()

    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    val updateState by vm.updateState.collectAsStateWithLifecycle()

    // 启动时静默检查一次更新（内部有 12 小时节流），有新版就在首页齿轮旁点个红点
    LaunchedEffect(Unit) { vm.checkUpdate() }

    // 从通知 / 小组件点进来：直接进对应栏目
    LaunchedEffect(initialGroupId, groups.size) {
        if (initialGroupId > 0 && groups.any { it.id == initialGroupId }) {
            screen = Screen.Edit(initialGroupId)
        }
    }

    // Android 13+ 通知权限，进 App 就问一次
    val context = LocalContext.current
    var askedNotify by remember { mutableStateOf(false) }
    val notifyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (!askedNotify) {
            askedNotify = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    BackHandler(enabled = screen !is Screen.Home) { screen = Screen.Home }

    when (val s = screen) {
        is Screen.Home -> HomeScreen(
            groups = groups,
            events = events,
            doneToday = doneToday,
            hasUpdate = updateState is UpdateState.Available,
            onOpenGroup = { screen = Screen.Edit(it) },
            onOpenSettings = { screen = Screen.Settings },
            onCreateGroup = { name, color, onCreated ->
                vm.createGroup(name, color) { id ->
                    screen = Screen.Edit(id)
                    onCreated()
                }
            },
            onComplete = vm::complete
        )

        is Screen.Edit -> {
            val group = groups.firstOrNull { it.id == s.groupId }
            if (group == null) {
                LaunchedEffect(Unit) { screen = Screen.Home }
            } else {
                EditScreen(
                    vm = vm,
                    group = group,
                    groupEvents = events.filter { it.groupId == group.id },
                    doneToday = doneToday,
                    onBack = { screen = Screen.Home },
                    onDeleteGroup = {
                        vm.deleteGroup(group.id)
                        screen = Screen.Home
                    }
                )
            }
        }

        is Screen.Settings -> SettingsScreen(
            vm = vm,
            groups = groups,
            doneToday = doneToday,
            onBack = { screen = Screen.Home }
        )
    }
}
