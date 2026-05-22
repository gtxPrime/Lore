package com.gxdevs.athera.utils

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.gxdevs.athera.AppDatabase
import com.gxdevs.athera.JournalEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import androidx.core.net.toUri

object BackupManager {

    suspend fun exportData(
        context: Context,
        outputUri: Uri,
        includeMedia: Boolean
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val entries = db.journalDao().getAllEntriesSync()
            val gson = Gson()
            
            val cr = context.contentResolver
            
            var missingCount = 0
            cr.openOutputStream(outputUri)?.use { os ->
                ZipOutputStream(os).use { zos ->
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
            Result.success(missingCount)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun copyMediaToZip(context: Context, sourceUriStr: String, zos: ZipOutputStream): String? {
        try {
            val sourceUri = sourceUriStr.toUri()
            val inputStream: InputStream? = if (sourceUri.scheme == "content") {
                context.contentResolver.openInputStream(sourceUri)
            } else {
                File(sourceUri.path ?: sourceUriStr).inputStream()
            }
            
            if (inputStream != null) {
                // Generate a unique filename based on hash code or timestamp to avoid collisions
                val ext = getExtension(context, sourceUri)
                val filename = "media/media_${System.currentTimeMillis()}_${sourceUriStr.hashCode()}.$ext"
                
                try {
                    zos.putNextEntry(ZipEntry(filename))
                    inputStream.copyTo(zos)
                    zos.closeEntry()
                    return filename
                } catch (_: java.util.zip.ZipException) {
                    // Entry already exists, that's fine
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
        mergeMode: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(context)
            val dao = db.journalDao()
            val gson = Gson()
            val cr = context.contentResolver
            
            val mediaDir = File(context.filesDir, "lore_media")
            if (!mediaDir.exists()) mediaDir.mkdirs()

            var entriesJson: String? = null
            val extractedMedia = mutableMapOf<String, String>()

            cr.openInputStream(inputUri)?.use { stream ->
                ZipInputStream(stream).use { zis ->
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
                    
                    // If merge, check if entry exists. The simplest way to handle merge is to insert it.
                    // But if we want to avoid replacing newer data if same ID exists, we can try to fetch it.
                    // The user said: "merge data will fill empty slots only overwrite will replace all old data"
                    // If they mean keeping existing IDs intact and only inserting missing ones:
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

