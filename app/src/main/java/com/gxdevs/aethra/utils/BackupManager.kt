package com.gxdevs.aethra.utils

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.gxdevs.aethra.data.AppDatabase
import com.gxdevs.aethra.data.journal.JournalEntry
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

object BackupManager {

    private const val DEFAULT_BACKUP_PASSPHRASE = "AethraSanctuaryVaultSecureSeed"

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
            
            FileOutputStream(tempZipFile).use { fos ->
                ZipOutputStream(fos).use { zos ->
                    val exportedEntries = mutableListOf<JournalEntry>()
                    
                    for (entry in entries) {
                        var updatedEntry = entry.copy()
                        
                        if (includeMedia) {
                            // Process audioPath
                            if (!entry.audioPath.isNullOrBlank() && entry.audioPath != "null") {
                                val newPath = copyMediaToZip(context, entry.audioPath, zos)
                                if (newPath != null) updatedEntry = updatedEntry.copy(audioPath = newPath)
                                else missingCount++
                            }
                            // Process videoPath
                            if (!entry.videoPath.isNullOrBlank() && entry.videoPath != "null") {
                                val newPath = copyMediaToZip(context, entry.videoPath, zos)
                                if (newPath != null) updatedEntry = updatedEntry.copy(videoPath = newPath)
                                else missingCount++
                            }
                            // Process attachments
                            if (!entry.attachments.isNullOrBlank()) {
                                try {
                                    val raw = entry.attachments
                                    if (raw.trimStart().startsWith("[{")) {
                                        val listType = object : TypeToken<List<Map<String, Any>>>() {}.type
                                        val list: List<Map<String, Any>> = gson.fromJson(raw, listType)
                                        val newList = list.map { item ->
                                            val uriStr = item["uri"] as? String
                                            if (!uriStr.isNullOrBlank()) {
                                                val newPath = copyMediaToZip(context, uriStr, zos)
                                                if (newPath != null) item.toMutableMap().apply { put("uri", newPath) } 
                                                else {
                                                    missingCount++
                                                    item
                                                }
                                            } else item
                                        }
                                        updatedEntry = updatedEntry.copy(attachments = gson.toJson(newList))
                                    } else {
                                        val listType = object : TypeToken<List<String>>() {}.type
                                        val uris: List<String> = gson.fromJson(raw, listType)
                                        val newUris = uris.map { uriStr ->
                                            val res = copyMediaToZip(context, uriStr, zos)
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
                        exportedEntries.add(updatedEntry)
                    }
                    
                    // Write entries.json
                    val entriesJson = gson.toJson(exportedEntries)
                    zos.putNextEntry(ZipEntry("entries.json"))
                    zos.write(entriesJson.toByteArray())
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

    private fun copyMediaToZip(context: Context, sourceUriStr: String, zos: ZipOutputStream): String? {
        try {
            val sourceUri = sourceUriStr.toUri()
            val isEncFile = MediaEncryptionManager.isEncrypted(sourceUriStr)

            val inputStream: InputStream? = when {
                isEncFile -> {
                    // Decrypt on-the-fly so the backup contains plain media bytes
                    val encFile = File(sourceUri.path ?: sourceUriStr)
                    if (encFile.exists()) SecurityManager(context).openDecryptedStream(encFile)
                    else null
                }
                sourceUri.scheme == "content" -> context.contentResolver.openInputStream(sourceUri)
                else -> {
                    val f = File(sourceUri.path ?: sourceUriStr)
                    if (f.exists()) f.inputStream() else null
                }
            }

            if (inputStream != null) {
                val ext = inferExportExtension(context, sourceUriStr, sourceUri, isEncFile)
                val filename = "media/media_${System.currentTimeMillis()}_${sourceUriStr.hashCode()}.$ext"

                try {
                    zos.putNextEntry(ZipEntry(filename))
                    inputStream.copyTo(zos)
                    zos.closeEntry()
                    return filename
                } catch (_: java.util.zip.ZipException) {
                    return filename
                } finally {
                    inputStream.close()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Infers the real file extension for a media entry going into the backup ZIP.
     * For encrypted files, derives from the filename prefix (enc_audio_ -> m4a) or
     * falls back to MIME type / path extension for plain URIs.
     */
    private fun inferExportExtension(context: Context, sourceUriStr: String, sourceUri: Uri, isEncFile: Boolean): String {
        if (isEncFile) {
            val fileName = File(sourceUri.path ?: sourceUriStr).name
            return when {
                fileName.startsWith("enc_audio_") -> "m4a"
                fileName.startsWith("enc_video_") -> "mp4"
                // Generic enc_<ts>_<hash>.enc -> try to infer from MIME type if available
                else -> "jpg" // images are the most common attachment type; safe default
            }
        }
        return getExtension(context, sourceUri)
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

            var isEncrypted = true
            if (rawBytes.size >= 4) {
                val magic = (rawBytes[0].toInt() and 0xFF shl 24) or
                            (rawBytes[1].toInt() and 0xFF shl 16) or
                            (rawBytes[2].toInt() and 0xFF shl 8) or
                            (rawBytes[3].toInt() and 0xFF)
                if (magic == 0x504B0304) {
                    isEncrypted = false
                }
            }

            var decryptedBytes: ByteArray? = null
            if (isEncrypted) {
                // 1. Try provided PIN/key
                if (providedPin != null) {
                    decryptedBytes = decryptBackupBytes(rawBytes, providedPin)
                }

                // 2. Try current app PIN
                if (decryptedBytes == null && currentAppPin != null) {
                    decryptedBytes = decryptBackupBytes(rawBytes, currentAppPin)
                }

                // 3. Try default fallback
                if (decryptedBytes == null) {
                    decryptedBytes = decryptBackupBytes(rawBytes, null)
                }

                // If still null, decryption failed - need PIN
                if (decryptedBytes == null) {
                    return@withContext Result.failure(BackupEncryptedException())
                }
            }

            val finalBytes = decryptedBytes ?: rawBytes
            val mediaDir = File(context.filesDir, "lore_media")
            if (!mediaDir.exists()) mediaDir.mkdirs()

            var entriesJson: String? = null
            val extractedMedia = mutableMapOf<String, String>()

            java.io.ByteArrayInputStream(finalBytes).use { bais ->
                ZipInputStream(bais).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        if (entry.name == "entries.json") {
                            entriesJson = zis.readBytes().toString(Charsets.UTF_8)
                        } else if (entry.name.startsWith("media/")) {
                            val outFile = File(mediaDir, entry.name.substringAfter("media/"))
                            FileOutputStream(outFile).use { fos ->
                                zis.copyTo(fos)
                            }
                            extractedMedia[entry.name] = outFile.absolutePath
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            if (entriesJson != null) {
                val listType = object : TypeToken<List<JournalEntry>>() {}.type
                val importedEntries: List<JournalEntry> = gson.fromJson(entriesJson, listType)
                
                if (!mergeMode) {
                    dao.deleteAllEntries()
                }

                for (journalEntry in importedEntries) {
                    var newEntry = journalEntry

                    // Update media paths to point to extracted local files
                    if (!newEntry.audioPath.isNullOrBlank() && extractedMedia.containsKey(newEntry.audioPath)) {
                        newEntry = newEntry.copy(audioPath = extractedMedia[newEntry.audioPath])
                    }
                    if (!newEntry.videoPath.isNullOrBlank() && extractedMedia.containsKey(newEntry.videoPath)) {
                        newEntry = newEntry.copy(videoPath = extractedMedia[newEntry.videoPath])
                    }
                    if (!newEntry.attachments.isNullOrBlank()) {
                        try {
                            val raw = newEntry.attachments
                            if (raw.trimStart().startsWith("[{")) {
                                val itemType = object : TypeToken<List<Map<String, Any>>>() {}.type
                                val list: List<Map<String, Any>> = gson.fromJson(raw, itemType)
                                val newList = list.map { item ->
                                    val uriStr = item["uri"] as? String
                                    if (!uriStr.isNullOrBlank() && extractedMedia.containsKey(uriStr)) {
                                        item.toMutableMap().apply { put("uri", extractedMedia[uriStr]!!) }
                                    } else item
                                }
                                newEntry = newEntry.copy(attachments = gson.toJson(newList))
                            } else {
                                val itemType = object : TypeToken<List<String>>() {}.type
                                val uris: List<String> = gson.fromJson(raw, itemType)
                                val newUris = uris.map { uriStr ->
                                    if (extractedMedia.containsKey(uriStr)) extractedMedia[uriStr]!! else uriStr
                                }
                                newEntry = newEntry.copy(attachments = gson.toJson(newUris))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    // If any media was extracted from the ZIP, it's now plain - reset flag
                    val hadMedia = !newEntry.audioPath.isNullOrBlank() ||
                                   !newEntry.videoPath.isNullOrBlank() ||
                                   !newEntry.attachments.isNullOrBlank()
                    if (hadMedia && extractedMedia.isNotEmpty()) {
                        newEntry = newEntry.copy(isEncrypted = false)
                    }

                    // Re-encrypt media if the setting is on
                    if (reEncryptMedia) {
                        if (!newEntry.audioPath.isNullOrBlank() && !MediaEncryptionManager.isEncrypted(newEntry.audioPath)) {
                            val enc = MediaEncryptionManager.encryptAndCopyUri(context,
                                newEntry.audioPath
                            )
                            if (enc != null) newEntry = newEntry.copy(audioPath = enc, isEncrypted = true)
                        }
                        if (!newEntry.videoPath.isNullOrBlank() && !MediaEncryptionManager.isEncrypted(newEntry.videoPath)) {
                            val enc = MediaEncryptionManager.encryptAndCopyUri(context,
                                newEntry.videoPath
                            )
                            if (enc != null) newEntry = newEntry.copy(videoPath = enc, isEncrypted = true)
                        }
                        if (!newEntry.attachments.isNullOrBlank()) {
                            val encJson = MediaEncryptionManager.encryptAttachmentsJson(context,
                                newEntry.attachments
                            )
                            newEntry = newEntry.copy(attachments = encJson, isEncrypted = true)
                        }
                    }

                    if (mergeMode) {
                        val existing = dao.getEntryById(newEntry.id)
                        if (existing == null) {
                            dao.insertEntry(newEntry)
                        }
                    } else {
                        dao.insertEntry(newEntry)
                    }
                }
            } else {
                return@withContext Result.failure(Exception("entries.json not found in backup"))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}

