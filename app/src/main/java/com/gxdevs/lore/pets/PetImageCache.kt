package com.gxdevs.lore.pets

import android.content.Context
import android.util.Base64
import android.util.Log
import com.gxdevs.lore.data.AppDatabase
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

    companion object {
        private val KEYS_TO_TRY = listOf("nurtale_26_px", "lore_26_px")
        private const val ENC_PREFIX_STATIC = "enc:"

        /**
         * Decrypts (if needed) and normalises a raw imageUrl from pets.json
         * into a direct-download URL. Safe to call from any thread / composable.
         */
        fun resolveUrl(raw: String?): String {
            if (raw.isNullOrBlank()) return ""
            val decrypted = decryptStatic(raw)
            val normalized = normalizeStatic(decrypted)
            android.util.Log.d("PetImageCache", "[Resolve] raw='$raw' -> decrypted='$decrypted' -> normalized='$normalized'")
            return normalized
        }

        private fun decryptStatic(raw: String): String {
            if (!raw.startsWith(ENC_PREFIX_STATIC)) return raw
            val b64 = raw.removePrefix(ENC_PREFIX_STATIC)
            val encoded = try {
                Base64.decode(b64, Base64.DEFAULT)
            } catch (e: Exception) {
                android.util.Log.e("PetImageCache", "Base64 decode failed for static URL: ${e.message}")
                return ""
            }

            for (keyStr in KEYS_TO_TRY) {
                try {
                    val keyBytes = keyStr.toByteArray(Charsets.UTF_8)
                    val decrypted = ByteArray(encoded.size) { i ->
                        (encoded[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
                    }
                    val result = String(decrypted, Charsets.UTF_8)
                    if (result.startsWith("http://") || result.startsWith("https://")) {
                        return result
                    }
                } catch (_: Exception) {}
            }
            android.util.Log.e("PetImageCache", "All decryption keys failed for raw URL: $raw")
            return ""
        }

        private fun normalizeStatic(url: String): String = when {
            url.contains("dropbox.com") -> url
                .replace("www.dropbox.com", "dl.dropboxusercontent.com")
                .replace("&dl=0", "&dl=1")
                .replace("?dl=0", "?dl=1")
                .let { if (!it.contains("dl=1")) "$it&dl=1" else it }
            else -> url
        }
    }

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
        return decryptStatic(raw)
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

    // ── Download with Retries & Redirect Handling ────────────────────────────

    private fun downloadToFile(urlString: String, dest: File): Boolean {
        var currentUrl = urlString
        var retries = 3
        while (retries > 0) {
            try {
                var attempts = 0
                while (attempts < 5) {
                    Log.d(tag, "[Download] Connecting (attempt ${attempts + 1}): $currentUrl")
                    val conn = URL(currentUrl).openConnection() as HttpURLConnection
                    conn.connectTimeout          = 15_000
                    conn.readTimeout             = 20_000
                    conn.requestMethod           = "GET"
                    conn.instanceFollowRedirects = false // Manual handle for cross-domain redirects (e.g. Dropbox -> S3)
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Lore/1.0)")
                    conn.setRequestProperty("Accept", "image/png,image/*;q=0.9,*/*;q=0.8")

                    val code = conn.responseCode
                    Log.d(tag, "[Download] Response code: $code for $currentUrl")

                    if (code == HttpURLConnection.HTTP_OK) {
                        conn.inputStream.use { input ->
                            dest.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        conn.disconnect()
                        if (dest.exists() && dest.length() > 100) {
                            Log.i(tag, "[Download] Success! Saved ${dest.length()} bytes to ${dest.name}")
                            return true
                        } else {
                            Log.w(tag, "[Download] File saved but empty/too small (${dest.length()} bytes)")
                        }
                    } else if (code in 301..308) {
                        val location = conn.getHeaderField("Location")
                        conn.disconnect()
                        if (location.isNullOrBlank()) {
                            Log.e(tag, "[Download] Redirect code $code received but Location header missing!")
                            break
                        }
                        Log.d(tag, "[Download] Redirecting ($code) -> $location")
                        currentUrl = location
                        attempts++
                    } else {
                        Log.w(tag, "[Download] HTTP error $code for $currentUrl")
                        conn.disconnect()
                        break
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "[Download] Exception (retries left=$retries) for $currentUrl: ${e.message}", e)
            }
            retries--
        }
        if (dest.exists()) {
            dest.delete()
        }
        Log.e(tag, "[Download] Failed all download attempts for $urlString")
        return false
    }
}
