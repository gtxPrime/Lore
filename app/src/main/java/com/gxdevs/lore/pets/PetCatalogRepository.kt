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
            val jsonString = context.assets.open("pets.json").bufferedReader().use { it.readText() }
            val catalog = gson.fromJson(jsonString, PetCatalogJson::class.java)
            val meta = metaDao.getMeta()
            if (defDao.getAllDefinitionsSuspend().isNotEmpty() && meta?.catalogVersion == catalog.version) return@withContext
            Log.i(tag, "Seeding catalog from assets/pets.json (v${catalog.version})...")
            mergeCatalog(catalog)
            metaDao.upsert(
                PetCatalogMeta(
                    id = 1,
                    catalogVersion = catalog.version,
                    lastFetchedEpoch = meta?.lastFetchedEpoch ?: 0L
                )
            )
            Log.i(tag, "Successfully seeded ${catalog.pets.size} pets from assets!")
        } catch (e: Exception) {
            Log.e(tag, "Failed to seed catalog from assets/pets.json", e)
        }
    }

    suspend fun syncIfDue(): SyncResult = withContext(Dispatchers.IO) {
        // Ensure database has local seed if empty
        seedFromAssetsIfEmpty()

        val meta = metaDao.getMeta() ?: PetCatalogMeta()
        val elapsed = System.currentTimeMillis() - meta.lastFetchedEpoch

        if (elapsed < fetchCooldownMs) {
            Log.d(tag, "Skipping sync — only ${elapsed / 3600000}h since last fetch")
            return@withContext SyncResult.Skipped
        }

        // Resolve URL exclusively from Firebase Remote Config
        val url = resolveUrl()
        if (url == null) {
            Log.w(tag, "pets_json_url not set in Remote Config — skipping sync")
            return@withContext SyncResult.NoConfig
        }

        val json = try {
            fetchJson(url)
        } catch (e: IOException) {
            Log.w(tag, "No internet or host unreachable: ${e.message}")
            return@withContext SyncResult.NoInternet
        }

        val catalog = try {
            gson.fromJson(json, PetCatalogJson::class.java)
        } catch (e: Exception) {
            Log.e(tag, "Parse error", e)
            return@withContext SyncResult.ParseError(e.message ?: "unknown")
        }

        if (catalog.version <= meta.catalogVersion) {
            metaDao.upsert(meta.copy(lastFetchedEpoch = System.currentTimeMillis()))
            return@withContext SyncResult.AlreadyCurrent(catalog.version)
        }

        Log.i(tag, "Updating catalog v${meta.catalogVersion} → v${catalog.version}")
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
            if (raw.isBlank()) null else normalizeUrl(raw)
        } catch (e: Exception) {
            Log.w(tag, "Firebase Remote Config unavailable", e)
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
        val conn = URL(urlString).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout          = 10_000
            conn.readTimeout             = 15_000
            conn.requestMethod           = "GET"
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode != HttpURLConnection.HTTP_OK)
                throw IOException("HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
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
