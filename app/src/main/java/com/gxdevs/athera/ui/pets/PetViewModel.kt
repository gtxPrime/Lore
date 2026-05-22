package com.gxdevs.athera.ui.pets

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.gxdevs.athera.AppDatabase
import com.gxdevs.athera.MoodConstants
import com.gxdevs.athera.PetProgress
import com.gxdevs.athera.pets.PetDefinition
import com.gxdevs.athera.pets.PetStageDefinition
import com.gxdevs.athera.ui.journal.Emotion
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

// â”€â”€â”€ UI model â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

data class PetUiState(
    val petId: String,
    val name: String,
    val emotion: String,         // lowercase: "bright", "calm", â€¦
    val moodId: String,          // capitalized: "Bright" â€” for MoodConstants colour lookup
    val level: Int,
    val description: String,
    val journalCount: Int,       // effective journals for this pet's level
    val stageIndex: Int,         // 0-based; -1 = locked
    val stageName: String,
    val totalStages: Int,
    val progressInStage: Float,
    val journalsToNext: Int,
    val isFullyGrown: Boolean,
    val currentStageImageUrl: String?,   // Cloudinary URL (download trigger)
    val localImagePath: String?          // absolute local path if cached; null = not yet downloaded
)

data class PetsScreenState(
    val pets: List<PetUiState> = emptyList(),
    val isLoading: Boolean = true
)

// â”€â”€â”€ ViewModel â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

class PetViewModel(application: Application) : AndroidViewModel(application) {

    private val db            = AppDatabase.getDatabase(application)
    private val journalDao    = db.journalDao()
    private val petProgressDao = db.petProgressDao()
    private val defDao        = db.petDefinitionDao()
    private val stageDao      = db.petStageDefinitionDao()
    private val cacheDao      = db.cachedStageImageDao()
    private val gson          = Gson()

