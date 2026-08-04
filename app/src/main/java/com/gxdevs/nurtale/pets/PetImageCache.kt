package com.gxdevs.nurtale.pets

import android.content.Context
import android.util.Base64
import android.util.Log
import com.gxdevs.nurtale.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages permanent, on-demand download + caching of remote pet stage images.
 *
 * URL pipeline (applied in order before every download):
 *   1. decryptUrl()    — decode "enc:<base64>" XOR-encrypted URLs from pets.json
 *   2. normalizeUrl()  — convert CDN share links to direct download URLs
 */
class PetImageCache(private val context: Context) {

    private val tag = "PetImageCache"
    private val db = AppDatabase.getDatabase(context)
    private val cacheDao = db.cachedStageImageDao()
    private val imageDir by lazy {
        File(context.filesDir, "pet_images").also { it.mkdirs() }
    }

    private val ENC_KEY = "nurtale_26_px"
    private val ENC_PREFIX = "enc:"

    // ── Public API ────────────────────────────────────────────────────────────

    suspend fun getOrDownload(petId: String, stage: Int, imageUrl: String?): File? =
        withContext(Dispatchers.IO) {

            if (imageUrl.isNullOrBlank()) {
                Log.d(tag, "No image URL for $petId stage $stage — emoji fallback")
                return@withContext null
            }

            val decrypted = decryptUrl(imageUrl)
            val directUrl = normalizeUrl(decrypted)

            // Return cached file if it already exists on disk
            val cached = cacheDao.getCachedImage(petId, stage)
            if (cached != null) {
                val file = File(cached.localPath)
                if (file.exists() && file.length() > 100) {
                    Log.d(tag, "Cache hit: $petId stage $stage")
                    return@withContext file
                }
            }

            val localFile = File(imageDir, "${petId}_stage${stage}.png")
            if (localFile.exists() && localFile.length() > 100) {
                cacheDao.insert(
                    CachedStageImage(
                        petId = petId,
                        stage = stage,
                        localPath = localFile.absolutePath
                    )
                )
                return@withContext localFile
            }

            Log.i(tag, "Downloading $petId stage $stage from $directUrl")
            if (!downloadToFile(directUrl, localFile)) {
                Log.e(tag, "Download failed for $petId stage $stage")
                return@withContext null
            }

            cacheDao.insert(
                CachedStageImage(
                    petId = petId,
                    stage = stage,
                    localPath = localFile.absolutePath
                )
            )
            Log.i(tag, "Cached $petId stage $stage → ${localFile.name}")
            localFile
        }

    // ── Decryption ────────────────────────────────────────────────────────────

    private fun decryptUrl(raw: String): String {
        if (!raw.startsWith(ENC_PREFIX)) return raw
        return try {
            val b64 = raw.removePrefix(ENC_PREFIX)
            val encoded = Base64.decode(b64, Base64.DEFAULT)
            val keyBytes = ENC_KEY.toByteArray(Charsets.UTF_8)
            val decrypted = ByteArray(encoded.size) { i ->
                (encoded[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
            }
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(tag, "URL decryption failed: ${e.message}")
            ""
        }
    }

    // ── URL normalisation ─────────────────────────────────────────────────────

    private fun normalizeUrl(url: String): String = when {
        url.contains("dropbox.com") -> url
            .replace("www.dropbox.com", "dl.dropboxusercontent.com")
            .replace("&dl=0", "&dl=1")
            .replace("?dl=0", "?dl=1")
            .let { if (!it.contains("dl=1")) "$it&dl=1" else it }
        else -> url
    }

    // ── Download with Retries ──────────────────────────────────────────────────

    private fun downloadToFile(urlString: String, dest: File): Boolean {
        var retries = 3
        while (retries > 0) {
            try {
                val conn = URL(urlString).openConnection() as HttpURLConnection
                conn.connectTimeout          = 10_000
                conn.readTimeout             = 20_000
                conn.requestMethod           = "GET"
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                conn.setRequestProperty("Accept", "image/png,image/*;q=0.9,*/*;q=0.8")

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    conn.inputStream.use { input ->
                        dest.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    conn.disconnect()
                    if (dest.exists() && dest.length() > 100) {
                        return true
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.w(tag, "Download retry ($retries left) for $urlString: ${e.message}")
            }
            retries--
        }
        if (dest.exists()) dest.delete()
        return false
    }
}
