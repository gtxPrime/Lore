package com.gxdevs.nurtale.ui.pets

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.gxdevs.nurtale.data.AppDatabase
import com.gxdevs.nurtale.data.mood.MoodConstants
import com.gxdevs.nurtale.pets.PetProgress
import com.gxdevs.nurtale.pets.PetDefinition
import com.gxdevs.nurtale.pets.PetStageDefinition
import com.gxdevs.nurtale.pets.PetImageCache
import com.gxdevs.nurtale.ui.journal.Emotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

// --- UI model ------------------------------------------------------------------------------------

data class PetUiState(
    val petId: String,
    val name: String,
    val emotion: String,         // lowercase: "bright", "calm", …
    val moodId: String,          // capitalized: "Bright" - for MoodConstants colour lookup
    val level: Int,
    val description: String,
    val journalCount: Int,       // effective journals for this pet's level
    val stageIndex: Int,         // 0-based; -1 = locked
    val stageName: String,
    val totalStages: Int,
    val progressInStage: Float,
    val journalsToNext: Int,
    val isFullyGrown: Boolean,
    val currentStageImageUrl: String?,   // Cloudinary / Dropbox URL (download trigger)
    val localImagePath: String?          // absolute local path if cached; null = not yet downloaded
)

data class PetsScreenState(
    val pets: List<PetUiState> = emptyList(),
    val isLoading: Boolean = true,
    val isDemoMode: Boolean = false,         // Default OFF for real users
    val demoStageOverride: Int? = null,      // null = Auto/Natural; 0 = Egg, 1 = Stage 2, …
    val useEmojiVisuals: Boolean = false,    // false = load real Dropbox images; true = emoji fallback
    val demoPetIndex: Int = 0
)

// --- ViewModel -----------------------------------------------------------------------------------

class PetViewModel(application: Application) : AndroidViewModel(application) {

    private val db             = AppDatabase.getDatabase(application)
    private val journalDao     = db.journalDao()
    private val petProgressDao  = db.petProgressDao()
    private val defDao         = db.petDefinitionDao()
    private val stageDao       = db.petStageDefinitionDao()
    private val cacheDao       = db.cachedStageImageDao()
    private val gson           = Gson()
    private val imageCache     = PetImageCache(application)

    private val prefs = application.getSharedPreferences("nurtale_demo_prefs", Context.MODE_PRIVATE)

    // --- Demo Mode State Controls ----------------------------------------------------------------

    private val _isDemoMode = MutableStateFlow(prefs.getBoolean("is_demo_mode", false))
    val isDemoMode: StateFlow<Boolean> = _isDemoMode.asStateFlow()

    private val _demoStageOverride = MutableStateFlow<Int?>(
        if (prefs.contains("demo_stage_override")) prefs.getInt("demo_stage_override", 0) else null
    )
    val demoStageOverride: StateFlow<Int?> = _demoStageOverride.asStateFlow()

    private val _demoPetIndex = MutableStateFlow(prefs.getInt("demo_pet_index", 0))
    val demoPetIndex: StateFlow<Int> = _demoPetIndex.asStateFlow()

    private val _useEmojiVisuals = MutableStateFlow(false)
    val useEmojiVisuals: StateFlow<Boolean> = _useEmojiVisuals.asStateFlow()

    // Combined DB Flow (5 items max for standard combine)
    private val dbStateFlow = combine(
        defDao.getAllDefinitions(),
        stageDao.getAllStages(),
        petProgressDao.getAllPetProgress(),
        cacheDao.getAllCachedImages(),
        com.gxdevs.nurtale.data.SettingsRepository(application).isDecoyMode
    ) { defs, allStages, progressList, cachedImages, isDecoy ->
        DbState(defs, allStages, progressList, cachedImages, isDecoy)
    }

    // Combined Demo Flow
    private val demoControlsFlow = combine(
        _isDemoMode,
        _demoStageOverride,
        _useEmojiVisuals,
        _demoPetIndex
    ) { isDemo, stageOverride, forceEmoji, petIndex ->
        DemoControls(isDemo, stageOverride, forceEmoji, petIndex)
    }

