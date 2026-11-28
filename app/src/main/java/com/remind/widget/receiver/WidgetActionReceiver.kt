package com.remind.widget.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.remind.widget.MainActivity
import com.remind.widget.core.Notifier
import com.remind.widget.data.ReminderRepository
import com.remind.widget.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 桌面小组件 / 通知动作的统一入口。
 *
 * 为什么不用 Activity 承接：点一下小组件里的事件就弹 App 会很烦，
 * 用户要的是"点一下、划掉、留在桌面"，所以全部走 BroadcastReceiver。
 */
class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val appContext = context.applicationContext

        when (action) {
            ACTION_COMPLETE -> {
                val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
                if (eventId <= 0) {
                    // 没有 eventId 说明点的是"栏目头"，行为改成打开 App 定位到该栏目
                    val gid = intent.getLongExtra(EXTRA_GROUP_ID, -1L)
                    if (gid > 0) startApp(appContext, gid)
                    return
                }
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val ok = ReminderRepository.get(appContext).completeEvent(eventId)
                        if (ok) Notifier.cancel(appContext, eventId)
                    } catch (t: Throwable) {
                        Log.e("WidgetAction", "完成失败 event=$eventId", t)
                    } finally {
                        pending.finish()
                    }
                }
            }

            ACTION_UNDO -> {
                val recordId = intent.getLongExtra(EXTRA_RECORD_ID, -1L)
                if (recordId <= 0) return
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        ReminderRepository.get(appContext).undoComplete(recordId)
                    } catch (t: Throwable) {
                        Log.e("WidgetAction", "撤销失败 record=$recordId", t)
                    } finally {
                        pending.finish()
                    }
                }
            }

            ACTION_TOGGLE_PAGE -> {
                // 左右分栏后不再需要翻页，这个动作保留只为兼容已发布版本发出过的 PendingIntent
                val widgetId = intent.getIntExtra(EXTRA_WIDGET_ID, -1)
                if (widgetId < 0) return
                WidgetUpdater.resetWidget(appContext, widgetId)
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        WidgetUpdater.refreshWidget(appContext, widgetId)
                    } finally {
                        pending.finish()
                    }
                }
            }

            /** 左侧点击某个栏目：记住选中项并刷新（-1 = 已完成） */
            ACTION_SELECT_GROUP -> {
                val widgetId = intent.getIntExtra(EXTRA_WIDGET_ID, -1)
                if (widgetId < 0) return
                val groupId = intent.getLongExtra(EXTRA_GROUP_ID, 0L)
                WidgetUpdater.setSelectedGroup(appContext, widgetId, groupId)
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        WidgetUpdater.refreshWidget(appContext, widgetId)
                    } catch (t: Throwable) {
                        Log.e("WidgetAction", "切换栏目失败", t)
                    } finally {
                        pending.finish()
                    }
                }
            }

            ACTION_OPEN_APP -> {
                val gid = intent.getLongExtra(EXTRA_GROUP_ID, -1L)
                startApp(appContext, gid)
            }

            ACTION_REFRESH -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        WidgetUpdater.refresh(appContext)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        private fun startApp(context: Context, groupId: Long) {
            val open = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (groupId > 0) putExtra(MainActivity.EXTRA_OPEN_GROUP_ID, groupId)
            }
            runCatching { context.startActivity(open) }
        }

        const val ACTION_COMPLETE = "com.remind.widget.ACTION_COMPLETE"
        const val ACTION_SELECT_GROUP = "com.remind.widget.ACTION_SELECT_GROUP"
        const val ACTION_UNDO = "com.remind.widget.ACTION_UNDO"
        const val ACTION_TOGGLE_PAGE = "com.remind.widget.ACTION_TOGGLE_PAGE"
        const val ACTION_OPEN_APP = "com.remind.widget.ACTION_OPEN_APP"
        const val ACTION_REFRESH = "com.remind.widget.ACTION_REFRESH"

        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_RECORD_ID = "record_id"
        const val EXTRA_WIDGET_ID = "widget_id"
        const val EXTRA_PAGE = "page"
        const val EXTRA_GROUP_ID = "group_id"
    }
}
