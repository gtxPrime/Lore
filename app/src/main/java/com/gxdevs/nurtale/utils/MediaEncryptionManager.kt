package com.gxdevs.nurtale.utils

import android.content.Context
import androidx.core.net.toUri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.gxdevs.nurtale.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Handles all encrypted-media operations for the app:
 *  - Encrypting content:// or file:// URIs to internal app storage
 *  - Decrypting to a temp file for playback
 *  - Deleting encrypted files from internal storage
 *  - Batch-migrating all existing journal entries when the user turns on Encrypt Media
 */
object MediaEncryptionManager {

    private const val ENC_DIR = "encrypted_media"
    private const val ENC_EXT = ".enc"

    /** Returns the encrypted_media directory, creating it if needed. */
    fun encDir(context: Context): File =
        File(context.filesDir, ENC_DIR).also { it.mkdirs() }

    /** Returns true if [path] points to an encrypted file managed by this class. */
    fun isEncrypted(path: String?): Boolean =
        path != null && (path.contains(ENC_DIR) && path.endsWith(ENC_EXT))

    /** Sniffs the decrypted stream of an encrypted file to check if it's a video. */
    fun isVideoEncrypted(context: Context, encPath: String): Boolean {
        try {
            val file = File(encPath)
            if (!file.exists()) return false
            val stream = openDecryptedStream(context, encPath) ?: return false
            stream.use { input ->
                val header = ByteArray(12)
                val read = input.read(header)
                if (read < 8) return false
                // Check for MP4 signature: 'ftyp' at bytes 4..7 (hex: 66 74 79 70)
                val isMp4 = (header[4].toInt() and 0xFF) == 0x66 && (header[5].toInt() and 0xFF) == 0x74 && 
                            (header[6].toInt() and 0xFF) == 0x79 && (header[7].toInt() and 0xFF) == 0x70
                // Check for EBML (mkv/webm) signature: 1A 45 DF A3 at bytes 0..3
                val isMkv = (header[0].toInt() and 0xFF) == 0x1A && (header[1].toInt() and 0xFF) == 0x45 && 
                            (header[2].toInt() and 0xFF) == 0xDF && (header[3].toInt() and 0xFF) == 0xA3
                return isMp4 || isMkv
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Sniffs the first 12 bytes of [header] and returns:
     *  "VIDEO" for MP4/MKV/WebM/AVI,  "AUDIO" for WAV/MP3/M4A/OGG/FLAC, "IMAGE" otherwise.
     * The [header] must be at least 12 bytes (fewer bytes → best-effort).
     */
    fun sniffMediaType(header: ByteArray): String {
        val b = header
        val len = b.size
        // MP4 / M4V / M4A: 'ftyp' at bytes 4–7
        if (len >= 8 &&
            b[4].toInt() and 0xFF == 0x66 && b[5].toInt() and 0xFF == 0x74 &&
            b[6].toInt() and 0xFF == 0x79 && b[7].toInt() and 0xFF == 0x70) {
            // Inspect the 4-byte major brand at bytes 8–11 to distinguish audio from video.
            // Audio brands: M4A , M4B , f4a , f4b , isom (used by Android MediaRecorder
            //               for .m4a), mp42, MSNV (voice recorders).
            // Everything else (avc1, mp41, M4V , f4v , etc.) is treated as video.
            if (len >= 12) {
                val brand = String(b, 8, 4, Charsets.ISO_8859_1)
                val isAudioBrand = brand == "M4A " || brand == "M4B " ||
                    brand == "f4a " || brand == "f4b " ||
                    brand == "isom" || brand == "mp42" ||
                    brand == "MSNV"
                return if (isAudioBrand) "AUDIO" else "VIDEO"
            }
            return "VIDEO"
        }
        // EBML (MKV / WebM)
        if (len >= 4 &&
            b[0].toInt() and 0xFF == 0x1A && b[1].toInt() and 0xFF == 0x45 &&
            b[2].toInt() and 0xFF == 0xDF && b[3].toInt() and 0xFF == 0xA3) return "VIDEO"
        // AVI: 'RIFF....AVI '
        if (len >= 12 &&
            b[0].toInt() and 0xFF == 0x52 && b[1].toInt() and 0xFF == 0x49 &&
            b[2].toInt() and 0xFF == 0x46 && b[3].toInt() and 0xFF == 0x46 &&
            b[8].toInt() and 0xFF == 0x41 && b[9].toInt() and 0xFF == 0x56 &&
            b[10].toInt() and 0xFF == 0x49 && b[11].toInt() and 0xFF == 0x20) return "VIDEO"
        // WAV: 'RIFF....WAVE'
        if (len >= 12 &&
            b[0].toInt() and 0xFF == 0x52 && b[1].toInt() and 0xFF == 0x49 &&
            b[2].toInt() and 0xFF == 0x46 && b[3].toInt() and 0xFF == 0x46 &&
            b[8].toInt() and 0xFF == 0x57 && b[9].toInt() and 0xFF == 0x41 &&
            b[10].toInt() and 0xFF == 0x56 && b[11].toInt() and 0xFF == 0x45) return "AUDIO"
        // MP3: ID3 tag or FF FB / FF F3 / FF F2 sync
        if (len >= 3 &&
            b[0].toInt() and 0xFF == 0x49 && b[1].toInt() and 0xFF == 0x44 && b[2].toInt() and 0xFF == 0x33) return "AUDIO"
        if (len >= 2 && b[0].toInt() and 0xFF == 0xFF &&
            (b[1].toInt() and 0xFF == 0xFB || b[1].toInt() and 0xFF == 0xF3 || b[1].toInt() and 0xFF == 0xF2)) return "AUDIO"
        // OGG (Ogg Vorbis / Opus)
        if (len >= 4 &&
            b[0].toInt() and 0xFF == 0x4F && b[1].toInt() and 0xFF == 0x67 &&
            b[2].toInt() and 0xFF == 0x67 && b[3].toInt() and 0xFF == 0x53) return "AUDIO"
        // FLAC
        if (len >= 4 &&
            b[0].toInt() and 0xFF == 0x66 && b[1].toInt() and 0xFF == 0x4C &&
            b[2].toInt() and 0xFF == 0x61 && b[3].toInt() and 0xFF == 0x43) return "AUDIO"
        // AMR (Android voice recorder default)
        if (len >= 6 &&
            b[0].toInt() and 0xFF == 0x23 && b[1].toInt() and 0xFF == 0x21 &&
            b[2].toInt() and 0xFF == 0x41 && b[3].toInt() and 0xFF == 0x4D &&
            b[4].toInt() and 0xFF == 0x52 && b[5].toInt() and 0xFF == 0x0A) return "AUDIO"
        return "IMAGE"
    }

    /** Sniffs the first 12 bytes of an unencrypted file to determine its media type. */
    fun sniffPlainFileType(file: File): String {
        if (!file.exists()) return "IMAGE"
        return try {
            val buf = ByteArray(12)
            val read = file.inputStream().use { it.read(buf) }
            sniffMediaType(if (read < buf.size) buf.copyOf(read) else buf)
        } catch (_: Exception) { "IMAGE" }
    }

    /** Extension to use in ZIP for a given sniffed media type. */
    fun mediaTypeToExtension(mediaType: String, encFileName: String): String = when (mediaType) {
        "VIDEO" -> {
            // Prefer the filename prefix hint (enc_video_ → mp4)
            when {
                encFileName.startsWith("enc_video_") -> "mp4"
                else -> "mp4"
            }
        }
        "AUDIO" -> {
            when {
                encFileName.startsWith("enc_audio_") -> "m4a"
                else -> "m4a"
            }
        }
        else -> "jpg"
    }

    /**
     * Reads a URI (content:// or file://), copies the data into the encrypted_media
     * directory and encrypts it with SecurityManager. Returns the absolute path of
     * the new encrypted file, or null on failure.
     */
    suspend fun encryptAndCopyUri(context: Context, uriStr: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val uri = uriStr.toUri()
                val dest = File(encDir(context), "enc_${System.currentTimeMillis()}_${uriStr.hashCode()}$ENC_EXT")

                val inputStream = when (uri.scheme) {
                    "content" -> context.contentResolver.openInputStream(uri)
                    "file" -> File(uri.path ?: uriStr).inputStream()
                    else -> {
                        // Plain absolute path (no scheme)
                        val f = File(uriStr)
                        if (f.exists()) f.inputStream() else null
                    }
                } ?: return@withContext null

                inputStream.use { SecurityManager(context).encryptStream(it, dest) }
                dest.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    /**
     * Decrypts an encrypted file to a temporary file in cacheDir/dec_tmp/.
     * The caller must delete the returned temp file when done.
     */
    suspend fun decryptToTemp(context: Context, encPath: String, extension: String? = null): File? =
        withContext(Dispatchers.IO) {
            try {
                val encFile = File(encPath)
                if (!encFile.exists()) return@withContext null
                val ext = extension ?: inferExtFromName()
                SecurityManager(context).decryptToTemp(encFile, ext)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    /** Opens a decrypted InputStream for the given encrypted path. */
    fun openDecryptedStream(context: Context, encPath: String): java.io.InputStream? {
        return try {
            val file = File(encPath)
            if (!file.exists()) null
            else SecurityManager(context).openDecryptedStream(file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Deletes the encrypted file from internal storage.
     * Safe to call if the file does not exist.
     */
    fun deleteEncrypted(encPath: String) {
        try {
            val cleanPath = if (encPath.startsWith("file:/")) {
                encPath.toUri().path ?: encPath
            } else {
                encPath
            }
            val file = File(cleanPath)
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Iterates all JournalEntry records in the DB, encrypts any unencrypted external
     * media (content:// URIs or file paths NOT already in encrypted_media), and updates
     * the DB record with the new encrypted path.
     *
     * Reports progress via [onProgress]: Pair(current, total).
     */
    suspend fun migrateExistingEntries(
        context: Context,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        val dao = AppDatabase.getDatabase(context).journalDao()
        val gson = Gson()
        val entries = dao.getAllEntriesSync()
        val total = entries.size

        entries.forEachIndexed { index, entry ->
            onProgress(index, total)
            var updated = entry

            // audioPath
            if (!entry.audioPath.isNullOrBlank() && !isEncrypted(entry.audioPath)) {
                val newPath = encryptAndCopyUri(context, entry.audioPath)
                if (newPath != null) updated = updated.copy(audioPath = newPath)
            }

            // videoPath
            if (!entry.videoPath.isNullOrBlank() && !isEncrypted(entry.videoPath)) {
                val newPath = encryptAndCopyUri(context, entry.videoPath)
                if (newPath != null) updated = updated.copy(videoPath = newPath)
            }

            // attachments JSON
            if (!entry.attachments.isNullOrBlank()) {
                val newAttachments = migrateAttachmentsJson(context, gson, entry.attachments)
                if (newAttachments != null) updated = updated.copy(attachments = newAttachments)
            }

            if (updated != entry) {
                dao.insertEntry(updated.copy(isEncrypted = true))
            }
        }
        onProgress(total, total)
    }

    private suspend fun migrateAttachmentsJson(
        context: Context,
        gson: Gson,
        raw: String
    ): String? {
        return try {
            val trimmed = raw.trim()
            if (trimmed == "[]") return null // Nothing to migrate

            if (trimmed.startsWith("[{")) {
                // Format A: [{uri, type, name}]
                val listType = object : TypeToken<List<Map<String, Any>>>() {}.type
                val list: List<Map<String, Any>> = gson.fromJson(raw, listType)
                var changed = false
                val newList = list.map { item ->
                    val uriStr = item["uri"] as? String
                    if (!uriStr.isNullOrBlank() && !isEncrypted(uriStr)) {
                        val enc = encryptAndCopyUri(context, uriStr)
                        if (enc != null) {
                            changed = true
                            item.toMutableMap().apply { put("uri", enc) }
                        } else item
                    } else item
                }
                if (changed) gson.toJson(newList) else null
            } else {
                // Format B: ["uri1", "uri2"]
                val listType = object : TypeToken<List<String>>() {}.type
                val uris: List<String> = gson.fromJson(raw, listType)
                var changed = false
                val objectList = uris.map { uriStr ->
                    val isAlreadyEnc = isEncrypted(uriStr)
                    val encPath = if (!isAlreadyEnc) {
                        val enc = encryptAndCopyUri(context, uriStr)
                        if (enc != null) { changed = true; enc } else uriStr
                    } else uriStr

                    val typeStr = if (!isAlreadyEnc) {
                        val mimeType = try { context.contentResolver.getType(uriStr.toUri()) } catch (_: Exception) { null }
                        when {
                            mimeType?.startsWith("video") == true || uriStr.endsWith(".mp4", ignoreCase = true) -> "VIDEO"
                            mimeType?.startsWith("audio") == true -> "FILE"
                            else -> "IMAGE"
                        }
                    } else {
                        "IMAGE"
                    }
                    mapOf("uri" to encPath, "type" to typeStr, "name" to "Attached Media")
                }
                if (changed) gson.toJson(objectList) else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Encrypts all media in the given attachments JSON string and returns updated JSON.
     * Used when saving a new journal entry with Encrypt Media enabled.
     */
    suspend fun encryptAttachmentsJson(context: Context, raw: String): String {
        val gson = Gson()
        return migrateAttachmentsJson(context, gson, raw) ?: raw
    }

    /** Guess the real extension from the encrypted file name pattern enc_<ts>_<hash>.enc */
    private fun inferExtFromName(): String {
        // We don't store original extension in the filename, so return empty for now.
        // Glide/MediaPlayer can handle this via content-type sniffing.
        return ""
    }

    /**
     * Cleans up all decryption temp files in cacheDir/dec_tmp that are older than
     * [maxAgeMs] milliseconds. Call this on app startup.
     */
    fun cleanUpTempFiles(context: Context, maxAgeMs: Long = 24 * 60 * 60 * 1000L) {
        try {
            val tmpDir = File(context.cacheDir, "dec_tmp")
            if (!tmpDir.exists()) return
            val cutoff = System.currentTimeMillis() - maxAgeMs
            tmpDir.listFiles()?.forEach { file ->
                if (file.lastModified() < cutoff) file.delete()
            }
        } catch (_: Exception) {}
    }
}
