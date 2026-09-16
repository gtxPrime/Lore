package com.gxdevs.lore.pets

import android.content.Context
import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.gson.Gson
import com.gxdevs.lore.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Central repository for the remote pet catalog.
 */
class PetCatalogRepository(private val context: Context) {

    private val tag = "PetCatalogRepo"
    private val gson = Gson()

    private val db = AppDatabase.getDatabase(context)
    private val metaDao = db.petCatalogMetaDao()
    private val defDao = db.petDefinitionDao()
    private val stageDao = db.petStageDefinitionDao()

    private val rcKEY = "pets_json_url"
    private val fetchCooldownMs = 24 * 60 * 60 * 1000L

    // --- Public API ---

    suspend fun seedFromAssetsIfEmpty() = withContext(Dispatchers.IO) {
        try {
            val existingDefs = defDao.getAllDefinitionsSuspend()
            val meta = metaDao.getMeta()
            Log.d(tag, "[Seed] Current DB defs count: ${existingDefs.size}, catalogVersion: ${meta?.catalogVersion}")

            val jsonString = context.assets.open("pets.json").bufferedReader().use { it.readText() }
            val catalog = gson.fromJson(jsonString, PetCatalogJson::class.java)

            Log.i(tag, "[Seed] Seeding/Updating catalog from assets/pets.json (v${catalog.version}, ${catalog.pets.size} pets)...")
            mergeCatalog(catalog)
            metaDao.upsert(
                PetCatalogMeta(
                    id = 1,
                    catalogVersion = catalog.version,
                    lastFetchedEpoch = meta?.lastFetchedEpoch ?: 0L
                )
            )
            Log.i(tag, "[Seed] Successfully synced ${catalog.pets.size} pets from assets!")
        } catch (e: Exception) {
            Log.e(tag, "[Seed] Failed to seed catalog from assets/pets.json", e)
        }
    }

    suspend fun syncIfDue(): SyncResult = withContext(Dispatchers.IO) {
        seedFromAssetsIfEmpty()

        val meta = metaDao.getMeta() ?: PetCatalogMeta()
        val elapsed = System.currentTimeMillis() - meta.lastFetchedEpoch

        if (elapsed < fetchCooldownMs) {
            Log.d(tag, "[Sync] Skipping sync — only ${elapsed / 3600000}h since last fetch (cooldown: 24h)")
            return@withContext SyncResult.Skipped
        }

        val url = resolveUrl()
        if (url == null) {
            Log.w(tag, "[Sync] pets_json_url not set in Remote Config — skipping sync")
            return@withContext SyncResult.NoConfig
        }

        Log.i(tag, "[Sync] Fetching pet catalog JSON from: $url")
        val json = try {
            fetchJson(url)
        } catch (e: IOException) {
            Log.w(tag, "[Sync] No internet or host unreachable: ${e.message}")
            return@withContext SyncResult.NoInternet
        }

        val catalog = try {
            gson.fromJson(json, PetCatalogJson::class.java)
        } catch (e: Exception) {
            Log.e(tag, "[Sync] Parse error for catalog JSON", e)
            return@withContext SyncResult.ParseError(e.message ?: "unknown")
        }

        Log.i(tag, "[Sync] Remote catalog parsed: v${catalog.version} with ${catalog.pets.size} pets")

        if (catalog.version <= meta.catalogVersion) {
            Log.d(tag, "[Sync] Remote version v${catalog.version} <= current local v${meta.catalogVersion} — up to date")
            metaDao.upsert(meta.copy(lastFetchedEpoch = System.currentTimeMillis()))
            return@withContext SyncResult.AlreadyCurrent(catalog.version)
        }

        Log.i(tag, "[Sync] Updating catalog v${meta.catalogVersion} → v${catalog.version}")
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

    // --- Internals ---

    private fun resolveUrl(): String? {
        return try {
            val rc = FirebaseRemoteConfig.getInstance()
            val raw = rc.getString(rcKEY)
            Log.d(tag, "[RC] pets_json_url raw value from Remote Config: '$raw'")
            if (raw.isBlank()) null else normalizeUrl(raw)
        } catch (e: Exception) {
            Log.w(tag, "[RC] Firebase Remote Config unavailable", e)
            null
        }
    }


    private fun normalizeUrl(url: String): String = when {
        url.contains("dropbox.com") -> url
            .replace("www.dropbox.com", "dl.dropboxusercontent.com")
            .replace("&dl=0", "&dl=1")
            .replace("?dl=0", "?dl=1")
            .let { if (!it.contains("dl=1")) "$it&dl=1" else it }
        else -> url
    }

    private fun fetchJson(urlString: String): String {
        var currentUrl = urlString
        var attempts = 0
        while (attempts < 5) {
            val conn = URL(currentUrl).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout          = 15_000
                conn.readTimeout             = 20_000
                conn.requestMethod           = "GET"
                conn.instanceFollowRedirects = false   // Handle cross-domain redirects manually
                conn.setRequestProperty("Accept", "application/json, text/plain, */*")
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Lore/1.0)")

                val code = conn.responseCode
                if (code == HttpURLConnection.HTTP_OK) {
                    return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                } else if (code in 301..308) {
                    val location = conn.getHeaderField("Location")
                        ?: throw IOException("Redirect with no Location header from $currentUrl")
                    currentUrl = location
                    attempts++
                } else {
                    throw IOException("HTTP $code for $currentUrl")
                }
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("Too many redirects for $urlString")
    }

    private suspend fun mergeCatalog(catalog: PetCatalogJson) {
        val defs = catalog.pets.map { p ->
            PetDefinition(
                petId = p.id, name = p.name, emotion = p.emotion,
                level = p.level, description = p.description, totalStages = p.totalStages
            )
        }
        val allStages = catalog.pets.flatMap { p ->
            p.stages.map { s ->
                PetStageDefinition(
                    petId = p.id, stage = s.stage, stageName = s.stageName,
                    journalsRequired = s.journalsRequired, imageUrl = s.imageUrl
                )
            }
        }

        defDao.upsertAll(defs)
        stageDao.upsertAll(allStages)

        catalog.pets.forEach { p ->
            val valid = p.stages.map { it.stage }
            if (valid.isNotEmpty()) stageDao.deleteObsoleteStages(p.id, valid)
        }
    }

    sealed class SyncResult {
        object Skipped : SyncResult()
        object NoConfig : SyncResult()
        object NoInternet : SyncResult()
        data class AlreadyCurrent(val version: Int) : SyncResult()
        data class Updated(val newVersion: Int, val petsAdded: Int) : SyncResult()
        data class ParseError(val message: String) : SyncResult()
    }
}
