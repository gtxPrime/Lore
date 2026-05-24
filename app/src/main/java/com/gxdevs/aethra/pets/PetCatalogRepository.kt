package com.gxdevs.aethra.pets

import android.content.Context
import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.gson.Gson
import com.gxdevs.aethra.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Central repository for the remote pet catalog.
 *
 * Fetch rules:
 *   1. At most once every 24 hours.
 *   2. NEVER mid-journal or cold-start â€” WorkManager worker enforces this.
 *   3. Update only if remote version > local version.
 *   4. NEVER overwrite user [com.gxdevs.aethra.PetProgress] rows.
 *   5. Stage images NOT downloaded here; see [PetImageCache].
 *   6. URL sourced exclusively from Firebase Remote Config (key: "pets_json_url").
 *      If RC is unavailable or the key is blank, sync is silently skipped until
 *      the next 24-hour window when RC may be reachable again.
 */
class PetCatalogRepository(private val context: Context) {

    private val TAG = "PetCatalogRepo"
    private val gson = Gson()

    private val db = AppDatabase.getDatabase(context)
    private val metaDao = db.petCatalogMetaDao()
    private val defDao = db.petDefinitionDao()
    private val stageDao = db.petStageDefinitionDao()

    private val RC_KEY = "pets_json_url"
    private val FETCH_COOLDOWN_MS = 24 * 60 * 60 * 1000L

    // â”€â”€ Public API â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    suspend fun syncIfDue(): SyncResult = withContext(Dispatchers.IO) {
        val meta = metaDao.getMeta() ?: PetCatalogMeta()
        val elapsed = System.currentTimeMillis() - meta.lastFetchedEpoch

        if (elapsed < FETCH_COOLDOWN_MS) {
            Log.d(TAG, "Skipping sync â€” only ${elapsed / 3600000}h since last fetch")
            return@withContext SyncResult.Skipped
        }

        // Resolve URL exclusively from Firebase Remote Config
        val url = resolveUrl()
        if (url == null) {
            Log.w(TAG, "pets_json_url not set in Remote Config â€” skipping sync")
            return@withContext SyncResult.NoConfig
        }

        val json = try {
            fetchJson(url)
        } catch (e: IOException) {
            Log.w(TAG, "No internet or host unreachable: ${e.message}")
            return@withContext SyncResult.NoInternet
        }

        val catalog = try {
            gson.fromJson(json, PetCatalogJson::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Parse error", e)
            return@withContext SyncResult.ParseError(e.message ?: "unknown")
        }

        if (catalog.version <= meta.catalogVersion) {
            metaDao.upsert(meta.copy(lastFetchedEpoch = System.currentTimeMillis()))
            return@withContext SyncResult.AlreadyCurrent(catalog.version)
        }

        Log.i(TAG, "Updating catalog v${meta.catalogVersion} â†’ v${catalog.version}")
        mergeCatalog(catalog)
        metaDao.upsert(
            PetCatalogMeta(
                id = 1,
                catalogVersion = catalog.version,
                lastFetchedEpoch = System.currentTimeMillis()
            )
        )
        SyncResult.Updated(catalog.version, catalog.pets.size)
    }

    /** Seed the demo catalog on first launch if the DB is empty. */
    suspend fun seedDemoCatalogIfEmpty() = withContext(Dispatchers.IO) {
        if (defDao.getAllDefinitionsSuspend().isNotEmpty()) return@withContext
        Log.i(TAG, "Seeding demo catalog")
        val catalog = gson.fromJson(DEMO_CATALOG_JSON, PetCatalogJson::class.java)
        mergeCatalog(catalog)
        // lastFetchedEpoch = 0 so a real remote fetch triggers after 24 h
        metaDao.upsert(PetCatalogMeta(id = 1, catalogVersion = catalog.version, lastFetchedEpoch = 0L))
    }

    // â”€â”€ Internals â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Returns the pets JSON URL from Firebase Remote Config, or null if:
     *  - The RC key "pets_json_url" is blank / not yet published
     *  - RC itself throws (e.g. google-services.json missing in a dev build)
     *
     * Returning null causes [syncIfDue] to emit [SyncResult.NoConfig] so the
     * WorkManager worker skips quietly and retries next 24-hour window.
     */
    private fun resolveUrl(): String? {
        return try {
            val rc = FirebaseRemoteConfig.getInstance()
            val url = rc.getString(RC_KEY)
            url.ifBlank { null }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase Remote Config unavailable", e)
            null
        }
    }

