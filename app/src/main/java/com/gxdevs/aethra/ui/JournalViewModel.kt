package com.gxdevs.aethra.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.gxdevs.aethra.AppDatabase
import com.gxdevs.aethra.JournalEntry
import com.gxdevs.aethra.JournalRepository
import com.gxdevs.aethra.utils.MediaEncryptionManager
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

                // File cleanup — plain files
                if (entry.videoPath != null) {
                    try {
                        val file = File(entry.videoPath.toUri().path ?: entry.videoPath)
                        if (file.exists()) file.delete()
                    } catch (e: Exception) { e.printStackTrace() }
                }
                if (entry.audioPath != null) {
                    try {
                        if (MediaEncryptionManager.isEncrypted(entry.audioPath)) {
                            MediaEncryptionManager.deleteEncrypted(entry.audioPath)
                        } else {
                            val file = File(entry.audioPath)
                            if (file.exists()) file.delete()
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }

                // Encrypted attachment cleanup — parse JSON, delete any .enc files
                if (!entry.attachments.isNullOrBlank()) {
                    try {
                        val gson = Gson()
                        val raw = entry.attachments
                        if (raw.trimStart().startsWith("[{")) {
                            val listType = object : TypeToken<List<Map<String, Any>>>() {}.type
                            val list: List<Map<String, Any>> = gson.fromJson(raw, listType)
                            list.forEach { item ->
                                val uriStr = item["uri"] as? String
                                if (!uriStr.isNullOrBlank() && MediaEncryptionManager.isEncrypted(uriStr)) {
                                    MediaEncryptionManager.deleteEncrypted(uriStr)
                                }
                            }
                        } else {
                            val listType = object : TypeToken<List<String>>() {}.type
                            val uris: List<String> = gson.fromJson(raw, listType)
                            uris.forEach { uriStr ->
                                if (MediaEncryptionManager.isEncrypted(uriStr)) {
                                    MediaEncryptionManager.deleteEncrypted(uriStr)
                                }
                            }
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }
        }
    }

    fun updateEntry(entry: JournalEntry) {
        viewModelScope.launch {
            repository.insertEntry(entry)
        }
    }

    /**
     * Updates an entry and diffs the old vs new attachment lists:
     * - Deletes any orphaned .enc files from encrypted_media/ that were removed
     * - Re-encrypts any newly-added plain URIs if the entry has isEncrypted=true
     */
    fun updateEntryWithMediaDiff(oldEntry: JournalEntry, newEntry: JournalEntry) {
        viewModelScope.launch {
            val context = getApplication<android.app.Application>()
            var finalEntry = newEntry

            // ── 1. Diff attachments: delete removed .enc files ─────────────────
            val oldUris = parseAttachmentUris(oldEntry.attachments)
            val newUriSet = parseAttachmentUris(newEntry.attachments).toSet()

            oldUris.forEach { oldUri ->
                if (oldUri !in newUriSet && MediaEncryptionManager.isEncrypted(oldUri)) {
                    MediaEncryptionManager.deleteEncrypted(oldUri)
                }
            }

            // ── 2. Delete removed audioPath if it was encrypted ─────────────────
            if (oldEntry.audioPath != null && oldEntry.audioPath != newEntry.audioPath) {
                if (MediaEncryptionManager.isEncrypted(oldEntry.audioPath)) {
                    MediaEncryptionManager.deleteEncrypted(oldEntry.audioPath)
                }
            }

            // ── 3. Re-encrypt newly added plain URIs if entry is encrypted ──────
            if (newEntry.isEncrypted && !newEntry.attachments.isNullOrBlank()) {
                try {
                    val encJson = MediaEncryptionManager.encryptAttachmentsJson(context, newEntry.attachments)
                    finalEntry = finalEntry.copy(attachments = encJson)
                } catch (e: Exception) { e.printStackTrace() }
            }

            // ── 4. Re-encrypt new audio if needed ───────────────────────────────
            if (newEntry.isEncrypted &&
                !newEntry.audioPath.isNullOrBlank() &&
                !MediaEncryptionManager.isEncrypted(newEntry.audioPath)) {
                val enc = MediaEncryptionManager.encryptAndCopyUri(context, newEntry.audioPath!!)
                if (enc != null) finalEntry = finalEntry.copy(audioPath = enc)
            }

            repository.insertEntry(finalEntry)
        }
    }

    /** Parses both [{uri,...}] and ["uri"] attachment formats, returns flat list of URI strings. */
    private fun parseAttachmentUris(attachments: String?): List<String> {
        if (attachments.isNullOrBlank()) return emptyList()
        return try {
            val trimmed = attachments.trim()
            if (trimmed.startsWith("[{")) {
                val listType = object : TypeToken<List<Map<String, Any>>>() {}.type
                val list: List<Map<String, Any>> = Gson().fromJson(attachments, listType)
                list.mapNotNull { it["uri"] as? String }
            } else {
                val listType = object : TypeToken<List<String>>() {}.type
                Gson().fromJson(attachments, listType)
            }
        } catch (e: Exception) { emptyList() }
    }


    fun sealAsRelic(entryId: Long, unsealAfterDays: Int) {
        viewModelScope.launch {
            val currentList = (allEntries as? kotlinx.coroutines.flow.StateFlow)?.value ?: return@launch
            val entry = currentList.find { it.id == entryId } ?: return@launch
            val db = AppDatabase.getDatabase(getApplication())
            val unlockDateMs = System.currentTimeMillis() + unsealAfterDays.toLong() * 86400000L
            
            // 1. Update the entry itself
            val updatedEntry = entry.copy(
                isTimeCapsule = true,
                unlockDate = unlockDateMs
            )
            repository.insertEntry(updatedEntry)

            // 2. Insert into relics table
            db.relicDao().insertRelic(
                com.gxdevs.aethra.Relic(
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



