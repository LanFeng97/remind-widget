package com.remind.widget.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.remind.widget.data.ReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 闹钟到点回调。
 * goAsync() + 协程：BroadcastReceiver 的 onReceive 只有约 10 秒窗口，
 * 这里要碰数据库（查事件、写通知、排下一次），必须异步且显式告诉系统"我还在干活"。
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        if (eventId <= 0) return

        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderRepository.get(appContext).onAlarmFired(eventId)
            } catch (t: Throwable) {
                Log.e("AlarmReceiver", "处理闹钟失败 event=$eventId", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.remind.widget.ACTION_ALARM_FIRE"
        const val EXTRA_EVENT_ID = "event_id"
    }
}
