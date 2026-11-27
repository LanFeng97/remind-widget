package com.remind.widget.core

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.remind.widget.MainActivity
import com.remind.widget.R
import com.remind.widget.data.Event
import com.remind.widget.data.Group
import com.remind.widget.receiver.WidgetActionReceiver

/** 到点系统通知。渠道常驻，重要级别 HIGH（需要横幅 + 声音，吃药提醒不能静默） */
object Notifier {

    const val CHANNEL_ID = "remind"
    private const val CHANNEL_NAME = "提醒"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "栏目事件到点时的提醒"
            enableVibration(true)
            enableLights(true)
        }
        nm.createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun show(context: Context, group: Group, event: Event) {
        ensureChannel(context)
        if (!canPost(context)) return

        val notificationId = (10_000 + event.id).toInt()

        // 点通知 → 打开 App
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_GROUP_ID, group.id)
        }
        val openPi = PendingIntent.getActivity(
            context, notificationId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 通知上的"完成"按钮 → 直接记账，不用进 App
        val doneIntent = Intent(context, WidgetActionReceiver::class.java).apply {
            action = WidgetActionReceiver.ACTION_COMPLETE
            putExtra(WidgetActionReceiver.EXTRA_EVENT_ID, event.id)
        }
        val donePi = PendingIntent.getBroadcast(
            context, notificationId + 50_000, doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(group.name)
            .setContentText("${event.name} · ${Schedule.hhmm(event)}")
            .setColor(group.color.toInt())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openPi)
            .addAction(0, "完成", donePi)

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        }
    }

    fun cancel(context: Context, eventId: Long) {
        NotificationManagerCompat.from(context).cancel((10_000 + eventId).toInt())
    }
}
