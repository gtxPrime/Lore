package com.gxdevs.aethra.ui.journal

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.gxdevs.aethra.data.AppDatabase
import com.gxdevs.aethra.data.journal.JournalEntry
import com.gxdevs.aethra.utils.MediaEncryptionManager
import kotlin.collections.map
import kotlin.collections.toMutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import androidx.core.net.toUri

class AfterJournalViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val journalDao = database.journalDao()
    private val gson = Gson()

    // UI Validations / Selections
    val selectedEmotions = MutableStateFlow<List<Emotion>>(emptyList())
    val attachedFiles = MutableStateFlow<List<AttachedFile>>(emptyList())

    // --- Pending Data from Recording ---
    private var pendingType: String = "text"
    private var pendingContent: String? = null
    private var pendingTags: String? = null
    private var pendingFilePath: String? = null
    private var pendingAudioPath: String? = null
    private var pendingTimeSpent: Long = 0L
    private var pendingFormatRanges: String? = null

    /** When non-null, saveEntry will UPDATE only the emotions of this existing entry. */
    var editMoodEntryId: Long? = null
        private set

    fun setEditMoodEntryId(id: Long?) {
        editMoodEntryId = id
    }

    fun setInitialData(type: String, content: String?, tags: String?, filePath: String?, audioPath: String? = null, mediaUrisJson: String? = null, timeSpent: Long = 0L, formatRangesJson: String? = null) {
        this.pendingType = type
        this.pendingContent = content
        this.pendingTags = tags
        this.pendingFilePath = filePath
        this.pendingTimeSpent = timeSpent
        this.pendingFormatRanges = formatRangesJson
        
        if (audioPath != null && audioPath != "null") {
            // Audio is stored directly in JournalEntry.audioPath — do NOT also add it
            // to attachedFiles, which would cause it to appear in both audioPath AND
            // attachments JSON (leading to duplicate files on export and double-encryption).
            this.pendingAudioPath = audioPath
        }

        if (mediaUrisJson != null && mediaUrisJson != "null") {
            try {
                val uris = gson.fromJson(mediaUrisJson, Array<String>::class.java)
                uris.forEach { uriString ->
                val uri = uriString.toUri()
                    // Guess type based on extension or just use IMAGE
                    val typeEnum = if (uriString.contains("video")) FileType.VIDEO else FileType.IMAGE
                    addFile(uri, typeEnum, "Attached Media")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Optionally pre-populate attachedFiles if a file was passed directly
        if (filePath != null && filePath != "null" && attachedFiles.value.isEmpty()) {
            val uri = filePath.toUri()
            val fileType =
                    when (type) {
                        "video" -> FileType.VIDEO
                        "audio" -> FileType.FILE 
                        else -> FileType.FILE
                    }
            val name = if (type == "video") "Video Recording" else "Audio Recording"
            addFile(uri, fileType, name)
        }
    }


    fun toggleEmotion(emotion: Emotion) {
        val current = selectedEmotions.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.id == emotion.id }
        if (existingIndex != -1) {
            current.removeAt(existingIndex)
        } else {
            current.add(emotion)
        }
        selectedEmotions.value = current
    }

    fun addFile(uri: Uri, type: FileType, name: String) {
        val current = attachedFiles.value.toMutableList()
        current.add(AttachedFile(System.currentTimeMillis(), type, uri, name))
        attachedFiles.value = current
    }

    fun saveEntry(isRelic: Boolean = false, unlockDate: Long? = null, encryptMedia: Boolean = false) {
        viewModelScope.launch {
            val emotionsJson = gson.toJson(selectedEmotions.value)

            val moodEditId = editMoodEntryId
            if (moodEditId != null) {
                // Mood-only update path: patch emotions field on existing entry
                val existing = journalDao.getEntryById(moodEditId)
                if (existing != null) {
                    journalDao.insertEntry(existing.copy(emotions = emotionsJson))
                }
                return@launch
            }

            // Full new-entry path
            val filesJson =
                    gson.toJson(
                            attachedFiles.value.map {
                                mapOf(
                                        "uri" to it.uri.toString(),
                                        "type" to it.type,
                                        "name" to it.name
                                )
                            }
                    )


            var finalVideoPath: String? = null
            var finalAudioPath: String? = null

            if (pendingType == "video" && pendingFilePath != null && pendingFilePath != "null") {
                finalVideoPath = pendingFilePath
            } else if (pendingType == "audio" && pendingFilePath != null && pendingFilePath != "null") {
                finalAudioPath = pendingFilePath
            }
            if (pendingAudioPath != null && pendingAudioPath != "null") {
                finalAudioPath = pendingAudioPath
            }

            val newEntry =
                    JournalEntry(
                            timestamp = System.currentTimeMillis(),
                            emotions = emotionsJson,
                            attachments = filesJson,
                            content = pendingContent,
                            tags = pendingTags,
                            videoPath = finalVideoPath,
                            audioPath = finalAudioPath,
                            timeSpentWriting = pendingTimeSpent,
                            promptResponses = pendingFormatRanges,
                            isTimeCapsule = isRelic,
                            unlockDate = unlockDate
                    )
            val entryId = journalDao.insertEntry(newEntry)

            // If Encrypt Media is ON, encrypt all media and update the DB record
            if (encryptMedia) {
                val app = getApplication<Application>()
                var encEntry = newEntry.copy(id = entryId, isEncrypted = true)

                if (!finalAudioPath.isNullOrBlank() && !MediaEncryptionManager.isEncrypted(finalAudioPath)) {
                    val enc = MediaEncryptionManager.encryptAndCopyUri(app, finalAudioPath)
                    if (enc != null) encEntry = encEntry.copy(audioPath = enc)
                }
                if (!finalVideoPath.isNullOrBlank() && !MediaEncryptionManager.isEncrypted(finalVideoPath)) {
                    val enc = MediaEncryptionManager.encryptAndCopyUri(app, finalVideoPath)
                    if (enc != null) encEntry = encEntry.copy(videoPath = enc)
                }
                if (!filesJson.isNullOrBlank()) {
                    // encryptAttachmentsJson only re-encrypts items not already encrypted,
                    // so this is safe even if some URIs are already .enc paths.
                    val encJson = MediaEncryptionManager.encryptAttachmentsJson(app, filesJson)
                    encEntry = encEntry.copy(attachments = encJson)
                }
                journalDao.insertEntry(encEntry)
            }

            if (isRelic && unlockDate != null) {
                val unsealAfterDays = ((unlockDate - System.currentTimeMillis()) / 86400000L).toInt().coerceAtLeast(1)
                database.relicDao().insertRelic(
                    com.gxdevs.aethra.data.relic.Relic(
                        journalEntryId = entryId,
                        sealedAtTimestamp = System.currentTimeMillis(),
                        unsealAfterDays = unsealAfterDays,
                        titleSnapshot = pendingContent?.substringBefore("\n")?.take(60),
                        contentSnapshot = pendingContent,
                        moodSnapshot = selectedEmotions.value.firstOrNull()?.label
                    )
                )
            }
        }
    }
}
