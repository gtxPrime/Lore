package com.gxdevs.aethra.ui.journal

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.gxdevs.aethra.AppDatabase
import com.gxdevs.aethra.JournalEntry
import com.gxdevs.aethra.data.CustomEmotion
import com.gxdevs.aethra.utils.MediaEncryptionManager
import kotlin.collections.map
import kotlin.collections.toMutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.core.net.toUri

class AfterJournalViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val journalDao = database.journalDao()
    private val userAttributesDao = database.userAttributesDao()
    private val gson = Gson()

    // --- State ---
    private val _userEmotions =
            userAttributesDao
                    .getAllCustomEmotions()
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI Validations / Selections
    val selectedEmotions = MutableStateFlow<List<Emotion>>(emptyList())
    val attachedFiles = MutableStateFlow<List<AttachedFile>>(emptyList())

    // Combined Emotions: Defaults + Custom
    val allEmotions: StateFlow<List<Emotion>> =
            _userEmotions
                    .combine(selectedEmotions) { custom, selected ->
                        val defaults =
                                listOf(
                                        Emotion("anxious", "Anxious"),
                                        Emotion("content", "Content"),
                                        Emotion("energetic", "Energetic"),
                                        Emotion("stressed", "Stressed"),
                                        Emotion("grateful", "Grateful"),
                                        Emotion("tired", "Tired")
                                )
                        // Convert custom entity to UI model
                        val customMapped = custom.map { Emotion(it.id, it.label) }

                        // Merge: If we have separate logic or just append
                        val combined = defaults + customMapped

                        // Restore values from selection state if needed, or simpler: just expose
                        // available emotions
                        // and let UI manage selection state separately or sync it here.
                        // For simplicity, we return available emotions. The 'selectedEmotions' flow
                        // tracks what user picked.
                        combined
                    }
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
            // Save audio as pending file path if not already set, or handle separately
            // Since we added audioPath to JournalEntry, we can just track it.
            this.pendingAudioPath = audioPath
            val uri = audioPath.toUri()
            addFile(uri, FileType.FILE, "Audio Recording")
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

    fun addCustomEmotion(label: String) {
        viewModelScope.launch {
            val id = label.lowercase().replace(" ", "_")
            userAttributesDao.insertCustomEmotion(CustomEmotion(id, label))
        }
    }


    fun updateEmotionValue(id: String, value: Int) {
        val current = selectedEmotions.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) {
            current[index] = current[index].copy(value = value)
            selectedEmotions.value = current
        } else {
            // Need to fetch label from somewhere if adding for first time via slider?
            // In UI, we select first, then slide. So it should be in the list.
        }
    }

    fun toggleEmotion(emotion: Emotion) {
        val current = selectedEmotions.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.id == emotion.id }
        if (existingIndex != -1) {
            current.removeAt(existingIndex)
        } else {
            // Default NULL value (0 or special logic)? User said: "default to null"
            // My Emotion data class has value: Int. I will update it to Int? soon or handle 0 as
            // null.
            // For now assume 0 is default/unset if that fits, or change Emotion data class.
            // Let's rely on the Emotion class having nullable value
            current.add(emotion)
        }
        selectedEmotions.value = current
    }

    fun addFile(uri: Uri, type: FileType, name: String) {
        val current = attachedFiles.value.toMutableList()
        current.add(AttachedFile(System.currentTimeMillis(), type, uri, name))
        attachedFiles.value = current
    }

    fun removeFile(file: AttachedFile) {
        val current = attachedFiles.value.toMutableList()
        current.remove(file)
        attachedFiles.value = current
    }

    /** Saves a brand-new entry, or — if editMoodEntryId is set — only patches the emotions of that entry. */
    fun saveEntry(journalData: Map<String, Any?>, isRelic: Boolean = false, unlockDate: Long? = null, encryptMedia: Boolean = false) {
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
                    val enc = MediaEncryptionManager.encryptAndCopyUri(app, finalAudioPath!!)
                    if (enc != null) encEntry = encEntry.copy(audioPath = enc)
                }
                if (!finalVideoPath.isNullOrBlank() && !MediaEncryptionManager.isEncrypted(finalVideoPath)) {
                    val enc = MediaEncryptionManager.encryptAndCopyUri(app, finalVideoPath!!)
                    if (enc != null) encEntry = encEntry.copy(videoPath = enc)
                }
                if (!filesJson.isNullOrBlank()) {
                    val encJson = MediaEncryptionManager.encryptAttachmentsJson(app, filesJson)
                    encEntry = encEntry.copy(attachments = encJson)
                }
                journalDao.insertEntry(encEntry)
            }

            if (isRelic && unlockDate != null) {
                val unsealAfterDays = ((unlockDate - System.currentTimeMillis()) / 86400000L).toInt().coerceAtLeast(1)
                database.relicDao().insertRelic(
                    com.gxdevs.aethra.Relic(
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