    // â”€â”€ Live state â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    val petsState: StateFlow<PetsScreenState> = combine(
        defDao.getAllDefinitions(),
        stageDao.getAllStages(),
        petProgressDao.getAllPetProgress(),
        cacheDao.getAllCachedImages()
    ) { defs, allStages, progressList, cachedImages ->

        // Fast-path: catalog not seeded yet â€” fall back to MoodConstants dummy set
        if (defs.isEmpty()) {
            return@combine fallbackToMoodConstants(progressList)
        }

        val stagesByPet    = allStages.groupBy { it.petId }
        val progressByMood = progressList.associateBy { it.moodId.lowercase() }
        val cacheByKey     = cachedImages.associateBy { "${it.petId}_${it.stage}" }
        val defsByEmotion  = defs.groupBy { it.emotion.lowercase() }
            .mapValues { (_, v) -> v.sortedBy { it.level } }

        val pets = defs
            .sortedWith(compareBy({ it.emotion.lowercase() }, { it.level }))
            .mapNotNull { def -> buildPetUiState(def, stagesByPet, progressByMood, cacheByKey, defsByEmotion) }

        PetsScreenState(pets = pets, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PetsScreenState())

    init {
        viewModelScope.launch {
            val settingsRepo = com.gxdevs.athera.data.SettingsRepository(application)
            settingsRepo.dailySelectedJournals.collect {
                recalculateFromHistorySuspend()
            }
        }
    }

    // â”€â”€ Journal-save hook â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun onJournalSaved() {
        viewModelScope.launch {
            val oldProgress = petProgressDao.getAllPetProgressSync().associateBy { it.moodId }
            recalculateFromHistorySuspend()
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

    private suspend fun recalculateFromHistorySuspend() {
        val entries  = journalDao.getAllEntriesWithEmotions()
        val calendar = Calendar.getInstance()
        val context = getApplication<Application>()
        val settingsRepo = com.gxdevs.athera.data.SettingsRepository(context)
        val selectedJournalsJson = settingsRepo.dailySelectedJournals.firstOrNull() ?: "{}"
        val selectedJournalsMap = mutableMapOf<String, Long>()
        try {
            val jsonObj = org.json.JSONObject(selectedJournalsJson)
            jsonObj.keys().forEach { key ->
                selectedJournalsMap[key] = jsonObj.getLong(key)
            }
        } catch (_: Exception) {}

        val entriesByDay = mutableMapOf<String, MutableList<com.gxdevs.athera.JournalEntry>>()
        entries.forEach { entry ->
            calendar.timeInMillis = entry.timestamp
            val dayKey = "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.DAY_OF_YEAR)}"
            entriesByDay.getOrPut(dayKey) { mutableListOf() }.add(entry)
        }

        val moodDayCounts = mutableMapOf<String, Int>()
        entriesByDay.forEach { (dayKey, dayEntries) ->
            val explicitlySelectedId = selectedJournalsMap[dayKey]
            val chosenEntry = dayEntries.find { it.id == explicitlySelectedId } 
                ?: dayEntries.maxByOrNull { it.timestamp }
            
            chosenEntry?.emotions?.let { emotionsJson ->
                val dominant = resolveDominantMood(emotionsJson)
                if (dominant != null) {
                    moodDayCounts[dominant] = (moodDayCounts[dominant] ?: 0) + 1
                }
            }
        }

        petProgressDao.deleteAll()
        moodDayCounts.forEach { (mood, count) ->
            if (count > 0) petProgressDao.upsertPetProgress(PetProgress(moodId = mood, journalCount = count))
        }
    }

    suspend fun recalculateAndDownloadResources(context: Context) {
        // 1. Recalculate progress based on new imported journals
        recalculateFromHistorySuspend()
        
        // 2. Ensure catalog is seeded or synced
        val repo = com.gxdevs.athera.pets.PetCatalogRepository(context)
        repo.seedDemoCatalogIfEmpty()
        repo.syncIfDue()

        // 3. Download newly unlocked images
        val defs = defDao.getAllDefinitionsSuspend()
        val allStages = mutableListOf<PetStageDefinition>()
        for (def in defs) {
            allStages.addAll(stageDao.getStagesForPet(def.petId))
        }
        val stagesByPet = allStages.groupBy { it.petId }
        val progressList = petProgressDao.getAllPetProgressSync()
        val progressMap = progressList.associateBy { it.moodId.lowercase() }
        val defsByEmotion = defs.groupBy { it.emotion.lowercase() }.mapValues { (_, v) -> v.sortedBy { it.level } }

        val imageCache = com.gxdevs.athera.pets.PetImageCache(context)

        for (def in defs) {
            val emotionKey = def.emotion.lowercase()
            val stages = stagesByPet[def.petId]?.sortedBy { it.stage } ?: emptyList()
            val totalJournals = progressMap[emotionKey]?.journalCount ?: 0
            val emotionDefs = defsByEmotion[emotionKey] ?: emptyList()
            val levelIndex = emotionDefs.indexOfFirst { it.petId == def.petId }
            
            val (effectiveJournals, isLocked) = resolveEffectiveJournals(def, levelIndex, emotionDefs, stagesByPet, totalJournals)
            
            if (!isLocked && stages.isNotEmpty()) {
                var stageIdx = 0
                for (i in stages.indices) {
                    if (effectiveJournals >= stages[i].journalsRequired) stageIdx = i
                }
                val currentStageDef = stages[stageIdx]
                imageCache.getOrDownload(def.petId, currentStageDef.stage, currentStageDef.imageUrl)
            }
        }
    }

    // â”€â”€ Private helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private fun buildPetUiState(
        def: PetDefinition,
        stagesByPet: Map<String, List<PetStageDefinition>>,
        progressByMood: Map<String, PetProgress>,
        cacheByKey: Map<String, com.gxdevs.athera.pets.CachedStageImage>,
        defsByEmotion: Map<String, List<PetDefinition>>
    ): PetUiState? {
        val emotionKey = def.emotion.lowercase()
        val moodId     = emotionKey.replaceFirstChar { it.uppercase() }
        val stages     = stagesByPet[def.petId]?.sortedBy { it.stage } ?: emptyList()
        val totalJournals = progressByMood[emotionKey]?.journalCount ?: 0
        val emotionDefs   = defsByEmotion[emotionKey] ?: emptyList()
        val levelIndex    = emotionDefs.indexOfFirst { it.petId == def.petId }

        // Hide pet entirely if user has NEVER journaled about this mood
        if (totalJournals < 1) return null

        // Determine effective journals & locked state
        val (effectiveJournals, isLocked) = resolveEffectiveJournals(
            def, levelIndex, emotionDefs, stagesByPet, totalJournals
        )

        if (isLocked || stages.isEmpty()) {
            return PetUiState(
                petId = def.petId, name = def.name, emotion = emotionKey, moodId = moodId,
                level = def.level, description = def.description, journalCount = 0,
                stageIndex = -1, stageName = "Locked", totalStages = def.totalStages,
                progressInStage = 0f, journalsToNext = stages.firstOrNull()?.journalsRequired ?: 0,
                isFullyGrown = false, currentStageImageUrl = null, localImagePath = null
            )
        }

        // Resolve current stage (0-based index into sorted stages list)
        var stageIdx = 0
        for (i in stages.indices) {
            if (effectiveJournals >= stages[i].journalsRequired) stageIdx = i
        }

        val currentStageDef = stages[stageIdx]
        val nextStageDef    = stages.getOrNull(stageIdx + 1)

        val progressInStage = nextStageDef?.let { next ->
            val within = effectiveJournals - currentStageDef.journalsRequired
            val range  = next.journalsRequired - currentStageDef.journalsRequired
            if (range > 0) (within.toFloat() / range).coerceIn(0f, 1f) else 1f
        } ?: 1f

        val journalsToNext = nextStageDef?.let {
            (it.journalsRequired - effectiveJournals).coerceAtLeast(0)
        } ?: 0

        val localPath = cacheByKey["${def.petId}_${currentStageDef.stage}"]
            ?.localPath
            ?.takeIf { File(it).exists() }

        return PetUiState(
            petId = def.petId, name = def.name, emotion = emotionKey, moodId = moodId,
            level = def.level, description = def.description, journalCount = effectiveJournals,
            stageIndex = stageIdx, stageName = currentStageDef.stageName,
            totalStages = def.totalStages, progressInStage = progressInStage,
            journalsToNext = journalsToNext, isFullyGrown = nextStageDef == null,
            currentStageImageUrl = currentStageDef.imageUrl, localImagePath = localPath
        )
    }

    /**
     * Level-1 pets use raw journal count.
     * Level-N pets (N>1) are locked until level-(N-1) is maxed; then effective = total - prevMax.
     */
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

    /** Fallback when catalog DB is empty â€” mirror old MoodConstants behaviour */
    private fun fallbackToMoodConstants(progressList: List<PetProgress>): PetsScreenState {
        val progressMap = progressList.associateBy { it.moodId }
        // Only include moods the user has actually journaled about (count >= 1)
        val pets = MoodConstants.ALL_MOODS.mapNotNull { moodId ->
            val count    = progressMap[moodId]?.journalCount ?: 0
            if (count < 1) return@mapNotNull null   // no journal for this mood â€” hide entirely
            val stageIdx = MoodConstants.stageFor(count)
            val stageName = if (stageIdx < 0) "Egg"
                            else MoodConstants.stages.getOrNull(stageIdx)?.name ?: "Egg"
            val nextStage = MoodConstants.stages.getOrNull(stageIdx + 1)
            PetUiState(
                petId = "fallback_${moodId.lowercase()}",
                name = moodId,
                emotion = moodId.lowercase(),
                moodId = moodId,
                level = 1,
                description = MoodConstants.descriptionOf[moodId] ?: "",
                journalCount = count,
                stageIndex = stageIdx.coerceAtLeast(0),  // treat as egg (0) rather than locked (-1)
                stageName = stageName,
                totalStages = MoodConstants.stages.size,
                progressInStage = MoodConstants.progressInStage(count),
                journalsToNext = nextStage?.let { (it.journalsNeeded - count).coerceAtLeast(0) } ?: 0,
                isFullyGrown = stageIdx >= MoodConstants.stages.size - 1 && count >= MoodConstants.totalJournalsForFullGrown,
                currentStageImageUrl = null,
                localImagePath = null
            )
        }
        return PetsScreenState(pets = pets, isLoading = false)
    }

    private suspend fun checkAndNotifyEvolution(moodId: String, oldCount: Int, newCount: Int) {
        // Find all pets for this emotion, check if any crossed a stage threshold
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
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("$petName evolved! âœ¨")
                .setContentText("$petName has reached the $stageName stage.")
                .setAutoCancel(true)
                .build()
        )
    }

    companion object {
        const val CHANNEL_COMPANION = "companion_alerts"

        /**
         * Set to true to use emoji visuals instead of downloaded Cloudinary images.
         * Toggle this during development while pet images are not yet available.
         */
        var DEMO_MODE = true
    }
}

