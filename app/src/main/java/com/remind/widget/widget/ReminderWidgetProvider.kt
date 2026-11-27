package com.remind.widget.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.remind.widget.core.AlarmScheduler
import com.remind.widget.data.ReminderRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 小组件生命周期入口。
 * 注意：这里所有"要查库"的活都得走 goAsync + 协程，
 * 因为 AppWidgetProvider.onUpdate 仍然处在 onReceive 的调用栈里，
 * 直接开线程而不 goAsync 会在方法返回后被系统掐掉。
 */
class ReminderWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 首次添加小组件时把示例数据灌进去，免得用户看到一片空白
                ReminderRepository.get(appContext).seedIfEmpty()
                appWidgetIds.forEach {
                    runCatching { WidgetUpdater.refreshWidget(appContext, it) }
                        .onFailure { e -> Log.e(TAG, "update widget=$it 失败", e) }
                }
            } finally {
                pending.finish()
            }
        }
    }

    /** 用户拉伸组件 → 每页宽度要跟着变，否则横滑会错位 */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetUpdater.refreshWidget(appContext, appWidgetId)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetUpdater.setPage(context, it, 0) }
    }

    /** 第一个小组件被添加：重建闹钟，确保提醒是活的 */
    override fun onEnabled(context: Context) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderRepository.get(appContext).seedIfEmpty()
                AlarmScheduler.resync(appContext)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ReminderWidget"
    }
}
