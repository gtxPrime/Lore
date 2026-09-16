package com.gxdevs.lore.utils

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
import com.gxdevs.lore.MainActivity
import java.util.Calendar

const val CHANNEL_DAILY            = "daily_reminder"
const val CHANNEL_COMPANION        = "companion_alerts"
const val CHANNEL_RELIC            = "relic_alerts"
const val CHANNEL_MEDIA_PROCESSING = "media_processing"
const val CHANNEL_DRIVE_BACKUP     = "drive_backup"
const val NOTIF_DAILY_ID           = 1001
const val NOTIF_MEDIA_PROCESSING_ID = 2001
const val NOTIF_DRIVE_BACKUP_ID    = 3001

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
    nm.createNotificationChannel(
        NotificationChannel(CHANNEL_MEDIA_PROCESSING, "Media Encryption & Decryption", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shows background progress when encrypting or decrypting media files."
        }
    )
    nm.createNotificationChannel(
        NotificationChannel(CHANNEL_DRIVE_BACKUP, "Google Drive Backup & Sync", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shows ongoing progress when backing up or syncing Sanctuary data to Google Drive."
        }
    )
}

/** Builds an ongoing Notification object for Google Drive backup (used by WorkManager setForeground). */
fun buildDriveBackupNotification(context: Context, title: String, message: String, progress: Int = -1): android.app.Notification {
    val builder = NotificationCompat.Builder(context, CHANNEL_DRIVE_BACKUP)
        .setSmallIcon(com.gxdevs.lore.R.drawable.scroll)
        .setContentTitle(title)
        .setContentText(message)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)

    if (progress in 0..99) {
        builder.setProgress(100, progress, false)
    } else {
        builder.setProgress(0, 0, true)
    }

    return builder.build()
}

/** Shows or updates an ongoing background progress notification for Google Drive backup. */
fun showDriveBackupNotification(context: Context, title: String, message: String, progress: Int = -1) {
    try {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_DRIVE_BACKUP_ID, buildDriveBackupNotification(context, title, message, progress))
    } catch (_: Exception) {}
}

/** Shows a completion notification when Google Drive backup succeeds. */
fun showDriveBackupSuccessNotification(context: Context, timeStr: String) {
    try {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val tapIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("navigate_to", "identity")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_DRIVE_BACKUP)
            .setSmallIcon(com.gxdevs.lore.R.drawable.scroll)
            .setContentTitle("Google Drive Backup Complete")
            .setContentText("Sanctuary data backed up successfully ($timeStr)")
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(tapIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        nm.notify(NOTIF_DRIVE_BACKUP_ID, notif)
    } catch (_: Exception) {}
}

/** Shows a failure notification when Google Drive backup fails. */
fun showDriveBackupFailedNotification(context: Context, errorMsg: String) {
    try {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val tapIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("navigate_to", "identity")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, CHANNEL_DRIVE_BACKUP)
            .setSmallIcon(com.gxdevs.lore.R.drawable.scroll)
            .setContentTitle("Google Drive Backup Failed")
            .setContentText("Could not back up to Google Drive: $errorMsg")
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(tapIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        nm.notify(NOTIF_DRIVE_BACKUP_ID, notif)
    } catch (_: Exception) {}
}

/** Cancels the ongoing Google Drive backup notification. */
fun cancelDriveBackupNotification(context: Context) {
    try {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIF_DRIVE_BACKUP_ID)
    } catch (_: Exception) {}
}

/** Shows or updates an ongoing background progress notification for media encryption/decryption. */
fun showMediaProgressNotification(context: Context, title: String, message: String, progress: Int) {
    try {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(context, CHANNEL_MEDIA_PROCESSING)
            .setSmallIcon(com.gxdevs.lore.R.drawable.scroll)
            .setContentTitle(title)
            .setContentText(message)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (progress in 0..99) {
            builder.setProgress(100, progress, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        nm.notify(NOTIF_MEDIA_PROCESSING_ID, builder.build())
    } catch (_: Exception) {}
}

/** Cancels the ongoing background media processing notification. */
fun cancelMediaProgressNotification(context: Context) {
    try {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIF_MEDIA_PROCESSING_ID)
    } catch (_: Exception) {}
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
            ReminderVariant("Write in your Lore", "A few words today keep the silence away."),
            ReminderVariant("Pause and Reflect", "Capture a moment from today before it fades."),
            ReminderVariant("Your Daily Sanctuary", "Take a moment to write down your thoughts."),
            ReminderVariant("A Moment of Peace", "How was your day? Put it into words."),
            ReminderVariant("Reflect on Today", "Every day has a story. What is yours?"),
            ReminderVariant("Lore Journaling", "Speak your mind, clear your thoughts, and find peace."),
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
        // Query recent DB entries to generate AI-tailored pet notification text
        val customNotif = try {
            runBlocking {
                val db = com.gxdevs.lore.data.AppDatabase.getDatabase(context)
                val recentEntries = db.journalDao().getAllEntriesSync().takeLast(7)
                com.gxdevs.lore.utils.TailoredWellbeingEngine.generateTailoredNotification(recentEntries)
            }
        } catch (_: Exception) {
            val variant = reminderVariants.random()
            com.gxdevs.lore.utils.TailoredWellbeingEngine.CustomNotificationVariant(variant.title, variant.text, "Sage")
        }

        val notif = NotificationCompat.Builder(context, CHANNEL_DAILY)
            .setSmallIcon(com.gxdevs.lore.R.drawable.scroll)
            .setContentTitle(customNotif.title)
            .setContentText(customNotif.text)
            .setAutoCancel(true)
            .setContentIntent(tapIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()
        nm.notify(NOTIF_DAILY_ID, notif)

        // Reschedule the exact alarm for tomorrow at the same hour/minute
        val settingsRepo = com.gxdevs.lore.data.SettingsRepository(context)
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

