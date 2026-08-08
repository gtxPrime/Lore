package com.gxdevs.lore.utils

import android.content.Context
import androidx.core.net.toUri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.gxdevs.lore.data.AppDatabase
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
        if (encPath.contains("enc_video_")) return true
        if (encPath.contains("enc_audio_") || encPath.contains("enc_image_")) return false

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
                val isVideoBrand = if (isMp4 && read >= 12) {
                    val brand = String(header, 8, 4, Charsets.ISO_8859_1)
                    brand != "M4A " && brand != "M4B " && brand != "f4a " && brand != "f4b " && brand != "MSNV"
                } else isMp4
                return isVideoBrand || isMkv
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Sniffs the first 12 bytes of [header] and returns:
     *  "VIDEO" for MP4/MKV/WebM/AVI,  "AUDIO" for WAV/MP3/M4A/OGG/FLAC, "IMAGE" otherwise.
     * The [header] must be at least 12 bytes (fewer bytes ? best-effort).
     */
    fun sniffMediaType(header: ByteArray): String {
        val b = header
        val len = b.size
        // MP4 / M4V / M4A / MOV: 'ftyp' at bytes 4-7
        if (len >= 8 &&
            b[4].toInt() and 0xFF == 0x66 && b[5].toInt() and 0xFF == 0x74 &&
            b[6].toInt() and 0xFF == 0x79 && b[7].toInt() and 0xFF == 0x70) {
            // Inspect the 4-byte major brand at bytes 8-11 to distinguish audio from video.
            // Audio brands: M4A , M4B , f4a , f4b , MSNV (voice recorders).
            // isom, mp41, mp42, avc1, M4V , f4v , qt  , etc. are VIDEO.
            if (len >= 12) {
                val brand = String(b, 8, 4, Charsets.ISO_8859_1)
                val isAudioBrand = brand == "M4A " || brand == "M4B " ||
                    brand == "f4a " || brand == "f4b " ||
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

    /** Extension to use in ZIP or cache for a given encrypted filename prefix or sniffed media type. */
    fun mediaTypeToExtension(mediaType: String, encFileName: String): String = when {
        encFileName.contains("enc_video_") -> "mp4"
        encFileName.contains("enc_audio_") -> "m4a"
        encFileName.contains("enc_image_") -> "jpg"
        mediaType == "VIDEO" -> "mp4"
        mediaType == "AUDIO" -> "m4a"
        else -> "jpg"
    }

    /**
     * Reads a URI (content:// or file://), copies the data into the encrypted_media
     * directory and encrypts it with SecurityManager. Returns the absolute path of
     * the new encrypted file, or null on failure.
     */
    suspend fun encryptAndCopyUri(
        context: Context,
        uriStr: String,
        typeHint: String? = null,
        onProgress: ((Int) -> Unit)? = null
    ): String? =
        withContext(Dispatchers.IO) {
            try {
                val uri = uriStr.toUri()
                
                // Detect media type: VIDEO, AUDIO, or IMAGE
                val mediaType = typeHint ?: run {
                    val lower = uriStr.lowercase()
                    val mimeType = try { context.contentResolver.getType(uri) } catch (_: Exception) { null }
                    when {
                        mimeType?.startsWith("video") == true || lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".webm") -> "VIDEO"
                        mimeType?.startsWith("audio") == true || lower.endsWith(".m4a") || lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".aac") -> "AUDIO"
                        else -> "IMAGE"
                    }
                }

                val prefix = when (mediaType) {
                    "VIDEO" -> "enc_video_"
                    "AUDIO" -> "enc_audio_"
                    else -> "enc_image_"
                }

                val dest = File(encDir(context), "${prefix}${System.currentTimeMillis()}_${Math.abs(uriStr.hashCode())}$ENC_EXT")

                var totalSize = -1L
                if (uri.scheme == "content") {
                    try {
                        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                            val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                            if (sizeIndex != -1 && cursor.moveToFirst()) {
                                if (!cursor.isNull(sizeIndex)) {
                                    totalSize = cursor.getLong(sizeIndex)
                                }
                            }
                        }
                    } catch (_: Exception) {}
                    if (totalSize <= 0) {
                        try {
                            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                                totalSize = it.length
                            }
                        } catch (_: Exception) {}
                    }
                } else if (uri.scheme == "file" || uri.scheme == null) {
                    val f = File(uri.path ?: uriStr)
                    if (f.exists()) {
                        totalSize = f.length()
                    }
                }

                val inputStream = when (uri.scheme) {
                    "content" -> context.contentResolver.openInputStream(uri)
                    "file" -> File(uri.path ?: uriStr).inputStream()
                    else -> {
                        val f = File(uriStr)
                        if (f.exists()) f.inputStream() else null
                    }
                } ?: return@withContext null

                val isLargeFile = totalSize > 2L * 1024L * 1024L
                val fileName = try { uri.lastPathSegment ?: "media" } catch (_: Exception) { "media" }

                onProgress?.invoke(1)
                if (isLargeFile) {
                    showMediaProgressNotification(context, "Encrypting Media", "Processing $fileName... 1%", 1)
                }

                try {
                    inputStream.use { input ->
                        SecurityManager(context).encryptStream(input, dest, totalSize) { pct ->
                            onProgress?.invoke(pct)
                            if (isLargeFile) {
                                showMediaProgressNotification(context, "Encrypting Media", "Processing $fileName... $pct%", pct)
                            }
                        }
                    }
                    // Generate lightweight encrypted companion thumbnail for instant 2ms loading
                    if (mediaType == "VIDEO" || mediaType == "IMAGE") {
                        createAndEncryptThumbnail(context, uri, dest, mediaType == "VIDEO")
                    }
                } finally {
                    if (isLargeFile) {
                        cancelMediaProgressNotification(context)
                    }
                }

                dest.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    /** Generates a small JPEG thumbnail (~15 KB) and encrypts it as a companion file thumb_<destName>. */
    private fun createAndEncryptThumbnail(context: Context, uri: android.net.Uri, destEncFile: File, isVideo: Boolean) {
        try {
            val thumbFile = File(destEncFile.parentFile, "thumb_" + destEncFile.name)
            val bitmap: android.graphics.Bitmap? = if (isVideo) {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    if (uri.scheme == "content" || uri.scheme == "file") {
                        retriever.setDataSource(context, uri)
                    } else {
                        retriever.setDataSource(uri.path ?: uri.toString())
                    }
                    retriever.getFrameAtTime(500000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.frameAtTime
                } catch (_: Exception) { null }
                finally {
                    try { retriever.release() } catch (_: Exception) {}
                }
            } else {
                try {
                    val stream = when (uri.scheme) {
                        "content" -> context.contentResolver.openInputStream(uri)
                        "file" -> File(uri.path ?: uri.toString()).inputStream()
                        else -> File(uri.toString()).inputStream()
                    }
                    stream?.use { input ->
                        val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
                        android.graphics.BitmapFactory.decodeStream(input, null, options)
                    }
                } catch (_: Exception) { null }
            }

            if (bitmap != null) {
                val scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, 300, 300, true)
                val baos = java.io.ByteArrayOutputStream()
                scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, baos)
                val bytes = baos.toByteArray()
                java.io.ByteArrayInputStream(bytes).use { input ->
                    SecurityManager(context).encryptStream(input, thumbFile)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Decrypts the companion thumbnail file (if available) in ~2ms for instant grid display.
     * Falls back to getOrDecryptTempFile on the full file for legacy items without a companion thumbnail.
     */
    suspend fun getOrDecryptThumbnail(
        context: Context,
        encPath: String,
        onProgress: ((Int) -> Unit)? = null
    ): File? = withContext(Dispatchers.IO) {
        try {
            val mainFile = File(encPath)
            if (!mainFile.exists()) return@withContext null
            val thumbEncFile = File(mainFile.parentFile, "thumb_" + mainFile.name)
            if (thumbEncFile.exists()) {
                val thumbDec = getOrDecryptTempFile(context, thumbEncFile.absolutePath, "jpg")
                if (thumbDec != null) {
                    onProgress?.invoke(100)
                    return@withContext thumbDec
                }
            }
            // Fallback for legacy files
            getOrDecryptTempFile(context, encPath, null, onProgress)
        } catch (_: Exception) {
            getOrDecryptTempFile(context, encPath, null, onProgress)
        }
    }

    /**
     * Decrypts an encrypted file to a persistent cached temp file in cacheDir/dec_cache/.
     * If the cached temp file already exists and is valid, returns it immediately without re-decrypting.
     */
    suspend fun getOrDecryptTempFile(
        context: Context,
        encPath: String,
        extension: String? = null,
        onProgress: ((Int) -> Unit)? = null
    ): File? = withContext(Dispatchers.IO) {
        try {
            val encFile = File(encPath)
            if (!encFile.exists()) return@withContext null

            val ext = extension ?: inferExtFromName(encFile.name)
            val extSuffix = if (ext.isNotBlank()) ".$ext" else ""
            val cacheDir = File(context.cacheDir, "dec_cache").also { it.mkdirs() }

            // Hash the encPath to create a deterministic cached temp file name
            val hash = java.security.MessageDigest.getInstance("MD5")
                .digest(encPath.toByteArray())
                .joinToString("") { "%02x".format(it) }

            val cacheFile = File(cacheDir, "dec_${hash}$extSuffix")

            // Re-use cached decrypted file if it exists and is non-empty
            if (cacheFile.exists() && cacheFile.length() > 0) {
                onProgress?.invoke(100)
                return@withContext cacheFile
            }

            val isLargeFile = encFile.length() > 2L * 1024L * 1024L
            onProgress?.invoke(1)
            if (isLargeFile) {
                showMediaProgressNotification(context, "Decrypting Media", "Decrypting ${encFile.name}... 1%", 1)
            }

            try {
                SecurityManager(context).decryptFile(encFile, cacheFile) { pct ->
                    onProgress?.invoke(pct)
                    if (isLargeFile) {
                        showMediaProgressNotification(context, "Decrypting Media", "Decrypting ${encFile.name}... $pct%", pct)
                    }
                }
            } finally {
                if (isLargeFile) {
                    cancelMediaProgressNotification(context)
                }
            }

            if (cacheFile.exists() && cacheFile.length() > 0) cacheFile else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** Guess the real extension from the encrypted file name pattern enc_video_ / enc_audio_ / enc_image_ */
    private fun inferExtFromName(encFileName: String): String {
        return when {
            encFileName.contains("enc_video_") -> "mp4"
            encFileName.contains("enc_audio_") -> "m4a"
            encFileName.contains("enc_image_") -> "jpg"
            else -> ""
        }
    }

    /** Legacy wrapper for decryptToTemp that delegates to [getOrDecryptTempFile]. */
    suspend fun decryptToTemp(
        context: Context,
        encPath: String,
        extension: String? = null
    ): File? = getOrDecryptTempFile(context, encPath, extension)

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
                val newPath = encryptAndCopyUri(context, entry.audioPath, "AUDIO")
                if (newPath != null) updated = updated.copy(audioPath = newPath)
            }

            // videoPath
            if (!entry.videoPath.isNullOrBlank() && !isEncrypted(entry.videoPath)) {
                val newPath = encryptAndCopyUri(context, entry.videoPath, "VIDEO")
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
     * Cleans up decryption temp and cache files in cacheDir/dec_tmp and cacheDir/dec_cache
     * that are older than [maxAgeMs] milliseconds. Call this on app startup.
     */
    fun cleanUpTempFiles(context: Context, maxAgeMs: Long = 24 * 60 * 60 * 1000L) {
        try {
            val cutoff = System.currentTimeMillis() - maxAgeMs
            listOf(File(context.cacheDir, "dec_tmp"), File(context.cacheDir, "dec_cache")).forEach { dir ->
                if (dir.exists()) {
                    dir.listFiles()?.forEach { file ->
                        if (file.lastModified() < cutoff) file.delete()
                    }
                }
            }
        } catch (_: Exception) {}
    }
}
