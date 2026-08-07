package com.gxdevs.lore.ui.pets

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.gxdevs.lore.data.AppDatabase
import com.gxdevs.lore.data.mood.MoodConstants
import com.gxdevs.lore.pets.PetProgress
import com.gxdevs.lore.pets.PetDefinition
import com.gxdevs.lore.pets.PetStageDefinition
import com.gxdevs.lore.pets.PetImageCache
import com.gxdevs.lore.ui.journal.Emotion
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
import com.gxdevs.lore.utils.PremiumManager

// --- UI model ------------------------------------------------------------------------------------

data class PetUiState(
    val petId: String,
    val name: String,             // raw JSON name — always present
    val displayName: String,      // nickname (premium) or JSON name (free) — use this in UI
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

// --- Pet Journey (History Timeline) models -------------------------------------------------------

/** One stage in the pet's evolution history, enriched with the unlock date. */
data class PetJourneyStage(
    val stageIndex: Int,           // 0-based (0 = Egg)
    val stageName: String,
    val journalsRequired: Int,     // cumulative journals needed to reach this stage
    val localImagePath: String?,   // cached image on disk (nullable if not yet downloaded)
    val unlockedOnDateMillis: Long? // epoch-ms of the day this stage was first reached (null = not reached yet)
)

/** A single calendar day's contribution to a pet's journey. */
data class PetJourneyDay(
    val dateMillis: Long,                       // start-of-day epoch-ms (for sorting)
    val dateLabel: String,                      // e.g. "Aug 4, 2026"
    val journalSnippets: List<JournalSnippet>,  // entries written that day for this pet's emotion
    val stageReachedAfterThis: PetJourneyStage? // non-null when this day's journals crossed a stage threshold
)

/** Lightweight summary of a journal entry for the timeline card. */
data class JournalSnippet(
    val id: Long,
    val timestamp: Long,
    val snippet: String,     // first ~80 chars of content, or prompt title
    val moodId: String?      // resolved mood bucket for this entry
)

/** Full data package consumed by PetJourneyScreen. */
data class PetJourneyData(
    val petName: String,
    val moodId: String,
    val currentStageIndex: Int,
    val days: List<PetJourneyDay>,           // oldest → newest
    val allStages: List<PetJourneyStage>,    // complete stage list (including locked future stages)
    val totalJournalCount: Int
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

    private val prefs = application.getSharedPreferences("lore_demo_prefs", Context.MODE_PRIVATE)

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
        com.gxdevs.lore.data.SettingsRepository(application).isDecoyMode
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
        demoControlsFlow,
        com.gxdevs.lore.data.SettingsRepository(application).allPetNicknames
    ) { dbState, demo, nicknames ->

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
                    stageOverride = demo.stageOverride,
                    nicknames = nicknames
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
            val repo = com.gxdevs.lore.pets.PetCatalogRepository(application)
            repo.seedFromAssetsIfEmpty()
            recalculateFromHistorySuspend()
            // Preload ALL 25 pet stage images in parallel so all pets load instantly!
            preloadAllPetImages()
        }

        viewModelScope.launch {
            val settingsRepo = com.gxdevs.lore.data.SettingsRepository(application)
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
        if (next) {
            preloadAllPetImages()
        }
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

    fun updatePetNickname(petId: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            defDao.updatePetName(petId, newName)
            recalculateFromHistorySuspend()
        }
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

    // --- Pet Journey (History Timeline) ---------------------------------------------------------

    private val _petJourneyState = MutableStateFlow<PetJourneyData?>(null)
    val petJourneyState: StateFlow<PetJourneyData?> = _petJourneyState.asStateFlow()

    fun loadJourney(pet: PetUiState) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()

            // Demo mode: skip real DB and generate synthetic journey data
            if (_isDemoMode.value) {
                _petJourneyState.value = buildDemoJourneyData(pet, context)
                return@launch
            }

            val emotionKey = pet.emotion.lowercase()

            // 1. Fetch all journal entries that have emotions data
            val allEntries = journalDao.getAllEntriesWithEmotions()

            // 2. Filter entries that belong to this pet's emotion bucket
            val petEntries = allEntries.filter { entry ->
                val entryMood = resolveDominantMood(entry.emotions)
                entryMood?.lowercase() == emotionKey
            }.sortedBy { it.timestamp }

            // 3. Fetch stage definitions for this pet
            val stages = stageDao.getStagesForPet(pet.petId).sortedBy { it.stage }

            // 4. Fetch cached images for all stages of this pet
            val cachedImages = stages.associate { stageDef ->
                stageDef.stage to (cacheDao.getCachedImage(pet.petId, stageDef.stage)?.localPath
                    ?.takeIf { File(it).exists() }
                    ?: File(context.filesDir, "pet_images/${pet.petId}_stage${stageDef.stage}.png")
                        .takeIf { it.exists() && it.length() > 100 }?.absolutePath)
            }

            // 5. Group entries by calendar day
            val calendar = java.util.Calendar.getInstance()
            val dayGroups = mutableMapOf<String, MutableList<com.gxdevs.lore.data.journal.JournalEntry>>()
            petEntries.forEach { entry ->
                calendar.timeInMillis = entry.timestamp
                val dayKey = "${calendar.get(java.util.Calendar.YEAR)}-${calendar.get(java.util.Calendar.DAY_OF_YEAR)}"
                dayGroups.getOrPut(dayKey) { mutableListOf() }.add(entry)
            }

            // 6. Build cumulative count per day and detect stage crossings
            val sortedDayKeys = dayGroups.keys.sorted()
            var cumulativeCount = 0
            var previousStageIdx = -1

            // Map stage journalsRequired → PetJourneyStage (will fill unlock date)
            val stageUnlockDates = mutableMapOf<Int, Long>() // stageIndex → dateMillis

            val journeyDays = mutableListOf<PetJourneyDay>()

            val dateFormatter = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())

            for (dayKey in sortedDayKeys) {
                val dayEntries = dayGroups[dayKey] ?: continue
                val firstEntry = dayEntries.first()
                calendar.timeInMillis = firstEntry.timestamp
                val startOfDay = calendar.apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis

                cumulativeCount += dayEntries.size

                // Determine which stage was reached after this day
                var newStageIdx = -1
                for (i in stages.indices) {
                    if (cumulativeCount >= stages[i].journalsRequired) newStageIdx = i
                }

                val stageReached: PetJourneyStage?
                if (newStageIdx > previousStageIdx && newStageIdx >= 0) {
                    val stageDef = stages[newStageIdx]
                    stageUnlockDates[newStageIdx] = startOfDay
                    stageReached = PetJourneyStage(
                        stageIndex = newStageIdx,
                        stageName = stageDef.stageName,
                        journalsRequired = stageDef.journalsRequired,
                        localImagePath = cachedImages[stageDef.stage],
                        unlockedOnDateMillis = startOfDay
                    )
                    previousStageIdx = newStageIdx
                } else {
                    stageReached = null
                }

                val snippets = dayEntries.map { e ->
                    JournalSnippet(
                        id = e.id,
                        timestamp = e.timestamp,
                        snippet = (e.content?.take(80)?.trim() ?: e.promptResponses?.take(60)?.trim() ?: "Journal entry"),
                        moodId = resolveDominantMood(e.emotions)
                    )
                }

                journeyDays.add(
                    PetJourneyDay(
                        dateMillis = startOfDay,
                        dateLabel = dateFormatter.format(java.util.Date(firstEntry.timestamp)),
                        journalSnippets = snippets,
                        stageReachedAfterThis = stageReached
                    )
                )
            }

            // 7. Build full stage list (including locked future stages)
            val allJourneyStages = stages.mapIndexed { i, stageDef ->
                PetJourneyStage(
                    stageIndex = i,
                    stageName = stageDef.stageName,
                    journalsRequired = stageDef.journalsRequired,
                    localImagePath = cachedImages[stageDef.stage],
                    unlockedOnDateMillis = stageUnlockDates[i]
                )
            }

            _petJourneyState.value = PetJourneyData(
                petName = pet.name,
                moodId = pet.moodId,
                currentStageIndex = pet.stageIndex,
                days = journeyDays,
                allStages = allJourneyStages,
                totalJournalCount = cumulativeCount
            )
        }
    }

    fun clearJourney() {
        _petJourneyState.value = null
    }

    // ── Demo journey builder ─────────────────────────────────────────────────

    private suspend fun buildDemoJourneyData(pet: PetUiState, context: android.content.Context): PetJourneyData {
        val emotionKey = pet.emotion.lowercase()

        // Mood-specific journal snippet pools
        val snippetPool: Map<String, List<String>> = mapOf(
            "bright" to listOf(
                "Woke up feeling genuinely alive today — sunlight through the curtains hit different.",
                "Had a burst of creative energy and finished three things on my to-do list.",
                "Laughed so hard my stomach hurt. I needed that more than I knew.",
                "Everything felt possible today. The world was soft and bright.",
                "Started that project I'd been putting off. It felt amazing to begin.",
                "Grateful for small things: good coffee, a kind text, a clear sky.",
                "Danced to music alone in my room. No reason. Just joy.",
                "Called an old friend out of nowhere. We talked for two hours.",
                "Finished reading that book and felt the warm glow of completion.",
                "Ran farther than usual and felt like I could keep going forever.",
                "Made someone smile today without even trying. That counts for something.",
                "The sun set in shades of gold and I stood there just watching.",
            ),
            "calm" to listOf(
                "Everything felt unhurried today. I moved through the hours gently.",
                "Sat by the window with tea and did absolutely nothing for twenty minutes.",
                "Breathed. Slowly. On purpose. It helped more than I expected.",
                "Took a long walk with no destination. The mind settled on its own.",
                "Read something quiet and let the words sink in without rushing.",
                "The evening was still. I felt part of that stillness.",
                "Cooked a simple meal and it felt like meditation.",
                "No agenda today. Just being. It was enough.",
                "Listened to rain for a while. Everything softened.",
                "Wrote in my journal by candlelight. The pace felt right.",
                "Had a slow morning — didn't rush a single thing.",
                "Found a comfortable bench in the park and just sat with the trees.",
            ),
            "heavy" to listOf(
                "Carrying something I can't fully name yet. Writing helps a little.",
                "The tiredness today felt deeper than just sleep.",
                "Cried in the shower. Didn't even know why at first.",
                "Some days the weight is heavier. Today was one of those.",
                "Missing someone without knowing exactly who.",
                "Everything required more effort than it should have.",
                "Stayed in bed longer than I meant to. The world felt far away.",
                "Let myself feel it instead of pushing through. That was brave.",
                "Grief shows up uninvited. I'm learning to let it visit.",
                "Didn't accomplish much but I got through the day. That's enough.",
                "Wrote three pages of feelings I've never said out loud.",
                "It's okay not to be okay. I'm reminding myself of that.",
            ),
            "tangled" to listOf(
                "My thoughts are looping and I can't find the thread to pull.",
                "Anxious for no clear reason. My chest has been tight all day.",
                "Everything feels like too much at once. I don't know where to start.",
                "Made a decision, then unmade it, then made it again.",
                "The overwhelm is real. I'm trying to take one breath at a time.",
                "Worried about things that haven't happened yet. Working on it.",
                "Wrote out everything in my head just to see it outside of me.",
                "Confusion is uncomfortable but I'm in it. That's okay.",
                "Lists help. Made four of them. Still tangled but slightly less.",
                "Talked through the knot with myself for an hour. Partly untangled.",
                "Some feelings don't need to be solved, just witnessed.",
                "The storm in my mind was loud today. I waited it out.",
            ),
            "dark" to listOf(
                "The shadows felt close today. I wrote them down to make them smaller.",
                "Sat with the darkness instead of running from it.",
                "Fear visited again. I'm getting better at not believing everything it says.",
                "The night feels long but mornings do come eventually.",
                "There's a quiet I'm learning to sit inside without panicking.",
                "Wrote at 2am because the thoughts needed somewhere to go.",
                "The silence was heavy tonight. I lit a candle and kept going.",
                "Not every day has light. I'm learning to navigate by feel.",
                "Something in me is stubborn and refuses to stop trying. Good.",
                "I felt the edge of something vast and dark — and stepped back.",
                "Named three fears tonight. Naming them made them smaller.",
                "Tomorrow is a door I haven't opened yet.",
            ),
            "blank" to listOf(
                "Nothing in particular. A hollow kind of Tuesday.",
                "Couldn't feel much today. Wrote anyway.",
                "Static. Like the signal dropped somewhere inside me.",
                "No strong feelings — just quiet and grey and fine.",
                "Routine. Clock. Meals. Sleep. Repeat. Something is waiting.",
                "Felt like a placeholder today. Still showed up.",
                "The blankness isn't emptiness — it's potential, maybe.",
                "Trying to find the feeling beneath the numbness.",
                "Did the ordinary things and let that be enough.",
                "Waited for something to spark. Still waiting. Still here.",
                "Sometimes nothing is also a kind of rest.",
                "Even flat days contain something worth noting.",
            )
        )
        val pool = snippetPool[emotionKey] ?: snippetPool["blank"]!!

        // Fetch stage definitions and cached images
        val stages = stageDao.getStagesForPet(pet.petId).sortedBy { it.stage }
        val cachedImages = stages.associate { stageDef ->
            stageDef.stage to (cacheDao.getCachedImage(pet.petId, stageDef.stage)?.localPath
                ?.takeIf { File(it).exists() }
                ?: File(context.filesDir, "pet_images/${pet.petId}_stage${stageDef.stage}.png")
                    .takeIf { it.exists() && it.length() > 100 }?.absolutePath)
        }

        // Fallback: use MoodConstants stage names when DB is empty
        val stageNames: List<Pair<String, Int>> = if (stages.isNotEmpty()) {
            stages.map { it.stageName to it.journalsRequired }
        } else {
            MoodConstants.stages.map { it.name to it.journalsNeeded }
        }
        val totalStages = stageNames.size

        // Total journals needed = the journals required to reach the pet's current stage index
        val targetStageIdx = pet.stageIndex.coerceIn(0, stageNames.lastIndex)
        val totalJournalsNeeded = stageNames[targetStageIdx].second

        // Build synthetic day schedule spread over time, going back from today
        // Layout: 1-2 journals most days, an occasional 3-journal burst day
        data class DayPlan(val daysAgo: Int, val journalCount: Int)
        val dayPlan = mutableListOf<DayPlan>()
        var remaining = totalJournalsNeeded
        // Spread start proportionally: more stages/journals = further back in history
        var daysAgo = (totalJournalsNeeded * 1.2).toInt().coerceAtLeast(20)
        var snippetIdx = 0

        while (remaining > 0 && daysAgo >= 0) {
            val count = when {
                remaining >= 3 && daysAgo % 7 == 0 -> 3   // burst on Sundays
                remaining >= 2 && daysAgo % 3 != 0 -> 2
                else -> 1
            }.coerceAtMost(remaining)
            dayPlan.add(DayPlan(daysAgo, count))
            remaining -= count
            daysAgo -= kotlin.random.Random.nextInt(1, 3) // skip 1-2 days between entries
        }

        val nowMs = System.currentTimeMillis()
        val msPerDay = 24L * 60 * 60 * 1000
        val dateFormatter = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
        val calendar = java.util.Calendar.getInstance()

        var cumulativeCount = 0
        var previousStageIdx = -1
        val stageUnlockDates = mutableMapOf<Int, Long>()
        val journeyDays = mutableListOf<PetJourneyDay>()
        var snippetId = 1L

        for (plan in dayPlan.sortedBy { it.daysAgo }.reversed()) {
            val startOfDay = run {
                calendar.timeInMillis = nowMs - plan.daysAgo * msPerDay
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                calendar.timeInMillis
            }
            cumulativeCount += plan.journalCount

            // Build snippets for the day
            val daySnippets = (0 until plan.journalCount).map { j ->
                val text = pool[snippetIdx % pool.size]
                snippetIdx++
                val entryTime = startOfDay + (8 + j * 5) * 3600_000L // 8am, 1pm, 6pm
                JournalSnippet(
                    id        = snippetId++,
                    timestamp = entryTime,
                    snippet   = text,
                    moodId    = pet.moodId
                )
            }

            // Detect stage crossing
            var newStageIdx = -1
            for (i in stageNames.indices) {
                if (cumulativeCount >= stageNames[i].second) newStageIdx = i
            }

            val stageReached: PetJourneyStage?
            if (newStageIdx > previousStageIdx && newStageIdx >= 0) {
                stageUnlockDates[newStageIdx] = startOfDay
                val stageStageDef = stages.getOrNull(newStageIdx)
                stageReached = PetJourneyStage(
                    stageIndex            = newStageIdx,
                    stageName             = stageNames[newStageIdx].first,
                    journalsRequired      = stageNames[newStageIdx].second,
                    localImagePath        = stageStageDef?.let { cachedImages[it.stage] },
                    unlockedOnDateMillis  = startOfDay
                )
                previousStageIdx = newStageIdx
            } else {
                stageReached = null
            }

            journeyDays.add(
                PetJourneyDay(
                    dateMillis            = startOfDay,
                    dateLabel             = dateFormatter.format(java.util.Date(startOfDay)),
                    journalSnippets       = daySnippets,
                    stageReachedAfterThis = stageReached
                )
            )
        }

        val allJourneyStages = stageNames.mapIndexed { i, (name, req) ->
            PetJourneyStage(
                stageIndex           = i,
                stageName            = name,
                journalsRequired     = req,
                localImagePath       = stages.getOrNull(i)?.let { cachedImages[it.stage] },
                unlockedOnDateMillis = stageUnlockDates[i]
            )
        }

        return PetJourneyData(
            petName           = pet.name,
            moodId            = pet.moodId,
            currentStageIndex = pet.stageIndex,
            days              = journeyDays,
            allStages         = allJourneyStages,
            totalJournalCount = totalJournalsNeeded
        )
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
        val settingsRepo = com.gxdevs.lore.data.SettingsRepository(context)
        val selectedJournalsJson = settingsRepo.dailySelectedJournals.firstOrNull() ?: "{}"
        val selectedJournalsMap  = mutableMapOf<String, Long>()
        try {
            val jsonObj = org.json.JSONObject(selectedJournalsJson)
            jsonObj.keys().forEach { key ->
                selectedJournalsMap[key] = jsonObj.getLong(key)
            }
        } catch (_: Exception) {}

        // Premium gate: free = 1 journal XP per day per mood, premium = up to 3
        val isPremium  = settingsRepo.isPremiumUnlocked.firstOrNull() ?: false
        val dailyXpCap = if (isPremium) 3 else 1

        val entriesByDay = mutableMapOf<String, MutableList<com.gxdevs.lore.data.journal.JournalEntry>>()
        entries.forEach { entry ->
            calendar.timeInMillis = entry.timestamp
            val dayKey = "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.DAY_OF_YEAR)}"
            entriesByDay.getOrPut(dayKey) { mutableListOf() }.add(entry)
        }

        val moodDayCounts = mutableMapOf<String, Int>()

        entriesByDay.forEach { (dayKey, dayEntries) ->
            val selId       = selectedJournalsMap[dayKey]
            val chosenEntry = if (selId != null) dayEntries.find { it.id == selId } else null

            if (dailyXpCap == 1) {
                // ── Free tier: credit exactly 1 dominant mood per day ─────────
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
            } else {
                // ── Premium tier: up to dailyXpCap entries credited per mood ──
                val orderedEntries = if (chosenEntry != null) {
                    listOf(chosenEntry) + dayEntries.filter { it.id != chosenEntry.id }
                } else {
                    dayEntries
                }
                val dayMoodXp = mutableMapOf<String, Int>()
                for (entry in orderedEntries) {
                    val mood = resolveDominantMood(entry.emotions)?.lowercase() ?: continue
                    val current = dayMoodXp[mood] ?: 0
                    if (current < dailyXpCap) dayMoodXp[mood] = current + 1
                }
                dayMoodXp.forEach { (mood, xp) ->
                    moodDayCounts[mood] = (moodDayCounts[mood] ?: 0) + xp
                }
            }
        }

        MoodConstants.ALL_MOODS.forEach { mood ->
            val key   = mood.lowercase()
            val count = moodDayCounts[key] ?: 0
            petProgressDao.upsertPetProgress(PetProgress(moodId = key, journalCount = count))
        }

        val repo = com.gxdevs.lore.pets.PetCatalogRepository(context)
        repo.seedFromAssetsIfEmpty()
        repo.syncIfDue()
    }

    // --- Private helpers ---------------------

    private fun buildPetUiState(
        def: PetDefinition,
        stagesByPet: Map<String, List<PetStageDefinition>>,
        progressByMood: Map<String, PetProgress>,
        cacheByKey: Map<String, com.gxdevs.lore.pets.CachedStageImage>,
        defsByEmotion: Map<String, List<PetDefinition>>,
        isDemo: Boolean,
        stageOverride: Int?,
        nicknames: Map<String, String> = emptyMap()
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
            val neededToUnlock = if (levelIndex > 0) {
                var cumulativePrevMax = 0
                for (i in 0 until levelIndex) {
                    val prevDef = emotionDefs.getOrNull(i) ?: continue
                    val prevStages = stagesByPet[prevDef.petId]?.sortedBy { it.stage } ?: emptyList()
                    val prevMax = prevStages.lastOrNull()?.journalsRequired ?: 0
                    cumulativePrevMax += prevMax
                }
                (cumulativePrevMax - totalJournals).coerceAtLeast(0)
            } else {
                stages.firstOrNull()?.journalsRequired ?: 0
            }
            return PetUiState(
                petId = def.petId, name = def.name, displayName = def.name,
                emotion = emotionKey, moodId = moodId,
                level = def.level, description = def.description, journalCount = 0,
                stageIndex = -1, stageName = "Locked", totalStages = def.totalStages,
                progressInStage = 0f, journalsToNext = neededToUnlock,
                isFullyGrown = false, currentStageImageUrl = null, localImagePath = null
            )
        }

        val stageIdx: Int
        val currentStageDef: PetStageDefinition
        val nextStageDef: PetStageDefinition?
        val highestReachedIdx: Int

        if (isDemo && stageOverride != null) {
            stageIdx = stageOverride.coerceIn(0, stages.lastIndex)
            highestReachedIdx = stageIdx
            currentStageDef = stages[stageIdx]
            nextStageDef = stages.getOrNull(stageIdx + 1)
        } else {
            val journalsToTest = if (isDemo && totalJournals < 1) 1 else effectiveJournals
            var tempHighest = -1
            for (i in stages.indices) {
                if (journalsToTest >= stages[i].journalsRequired) {
                    tempHighest = i
                }
            }
            highestReachedIdx = tempHighest
            if (highestReachedIdx == -1) {
                stageIdx = 0
                currentStageDef = stages.first()
                nextStageDef = stages.first()
            } else {
                stageIdx = highestReachedIdx
                currentStageDef = stages[stageIdx]
                nextStageDef = stages.getOrNull(stageIdx + 1)
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            if (!currentStageDef.imageUrl.isNullOrBlank()) {
                imageCache.getOrDownload(def.petId, currentStageDef.stage, currentStageDef.imageUrl)
            }
        }

        val progressInStage = nextStageDef?.let { next ->
            val prevRequired = if (highestReachedIdx == -1) 0 else currentStageDef.journalsRequired
            val within = effectiveJournals - prevRequired
            val range  = next.journalsRequired - prevRequired
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

        // Resolve displayName: premium nickname (if set) overrides JSON name everywhere
        val nickname = nicknames[def.petId]
        val displayName = if (!nickname.isNullOrBlank()) nickname else def.name

        return PetUiState(
            petId = def.petId, name = def.name, displayName = displayName,
            emotion = emotionKey, moodId = moodId,
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
        if (levelIndex <= 0) return Pair(totalJournals, false)
        var cumulativePrevMax = 0
        for (i in 0 until levelIndex) {
            val prevDef = emotionDefs.getOrNull(i) ?: continue
            val prevStages = stagesByPet[prevDef.petId]?.sortedBy { it.stage } ?: emptyList()
            val prevMax = prevStages.lastOrNull()?.journalsRequired ?: 0
            cumulativePrevMax += prevMax
        }
        if (totalJournals < cumulativePrevMax) {
            return Pair(0, true)
        }
        return Pair((totalJournals - cumulativePrevMax).coerceAtLeast(0), false)
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
                displayName = moodId,
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
                .setSmallIcon(com.gxdevs.lore.R.drawable.paw_print)
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
        val cachedImages: List<com.gxdevs.lore.pets.CachedStageImage>,
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
