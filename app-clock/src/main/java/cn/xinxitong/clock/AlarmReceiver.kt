package cn.xinxitong.clock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

const val CLOCK_CHANNEL_ID = "clock_alarms"
private const val CHANNEL_NAME = "闹钟与计时器"

/** 建通知渠道（API 26+；minSdk 29，无需版本守卫）。 */
fun ensureClockChannel(context: Context) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (manager.getNotificationChannel(CLOCK_CHANNEL_ID) == null) {
        val channel = NotificationChannel(
            CLOCK_CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "闹钟响起与计时器完成提醒"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }
}

/** 发一条时钟通知：默认声音 + 振动，点按打开时钟应用。 */
fun showClockNotification(context: Context, notificationId: Int, title: String, text: String) {
    ensureClockChannel(context)
    val openIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pending = PendingIntent.getActivity(
        context,
        notificationId,
        openIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = NotificationCompat.Builder(context, CLOCK_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle(title)
        .setContentText(text)
        .setContentIntent(pending)
        .setAutoCancel(true)
        .setDefaults(NotificationCompat.DEFAULT_ALL)
        .build()
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.notify(notificationId, notification)
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // 开机后重新安排所有已启用的闹钟（系统重启会清掉 AlarmManager 的安排）。
            val store = AlarmStore(context.applicationContext)
            val alarms = runBlocking { store.alarms.first() }
            for (alarm in alarms) {
                if (alarm.enabled) AlarmScheduler.schedule(context.applicationContext, alarm)
            }
            return
        }
        val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        val label = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_LABEL).orEmpty()
        val timeText = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_TIME).orEmpty()
        val title = if (label.isBlank()) "闹钟" else label
        val text = if (timeText.isBlank()) "到时间了" else "$timeText 的闹钟响了"
        showClockNotification(
            context,
            notificationId = 1000 + (alarmId % 1000).toInt().coerceAtLeast(0),
            title = title,
            text = text,
        )
        // 重复闹钟：安排下一次触发；仅一次的闹钟不重新安排。
        if (alarmId >= 0) {
            val store = AlarmStore(context.applicationContext)
            val alarm = runBlocking { store.getAlarm(alarmId) }
            if (alarm != null && alarm.enabled && alarm.repeatDays.isNotEmpty()) {
                AlarmScheduler.schedule(context.applicationContext, alarm)
            }
        }
    }
}
