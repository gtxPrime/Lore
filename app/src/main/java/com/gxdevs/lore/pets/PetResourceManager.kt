package com.gxdevs.lore.pets

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.gxdevs.lore.data.AppDatabase
import com.gxdevs.lore.data.journal.JournalEntry
import com.gxdevs.lore.data.mood.MoodConstants
import com.gxdevs.lore.ui.journal.Emotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Manages full recalculation and parallel pre-downloading of all pet stage images & files.
 * Call after data imports (Google Drive restore or manual ZIP import) to guarantee
 * all pet companion assets are ready on disk.
 */
object PetResourceManager {

    private const val TAG = "PetResourceManager"

    /**
     * Recalculates pet progress from journal history and pre-downloads ALL stage files/images in parallel.
     */
    suspend fun syncAndDownloadPetResources(context: Context) = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Starting full pet resource recalculation and asset sync...")
            val db = AppDatabase.getDatabase(context)
            val journalDao = db.journalDao()
            val petProgressDao = db.petProgressDao()
            val defDao = db.petDefinitionDao()
            val stageDao = db.petStageDefinitionDao()

            // 1. Ensure pet catalog is seeded from assets
            val catalogRepo = PetCatalogRepository(context)
            catalogRepo.seedFromAssetsIfEmpty()

            // 2. Recalculate mood journal counts from history
            val entries = journalDao.getAllEntriesWithEmotions()
            val calendar = Calendar.getInstance()
            val settingsRepo = com.gxdevs.lore.data.SettingsRepository(context)
            val selectedJournalsJson = settingsRepo.dailySelectedJournals.firstOrNull() ?: "{}"
            val selectedJournalsMap = mutableMapOf<String, Long>()
            try {
                val jsonObj = org.json.JSONObject(selectedJournalsJson)
                jsonObj.keys().forEach { key ->
                    selectedJournalsMap[key] = jsonObj.getLong(key)
                }
            } catch (_: Exception) {}

            val isPremium = settingsRepo.isPremiumUnlocked.firstOrNull() ?: false
            val dailyXpCap = if (isPremium) 3 else 1

            val entriesByDay = mutableMapOf<String, MutableList<JournalEntry>>()
            entries.forEach { entry ->
                calendar.timeInMillis = entry.timestamp
                val dayKey = "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.DAY_OF_YEAR)}"
                entriesByDay.getOrPut(dayKey) { mutableListOf() }.add(entry)
            }

            val moodDayCounts = mutableMapOf<String, Int>()
            entriesByDay.forEach { (dayKey, dayEntries) ->
                val selId = selectedJournalsMap[dayKey]
                val chosenEntry = if (selId != null) dayEntries.find { it.id == selId } else null

                if (dailyXpCap == 1) {
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
                val key = mood.lowercase()
                val count = moodDayCounts[key] ?: 0
                petProgressDao.upsertPetProgress(PetProgress(moodId = key, journalCount = count))
            }

            // 3. Pre-download all pet stage images in parallel
            val imageCache = PetImageCache(context)
            val defs = defDao.getAllDefinitionsSuspend()
            coroutineScope {
                val jobs = defs.flatMap { def ->
                    val stages = stageDao.getStagesForPet(def.petId)
                    stages.mapNotNull { stageDef ->
                        if (!stageDef.imageUrl.isNullOrBlank()) {
                            launch {
                                imageCache.getOrDownload(def.petId, stageDef.stage, stageDef.imageUrl)
                            }
                        } else null
                    }
                }
                jobs.joinAll()
            }
            Log.i(TAG, "Pet resource sync & downloads completed successfully!")
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing pet resources: ${e.message}", e)
        }
    }

    private fun resolveDominantMood(emotionsJson: String?): String? {
        if (emotionsJson.isNullOrBlank()) return null
        return try {
            val gson = Gson()
            val ems = gson.fromJson(emotionsJson, Array<Emotion>::class.java)
            val counts = mutableMapOf<String, Int>()
            ems.forEach { em ->
                val mood = MoodConstants.emotionToMood(em.label)
                counts[mood] = (counts[mood] ?: 0) + 1
            }
            counts.maxByOrNull { it.value }?.key
        } catch (_: Exception) { null }
    }
}
