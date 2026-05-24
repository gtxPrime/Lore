package com.gxdevs.aethra.pets

import android.content.Context
import android.util.Log
import com.gxdevs.aethra.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages permanent, on-demand download + caching of Cloudinary pet stage images.
 *
 * Rules:
 *   - Images are ONLY downloaded when a user UNLOCKS that stage.
 *   - Once cached to local storage, they are NEVER re-downloaded.
 *   - Files are stored in: [Context.filesDir]/pet_images/{petId}_{stage}.png
 */
class PetImageCache(private val context: Context) {

    private val TAG = "PetImageCache"
    private val db = AppDatabase.getDatabase(context)
    private val cacheDao = db.cachedStageImageDao()
    private val imageDir by lazy {
        File(context.filesDir, "pet_images").also { it.mkdirs() }
    }

    /**
     * Returns the local [File] for a stage image, downloading it from [imageUrl]
     * if it has not been cached yet. Returns null if the download fails.
     *
     * Call this ONLY when a stage is newly unlocked — never on every render.
     */
    suspend fun getOrDownload(petId: String, stage: Int, imageUrl: String): File? =
        withContext(Dispatchers.IO) {
            // 1. Check DB cache record
            val cached = cacheDao.getCachedImage(petId, stage)
            if (cached != null) {
                val file = File(cached.localPath)
                if (file.exists()) {
                    Log.d(TAG, "Cache hit: $petId stage $stage")
                    return@withContext file
                }
                // File was deleted externally — re-download
                Log.w(TAG, "Cache record exists but file missing, re-downloading")
            }

            // 2. Download from Cloudinary
            val localFile = File(imageDir, "${petId}_stage${stage}.png")
            Log.i(TAG, "Downloading $imageUrl → ${localFile.name}")
            val success = downloadToFile(imageUrl, localFile)

            if (!success) {
                Log.e(TAG, "Download failed for $petId stage $stage")
                return@withContext null
            }

            // 3. Record in DB — permanent, never deleted unless user clears all data
            cacheDao.insert(
                CachedStageImage(
                    petId = petId,
                    stage = stage,
                    localPath = localFile.absolutePath
                )
            )

            Log.i(TAG, "Cached $petId stage $stage to ${localFile.absolutePath}")
            localFile
        }

    /**
     * Returns the cached local [File] if it already exists, without downloading.
     * Returns null if not yet cached.
     */
    suspend fun getCachedOnly(petId: String, stage: Int): File? = withContext(Dispatchers.IO) {
        val cached = cacheDao.getCachedImage(petId, stage) ?: return@withContext null
        val file = File(cached.localPath)
        if (file.exists()) file else null
    }

    /**
     * Checks if a stage image is already cached without hitting the DB.
     * Useful for quick UI state checks on the main thread after an initial cache load.
     */
    fun isCachedLocally(petId: String, stage: Int): Boolean {
        return File(imageDir, "${petId}_stage${stage}.png").exists()
    }

    /** Purge all cached image files and DB records. Called from "Delete Everything". */
    suspend fun purgeAll() = withContext(Dispatchers.IO) {
        cacheDao.deleteAll()
        imageDir.listFiles()?.forEach { it.delete() }
        Log.i(TAG, "Purged all cached pet stage images")
    }

    // --- Private helpers ---

    private fun downloadToFile(urlString: String, dest: File): Boolean {
        return try {
            val conn = URL(urlString).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.requestMethod = "GET"
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${conn.responseCode}")
            }
            conn.inputStream.use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            conn.disconnect()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Download error: ${e.message}")
            dest.delete() // Clean up partial file
            false
        }
    }
}

