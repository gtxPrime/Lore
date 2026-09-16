package com.gxdevs.lore.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.gxdevs.lore.data.AppDatabase
import com.gxdevs.lore.data.journal.JournalEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import androidx.core.net.toUri

class BackupEncryptedException : Exception("Backup is encrypted")

// --- Manifest schema --------------------------------------------------------

private data class ManifestEntry(val zip_path: String, val type: String)
private data class BackupManifest(
    val schema_version: Int = 2,
    val media_entries: List<ManifestEntry> = emptyList()
)

// --- Helpers ----------------------------------------------------------------

/**
 * Reads at most [maxBytes] from [stream]. Returns null if more than [maxBytes]
 * are available (indicates an oversized / corrupt entry). Compatible with API 26+.
 */
private fun readBounded(stream: java.io.InputStream, maxBytes: Int): ByteArray? {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(8192)
    var total = 0
    while (true) {
        val read = stream.read(buf)
        if (read == -1) break
        total += read
        if (total > maxBytes) return null
        out.write(buf, 0, read)
    }
    return out.toByteArray()
}

// --- BackupManager ----------------------------------------------------------

object BackupManager {

    private const val DEFAULT_BACKUP_PASSPHRASE = "AethraSanctuaryVaultSecureSeed"

    /** Directory used for plain (non-encrypted) imported media. */
    const val AETH_MEDIA_DIR = "aeth_media"

    // -- Outer-envelope AES-CBC helpers --------------------------------------

