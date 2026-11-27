package com.remind.widget.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.remind.widget.core.AlarmScheduler
import com.remind.widget.data.ReminderRepository
import com.remind.widget.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 系统事件后重建全部闹钟。
 * 覆盖：开机 / 应用更新 / 手动改时间 / 改时区 / 精确闹钟权限被重新授予。
 * 少任何一个都可能出现"重启后再也不提醒"——这是提醒类 App 最常见的翻车点。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in WATCHED) return

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderRepository.get(appContext).seedIfEmpty()
                AlarmScheduler.resync(appContext)
                WidgetUpdater.refresh(appContext)
                Log.i("BootReceiver", "闹钟已重建，触发=$action")
            } catch (t: Throwable) {
                Log.e("BootReceiver", "重建闹钟失败", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val WATCHED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        )
    }
}
