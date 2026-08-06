package com.gxdevs.lore.ui.journal

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.gxdevs.lore.data.AppDatabase
import com.gxdevs.lore.data.journal.JournalEntry
import com.gxdevs.lore.data.mood.MoodConstants
import com.gxdevs.lore.utils.AdaptiveMoodModel
import com.gxdevs.lore.utils.EmotionModelManager
import com.gxdevs.lore.utils.LanguageDetector
import com.gxdevs.lore.utils.MediaEncryptionManager
import com.gxdevs.lore.utils.MoodScoringEngine
import com.gxdevs.lore.utils.SentimentScorer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.net.toUri

/**
 * ViewModel for the AfterJournal screen.
 *
 * Orchestrates the 4-layer on-device AI pipeline:
 *
 * Layer 1 — MoodScoringEngine  (pure Kotlin lexicon, always runs, 0 KB overhead)
 * Layer 2 — SentimentScorer    (pure Kotlin VADER-style algorithm, 0 KB overhead)
 * Layer 3 — ML Kit Language ID (bundled, 110 languages, ~900KB in APK)
 * Layer 4 — MediaPipe TextClassifier (MobileBERT INT8, ~20MB in assets, optional)
 * Layer 5 — AdaptiveMoodModel  (Naive Bayes via Room, personalises over time)
 *
 * Privacy: All processing is 100% on-device. No user text ever leaves the device.
 */
class AfterJournalViewModel(application: Application) : AndroidViewModel(application) {

    private val database  = AppDatabase.getDatabase(application)
    private val journalDao = database.journalDao()
    private val moodWeightDao = database.moodWordWeightDao()
    private val gson = Gson()

    // ── UI State ──────────────────────────────────────────────────────────────

    val selectedEmotions = MutableStateFlow<List<Emotion>>(emptyList())
    val attachedFiles    = MutableStateFlow<List<AttachedFile>>(emptyList())

    /** Full AI analysis result — used to display confidence, tags, scores in UI. */
    private val _moodAnalysis = MutableStateFlow<MoodAnalysisUiState>(MoodAnalysisUiState())
    val moodAnalysis: StateFlow<MoodAnalysisUiState> = _moodAnalysis.asStateFlow()

    /** True while AI analysis is running in background. */
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    // ── Pending Entry Data ────────────────────────────────────────────────────

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

    // ── Initial Data + AI Analysis ────────────────────────────────────────────

