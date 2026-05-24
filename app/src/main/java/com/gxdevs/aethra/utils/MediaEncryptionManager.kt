package com.gxdevs.aethra.utils

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.gxdevs.aethra.AppDatabase
import com.gxdevs.aethra.JournalEntry
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

                val inputStream = when {
                    uri.scheme == "content" -> context.contentResolver.openInputStream(uri)
                    uri.scheme == "file"    -> File(uri.path ?: uriStr).inputStream()
                    else                   -> {
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
    suspend fun decryptToTemp(context: Context, encPath: String): File? =
        withContext(Dispatchers.IO) {
            try {
                val encFile = File(encPath)
                if (!encFile.exists()) return@withContext null
                SecurityManager(context).decryptToTemp(encFile, inferExtFromName(encPath))
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
            val file = File(encPath)
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
            if (trimmed == "[]" || trimmed == "[]") return null // Nothing to migrate

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
                val newUris = uris.map { uriStr ->
                    if (!isEncrypted(uriStr)) {
                        val enc = encryptAndCopyUri(context, uriStr)
                        if (enc != null) { changed = true; enc } else uriStr
                    } else uriStr
                }
                if (changed) gson.toJson(newUris) else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Encrypts all media in the given [attachments] JSON string and returns updated JSON.
     * Used when saving a new journal entry with Encrypt Media enabled.
     */
    suspend fun encryptAttachmentsJson(context: Context, raw: String): String {
        val gson = Gson()
        return migrateAttachmentsJson(context, gson, raw) ?: raw
    }

    private fun getExtension(context: Context, uri: Uri): String {
        if (uri.scheme == "content") {
            val mimeType = context.contentResolver.getType(uri)
            return when {
                mimeType?.startsWith("image/") == true -> mimeType.substringAfter("/")
                mimeType?.startsWith("video/") == true -> mimeType.substringAfter("/")
                mimeType?.startsWith("audio/") == true -> mimeType.substringAfter("/")
                else -> "bin"
            }
        }
        val path = uri.path ?: ""
        val lastDot = path.lastIndexOf('.')
        return if (lastDot != -1) path.substring(lastDot + 1) else "bin"
    }

    /** Guess the real extension from the encrypted file name pattern enc_<ts>_<hash>.enc */
    private fun inferExtFromName(encPath: String): String {
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
