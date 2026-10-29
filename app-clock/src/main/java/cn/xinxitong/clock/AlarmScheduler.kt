package cn.xinxitong.clock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object AlarmScheduler {
    const val EXTRA_ALARM_ID = "alarm_id"
    const val EXTRA_ALARM_LABEL = "alarm_label"
    const val EXTRA_ALARM_TIME = "alarm_time"

    private fun pendingIntent(context: Context, alarm: AlarmItem): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarm.id)
            putExtra(EXTRA_ALARM_LABEL, alarm.label)
            putExtra(EXTRA_ALARM_TIME, formatAlarmTime(alarm.hour, alarm.minute))
        }
        return PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** 是否有精确闹钟权限（API 31 以下不需要此权限）。 */
    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return manager.canScheduleExactAlarms()
    }

    /**
     * 安排一条闹钟。精确闹钟权限可用时用精确接口，否则退回非精确接口
     * （Doze 下仍会触发，但时间可能不精确，界面会如实说明）。
     */
    fun schedule(context: Context, alarm: AlarmItem) {
        if (!alarm.enabled) return
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = nextAlarmTimeMillis(
            alarm.hour,
            alarm.minute,
            alarm.repeatDays.toSet(),
            System.currentTimeMillis(),
        )
        val pending = pendingIntent(context, alarm)
        if (canScheduleExactAlarms(context)) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun cancel(context: Context, alarm: AlarmItem) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.cancel(pendingIntent(context, alarm))
    }
}
