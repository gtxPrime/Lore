package com.gxdevs.athera.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gxdevs.athera.AppDatabase
import com.gxdevs.athera.JournalEntry
import com.gxdevs.athera.JournalRepository
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.core.net.toUri

class JournalViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: JournalRepository
    val allEntries: Flow<List<JournalEntry>>

    // Calculated streak based on entries
    val streakDays: Flow<Int>

    init {
        val database = AppDatabase.getDatabase(application)
        repository = JournalRepository(database)
        allEntries =
                repository.allEntries.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        streakDays =
                allEntries
                        .map { entries -> calculateStreakCorrectly(entries) }
                        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    }

    // Helper to streamline streak calc
    private fun calculateStreakCorrectly(entries: List<JournalEntry>): Int {
        if (entries.isEmpty()) return 0

        val uniqueDates =
                entries
                        .map {
                            Instant.ofEpochMilli(it.timestamp)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate()
                        }
                        .distinct()
                        .sortedDescending()

        if (uniqueDates.isEmpty()) return 0

        val today = java.time.LocalDate.now()
        val yesterday = today.minusDays(1)

        // If no entry today or yesterday, streak is broken -> 0
        val lastEntryDate = uniqueDates.first()
        if (lastEntryDate != today && lastEntryDate != yesterday) {
            return 0
        }

        var streak = 0
        var expectedDate = lastEntryDate // Start checking backwards from the latest valid entry day

        for (date in uniqueDates) {
            if (date == expectedDate) {
                streak++
                expectedDate = expectedDate.minusDays(1)
            } else {
                // Since it's sorted descending, if we skip a day, the streak breaks
                break
            }
        }

        return streak
    }

    fun addVideoEntry(videoUri: String) {
        viewModelScope.launch {
            val entry =
                    JournalEntry(
                            timestamp = System.currentTimeMillis(),
                            videoPath = videoUri,
                            isEncrypted =
                                    true // Assuming videos are sensitive/private by default in this
                            // app context
                            )
            repository.insertEntry(entry)
        }
    }

    fun addAudioEntry(audioPath: String) {
        viewModelScope.launch {
            val entry =
                    JournalEntry(
                            timestamp = System.currentTimeMillis(),
                            audioPath = audioPath,
                            isEncrypted = true
                    )
            repository.insertEntry(entry)
        }
    }

    fun addTextEntry(content: String, tags: String?) {
        viewModelScope.launch {
            val entry =
                    JournalEntry(
                            timestamp = System.currentTimeMillis(),
                            content = content,
                            tags = tags,
                            isEncrypted = true
                    )
            repository.insertEntry(entry)
        }
    }

    fun deleteEntry(entryId: Long) {
        viewModelScope.launch {
            // Find the entry to delete from the current state
            val currentList = (allEntries as? kotlinx.coroutines.flow.StateFlow)?.value ?: return@launch

            val entry = currentList.find { it.id == entryId }
            if (entry != null) {
                repository.deleteEntry(entry)

                // File cleanup
                if (entry.videoPath != null) {
                    try {
                        val file =
                                File(
                                    entry.videoPath.toUri().path
                                                ?: entry.videoPath
                                )
                        if (file.exists()) file.delete()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                if (entry.audioPath != null) {
                    try {
                        val file = File(entry.audioPath)
                        if (file.exists()) file.delete()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun updateEntry(entry: JournalEntry) {
        viewModelScope.launch {
            repository.insertEntry(entry)
        }
    }

    fun sealAsRelic(entryId: Long, unsealAfterDays: Int) {
        viewModelScope.launch {
            val currentList = (allEntries as? kotlinx.coroutines.flow.StateFlow)?.value ?: return@launch
            val entry = currentList.find { it.id == entryId } ?: return@launch
            val db = AppDatabase.getDatabase(getApplication())
            db.relicDao().insertRelic(
                com.gxdevs.athera.Relic(
                    journalEntryId = entryId,
                    sealedAtTimestamp = System.currentTimeMillis(),
                    unsealAfterDays = unsealAfterDays,
                    titleSnapshot = entry.content?.substringBefore("\n")?.take(60),
                    contentSnapshot = entry.content,
                    moodSnapshot = null
                )
            )
        }
    }
}