    fun setInitialData(
        type: String,
        content: String?,
        tags: String?,
        filePath: String?,
        audioPath: String? = null,
        mediaUrisJson: String? = null,
        timeSpent: Long = 0L,
        formatRangesJson: String? = null
    ) {
        pendingType = type
        pendingContent = content
        pendingTags = tags
        pendingFilePath = filePath
        pendingTimeSpent = timeSpent
        pendingFormatRanges = formatRangesJson

        // Handle audio
        if (audioPath != null && audioPath != "null") {
            pendingAudioPath = audioPath
        }

        // Handle media URIs
        if (mediaUrisJson != null && mediaUrisJson != "null") {
            try {
                val uris = gson.fromJson(mediaUrisJson, Array<String>::class.java)
                uris.forEach { uriString ->
                    val uri = uriString.toUri()
                    val typeEnum = if (uriString.contains("video")) FileType.VIDEO else FileType.IMAGE
                    addFile(uri, typeEnum, "Attached Media")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Handle direct file path
        if (filePath != null && filePath != "null" && attachedFiles.value.isEmpty()) {
            val uri = filePath.toUri()
            val fileType = when (type) {
                "video" -> FileType.VIDEO
                "audio" -> FileType.FILE
                else    -> FileType.FILE
            }
            val name = if (type == "video") "Video Recording" else "Audio Recording"
            addFile(uri, fileType, name)
        }

        // Launch the full AI pipeline in background
        runAiAnalysis(content)
    }

    /**
     * Runs the full 4-layer AI pipeline on [text].
     * Results are published to [_moodAnalysis] and pre-select the emotion chip.
     */
    private fun runAiAnalysis(text: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            _isAnalyzing.value = true

            try {
                // ── Layer 1: Lexicon Engine (always runs, <1ms) ───────────────
                val lexiconResult = MoodScoringEngine.analyze(
                    text,
                    runLanguageDetection = false // language done separately below
                )

                // ── Layer 2: VADER-style Sentiment Scorer (<2ms) ──────────────
                val sentimentResult = SentimentScorer.analyze(text)
                val sentimentAdjusted = SentimentScorer.toMoodAdjustments(
                    sentimentResult,
                    lexiconResult.moodScores
                )

                // ── Layer 3: ML Kit Language Detection (<5ms, bundled) ────────
                val langResult = LanguageDetector.detectSync(text)
                val langBoost  = LanguageDetector.lexiconWeightFor(langResult.bcp47Code)
                val langIsEn   = langResult.bcp47Code == "en" || langResult.isUndetermined

                // Apply language weight to adjusted scores
                val langAdjusted = sentimentAdjusted.mapValues { (mood, score) ->
                    when (mood) {
                        // Language boost only helps non-English entries find their category
                        MoodConstants.HEAVY, MoodConstants.TANGLED,
                        MoodConstants.DARK, MoodConstants.BRIGHT ->
                            if (!langIsEn) (score * langBoost).coerceIn(0f, 100f) else score
                        else -> score
                    }
                }

                // ── Layer 4: MediaPipe MobileBERT (~15–50ms if model present) ─
                val modelResult = EmotionModelManager.classify(text)
                val modelMerged = EmotionModelManager.mergeWithLexiconScores(
                    modelResult, langAdjusted, langIsEn
                )

                // ── Layer 5: Voice Note Acoustic Analysis (if audio attached) ──
                val audioFileToAnalyze = pendingAudioPath ?: if (pendingType == "audio") pendingFilePath else null
                val acousticSignal = com.gxdevs.lore.utils.VoiceNoteAnalyzer.analyzeAudioFile(audioFileToAnalyze)
                
                // ── Layer 6: Adaptive Model (Room Naive Bayes, personalised) ──
                val adaptiveAdjustments = AdaptiveMoodModel.getLearnedScoreAdjustments(
                    moodWeightDao, text
                )

                // Merge adaptive adjustments and acoustic signal into final scores
                val finalScores = modelMerged.toMutableMap()
                
                // Apply acoustic voice note boost if signal detected
                if (acousticSignal.durationMs > 0L) {
                    val acousticMoodKey = MoodConstants.ALL_MOODS.firstOrNull {
                        it.equals(acousticSignal.acousticMoodHint, ignoreCase = true)
                    } ?: MoodConstants.CALM
                    finalScores[acousticMoodKey] = ((finalScores[acousticMoodKey] ?: 0f) + 25f).coerceIn(0f, 100f)
                }

                for ((mood, bonus) in adaptiveAdjustments) {
                    finalScores[mood] = ((finalScores[mood] ?: 0f) + bonus).coerceIn(0f, 100f)
                }

                // ── Compute final top mood ────────────────────────────────────
                val topEntry    = finalScores.maxByOrNull { it.value }!!
                val sortedVals  = finalScores.values.sortedDescending()
                val gap         = (sortedVals.getOrElse(0) { 0f } - sortedVals.getOrElse(1) { 0f })
                val rawConf     = (gap / 100f).coerceIn(0f, 1f)

                // Blend confidence: lexicon gap + VADER signal + model confidence
                val modelConfBonus = if (modelResult.modelRan) modelResult.confidence * 0.15f else 0f
                val langBonus      = if (!langResult.isUndetermined) 0.05f else 0f
                val finalConfidence = (rawConf * 0.55f + sentimentResult.compound * 0.15f +
                                       modelConfBonus + langBonus).coerceIn(0.10f, 0.97f)

                val topMood = topEntry.key

                // Build UI state
                val uiState = MoodAnalysisUiState(
                    predictedMood   = topMood,
                    moodScores      = finalScores,
                    sentimentScore  = sentimentResult.compound,
                    confidence      = finalConfidence,
                    suggestedTags   = lexiconResult.suggestedTags,
                    detectedLanguage = LanguageDetector.languageNameFor(langResult.bcp47Code),
                    modelUsed       = when {
                        modelResult.modelRan -> "MobileBERT + Lexicon"
                        else -> "Lexicon + VADER"
                    },
                    isAnalysisDone  = true
                )

                withContext(Dispatchers.Main) {
                    _moodAnalysis.value = uiState
                    // Pre-select the predicted mood as the emotion chip
                    selectedEmotions.value = listOf(
                        Emotion(topMood.lowercase(), topMood)
                    )
                }
            } catch (e: Exception) {
                // Fallback: just use CALM
                withContext(Dispatchers.Main) {
                    selectedEmotions.value = listOf(Emotion("calm", MoodConstants.CALM))
                    _moodAnalysis.value = MoodAnalysisUiState(
                        predictedMood  = MoodConstants.CALM,
                        isAnalysisDone = true
                    )
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _isAnalyzing.value = false
                }
            }
        }
    }

    // ── Emotion Selection ─────────────────────────────────────────────────────

    fun selectEmotion(emotion: Emotion) {
        selectedEmotions.value = listOf(emotion)
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

    // ── Save Entry ────────────────────────────────────────────────────────────

    fun saveEntry(
        isRelic: Boolean = false,
        unlockDate: Long? = null,
        encryptMedia: Boolean = false,
        onComplete: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                val emotionsJson     = gson.toJson(selectedEmotions.value)
                val selectedMoodName = selectedEmotions.value.firstOrNull()?.label ?: MoodConstants.CALM
                val analysis         = _moodAnalysis.value

                // ── Train adaptive model from confirmed user mood ─────────────────
                // This is the key self-learning step: user's final mood choice
                // teaches the Room Naive Bayes model their personal language patterns.
                AdaptiveMoodModel.trainFromUserFeedback(
                    dao           = moodWeightDao,
                    text          = pendingContent,
                    confirmedMood = selectedMoodName
                )

                // ── Mood-only edit path ───────────────────────────────────────────
                val moodEditId = editMoodEntryId
                if (moodEditId != null) {
                    val existing = journalDao.getEntryById(moodEditId)
                    if (existing != null) {
                        journalDao.insertEntry(existing.copy(emotions = emotionsJson))
                    }
                    return@withContext
                }

                // ── Full new entry path ───────────────────────────────────────────
                val filesJson = gson.toJson(
                    attachedFiles.value.map {
                        mapOf("uri" to it.uri.toString(), "type" to it.type, "name" to it.name)
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

                val newEntry = JournalEntry(
                    timestamp        = System.currentTimeMillis(),
                    emotions         = emotionsJson,
                    attachments      = filesJson,
                    content          = pendingContent,
                    tags             = pendingTags,
                    videoPath        = finalVideoPath,
                    audioPath        = finalAudioPath,
                    timeSpentWriting = pendingTimeSpent,
                    promptResponses  = pendingFormatRanges,
                    isTimeCapsule    = isRelic,
                    unlockDate       = unlockDate,
                    // AI metadata persisted for future stats
                    detectedMood     = analysis.predictedMood,
                    moodConfidence   = analysis.confidence,
                    sentimentScore   = analysis.sentimentScore,
                    aiTags           = gson.toJson(analysis.suggestedTags)
                )

                val entryId = journalDao.insertEntry(newEntry)

                // Media encryption
                if (encryptMedia) {
                    val app = getApplication<Application>()
                    var encEntry = newEntry.copy(id = entryId, isEncrypted = true)

                    if (!finalAudioPath.isNullOrBlank() && !MediaEncryptionManager.isEncrypted(finalAudioPath)) {
                        MediaEncryptionManager.encryptAndCopyUri(app, finalAudioPath)?.let {
                            encEntry = encEntry.copy(audioPath = it)
                        }
                    }
                    if (!finalVideoPath.isNullOrBlank() && !MediaEncryptionManager.isEncrypted(finalVideoPath)) {
                        MediaEncryptionManager.encryptAndCopyUri(app, finalVideoPath)?.let {
                            encEntry = encEntry.copy(videoPath = it)
                        }
                    }
                    if (!filesJson.isNullOrBlank()) {
                        val encJson = MediaEncryptionManager.encryptAttachmentsJson(app, filesJson)
                        encEntry = encEntry.copy(attachments = encJson)
                    }
                    journalDao.insertEntry(encEntry)
                }

                // Time capsule (Relic)
                if (isRelic && unlockDate != null) {
                    val unsealAfterDays = ((unlockDate - System.currentTimeMillis()) / 86_400_000L)
                        .toInt().coerceAtLeast(1)
                    database.relicDao().insertRelic(
                        com.gxdevs.lore.data.relic.Relic(
                            journalEntryId  = entryId,
                            sealedAtTimestamp = System.currentTimeMillis(),
                            unsealAfterDays = unsealAfterDays,
                            titleSnapshot   = pendingContent?.substringBefore("\n")?.take(60),
                            contentSnapshot = pendingContent,
                            moodSnapshot    = selectedMoodName
                        )
                    )
                }

                // Recalculate pet progress and trigger level-up celebration check
                try {
                    com.gxdevs.lore.ui.pets.PetViewModel(getApplication()).onJournalSaved()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            withContext(Dispatchers.Main) {
                onComplete?.invoke()
            }
        }
    }
}

// ── UI State Data Classes ─────────────────────────────────────────────────────

/**
 * UI state exposed to [AfterJournalRecord] representing the full AI analysis.
 */
data class MoodAnalysisUiState(
    val predictedMood   : String            = MoodConstants.CALM,
    val moodScores      : Map<String, Float> = emptyMap(),
    val sentimentScore  : Float             = 0f,
    /** 0.0–1.0 — shown in UI as a confidence bar or percentage. */
    val confidence      : Float             = 0f,
    val suggestedTags   : List<String>      = emptyList(),
    val detectedLanguage: String            = "",
    val modelUsed       : String            = "",
    val isAnalysisDone  : Boolean           = false
)
