package com.gxdevs.athera.data

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
        // Pet Growth Selection
        val KEY_DAILY_SELECTED_JOURNALS = stringPreferencesKey("daily_selected_journals")
        val KEY_HAS_LONG_PRESSED_JOURNAL = booleanPreferencesKey("has_long_pressed_journal")
        // Lock method
        val KEY_USE_BIOMETRIC           = booleanPreferencesKey("use_biometric")
        // Daily reminder time (default: 10:00 AM)
        val KEY_REMINDER_HOUR           = intPreferencesKey("reminder_hour")
        val KEY_REMINDER_MINUTE         = intPreferencesKey("reminder_minute")
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

    val reminderHour: Flow<Int> = context.dataStore.data
        .map { it[KEY_REMINDER_HOUR] ?: 22 }

    val reminderMinute: Flow<Int> = context.dataStore.data
        .map { it[KEY_REMINDER_MINUTE] ?: 0 }

    val dailySelectedJournals: Flow<String> = context.dataStore.data
        .map { it[KEY_DAILY_SELECTED_JOURNALS] ?: "{}" }

    val hasLongPressedJournal: Flow<Boolean> = context.dataStore.data
        .map { it[KEY_HAS_LONG_PRESSED_JOURNAL] ?: false }

    // â”€â”€â”€ Setters â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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

    suspend fun saveDraft(title: String, content: String, timeSpent: Long = 0L) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DRAFT_TITLE]     = title
            prefs[KEY_DRAFT_CONTENT]   = content
            prefs[KEY_DRAFT_TIMESTAMP] = System.currentTimeMillis()
            prefs[KEY_DRAFT_TIME_SPENT] = timeSpent
        }
    }

    suspend fun clearDraft() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_DRAFT_TITLE)
            prefs.remove(KEY_DRAFT_CONTENT)
            prefs.remove(KEY_DRAFT_TIMESTAMP)
            prefs.remove(KEY_DRAFT_TIME_SPENT)
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
}