    // Live combined UI state
    val petsState: StateFlow<PetsScreenState> = combine(
        dbStateFlow,
        demoControlsFlow
    ) { dbState, demo ->

        if (dbState.isDecoy) {
            return@combine PetsScreenState(pets = emptyList(), isLoading = false)
        }

        // Fast-path: catalog not seeded yet - fall back to MoodConstants set
        if (dbState.defs.isEmpty()) {
            return@combine fallbackToMoodConstants(
                progressList = dbState.progressList,
                isDemo = demo.isDemo,
                stageOverride = demo.stageOverride,
                forceEmoji = demo.forceEmoji
            )
        }

        val stagesByPet    = dbState.allStages.groupBy { it.petId }
        val progressByMood = dbState.progressList.associateBy { it.moodId.lowercase() }
        val cacheByKey     = dbState.cachedImages.associateBy { "${it.petId}_${it.stage}" }
        val defsByEmotion  = dbState.defs.groupBy { it.emotion.lowercase() }
            .mapValues { (_, v) -> v.sortedBy { it.level } }

        val pets = dbState.defs
            .sortedWith(compareBy({ it.emotion.lowercase() }, { it.level }))
            .mapNotNull { def ->
                buildPetUiState(
                    def = def,
                    stagesByPet = stagesByPet,
                    progressByMood = progressByMood,
                    cacheByKey = cacheByKey,
                    defsByEmotion = defsByEmotion,
                    isDemo = demo.isDemo,
                    stageOverride = demo.stageOverride
                )
            }

        PetsScreenState(
            pets = pets,
            isLoading = false,
            isDemoMode = demo.isDemo,
            demoStageOverride = demo.stageOverride,
            useEmojiVisuals = demo.forceEmoji,
            demoPetIndex = demo.petIndex
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PetsScreenState())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val repo = com.gxdevs.nurtale.pets.PetCatalogRepository(application)
            repo.seedFromAssetsIfEmpty()
            recalculateFromHistorySuspend()
            // Preload ALL 25 pet stage images in parallel so all pets load instantly!
            preloadAllPetImages()
        }