    private fun fetchJson(urlString: String): String {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode != HttpURLConnection.HTTP_OK)
                throw IOException("HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Merges remote catalog into Room.
     * NEVER touches [com.gxdevs.aethra.PetProgress] or [CachedStageImage].
     */
    private suspend fun mergeCatalog(catalog: PetCatalogJson) {
        val defs = catalog.pets.map { p ->
            PetDefinition(
                petId = p.id, name = p.name, emotion = p.emotion,
                level = p.level, description = p.description, totalStages = p.total_stages
            )
        }
        val allStages = catalog.pets.flatMap { p ->
            p.stages.map { s ->
                PetStageDefinition(
                    petId = p.id, stage = s.stage, stageName = s.stage_name,
                    journalsRequired = s.journals_required, imageUrl = s.image_url
                )
            }
        }

        defDao.upsertAll(defs)
        stageDao.upsertAll(allStages)

        // Remove stages that no longer exist in remote
        catalog.pets.forEach { p ->
            val valid = p.stages.map { it.stage }
            if (valid.isNotEmpty()) stageDao.deleteObsoleteStages(p.id, valid)
        }
    }

    sealed class SyncResult {
        /** 24-hour cooldown not yet elapsed â€” nothing to do. */
        object Skipped : SyncResult()
        /** Remote Config key is blank or RC is unreachable â€” retry next window. */
        object NoConfig : SyncResult()
        /** Device has no internet connection â€” WorkManager will retry. */
        object NoInternet : SyncResult()
        /** Remote catalog version matches local â€” timestamp refreshed, no data change. */
        data class AlreadyCurrent(val version: Int) : SyncResult()
        /** Catalog successfully updated from remote. */
        data class Updated(val newVersion: Int, val petsAdded: Int) : SyncResult()
        /** Remote JSON could not be parsed. */
        data class ParseError(val message: String) : SyncResult()
    }

    companion object {
        // Full demo catalog matching the spec format â€” used for first-launch seeding
        val DEMO_CATALOG_JSON = """
{"version":4,"last_updated":"2026-05-17","pets":[
{"id":"bright_001","name":"Auros","emotion":"bright","level":1,
 "description":"Born from your brightest moments, Auros carries the warmth you gave it.",
 "total_stages":6,"stages":[
  {"stage":1,"stage_name":"Egg","journals_required":0,"image_url":"https://res.cloudinary.com/Aethra/image/upload/pets/auros_stage1.png"},
  {"stage":2,"stage_name":"Cracked","journals_required":5,"image_url":"https://res.cloudinary.com/Aethra/image/upload/pets/auros_stage2.png"},
  {"stage":3,"stage_name":"Hatchling","journals_required":15,"image_url":"https://res.cloudinary.com/Aethra/image/upload/pets/auros_stage3.png"},
  {"stage":4,"stage_name":"Juvenile","journals_required":35,"image_url":"https://res.cloudinary.com/Aethra/image/upload/pets/auros_stage4.png"},
  {"stage":5,"stage_name":"Mature","journals_required":70,"image_url":"https://res.cloudinary.com/Aethra/image/upload/pets/auros_stage5.png"},
  {"stage":6,"stage_name":"Mythical","journals_required":120,"image_url":"https://res.cloudinary.com/Aethra/image/upload/pets/auros_stage6.png"}]},
{"id":"bright_002","name":"Solfen","emotion":"bright","level":2,
 "description":"Second of the Bright lineage. Appears only after Auros reaches Mythical.",
 "total_stages":6,"stages":[
  {"stage":1,"stage_name":"Egg","journals_required":0,"image_url":"https://res.cloudinary.com/Aethra/image/upload/pets/solfen_stage1.png"},
  {"stage":2,"stage_name":"Cracked","journals_required":5,"image_url":"https://res.cloudinary.com/Aethra/image/upload/pets/solfen_stage2.png"},
  {"stage":3,"stage_name":"Hatchling","journals_required":15,"image_url":"https://res.cloudinary.com/Aethra/image/upload/pets/solfen_stage3.png"},
  {"stage":4,"stage_name":"Juvenile","journals_required":35,"image_url":"https://res.cloudinary.com/Aethra/image/upload/pets/solfen_stage4.png"},
  {"stage":5,"stage_name":"Mature","journals_required":70,"image_url":"https://res.cloudinary.com/Aethra/image/upload/pets/solfen_stage5.png"},
  {"stage":6,"stage_name":"Mythical","journals_required":120,"image_url":"https://res.cloudinary.com/Aethra/image/upload/pets/solfen_stage6.png"}]},
{"id":"calm_001","name":"Verdis","emotion":"calm","level":1,
 "description":"A serene presence that smooths the ripples of anxious thoughts.",
 "total_stages":6,"stages":[
  {"stage":1,"stage_name":"Egg","journals_required":0,"image_url":"https://res.cloudinary.com/Aethra/image/upload/pets/verdis_stage1.png"},
  {"stage":2,"stage_name":"Cracked","journals_required":5,"image_url":"https://res.cloudinary.com/Aethra/image/upload/pets/verdis_stage2.png"},
  {"stage":3,"stage_name":"Hatchling","journals_required":15,"image_url":"https://res.cloudinary.com/Aethra/image/upload/pets/verdis_stage3.png"},
  {"stage":4,"stage_name":"Juvenile","journals_required":35,"image_url":"https://res.cloudinary.com/Aethra/image/upload/pets/verdis_stage4.png"},
  {"stage":5,"stage_name":"Mature","journals_required":70,"image_url":"https://res.cloudinary.com/Aethra/image/upload/pets/verdis_stage5.png"},
  {"stage":6,"stage_name":"Mythical","journals_required":120,"image_url":"https://res.cloudinary.com/Aethra/image/upload/pets/verdis_stage6.png"}]},
{"id":"heavy_001","name":"Lumbre","emotion":"heavy","level":1,
 "description":"Formed from unspoken burdens, slowly turning grief into resilience.",
 "total_stages":6,"stages":[
  {"stage":1,"stage_name":"Egg","journals_required":0,"image_url":"https://res.cloudinary.com/Aethra/image/upload/pets/lumbre_stage1.png"},
  {"stage":2,"stage_name":"Cracked","journals_required":5,"image_url":"https://res.cloudinary.com/Aethra/image/upload/pets/lumbre_stage2.png"},
  {"stage":3,"stage_name":"Hatchling","journals_required":15,"image_url":"https://res.cloudinary.com/Aethra/image/upload/pets/lumbre_stage3.png"},
  {"stage":4,"stage_name":"Juvenile","journals_required":35,"image_url":"https://res.cloudinary.com/Aethra/image/upload/pets/lumbre_stage4.png"},
  {"stage":5,"stage_name":"Mature","journals_required":70,"image_url":"https://res.cloudinary.com/Aethra/image/upload/pets/lumbre_stage5.png"},
  {"stage":6,"stage_name":"Mythical","journals_required":120,"image_url":"https://res.cloudinary.com/Aethra/image/upload/pets/lumbre_stage6.png"}]},
{"id":"tangled_001","name":"Nixara","emotion":"tangled","level":1,
 "description":"A knot of confusion that teaches patience as feelings unravel.",
 "total_stages":6,"stages":[
  {"stage":1,"stage_name":"Egg","journals_required":0,"image_url":"https://res.cloudinary.com/Aethra/image/upload/pets/nixara_stage1.png"},
  {"stage":2,"stage_name":"Cracked","journals_required":5,"image_url":"https://res.cloudinary.com/Aethra/image/upload/pets/nixara_stage2.png"},
  {"stage":3,"stage_name":"Hatchling","journals_required":15,"image_url":"https://res.cloudinary.com/Aethra/image/upload/pets/nixara_stage3.png"},
  {"stage":4,"stage_name":"Juvenile","journals_required":35,"image_url":"https://res.cloudinary.com/Aethra/image/upload/pets/nixara_stage4.png"},
  {"stage":5,"stage_name":"Mature","journals_required":70,"image_url":"https://res.cloudinary.com/Aethra/image/upload/pets/nixara_stage5.png"},
  {"stage":6,"stage_name":"Mythical","journals_required":120,"image_url":"https://res.cloudinary.com/Aethra/image/upload/pets/nixara_stage6.png"}]},
{"id":"dark_001","name":"Umbrix","emotion":"dark","level":1,
 "description":"A shadow companion providing comfort in the quiet of the night.",
 "total_stages":6,"stages":[
  {"stage":1,"stage_name":"Egg","journals_required":0,"image_url":"https://res.cloudinary.com/Aethra/image/upload/pets/umbrix_stage1.png"},
  {"stage":2,"stage_name":"Cracked","journals_required":5,"image_url":"https://res.cloudinary.com/Aethra/image/upload/pets/umbrix_stage2.png"},
  {"stage":3,"stage_name":"Hatchling","journals_required":15,"image_url":"https://res.cloudinary.com/Aethra/image/upload/pets/umbrix_stage3.png"},
  {"stage":4,"stage_name":"Juvenile","journals_required":35,"image_url":"https://res.cloudinary.com/Aethra/image/upload/pets/umbrix_stage4.png"},
  {"stage":5,"stage_name":"Mature","journals_required":70,"image_url":"https://res.cloudinary.com/Aethra/image/upload/pets/umbrix_stage5.png"},
  {"stage":6,"stage_name":"Mythical","journals_required":120,"image_url":"https://res.cloudinary.com/Aethra/image/upload/pets/umbrix_stage6.png"}]},
{"id":"blank_001","name":"Vael","emotion":"blank","level":1,
 "description":"An empty slate, representing the potential for new beginnings.",
 "total_stages":6,"stages":[
  {"stage":1,"stage_name":"Egg","journals_required":0,"image_url":"https://res.cloudinary.com/Aethra/image/upload/pets/vael_stage1.png"},
  {"stage":2,"stage_name":"Cracked","journals_required":5,"image_url":"https://res.cloudinary.com/Aethra/image/upload/pets/vael_stage2.png"},
  {"stage":3,"stage_name":"Hatchling","journals_required":15,"image_url":"https://res.cloudinary.com/Aethra/image/upload/pets/vael_stage3.png"},
  {"stage":4,"stage_name":"Juvenile","journals_required":35,"image_url":"https://res.cloudinary.com/Aethra/image/upload/pets/vael_stage4.png"},
  {"stage":5,"stage_name":"Mature","journals_required":70,"image_url":"https://res.cloudinary.com/Aethra/image/upload/pets/vael_stage5.png"},
  {"stage":6,"stage_name":"Mythical","journals_required":120,"image_url":"https://res.cloudinary.com/Aethra/image/upload/pets/vael_stage6.png"}]}
]}""".trimIndent()
    }
}

