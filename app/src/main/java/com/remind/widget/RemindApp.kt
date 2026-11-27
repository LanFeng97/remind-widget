package com.remind.widget

import android.app.Application
import android.util.Log
import com.remind.widget.core.AlarmScheduler
import com.remind.widget.core.Notifier
import com.remind.widget.data.ReminderRepository
import com.remind.widget.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 冷启动时把该准备的东西一次性准备好：
 * 通知渠道 → 示例数据 → 闹钟 → 小组件。
 * 放在这里而不是 MainActivity，是为了"用户从桌面小组件点进来"时状态也是对的。
 */
class RemindApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannel(this)

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                ReminderRepository.get(this@RemindApp).seedIfEmpty()
                AlarmScheduler.resync(this@RemindApp)
                WidgetUpdater.refresh(this@RemindApp)
            }.onFailure { Log.e("RemindApp", "初始化失败", it) }
        }
    }
}
