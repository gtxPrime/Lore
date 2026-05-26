package com.gxdevs.aethra.utils

import android.app.AlarmManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
        NotificationChannel(CHANNEL_DAILY, "Daily Writing Reminder", NotificationManager.IMPORTANCE_HIGH).apply {
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
        set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
    }
    try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                cal.timeInMillis,
                intent
            )
        } else {
            am.setExact(
                AlarmManager.RTC_WAKEUP,
                cal.timeInMillis,
                intent
            )
        }
    } catch (e: SecurityException) {
        try {
            am.set(
                AlarmManager.RTC_WAKEUP,
                cal.timeInMillis,
                intent
            )
        } catch (_: Exception) {}
    }
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
    companion object {
        data class ReminderVariant(val title: String, val text: String)

        val reminderVariants = listOf(
            ReminderVariant("Write in your Aethra", "A few words today keep the silence away."),
            ReminderVariant("Pause and Reflect", "Capture a moment from today before it fades."),
            ReminderVariant("Your Daily Sanctuary", "Take a moment to write down your thoughts."),
            ReminderVariant("A Moment of Peace", "How was your day? Put it into words."),
            ReminderVariant("Reflect on Today", "Every day has a story. What is yours?"),
            ReminderVariant("Aethra Journaling", "Speak your mind, clear your thoughts, and find peace."),
            ReminderVariant("Mindful Reflection", "Settle down and record a memory from today."),
            ReminderVariant("Unburden Your Mind", "Write down whatever is on your heart tonight."),
            ReminderVariant("Capture the Day", "A blank page is waiting. Share your journey."),
            ReminderVariant("Daily Check-in", "Take a deep breath and write down how you feel."),
            ReminderVariant("Your Thoughts Matter", "Leave a trace of your day in your digital sanctuary.")
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val tapIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("navigate_to", "text_journal")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val variant = reminderVariants.random()
        val notif = NotificationCompat.Builder(context, CHANNEL_DAILY)
            .setSmallIcon(com.gxdevs.aethra.R.drawable.scroll)
            .setContentTitle(variant.title)
            .setContentText(variant.text)
            .setAutoCancel(true)
            .setContentIntent(tapIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()
        nm.notify(NOTIF_DAILY_ID, notif)

        // Reschedule the exact alarm for tomorrow at the same hour/minute
        val settingsRepo = com.gxdevs.aethra.data.SettingsRepository(context)
        try {
            runBlocking {
                val dailyReminderOn = settingsRepo.dailyReminder.first()
                if (dailyReminderOn) {
                    val hour = settingsRepo.reminderHour.first()
                    val minute = settingsRepo.reminderMinute.first()
                    scheduleDailyReminder(context, hour, minute)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

