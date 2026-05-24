package com.gxdevs.aethra.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        val KEY_APP_LOCK_ENABLED        = booleanPreferencesKey("app_lock_enabled")
        val KEY_HIDE_MEDIA_IN_GALLERY   = booleanPreferencesKey("hide_media_in_gallery")
        val KEY_DECOY_PIN               = booleanPreferencesKey("decoy_pin")
        val KEY_DAILY_REMINDER          = booleanPreferencesKey("daily_reminder")
        val KEY_COMPANION_ALERTS        = booleanPreferencesKey("companion_alerts")
        val KEY_RELIC_ALERTS            = booleanPreferencesKey("relic_alerts")
        val KEY_PAST_PROMPTS            = booleanPreferencesKey("past_prompts")
        val KEY_BLUR_JOURNALS           = booleanPreferencesKey("blur_journals")
        val KEY_BETA_WELCOME_SHOWN      = booleanPreferencesKey("beta_welcome_shown")
        val KEY_AUTO_SAVE_FREQUENCY     = floatPreferencesKey("auto_save_frequency")
        val KEY_REAL_PIN                = stringPreferencesKey("real_pin")
        val KEY_DECOY_PIN_VALUE         = stringPreferencesKey("decoy_pin_value")
        val KEY_IS_DECOY_MODE           = booleanPreferencesKey("is_decoy_mode")
        // App PIN (separate from biometric lock â€” PIN-based lock)
        val KEY_APP_PIN                 = stringPreferencesKey("app_pin")
        // Draft journal
        val KEY_DRAFT_TITLE             = stringPreferencesKey("draft_title")
        val KEY_DRAFT_CONTENT           = stringPreferencesKey("draft_content")
        val KEY_DRAFT_TIMESTAMP         = longPreferencesKey("draft_timestamp")
        val KEY_DRAFT_TIME_SPENT        = longPreferencesKey("draft_time_spent")
        val KEY_DRAFT_ATTACHMENTS       = stringPreferencesKey("draft_attachments")
        // Pet Growth Selection
        val KEY_DAILY_SELECTED_JOURNALS = stringPreferencesKey("daily_selected_journals")
        val KEY_HAS_LONG_PRESSED_JOURNAL = booleanPreferencesKey("has_long_pressed_journal")
        // Lock method
        val KEY_USE_BIOMETRIC           = booleanPreferencesKey("use_biometric")
        // Daily reminder time (default: 10:00 AM)
        val KEY_REMINDER_HOUR           = intPreferencesKey("reminder_hour")
        val KEY_REMINDER_MINUTE         = intPreferencesKey("reminder_minute")

        // Google auth state and backups
        val KEY_GOOGLE_LOGGED_IN        = booleanPreferencesKey("google_logged_in")
        val KEY_GOOGLE_ACCOUNT_NAME     = stringPreferencesKey("google_account_name")
        val KEY_GOOGLE_ACCOUNT_EMAIL    = stringPreferencesKey("google_account_email")
        val KEY_GOOGLE_ACCOUNT_PHOTO    = stringPreferencesKey("google_account_photo")
        val KEY_GDRIVE_BACKUP_ENABLED   = booleanPreferencesKey("gdrive_backup_enabled")
        val KEY_GDRIVE_INCLUDE_MEDIA    = booleanPreferencesKey("gdrive_include_media")
        val KEY_GDRIVE_LAST_SYNCED      = stringPreferencesKey("gdrive_last_synced")
        val KEY_SUBSCRIPTION_PLAN       = stringPreferencesKey("subscription_plan")
        // Encrypt Media
        val KEY_ENCRYPT_MEDIA           = booleanPreferencesKey("encrypt_media")
        val KEY_BACKUP_ENCRYPTION_KEY   = stringPreferencesKey("backup_encryption_key")
        // Auto-lock delay (seconds; 0 = instant, max 15)
        val KEY_AUTO_LOCK_DELAY         = intPreferencesKey("auto_lock_delay")
    }

    val appLockEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_APP_LOCK_ENABLED] ?: false }

    val hideMediaInGallery: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_HIDE_MEDIA_IN_GALLERY] ?: false }

    val decoyPin: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_DECOY_PIN] ?: false }

    val dailyReminder: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_DAILY_REMINDER] ?: true }

    val companionAlerts: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_COMPANION_ALERTS] ?: true }

    val relicAlerts: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_RELIC_ALERTS] ?: true }

    val pastPrompts: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_PAST_PROMPTS] ?: true }

    val blurJournals: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_BLUR_JOURNALS] ?: false }

    val betaWelcomeShown: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_BETA_WELCOME_SHOWN] ?: false }

    val autoSaveFrequency: Flow<Float> = context.dataStore.data
        .map { it[KEY_AUTO_SAVE_FREQUENCY] ?: 15f }

    val realPin: Flow<String?> = context.dataStore.data
        .map { it[KEY_REAL_PIN] }

    val decoyPinValue: Flow<String?> = context.dataStore.data
        .map { it[KEY_DECOY_PIN_VALUE] }

    val isDecoyMode: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_IS_DECOY_MODE] ?: false }

    val appPin: Flow<String?> = context.dataStore.data
        .map { it[KEY_APP_PIN] }

    val useBiometricLock: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_USE_BIOMETRIC] ?: true }

    val draftTitle: Flow<String?> = context.dataStore.data
        .map { it[KEY_DRAFT_TITLE] }

    val draftContent: Flow<String?> = context.dataStore.data
        .map { it[KEY_DRAFT_CONTENT] }

    val draftTimestamp: Flow<Long> = context.dataStore.data
        .map { it[KEY_DRAFT_TIMESTAMP] ?: 0L }

    val draftTimeSpent: Flow<Long> = context.dataStore.data
        .map { it[KEY_DRAFT_TIME_SPENT] ?: 0L }

    val draftAttachments: Flow<String?> = context.dataStore.data
        .map { it[KEY_DRAFT_ATTACHMENTS] }

    val reminderHour: Flow<Int> = context.dataStore.data
        .map { it[KEY_REMINDER_HOUR] ?: 22 }

    val reminderMinute: Flow<Int> = context.dataStore.data
        .map { it[KEY_REMINDER_MINUTE] ?: 0 }

    val dailySelectedJournals: Flow<String> = context.dataStore.data
        .map { it[KEY_DAILY_SELECTED_JOURNALS] ?: "{}" }

    val hasLongPressedJournal: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_HAS_LONG_PRESSED_JOURNAL] ?: false }

    val googleLoggedIn: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_GOOGLE_LOGGED_IN] ?: false }

    val googleAccountName: Flow<String?> = context.dataStore.data
        .map { it[KEY_GOOGLE_ACCOUNT_NAME] }

    val googleAccountEmail: Flow<String?> = context.dataStore.data
        .map { it[KEY_GOOGLE_ACCOUNT_EMAIL] }

    val googleAccountPhoto: Flow<String?> = context.dataStore.data
        .map { it[KEY_GOOGLE_ACCOUNT_PHOTO] }

    val gdriveBackupEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_GDRIVE_BACKUP_ENABLED] ?: false }

    val gdriveIncludeMedia: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_GDRIVE_INCLUDE_MEDIA] ?: true }

    val gdriveLastSynced: Flow<String?> = context.dataStore.data
        .map { it[KEY_GDRIVE_LAST_SYNCED] }

    val subscriptionPlan: Flow<String?> = context.dataStore.data
        .map { it[KEY_SUBSCRIPTION_PLAN] ?: "MYSTIC (PRO)" }

    val encryptMedia: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_ENCRYPT_MEDIA] ?: false }

    val backupEncryptionKey: Flow<String?> = context.dataStore.data
        .map { it[KEY_BACKUP_ENCRYPTION_KEY] }

    val autoLockDelay: Flow<Int> = context.dataStore.data
        .map { it[KEY_AUTO_LOCK_DELAY] ?: 0 }

    // ——— Setters ———

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_APP_LOCK_ENABLED] = enabled }
    }

    suspend fun setHideMediaInGallery(hide: Boolean) {
        context.dataStore.edit { it[KEY_HIDE_MEDIA_IN_GALLERY] = hide }
    }

    suspend fun setDecoyPin(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DECOY_PIN] = enabled }
    }

    suspend fun setDailyReminder(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DAILY_REMINDER] = enabled }
    }

    suspend fun setCompanionAlerts(enabled: Boolean) {
        context.dataStore.edit { it[KEY_COMPANION_ALERTS] = enabled }
    }

    suspend fun setRelicAlerts(enabled: Boolean) {
        context.dataStore.edit { it[KEY_RELIC_ALERTS] = enabled }
    }

    suspend fun setPastPrompts(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PAST_PROMPTS] = enabled }
    }

    suspend fun setBlurJournals(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BLUR_JOURNALS] = enabled }
    }

    suspend fun setBetaWelcomeShown(shown: Boolean) {
        context.dataStore.edit { it[KEY_BETA_WELCOME_SHOWN] = shown }
    }

    suspend fun setAutoSaveFrequency(frequency: Float) {
        context.dataStore.edit { it[KEY_AUTO_SAVE_FREQUENCY] = frequency }
    }

    suspend fun setRealPin(pin: String?) {
        context.dataStore.edit { prefs ->
            if (pin == null) prefs.remove(KEY_REAL_PIN) else prefs[KEY_REAL_PIN] = pin
        }
    }

    suspend fun setDecoyPinValue(pin: String?) {
        context.dataStore.edit { prefs ->
            if (pin == null) prefs.remove(KEY_DECOY_PIN_VALUE) else prefs[KEY_DECOY_PIN_VALUE] = pin
        }
    }

    suspend fun setIsDecoyMode(isActive: Boolean) {
        context.dataStore.edit { it[KEY_IS_DECOY_MODE] = isActive }
    }

    suspend fun setAppPin(pin: String?) {
        context.dataStore.edit { prefs ->
            if (pin == null) prefs.remove(KEY_APP_PIN) else prefs[KEY_APP_PIN] = pin
        }
    }

    suspend fun setUseBiometricLock(useBiometric: Boolean) {
        context.dataStore.edit { it[KEY_USE_BIOMETRIC] = useBiometric }
    }

    suspend fun saveDraft(title: String, content: String, timeSpent: Long = 0L, attachmentsJson: String? = null) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DRAFT_TITLE]     = title
            prefs[KEY_DRAFT_CONTENT]   = content
            prefs[KEY_DRAFT_TIMESTAMP] = System.currentTimeMillis()
            prefs[KEY_DRAFT_TIME_SPENT] = timeSpent
            if (attachmentsJson == null) {
                prefs.remove(KEY_DRAFT_ATTACHMENTS)
            } else {
                prefs[KEY_DRAFT_ATTACHMENTS] = attachmentsJson
            }
        }
    }

    suspend fun clearDraft() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_DRAFT_TITLE)
            prefs.remove(KEY_DRAFT_CONTENT)
            prefs.remove(KEY_DRAFT_TIMESTAMP)
            prefs.remove(KEY_DRAFT_TIME_SPENT)
            prefs.remove(KEY_DRAFT_ATTACHMENTS)
        }
    }

    suspend fun setReminderTime(hour: Int, minute: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_REMINDER_HOUR]   = hour
            prefs[KEY_REMINDER_MINUTE] = minute
        }
    }

    suspend fun setDailySelectedJournal(dayKey: String, entryId: Long) {
        context.dataStore.edit { prefs ->
            val currentJson = prefs[KEY_DAILY_SELECTED_JOURNALS] ?: "{}"
            try {
                val map = org.json.JSONObject(currentJson)
                map.put(dayKey, entryId)
                prefs[KEY_DAILY_SELECTED_JOURNALS] = map.toString()
            } catch (e: Exception) {
                val map = org.json.JSONObject()
                map.put(dayKey, entryId)
                prefs[KEY_DAILY_SELECTED_JOURNALS] = map.toString()
            }
        }
    }

    suspend fun setHasLongPressedJournal() {
        context.dataStore.edit { it[KEY_HAS_LONG_PRESSED_JOURNAL] = true }
    }

    suspend fun setGoogleLoggedIn(loggedIn: Boolean) {
        context.dataStore.edit { it[KEY_GOOGLE_LOGGED_IN] = loggedIn }
    }

    suspend fun setGoogleAccountName(name: String?) {
        context.dataStore.edit { prefs ->
            if (name == null) prefs.remove(KEY_GOOGLE_ACCOUNT_NAME) else prefs[KEY_GOOGLE_ACCOUNT_NAME] = name
        }
    }

    suspend fun setGoogleAccountEmail(email: String?) {
        context.dataStore.edit { prefs ->
            if (email == null) prefs.remove(KEY_GOOGLE_ACCOUNT_EMAIL) else prefs[KEY_GOOGLE_ACCOUNT_EMAIL] = email
        }
    }

    suspend fun setGoogleAccountPhoto(photoUrl: String?) {
        context.dataStore.edit { prefs ->
            if (photoUrl == null) prefs.remove(KEY_GOOGLE_ACCOUNT_PHOTO) else prefs[KEY_GOOGLE_ACCOUNT_PHOTO] = photoUrl
        }
    }

    suspend fun setGdriveBackupEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_GDRIVE_BACKUP_ENABLED] = enabled }
    }

    suspend fun setGdriveIncludeMedia(include: Boolean) {
        context.dataStore.edit { it[KEY_GDRIVE_INCLUDE_MEDIA] = include }
    }

    suspend fun setGdriveLastSynced(timeStr: String?) {
        context.dataStore.edit { prefs ->
            if (timeStr == null) prefs.remove(KEY_GDRIVE_LAST_SYNCED) else prefs[KEY_GDRIVE_LAST_SYNCED] = timeStr
        }
    }

    suspend fun setSubscriptionPlan(plan: String) {
        context.dataStore.edit { it[KEY_SUBSCRIPTION_PLAN] = plan }
    }

    suspend fun setEncryptMedia(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ENCRYPT_MEDIA] = enabled }
    }

    suspend fun setBackupEncryptionKey(key: String?) {
        context.dataStore.edit { prefs ->
            if (key == null) prefs.remove(KEY_BACKUP_ENCRYPTION_KEY) else prefs[KEY_BACKUP_ENCRYPTION_KEY] = key
        }
    }

    suspend fun setAutoLockDelay(seconds: Int) {
        context.dataStore.edit { it[KEY_AUTO_LOCK_DELAY] = seconds.coerceIn(0, 15) }
    }

    suspend fun clearGoogleAuth() {
        context.dataStore.edit { prefs ->
            prefs[KEY_GOOGLE_LOGGED_IN] = false
            prefs.remove(KEY_GOOGLE_ACCOUNT_NAME)
            prefs.remove(KEY_GOOGLE_ACCOUNT_EMAIL)
            prefs.remove(KEY_GOOGLE_ACCOUNT_PHOTO)
        }
    }
}

