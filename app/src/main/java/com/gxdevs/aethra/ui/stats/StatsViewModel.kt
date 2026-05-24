package com.gxdevs.aethra.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gxdevs.aethra.AppDatabase
import com.gxdevs.aethra.JournalEntry
import com.gxdevs.aethra.JournalRepository
import com.gxdevs.aethra.MoodConstants
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.core.net.toUri

class StatsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: JournalRepository

    private val _statsState: StateFlow<StatsState>
    val statsState: StateFlow<StatsState> get() = _statsState

    init {
        val database = AppDatabase.getDatabase(application)
        repository = JournalRepository(database)

        _statsState = repository.allEntries.map { entries -> calculateStats(entries) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsState())
    }

    private fun calculateStats(entries: List<JournalEntry>): StatsState {
        if (entries.isEmpty()) return StatsState()

        val calendar = java.util.Calendar.getInstance()
        val daysSet = mutableSetOf<String>()
        var totalWords = 0
        var totalTimeSpent = 0L
        
        var textCount = 0
        var voiceCount = 0
        var photoCount = 0
        var videoCount = 0
        
        val hourCounts = IntArray(24)
        val emotionCounts = mutableMapOf<String, Int>()
        val afterDarkEmotionCounts = mutableMapOf<String, Int>()
        
        // For 'This Week' view (Mon-Sun)
        val todayCal = java.util.Calendar.getInstance()
        val dow = todayCal.get(java.util.Calendar.DAY_OF_WEEK)
        val diffToMon = if (dow == java.util.Calendar.SUNDAY) -6 else java.util.Calendar.MONDAY - dow
        val startOfWeek = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_YEAR, diffToMon)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            clear(java.util.Calendar.MINUTE)
            clear(java.util.Calendar.SECOND)
            clear(java.util.Calendar.MILLISECOND)
        }.timeInMillis
        val endOfWeek = startOfWeek + 7 * 24 * 60 * 60 * 1000L
        
        val thisWeekMoodCounts = Array(7) { mutableMapOf<String, Int>() }

        val sortedEntries = entries.sortedBy { it.timestamp }
        val dates = sortedEntries.map { 
            calendar.timeInMillis = it.timestamp
            val y = calendar.get(java.util.Calendar.YEAR)
            val d = calendar.get(java.util.Calendar.DAY_OF_YEAR)
            Pair(y, d)
        }.distinct()

        var currentStreak = 0
        var longestStreak = 0
        var tempStreak = 0
        var prevDate: Pair<Int, Int>? = null

        val todayCalendar = java.util.Calendar.getInstance()
        val todayY = todayCalendar.get(java.util.Calendar.YEAR)
        val todayD = todayCalendar.get(java.util.Calendar.DAY_OF_YEAR)

        for (date in dates) {
            if (prevDate == null) {
                tempStreak = 1
            } else {
                val isConsecutive = (date.first == prevDate.first && date.second == prevDate.second + 1) ||
                                    (date.first == prevDate.first + 1 && date.second == 1 && prevDate.second >= 365)
                if (isConsecutive) {
                    tempStreak++
                } else {
                    tempStreak = 1
                }
            }
            if (tempStreak > longestStreak) longestStreak = tempStreak
            prevDate = date
        }

        if (prevDate != null) {
            val isToday = prevDate.first == todayY && prevDate.second == todayD
            val isYesterday = (prevDate.first == todayY && prevDate.second == todayD - 1) || 
                              (prevDate.first == todayY - 1 && todayD == 1 && prevDate.second >= 365)
            if (isToday || isYesterday) {
                currentStreak = tempStreak
            }
        }

        val cr = getApplication<Application>().contentResolver

        for (entry in entries) {
            calendar.timeInMillis = entry.timestamp
            val dateStr = "${calendar.get(java.util.Calendar.YEAR)}-${calendar.get(java.util.Calendar.DAY_OF_YEAR)}"
            daysSet.add(dateStr)

            val content = entry.content ?: ""
            totalWords += if (content.isBlank()) 0 else content.trim().split(Regex("\\s+")).size
            totalTimeSpent += entry.timeSpentWriting ?: 0L

            val hasText = !entry.content.isNullOrBlank()

            // --- Media counting ---
            // Two storage formats exist:
            //   Object-list (AfterJournalViewModel): [{uri, type:"IMAGE"|"VIDEO"|"FILE", name}]
            //     → audio appears as FILE in the list AND in audioPath — count from list only.
            //   Plain URI-list (TextJournalScreen):  ["content://...", ...]
            //     → visual media only; audio is in audioPath, legacy video in videoPath.
            var entryVoice = 0
            var entryPhoto = 0
            var entryVideo = 0

            val attachmentsRaw = entry.attachments
            if (!attachmentsRaw.isNullOrBlank()) {
                try {
                    if (attachmentsRaw.trimStart().startsWith("[{")) {
                        // --- Object-list format ---
                        // audioPath is redundant here — count only from typed list entries.
                        val objectListType = object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type
                        val list: List<Map<String, Any>> = com.google.gson.Gson().fromJson(
                            attachmentsRaw, objectListType)
                        for (item in list) {
                            when ((item["type"] as? String)?.uppercase()) {
                                "IMAGE" -> entryPhoto++
                                "VIDEO" -> entryVideo++
                                "FILE"  -> entryVoice++ // audio stored as FILE attachment
                            }
                        }
                        // videoPath is a separate legacy field not duplicated in this format
                        if (!entry.videoPath.isNullOrBlank() && entry.videoPath != "null") entryVideo++
                    } else {
                        // --- Plain URI-string list ---
                        // Use ContentResolver MIME type for accurate image vs video classification.
                        val uris: List<String> = com.google.gson.Gson().fromJson(
                            attachmentsRaw, object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
                        )
                        for (uriStr in uris) {
                            val mime = try { cr.getType(uriStr.toUri()) } catch (_: Exception) { null }
                            when {
                                mime?.startsWith("video") == true -> entryVideo++
                                mime?.startsWith("audio") == true -> entryVoice++
                                else -> entryPhoto++ // image/* or unknown → treat as photo
                            }
                        }
                        // Audio and video stored separately in this format
                        if (!entry.audioPath.isNullOrBlank() && entry.audioPath != "null") entryVoice++
                        if (!entry.videoPath.isNullOrBlank() && entry.videoPath != "null") entryVideo++
                    }
                } catch (_: Exception) {
                    // Parse error — fall back to legacy fields only
                    if (!entry.audioPath.isNullOrBlank() && entry.audioPath != "null") entryVoice++
                    if (!entry.videoPath.isNullOrBlank() && entry.videoPath != "null") entryVideo++
                }
            } else {
                // No attachments column — count standalone legacy fields
                if (!entry.audioPath.isNullOrBlank() && entry.audioPath != "null") entryVoice++
                if (!entry.videoPath.isNullOrBlank() && entry.videoPath != "null") entryVideo++
            }

            if (hasText) textCount++
            voiceCount += entryVoice
            photoCount += entryPhoto
            videoCount += entryVideo

            val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
            hourCounts[hour]++
            
            val isAfterDark = hour !in 5..<18

            if (!entry.emotions.isNullOrBlank()) {
                try {
                    val ems = com.google.gson.Gson().fromJson(entry.emotions, Array<com.gxdevs.aethra.ui.journal.Emotion>::class.java)
                    ems.forEach { em ->
                        val category = MoodConstants.emotionToMood(em.label)
                        emotionCounts[category] = emotionCounts.getOrDefault(category, 0) + 1
                        
                        if (isAfterDark) {
                            afterDarkEmotionCounts[category] = afterDarkEmotionCounts.getOrDefault(category, 0) + 1
                        }
                        
                        if (entry.timestamp in startOfWeek until endOfWeek) {
                            val dayIndex = (calendar.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7 // Mon=0, Sun=6
                            thisWeekMoodCounts[dayIndex][category] = thisWeekMoodCounts[dayIndex].getOrDefault(category, 0) + 1
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        val mostCommonHour = hourCounts.indices.maxByOrNull { hourCounts[it] } ?: 12
        val amPm = if (mostCommonHour >= 12) "pm" else "am"
        val hour12 = if (mostCommonHour % 12 == 0) 12 else mostCommonHour % 12
        val mostCommonHourStr = "$hour12:00 $amPm"
        
        val totalEmotions = emotionCounts.values.sum()
        val emotionPercents = emotionCounts.mapValues { if (totalEmotions > 0) it.value.toFloat() / totalEmotions else 0f }
        
        val allTimeDominant = emotionCounts.maxByOrNull { it.value }?.key
        val afterDarkFeeling = afterDarkEmotionCounts.maxByOrNull { it.value }?.key

        val latelyFeeling = if (entries.isNotEmpty()) {
            val recent = entries.takeLast(3) // Sorted by timestamp ascending, so takeLast gives newest
            var recentE: String? = null
            for (i in recent.indices.reversed()) {
                try {
                    if (!recent[i].emotions.isNullOrBlank()) {
                        val ems = com.google.gson.Gson().fromJson(recent[i].emotions, Array<com.gxdevs.aethra.ui.journal.Emotion>::class.java)
                        if (ems.isNotEmpty()) {
                            recentE = MoodConstants.emotionToMood(ems[0].label)
                            break
                        }
                    }
                } catch (_: Exception) {}
            }
            recentE
        } else null
        
        val thisWeekMoods = thisWeekMoodCounts.map { counts ->
            counts.maxByOrNull { it.value }?.key
        }

        return StatsState(
            daysShowedUp = daysSet.size,
            wordsWoven = totalWords,
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            timeInsideSec = totalTimeSpent,
            mostCommonHourStr = mostCommonHourStr,
            textCount = textCount,
            voiceCount = voiceCount,
            photoCount = photoCount,
            videoCount = videoCount,
            emotionPercents = emotionPercents,
            allTimeDominant = allTimeDominant,
            latelyFeeling = latelyFeeling,
            mostCommonHourRaw = mostCommonHour,
            afterDarkFeeling = afterDarkFeeling,
            thisWeekMoods = thisWeekMoods
        )
    }
}

data class StatsState(
    val daysShowedUp: Int = 0,
    val wordsWoven: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val timeInsideSec: Long = 0L,
    val mostCommonHourStr: String = "11:00 pm",
    val mostCommonHourRaw: Int = 23,
    val textCount: Int = 0,
    val voiceCount: Int = 0,
    val photoCount: Int = 0,
    val videoCount: Int = 0,
    val emotionPercents: Map<String, Float> = emptyMap(),
    val allTimeDominant: String? = null,
    val latelyFeeling: String? = null,
    val afterDarkFeeling: String? = null,
    val thisWeekMoods: List<String?> = List(7) { null }
)