    private fun encryptBackupBytes(data: ByteArray, pin: String?): ByteArray {
        val passphrase = if (!pin.isNullOrBlank()) pin else DEFAULT_BACKUP_PASSPHRASE
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(passphrase.toByteArray(Charsets.UTF_8))
        val keySpec = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")

        val random = java.security.SecureRandom()
        val iv = ByteArray(16)
        random.nextBytes(iv)
        val ivSpec = javax.crypto.spec.IvParameterSpec(iv)

        val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        val encrypted = cipher.doFinal(data)

        val result = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, result, 0, iv.size)
        System.arraycopy(encrypted, 0, result, iv.size, encrypted.size)
        return result
    }

    private fun decryptBackupBytes(encryptedData: ByteArray, pin: String?): ByteArray? {
        if (encryptedData.size < 16) return null
        try {
            val passphrase = if (!pin.isNullOrBlank()) pin else DEFAULT_BACKUP_PASSPHRASE
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val keyBytes = digest.digest(passphrase.toByteArray(Charsets.UTF_8))
            val keySpec = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")

            val iv = ByteArray(16)
            System.arraycopy(encryptedData, 0, iv, 0, 16)
            val ivSpec = javax.crypto.spec.IvParameterSpec(iv)

            val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec, ivSpec)

            val plain = cipher.doFinal(encryptedData, 16, encryptedData.size - 16)
            if (plain.size >= 4) {
                val magic = (plain[0].toInt() and 0xFF shl 24) or
                            (plain[1].toInt() and 0xFF shl 16) or
                            (plain[2].toInt() and 0xFF shl 8) or
                            (plain[3].toInt() and 0xFF)
                if (magic == 0x504B0304) {
                    return plain
                }
            }
        } catch (_: Exception) {}
        return null
    }

    // -- Export ---------------------------------------------------------------

    /**
     * Exports all journal entries (and optionally media) to [outputUri].
     * Media inside the ZIP is always written as plain bytes (decrypted on-the-fly
     * for .enc files). The outer ZIP can optionally be AES-CBC encrypted with [backupPin].
     *
     * Returns the number of media items that could not be found on device.
     */
    suspend fun exportData(
        context: Context,
        outputUri: Uri,
        includeMedia: Boolean,
        encryptBackup: Boolean = false,
        backupPin: String? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        val tempZipFile = File(context.cacheDir, "backup_temp_${System.currentTimeMillis()}.zip")
        tempZipFile.parentFile?.mkdirs()
        try {
            val db = AppDatabase.getDatabase(context)
            val entries = db.journalDao().getAllEntriesSync()
            val gson = Gson()

            var missingCount = 0
            val manifestEntries = mutableListOf<ManifestEntry>()

            FileOutputStream(tempZipFile).use { fos ->
                ZipOutputStream(fos).use { zos ->
                    val exportedEntries = mutableListOf<JournalEntry>()
                    // Tracks sourceUri -> zipPath so the same file is never zipped twice
                    val alreadyExported = mutableMapOf<String, String>()

                    for (entry in entries) {
                        var updatedEntry = entry.copy()

                        if (includeMedia) {
                            // audioPath
                            if (!entry.audioPath.isNullOrBlank() && entry.audioPath != "null") {
                                val result = copyMediaToZip(context, entry.audioPath, zos, manifestEntries, alreadyExported)
                                if (result != null) updatedEntry = updatedEntry.copy(audioPath = result)
                                else missingCount++
                            }
                            // videoPath
                            if (!entry.videoPath.isNullOrBlank() && entry.videoPath != "null") {
                                val result = copyMediaToZip(context, entry.videoPath, zos, manifestEntries, alreadyExported)
                                if (result != null) updatedEntry = updatedEntry.copy(videoPath = result)
                                else missingCount++
                            }
                            // attachments JSON
                            if (!entry.attachments.isNullOrBlank()) {
                                try {
                                    val raw = entry.attachments
                                    if (raw.trimStart().startsWith("[{")) {
                                        // Format A: [{uri, type, name}]
                                        val listType = object : TypeToken<List<Map<String, Any>>>() {}.type
                                        val list: List<Map<String, Any>> = gson.fromJson(raw, listType)
                                        val newList = list.map { item ->
                                            val uriStr = item["uri"] as? String
                                            if (!uriStr.isNullOrBlank()) {
                                                val newPath = copyMediaToZip(context, uriStr, zos, manifestEntries, alreadyExported)
                                                if (newPath != null) item.toMutableMap().apply { put("uri", newPath) }
                                                else { missingCount++; item }
                                            } else item
                                        }
                                        updatedEntry = updatedEntry.copy(attachments = gson.toJson(newList))
                                    } else {
                                        // Format B: ["uri1", "uri2"]
                                        val listType = object : TypeToken<List<String>>() {}.type
                                        val uris: List<String> = gson.fromJson(raw, listType)
                                        val newUris = uris.map { uriStr ->
                                            val res = copyMediaToZip(context, uriStr, zos, manifestEntries, alreadyExported)
                                            if (res == null) missingCount++
                                            res ?: uriStr
                                        }
                                        updatedEntry = updatedEntry.copy(attachments = gson.toJson(newUris))
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                        // Strip the isEncrypted flag � inside the ZIP everything is plain
                        exportedEntries.add(updatedEntry.copy(isEncrypted = false))
                    }

                    // Write entries.json
                    val entriesJson = gson.toJson(exportedEntries)
                    zos.putNextEntry(ZipEntry("entries.json"))
                    zos.write(entriesJson.toByteArray())
                    zos.closeEntry()

                    // Write manifest.json (schema v2)
                    val manifest = BackupManifest(schema_version = 2, media_entries = manifestEntries)
                    val manifestJson = gson.toJson(manifest)
                    zos.putNextEntry(ZipEntry("manifest.json"))
                    zos.write(manifestJson.toByteArray())
                    zos.closeEntry()
                }
            }

            val finalBytes = if (encryptBackup) {
                encryptBackupBytes(tempZipFile.readBytes(), backupPin)
            } else {
                tempZipFile.readBytes()
            }

            context.contentResolver.openOutputStream(outputUri)?.use { os ->
                os.write(finalBytes)
            }

            Result.success(missingCount)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        } finally {
            if (tempZipFile.exists()) tempZipFile.delete()
        }
    }

    /**
     * Opens the source URI / path (decrypting .enc files on-the-fly), writes plain bytes
     * into [zos] under a "media/..." entry, appends to [manifestEntries], and returns
     * the ZIP entry name (or null on failure).
     *
     * [alreadyExported] tracks source URIs already written to the ZIP so the same
     * file is never written twice (e.g. when audioPath and attachments both reference
     * the same recording).
     */
    private fun copyMediaToZip(
        context: Context,
        sourceUriStr: String,
        zos: ZipOutputStream,
        manifestEntries: MutableList<ManifestEntry>,
        alreadyExported: MutableMap<String, String> = mutableMapOf()
    ): String? {
        // Deduplicate: if this source was already exported, return the existing zip path
        alreadyExported[sourceUriStr]?.let { return it }

        try {
            val sourceUri = sourceUriStr.toUri()
            val isEncFile = MediaEncryptionManager.isEncrypted(sourceUriStr)
            val encFileName = if (isEncFile) File(sourceUri.path ?: sourceUriStr).name else ""

            // Build the decrypted input stream (non-null after early return)
            val rawStream: InputStream? = when {
                isEncFile -> {
                    val encFile = File(sourceUri.path ?: sourceUriStr)
                    if (encFile.exists()) SecurityManager(context).openDecryptedStream(encFile) else null
                }
                sourceUri.scheme == "content" -> context.contentResolver.openInputStream(sourceUri)
                else -> {
                    val f = File(sourceUri.path ?: sourceUriStr)
                    if (f.exists()) f.inputStream() else null
                }
            }
            if (rawStream == null) return null
            val inputStream: InputStream = rawStream

            // Sniff the first 12 bytes to determine media type + extension
            val headerBuf = ByteArray(12)
            val headerRead = inputStream.read(headerBuf)
            val effectiveHeader = if (headerRead > 0) headerBuf.copyOf(headerRead) else headerBuf

            val mediaType: String
            val ext: String

            if (isEncFile) {
                // Sniff the decrypted bytes � more reliable than enc filename prefix
                mediaType = MediaEncryptionManager.sniffMediaType(effectiveHeader)
                ext = MediaEncryptionManager.mediaTypeToExtension(mediaType, encFileName)
            } else if (sourceUri.scheme == "content") {
                val mime = context.contentResolver.getType(sourceUri) ?: ""
                mediaType = when {
                    mime.startsWith("video") -> "VIDEO"
                    mime.startsWith("audio") -> "AUDIO"
                    else -> "IMAGE"
                }
                ext = getExtensionFromMime(mime, sourceUri)
            } else {
                val file = File(sourceUri.path ?: sourceUriStr)
                mediaType = MediaEncryptionManager.sniffPlainFileType(file)
                ext = file.extension.ifBlank { mediaType.toLowercaseExt() }
            }

            val filename = "media/media_${System.currentTimeMillis()}_${Math.abs(sourceUriStr.hashCode())}.$ext"

            try {
                zos.putNextEntry(ZipEntry(filename))
                // Write the already-read header bytes first, then the rest
                if (headerRead > 0) zos.write(effectiveHeader, 0, headerRead)
                inputStream.copyTo(zos)
                zos.closeEntry()
            } catch (_: java.util.zip.ZipException) {
                // Entry already exists � skip silently
            } finally {
                inputStream.close()
            }

            manifestEntries.add(ManifestEntry(zip_path = filename, type = mediaType))
            // Record so subsequent references to the same source reuse this zip entry
            alreadyExported[sourceUriStr] = filename
            return filename
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun getExtensionFromMime(mime: String, uri: Uri): String {
        return when {
            mime.startsWith("image/") -> mime.substringAfter("/").let { if (it.length <= 5) it else "jpg" }
            mime.startsWith("video/") -> mime.substringAfter("/").let { if (it.length <= 5) it else "mp4" }
            mime.startsWith("audio/") -> when {
                mime.contains("mpeg") || mime.contains("mp3") -> "mp3"
                mime.contains("aac") || mime.contains("m4a") -> "m4a"
                mime.contains("wav") -> "wav"
                mime.contains("ogg") -> "ogg"
                mime.contains("amr") -> "amr"
                else -> "m4a"
            }
            else -> {
                val path = uri.path ?: ""
                val dot = path.lastIndexOf('.')
                if (dot != -1) path.substring(dot + 1) else "bin"
            }
        }
    }

    private fun String.toLowercaseExt(): String = when (this) {
        "VIDEO" -> "mp4"
        "AUDIO" -> "m4a"
        else    -> "jpg"
    }

    // -- Import ---------------------------------------------------------------

    /**
     * Imports a backup from [inputUri].
     *
     * - If [reEncryptMedia] is true  ? extracted media is re-encrypted into
     *   `filesDir/encrypted_media/` and `isEncrypted = true` is set on each entry.
     * - If [reEncryptMedia] is false ? extracted media is copied as plain files
     *   into `filesDir/aeth_media/`  and `isEncrypted = false` is set.
     *
     * All heavy I/O runs on [Dispatchers.IO]; no work happens on the main thread.
     */
    suspend fun importData(
        context: Context,
        inputUri: Uri,
        mergeMode: Boolean,
        reEncryptMedia: Boolean = false,
        providedPin: String? = null,
        currentAppPin: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val dao = db.journalDao()
            val gson = Gson()
            val cr = context.contentResolver

            val rawBytes = cr.openInputStream(inputUri)?.use { it.readBytes() }
                ?: return@withContext Result.failure(Exception("Failed to read backup file"))

            // Detect whether the outer envelope is encrypted (doesn't start with PK magic)
            var isEncrypted = true
            if (rawBytes.size >= 4) {
                val magic = (rawBytes[0].toInt() and 0xFF shl 24) or
                            (rawBytes[1].toInt() and 0xFF shl 16) or
                            (rawBytes[2].toInt() and 0xFF shl 8) or
                            (rawBytes[3].toInt() and 0xFF)
                if (magic == 0x504B0304) isEncrypted = false
            }

            var decryptedBytes: ByteArray? = null
            if (isEncrypted) {
                if (providedPin != null)
                    decryptedBytes = decryptBackupBytes(rawBytes, providedPin)
                if (decryptedBytes == null && currentAppPin != null)
                    decryptedBytes = decryptBackupBytes(rawBytes, currentAppPin)
                if (decryptedBytes == null)
                    decryptedBytes = decryptBackupBytes(rawBytes, null)
                if (decryptedBytes == null)
                    return@withContext Result.failure(BackupEncryptedException())
            }

            val finalBytes = decryptedBytes ?: rawBytes

            // Destination directory for plain (non-encrypted) imported media
            val aethMediaDir = File(context.filesDir, AETH_MEDIA_DIR).also { it.mkdirs() }

            var entriesJson: String? = null
            var manifest: BackupManifest? = null
            // zip_path ? extracted plain file path
            val extractedMedia = mutableMapOf<String, String>()

            java.io.ByteArrayInputStream(finalBytes).use { bais ->
                ZipInputStream(bais).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        when {
                            entry.name == "entries.json" -> {
                                // Cap at 50 MB to prevent OOM on malformed backup
                                val maxBytes = 50 * 1024 * 1024
                                val buf = readBounded(zis, maxBytes)
                                if (buf == null) {
                                    return@withContext Result.failure(Exception("entries.json exceeds 50 MB � corrupt backup?"))
                                }
                                entriesJson = buf.toString(Charsets.UTF_8)
                            }
                            entry.name == "manifest.json" -> {
                                val maxBytes = 1 * 1024 * 1024
                                val buf = readBounded(zis, maxBytes)
                                if (buf != null) {
                                    try {
                                        manifest = gson.fromJson(buf.toString(Charsets.UTF_8), BackupManifest::class.java)
                                    } catch (_: Exception) {}
                                }
                            }
                            entry.name.startsWith("media/") -> {
                                val fileName = entry.name.substringAfter("media/")
                                val outFile = File(aethMediaDir, fileName)
                                FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                                extractedMedia[entry.name] = outFile.absolutePath
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            if (entriesJson == null) {
                return@withContext Result.failure(Exception("entries.json not found in backup"))
            }

            // Build a type-hint lookup from manifest: zip_path ? "VIDEO"|"AUDIO"|"IMAGE"
            val typeHints: Map<String, String> = manifest?.media_entries
                ?.associateBy({ it.zip_path }, { it.type }) ?: emptyMap()

            val listType = object : TypeToken<List<JournalEntry>>() {}.type
            val importedEntries: List<JournalEntry> = gson.fromJson(entriesJson, listType)

            Log.i("BackupManager", "[Import] Parsed ${importedEntries.size} journal entries from backup (mergeMode=$mergeMode)")

            if (!mergeMode) {
                Log.w("BackupManager", "[Import] Overwrite mode active -> Wiping existing journals and pet_progress tables...")
                dao.deleteAllEntries()
                db.petProgressDao().deleteAll()
            }

            for (journalEntry in importedEntries) {

                var newEntry = journalEntry

                // -- Remap zip_path references to local file paths ----------

                // audioPath
                if (!newEntry.audioPath.isNullOrBlank() && extractedMedia.containsKey(newEntry.audioPath)) {
                    newEntry = newEntry.copy(audioPath = extractedMedia[newEntry.audioPath])
                }
                // videoPath
                if (!newEntry.videoPath.isNullOrBlank() && extractedMedia.containsKey(newEntry.videoPath)) {
                    newEntry = newEntry.copy(videoPath = extractedMedia[newEntry.videoPath])
                }
                // attachments
                if (!newEntry.attachments.isNullOrBlank()) {
                    newEntry = remapAttachments(newEntry, extractedMedia, typeHints, gson)
                }

                // -- Re-encrypt or leave plain -----------------------------

                if (reEncryptMedia) {
                    val plainAudioPath = newEntry.audioPath
                    val plainVideoPath = newEntry.videoPath
                    newEntry = reEncryptEntry(context, newEntry)
                    // Delete plain aeth_media copies that were superseded by .enc files
                    if (newEntry.audioPath != plainAudioPath && plainAudioPath != null &&
                        plainAudioPath.contains(AETH_MEDIA_DIR)) {
                        try { File(plainAudioPath).delete() } catch (_: Exception) {}
                    }
                    if (newEntry.videoPath != plainVideoPath && plainVideoPath != null &&
                        plainVideoPath.contains(AETH_MEDIA_DIR)) {
                        try { File(plainVideoPath).delete() } catch (_: Exception) {}
                    }
                } else {
                    // Plain � files already in aeth_media/, just clear encryption flag
                    newEntry = newEntry.copy(isEncrypted = false)
                }

                // -- Persist ----------------------------------------------

                if (mergeMode) {
                    val existing = dao.getEntryById(newEntry.id)
                    if (existing == null) dao.insertEntry(newEntry)
                } else {
                    dao.insertEntry(newEntry)
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Re-maps attachment URIs from zip_path keys to local absolute paths,
     * and restores the "type" field from manifest hints for Format A attachments.
     */
    private fun remapAttachments(
        entry: JournalEntry,
        extractedMedia: Map<String, String>,
        typeHints: Map<String, String>,
        gson: Gson
    ): JournalEntry {
        return try {
            val raw = entry.attachments ?: return entry
            if (raw.trimStart().startsWith("[{")) {
                // Format A: [{uri, type, name}]
                val itemType = object : TypeToken<List<Map<String, Any>>>() {}.type
                val list: List<Map<String, Any>> = gson.fromJson(raw, itemType)
                val newList = list.map { item ->
                    val uriStr = item["uri"] as? String
                    if (!uriStr.isNullOrBlank() && extractedMedia.containsKey(uriStr)) {
                        val localPath = extractedMedia[uriStr]!!
                        val hint = typeHints[uriStr]
                        item.toMutableMap().apply {
                            put("uri", localPath)
                            if (hint != null) put("type", hint)
                        }
                    } else item
                }
                entry.copy(attachments = gson.toJson(newList))
            } else {
                // Format B: ["uri1", "uri2"]
                val itemType = object : TypeToken<List<String>>() {}.type
                val uris: List<String> = gson.fromJson(raw, itemType)
                val newUris = uris.map { uriStr ->
                    if (extractedMedia.containsKey(uriStr)) extractedMedia[uriStr]!! else uriStr
                }
                entry.copy(attachments = gson.toJson(newUris))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            entry
        }
    }

    /**
     * Re-encrypts all media in [entry] using the device Keystore key.
     * Moves files from aeth_media/ into encrypted_media/ and updates paths.
     * Returns a new entry with [isEncrypted] = true.
     */
    private suspend fun reEncryptEntry(context: Context, entry: JournalEntry): JournalEntry {
        var updated = entry

        // audioPath
        if (!updated.audioPath.isNullOrBlank() && !MediaEncryptionManager.isEncrypted(updated.audioPath)) {
            val enc = MediaEncryptionManager.encryptAndCopyUri(context, updated.audioPath)
            if (enc != null) {
                // Delete the plain aeth_media copy
                try { File(updated.audioPath).delete() } catch (_: Exception) {}
                updated = updated.copy(audioPath = enc)
            }
        }

        // videoPath
        if (!updated.videoPath.isNullOrBlank() && !MediaEncryptionManager.isEncrypted(updated.videoPath)) {
            val enc = MediaEncryptionManager.encryptAndCopyUri(context, updated.videoPath)
            if (enc != null) {
                try { File(updated.videoPath).delete() } catch (_: Exception) {}
                updated = updated.copy(videoPath = enc)
            }
        }

        // attachments
        if (!updated.attachments.isNullOrBlank()) {
            val encJson = MediaEncryptionManager.encryptAttachmentsJson(context, updated.attachments)
            // Delete original plain files from aeth_media after encryption
            deleteAethMediaFromJson(updated.attachments, encJson)
            updated = updated.copy(attachments = encJson)
        }

        return updated.copy(isEncrypted = true)
    }

    /**
     * After encrypting attachments, deletes any plain files that were in aeth_media
     * but whose paths no longer appear in [newJson] (they've been replaced by .enc paths).
     */
    private fun deleteAethMediaFromJson(oldJson: String, newJson: String) {
        try {
            val gson = Gson()
            val oldPaths = extractUrisFromJson(oldJson, gson)
            val newPaths = extractUrisFromJson(newJson, gson).toSet()
            for (path in oldPaths) {
                if (path !in newPaths && path.contains(AETH_MEDIA_DIR)) {
                    try { File(path).delete() } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }

    private fun extractUrisFromJson(json: String, gson: Gson): List<String> {
        if (json.isBlank()) return emptyList()
        return try {
            if (json.trimStart().startsWith("[{")) {
                val t = object : TypeToken<List<Map<String, Any>>>() {}.type
                val list: List<Map<String, Any>> = gson.fromJson(json, t)
                list.mapNotNull { it["uri"] as? String }
            } else {
                val t = object : TypeToken<List<String>>() {}.type
                gson.fromJson(json, t)
            }
        } catch (_: Exception) { emptyList() }
    }
}
