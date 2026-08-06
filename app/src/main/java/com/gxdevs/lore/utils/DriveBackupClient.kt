package com.gxdevs.lore.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Thin HTTP client for Drive REST API using the app-private [appDataFolder].
 * Users never see the backup file in their regular Google Drive UI.
 *
 * All operations require a valid Drive access token from [DriveTokenHelper].
 */
object DriveBackupClient {

    private const val TAG = "DriveBackupClient"
    private const val BACKUP_FILE_NAME = "lore_backup.lore"
    private const val DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files"
    private const val DRIVE_UPLOAD_URL =
        "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&spaces=appDataFolder"

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Uploads [fileBytes] to Drive as [BACKUP_FILE_NAME] in appDataFolder.
     * If a file with that name already exists, it is updated (PATCH) rather
     * than creating a duplicate.
     *
     * Returns [Result.success] with the Drive file ID on success.
     */
    suspend fun uploadBackup(token: String, fileBytes: ByteArray): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val existingId = findExistingBackupId(token)
                return@withContext if (existingId != null) {
                    patchBackup(token, existingId, fileBytes)
                } else {
                    createBackup(token, fileBytes)
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "uploadBackup failed: ${e.message}", e)
                Result.failure(e)
            }
        }

    /**
     * Downloads the existing [BACKUP_FILE_NAME] bytes from Drive appDataFolder.
     * Returns [Result.failure] if no backup file exists in Drive.
     */
    suspend fun downloadBackup(token: String): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            try {
                val fileId = findExistingBackupId(token)
                    ?: return@withContext Result.failure(Exception("No backup file found in Google Drive. Please tap 'SYNC NOW' first to create a backup."))

                val url = URL("$DRIVE_FILES_URL/$fileId?alt=media")
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.connectTimeout = 30_000
                conn.readTimeout = 60_000

                val responseCode = conn.responseCode
                if (responseCode == 200) {
                    val bytes = conn.inputStream.readBytes()
                    android.util.Log.d(TAG, "? Drive backup downloaded: ${bytes.size} bytes")
                    Result.success(bytes)
                } else {
                    val error = conn.errorStream?.bufferedReader()?.readText() ?: "unknown"
                    android.util.Log.e(TAG, "? Drive download failed $responseCode: $error")
                    Result.failure(Exception("Drive download failed: HTTP $responseCode"))
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "downloadBackup failed: ${e.message}", e)
                Result.failure(e)
            }
        }

    /**
     * Returns the ISO 8601 modifiedTime of the existing backup file, or null
     * if no backup exists yet.
     */
    suspend fun getLastModifiedTime(token: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val query = URLEncoder.encode("name = '$BACKUP_FILE_NAME' and trashed = false", "UTF-8")
                val url = URL("$DRIVE_FILES_URL?spaces=appDataFolder&fields=files(modifiedTime)&q=$query")
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.connectTimeout = 15_000
                conn.readTimeout = 15_000

                if (conn.responseCode == 200) {
                    val json = JSONObject(conn.inputStream.bufferedReader().readText())
                    val files = json.getJSONArray("files")
                    if (files.length() > 0) {
                        files.getJSONObject(0).optString("modifiedTime")
                    } else null
                } else {
                    android.util.Log.w(TAG, "getLastModifiedTime HTTP ${conn.responseCode}")
                    null
                }
            } catch (e: Exception) {
                android.util.Log.w(TAG, "getLastModifiedTime failed: ${e.message}")
                null
            }
        }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Returns the Drive file ID of the existing backup (.lore), or null. */
    private fun findExistingBackupId(token: String): String? {
        return try {
            val query = URLEncoder.encode("name = '$BACKUP_FILE_NAME' and trashed = false", "UTF-8")
            val url = URL("$DRIVE_FILES_URL?spaces=appDataFolder&fields=files(id,name)&q=$query")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                val files = json.getJSONArray("files")
                if (files.length() > 0) {
                    val id = files.getJSONObject(0).getString("id")
                    val name = files.getJSONObject(0).optString("name")
                    android.util.Log.d(TAG, "Found existing backup file: name=$name, id=$id")
                    id
                } else {
                    android.util.Log.d(TAG, "No existing backup file found in Drive appDataFolder.")
                    null
                }
            } else {
                val error = conn.errorStream?.bufferedReader()?.readText() ?: ""
                android.util.Log.e(TAG, "findExistingBackupId failed HTTP $responseCode: $error")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "findExistingBackupId exception: ${e.message}", e)
            null
        }
    }

    /** Creates a new file in appDataFolder with multipart upload. */
    private fun createBackup(token: String, fileBytes: ByteArray): Result<String> {
        val boundary = "LoreBackupBoundary_${System.currentTimeMillis()}"
        val metadata = """{"name":"$BACKUP_FILE_NAME","parents":["appDataFolder"]}"""

        val url = URL(DRIVE_UPLOAD_URL)
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            conn.doOutput = true
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000

            conn.outputStream.use { out ->
                val writer = OutputStreamWriter(out, Charsets.UTF_8)
                writer.write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n")
                writer.write(metadata)
                writer.write("\r\n--$boundary\r\nContent-Type: application/octet-stream\r\n\r\n")
                writer.flush()
                out.write(fileBytes)
                writer.write("\r\n--$boundary--\r\n")
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode in 200..201) {
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                val fileId = json.getString("id")
                android.util.Log.d(TAG, "? Drive backup created: id=$fileId")
                Result.success(fileId)
            } else {
                val error = conn.errorStream?.bufferedReader()?.readText() ?: "unknown"
                android.util.Log.e(TAG, "? Drive create failed $responseCode: $error")
                Result.failure(Exception("Drive upload failed: HTTP $responseCode ($error)"))
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Updates an existing file using PATCH with media upload. */
    private fun patchBackup(token: String, fileId: String, fileBytes: ByteArray): Result<String> {
        val url = URL("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media")
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "PATCH"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/octet-stream")
            conn.setRequestProperty("Content-Length", fileBytes.size.toString())
            conn.doOutput = true
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000

            conn.outputStream.use { it.write(fileBytes) }

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                android.util.Log.d(TAG, "? Drive backup updated: id=$fileId, size=${fileBytes.size}")
                Result.success(fileId)
            } else {
                val error = conn.errorStream?.bufferedReader()?.readText() ?: "unknown"
                android.util.Log.e(TAG, "? Drive patch failed $responseCode: $error")
                Result.failure(Exception("Drive patch failed: HTTP $responseCode ($error)"))
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Formats an ISO 8601 date string to a user-friendly display string. */
    fun formatModifiedTime(isoTime: String?): String? {
        if (isoTime.isNullOrBlank()) return null
        return try {
            val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            iso.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val date: Date = iso.parse(isoTime) ?: return null
            SimpleDateFormat("MMM dd, yyyy | h:mm a", Locale.getDefault()).format(date)
        } catch (_: Exception) { isoTime }
    }
}
