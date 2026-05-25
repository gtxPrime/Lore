package com.gxdevs.aethra.pets

import android.content.Context
import android.util.Log
import com.gxdevs.aethra.data.AppDatabase
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

    private val tag = "PetImageCache"
    private val db = AppDatabase.getDatabase(context)
    private val cacheDao = db.cachedStageImageDao()
    private val imageDir by lazy {
        File(context.filesDir, "pet_images").also { it.mkdirs() }
    }

    /**
     * Returns the local [File] for a stage image, downloading it from [imageUrl]
     * if it has not been cached yet. Returns null if the download fails.
     *
     * Call this ONLY when a stage is newly unlocked â€” never on every render.
     */
    suspend fun getOrDownload(petId: String, stage: Int, imageUrl: String): File? =
        withContext(Dispatchers.IO) {
            // 1. Check DB cache record
            val cached = cacheDao.getCachedImage(petId, stage)
            if (cached != null) {
                val file = File(cached.localPath)
                if (file.exists()) {
                    Log.d(tag, "Cache hit: $petId stage $stage")
                    return@withContext file
                }
                // File was deleted externally â€” re-download
                Log.w(tag, "Cache record exists but file missing, re-downloading")
            }

            // 2. Download from Cloudinary
            val localFile = File(imageDir, "${petId}_stage${stage}.png")
            Log.i(tag, "Downloading $imageUrl â†’ ${localFile.name}")
            val success = downloadToFile(imageUrl, localFile)

            if (!success) {
                Log.e(tag, "Download failed for $petId stage $stage")
                return@withContext null
            }

            // 3. Record in DB â€” permanent, never deleted unless user clears all data
            cacheDao.insert(
                CachedStageImage(
                    petId = petId,
                    stage = stage,
                    localPath = localFile.absolutePath
                )
            )

            Log.i(tag, "Cached $petId stage $stage to ${localFile.absolutePath}")
            localFile
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
            Log.e(tag, "Download error: ${e.message}")
            dest.delete() // Clean up partial file
            false
        }
    }
}

