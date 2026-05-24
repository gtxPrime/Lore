package com.gxdevs.aethra.utils

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.gxdevs.aethra.MainActivity
import java.util.Calendar

const val CHANNEL_DAILY     = "daily_reminder"
const val CHANNEL_COMPANION = "companion_alerts"
const val CHANNEL_RELIC     = "relic_alerts"
const val NOTIF_DAILY_ID    = 1001

/** Creates all required notification channels. Call once from Application / MainActivity.onCreate. */
fun createNotificationChannels(context: Context) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    nm.createNotificationChannel(
        NotificationChannel(CHANNEL_DAILY, "Daily Writing Reminder", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Reminds you to write your daily journal entry."
        }
    )
    nm.createNotificationChannel(
        NotificationChannel(CHANNEL_COMPANION, "Companion Alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Notifies when a pet evolves to a new stage."
        }
    )
    nm.createNotificationChannel(
        NotificationChannel(CHANNEL_RELIC, "Relic Discovery", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Alerts for hidden relics and memory resurface events."
        }
    )
}

/** Schedule (or reschedule) the daily writing reminder at the given time (default 10:00 PM). */
fun scheduleDailyReminder(context: Context, hour: Int = 22, minute: Int = 0) {
    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = PendingIntent.getBroadcast(
        context, NOTIF_DAILY_ID,
        Intent(context, DailyReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
    }
    try {
        am.setInexactRepeating(
            AlarmManager.RTC_WAKEUP, cal.timeInMillis,
            AlarmManager.INTERVAL_DAY, intent
        )
    } catch (_: SecurityException) { /* exact alarm permission not granted on API 31+ */ }
}

/** Cancel the daily writing reminder. */
fun cancelDailyReminder(context: Context) {
    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = PendingIntent.getBroadcast(
        context, NOTIF_DAILY_ID,
        Intent(context, DailyReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    am.cancel(intent)
}

/** BroadcastReceiver that fires the daily reminder notification. */
class DailyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val tapIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_DAILY)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Time to write in your Aethra ðŸ“–")
            .setContentText("A few words today keep the silence away.")
            .setAutoCancel(true)
            .setContentIntent(tapIntent)
            .build()
        nm.notify(NOTIF_DAILY_ID, notif)
    }
}