        viewModelScope.launch {
            val settingsRepo = com.gxdevs.nurtale.data.SettingsRepository(application)
            settingsRepo.dailySelectedJournals.collect {
                recalculateFromHistorySuspend()
            }
        }
    }

    // --- Demo Mode Actions -----------------------------------------------------------------------

    fun toggleDemoMode() {
        val next = !_isDemoMode.value
        _isDemoMode.value = next
        prefs.edit().putBoolean("is_demo_mode", next).apply()
        if (next) preloadAllPetImages()
    }

    fun setDemoStageOverride(stage: Int?) {
        _demoStageOverride.value = stage
        if (stage == null) {
            prefs.edit().remove("demo_stage_override").apply()
        } else {
            prefs.edit().putInt("demo_stage_override", stage).apply()
        }
        preloadAllPetImages()
    }

    fun toggleEmojiVisuals() {
        _useEmojiVisuals.value = !_useEmojiVisuals.value
    }

    fun cycleDemoStage() {
        val current = _demoStageOverride.value
        val next = when (current) {
            null -> 0
            0 -> 1
            1 -> 2
            2 -> 3
            3 -> 4
            4 -> 5
            else -> null
        }
        setDemoStageOverride(next)
    }

    fun cycleDemoPet() {
        val next = _demoPetIndex.value + 1
        _demoPetIndex.value = next
        prefs.edit().putInt("demo_pet_index", next).apply()
        preloadAllPetImages()
    }

    fun unlockAllPetsInDemo() {
        viewModelScope.launch(Dispatchers.IO) {
            MoodConstants.ALL_MOODS.forEach { mood ->
                petProgressDao.upsertPetProgress(PetProgress(moodId = mood.lowercase(), journalCount = 55))
            }
            recalculateFromHistorySuspend()
            preloadAllPetImages()
        }
    }

    fun preloadAllPetImages() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val defs = defDao.getAllDefinitionsSuspend()
                val jobs = mutableListOf<Job>()
                for (def in defs) {
                    val stages = stageDao.getStagesForPet(def.petId)
                    for (stageDef in stages) {
                        if (!stageDef.imageUrl.isNullOrBlank()) {
                            val j = launch {
                                imageCache.getOrDownload(def.petId, stageDef.stage, stageDef.imageUrl)
                            }
                            jobs.add(j)
                        }
                    }
                }
                jobs.joinAll()
                Log.i("PetViewModel", "All pet stage images preloaded successfully!")
            } catch (e: Exception) {
                Log.e("PetViewModel", "Preload error: ${e.message}")
            }
        }
    }

    // --- Journal-save hook & settings sync ------------------

    private val _pendingLevelUpPet = MutableStateFlow<PetUiState?>(null)
    val pendingLevelUpPet: StateFlow<PetUiState?> = _pendingLevelUpPet.asStateFlow()

    fun clearPendingLevelUpPet() {
        _pendingLevelUpPet.value = null
    }

    fun onJournalSaved() {
        viewModelScope.launch {
            val oldPets = petsState.value.pets.associateBy { it.petId }
            val oldProgress = petProgressDao.getAllPetProgressSync().associateBy { it.moodId }
            recalculateFromHistorySuspend()
            kotlinx.coroutines.delay(200)
            val newPets = petsState.value.pets.associateBy { it.petId }

            newPets.forEach { (petId, newPet) ->
                val oldPet = oldPets[petId]
                val oldStage = oldPet?.stageIndex ?: -1
                val newStage = newPet.stageIndex
                if (newStage > oldStage || (oldStage < 0 && newStage >= 0)) {
                    _pendingLevelUpPet.value = newPet
                }
            }

            val newProgress = petProgressDao.getAllPetProgressSync().associateBy { it.moodId }
            newProgress.forEach { (moodId, newProg) ->
                val oldCount = oldProgress[moodId]?.journalCount ?: 0
                val newCount = newProg.journalCount
                if (newCount > oldCount) {
                    checkAndNotifyEvolution(moodId, oldCount, newCount)
                }
            }
        }
    }

    fun recalculateFromHistory() {
        viewModelScope.launch {
            recalculateFromHistorySuspend()
        }
    }

    fun recalculateAndDownloadResources(context: Context = getApplication()) {
        recalculateFromHistory()
    }

    private suspend fun recalculateFromHistorySuspend() {
        val entries  = journalDao.getAllEntriesWithEmotions()
        val calendar = Calendar.getInstance()
        val context  = getApplication<Application>()
        val settingsRepo = com.gxdevs.nurtale.data.SettingsRepository(context)
        val selectedJournalsJson = settingsRepo.dailySelectedJournals.firstOrNull() ?: "{}"
        val selectedJournalsMap  = mutableMapOf<String, Long>()
        try {
            val jsonObj = org.json.JSONObject(selectedJournalsJson)
            jsonObj.keys().forEach { key ->
                selectedJournalsMap[key] = jsonObj.getLong(key)
            }
        } catch (_: Exception) {}

        val entriesByDay = mutableMapOf<String, MutableList<com.gxdevs.nurtale.data.journal.JournalEntry>>()
        entries.forEach { entry ->
            calendar.timeInMillis = entry.timestamp
            val dayKey = "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.DAY_OF_YEAR)}"
            entriesByDay.getOrPut(dayKey) { mutableListOf() }.add(entry)
        }

        val moodDayCounts = mutableMapOf<String, Int>()
        entriesByDay.forEach { (dayKey, dayEntries) ->
            val selId = selectedJournalsMap[dayKey]
            val chosenEntry = if (selId != null) dayEntries.find { it.id == selId } else null
            val moodToCredit = if (chosenEntry != null) {
                resolveDominantMood(chosenEntry.emotions)
            } else {
                val dayCounts = mutableMapOf<String, Int>()
                dayEntries.forEach { e ->
                    val m = resolveDominantMood(e.emotions)
                    if (m != null) dayCounts[m] = (dayCounts[m] ?: 0) + 1
                }
                dayCounts.maxByOrNull { it.value }?.key
            }
            if (moodToCredit != null) {
                val key = moodToCredit.lowercase()
                moodDayCounts[key] = (moodDayCounts[key] ?: 0) + 1
            }
        }

        MoodConstants.ALL_MOODS.forEach { mood ->
            val key = mood.lowercase()
            val count = moodDayCounts[key] ?: 0
            petProgressDao.upsertPetProgress(PetProgress(moodId = key, journalCount = count))
        }

        val repo = com.gxdevs.nurtale.pets.PetCatalogRepository(context)
        repo.seedFromAssetsIfEmpty()
        repo.syncIfDue()
    }

    // --- Private helpers ---------------------

    private fun buildPetUiState(
        def: PetDefinition,
        stagesByPet: Map<String, List<PetStageDefinition>>,
        progressByMood: Map<String, PetProgress>,
        cacheByKey: Map<String, com.gxdevs.nurtale.pets.CachedStageImage>,
        defsByEmotion: Map<String, List<PetDefinition>>,
        isDemo: Boolean,
        stageOverride: Int?
    ): PetUiState? {
        val emotionKey = def.emotion.lowercase()
        val moodId     = emotionKey.replaceFirstChar { it.uppercase() }
        val stages     = stagesByPet[def.petId]?.sortedBy { it.stage } ?: emptyList()
        val totalJournals = progressByMood[emotionKey]?.journalCount ?: 0
        val emotionDefs   = defsByEmotion[emotionKey] ?: emptyList()
        val levelIndex    = emotionDefs.indexOfFirst { it.petId == def.petId }

        if (!isDemo && totalJournals < 1) return null

        val (effectiveJournals, isLockedReal) = resolveEffectiveJournals(
            def, levelIndex, emotionDefs, stagesByPet, totalJournals
        )

        val isLocked = if (isDemo) false else isLockedReal

        if (isLocked || stages.isEmpty()) {
            return PetUiState(
                petId = def.petId, name = def.name, emotion = emotionKey, moodId = moodId,
                level = def.level, description = def.description, journalCount = 0,
                stageIndex = -1, stageName = "Locked", totalStages = def.totalStages,
                progressInStage = 0f, journalsToNext = stages.firstOrNull()?.journalsRequired ?: 0,
                isFullyGrown = false, currentStageImageUrl = null, localImagePath = null
            )
        }

        var stageIdx = 0
        if (isDemo && stageOverride != null) {
            stageIdx = stageOverride.coerceIn(0, stages.lastIndex)
        } else {
            val journalsToTest = if (isDemo && totalJournals < 1) 1 else effectiveJournals
            for (i in stages.indices) {
                if (journalsToTest >= stages[i].journalsRequired) stageIdx = i
            }
        }

        val currentStageDef = stages.getOrNull(stageIdx) ?: stages.first()
        val nextStageDef    = stages.getOrNull(stageIdx + 1)

        viewModelScope.launch(Dispatchers.IO) {
            if (!currentStageDef.imageUrl.isNullOrBlank()) {
                imageCache.getOrDownload(def.petId, currentStageDef.stage, currentStageDef.imageUrl)
            }
        }

        val progressInStage = nextStageDef?.let { next ->
            val within = effectiveJournals - currentStageDef.journalsRequired
            val range  = next.journalsRequired - currentStageDef.journalsRequired
            if (range > 0) (within.toFloat() / range).coerceIn(0f, 1f) else 1f
        } ?: 1f

        val journalsToNext = nextStageDef?.let {
            (it.journalsRequired - effectiveJournals).coerceAtLeast(0)
        } ?: 0

        // 1. Try Room DB cache map first
        // 2. Direct disk fallback if Room DB has not emitted yet
        val app = getApplication<Application>()
        val localPath = cacheByKey["${def.petId}_${currentStageDef.stage}"]
            ?.localPath
            ?.takeIf { File(it).exists() }
            ?: File(app.filesDir, "pet_images/${def.petId}_stage${currentStageDef.stage}.png")
                .takeIf { it.exists() && it.length() > 100 }?.absolutePath

        return PetUiState(
            petId = def.petId, name = def.name, emotion = emotionKey, moodId = moodId,
            level = def.level, description = def.description, journalCount = if (isDemo) 55 else effectiveJournals,
            stageIndex = stageIdx, stageName = currentStageDef.stageName,
            totalStages = def.totalStages, progressInStage = if (isDemo) 1f else progressInStage,
            journalsToNext = if (isDemo) 0 else journalsToNext, isFullyGrown = nextStageDef == null,
            currentStageImageUrl = currentStageDef.imageUrl, localImagePath = localPath
        )
    }

    private fun resolveEffectiveJournals(
        def: PetDefinition,
        levelIndex: Int,
        emotionDefs: List<PetDefinition>,
        stagesByPet: Map<String, List<PetStageDefinition>>,
        totalJournals: Int
    ): Pair<Int, Boolean> {
        if (def.level <= 1) return Pair(totalJournals, false)
        val prevDef    = emotionDefs.getOrNull(levelIndex - 1) ?: return Pair(0, true)
        val prevStages = stagesByPet[prevDef.petId]?.sortedBy { it.stage } ?: emptyList()
        val prevMax    = prevStages.lastOrNull()?.journalsRequired ?: return Pair(0, true)
        if (totalJournals < prevMax) return Pair(0, true)
        return Pair((totalJournals - prevMax).coerceAtLeast(0), false)
    }

    private fun fallbackToMoodConstants(
        progressList: List<PetProgress>,
        isDemo: Boolean,
        stageOverride: Int?,
        forceEmoji: Boolean
    ): PetsScreenState {
        val progressMap = progressList.associateBy { it.moodId }
        val pets = MoodConstants.ALL_MOODS.mapNotNull { moodId ->
            val count = progressMap[moodId]?.journalCount ?: 0
            if (!isDemo && count < 1) return@mapNotNull null

            var stageIdx = MoodConstants.stageFor(if (isDemo && count < 1) 1 else count)
            if (isDemo && stageOverride != null) {
                stageIdx = stageOverride.coerceIn(0, MoodConstants.stages.lastIndex)
            }
            val safeStageIdx = stageIdx.coerceAtLeast(0)
            val stageName    = MoodConstants.stages.getOrNull(safeStageIdx)?.name ?: "Egg"
            val nextStage    = MoodConstants.stages.getOrNull(safeStageIdx + 1)

            PetUiState(
                petId = "fallback_${moodId.lowercase()}",
                name = moodId,
                emotion = moodId.lowercase(),
                moodId = moodId,
                level = 1,
                description = MoodConstants.descriptionOf[moodId] ?: "",
                journalCount = if (isDemo) 55 else count,
                stageIndex = safeStageIdx,
                stageName = stageName,
                totalStages = MoodConstants.stages.size,
                progressInStage = if (isDemo) 1f else MoodConstants.progressInStage(count),
                journalsToNext = if (isDemo) 0 else (nextStage?.let { (it.journalsNeeded - count).coerceAtLeast(0) } ?: 0),
                isFullyGrown = safeStageIdx >= MoodConstants.stages.size - 1,
                currentStageImageUrl = null,
                localImagePath = null
            )
        }
        return PetsScreenState(
            pets = pets,
            isLoading = false,
            isDemoMode = isDemo,
            demoStageOverride = stageOverride,
            useEmojiVisuals = forceEmoji
        )
    }

    private suspend fun checkAndNotifyEvolution(moodId: String, oldCount: Int, newCount: Int) {
        val emotionKey = moodId.lowercase()
        val defs = defDao.getAllDefinitionsSuspend()
            .filter { it.emotion.lowercase() == emotionKey }
            .sortedBy { it.level }

        for (def in defs) {
            val stages = stageDao.getStagesForPet(def.petId).sortedBy { it.stage }
            if (stages.isEmpty()) continue

            var prevStageIdx = 0
            var newStageIdx  = 0
            for (i in stages.indices) {
                if (oldCount >= stages[i].journalsRequired) prevStageIdx = i
                if (newCount >= stages[i].journalsRequired) newStageIdx  = i
            }

            if (newStageIdx > prevStageIdx) {
                fireEvolutionNotification(
                    context   = getApplication(),
                    petName   = def.name,
                    stageName = stages[newStageIdx].stageName
                )
            }
        }
    }

    private fun resolveDominantMood(emotionsJson: String?): String? {
        if (emotionsJson.isNullOrBlank()) return null
        return try {
            val ems    = gson.fromJson(emotionsJson, Array<Emotion>::class.java)
            val counts = mutableMapOf<String, Int>()
            ems.forEach { em ->
                val mood = MoodConstants.emotionToMood(em.label)
                counts[mood] = (counts[mood] ?: 0) + 1
            }
            counts.maxByOrNull { it.value }?.key
        } catch (_: Exception) { null }
    }

    private fun fireEvolutionNotification(context: Context, petName: String, stageName: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_COMPANION, "Companion Alerts", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Notifications when your pet evolves." }
        )
        nm.notify(
            petName.hashCode(),
            NotificationCompat.Builder(context, CHANNEL_COMPANION)
                .setSmallIcon(com.gxdevs.nurtale.R.drawable.paw_print)
                .setContentTitle("$petName evolved! ✨")
                .setContentText("$petName has reached the $stageName stage.")
                .setAutoCancel(true)
                .build()
        )
    }

    private data class DbState(
        val defs: List<PetDefinition>,
        val allStages: List<PetStageDefinition>,
        val progressList: List<PetProgress>,
        val cachedImages: List<com.gxdevs.nurtale.pets.CachedStageImage>,
        val isDecoy: Boolean
    )

    private data class DemoControls(
        val isDemo: Boolean,
        val stageOverride: Int?,
        val forceEmoji: Boolean,
        val petIndex: Int = 0
    )

    companion object {
        const val CHANNEL_COMPANION = "companion_alerts"
    }
}
