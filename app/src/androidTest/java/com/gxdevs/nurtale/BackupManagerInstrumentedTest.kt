package com.gxdevs.lore

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gxdevs.lore.data.AppDatabase
import com.gxdevs.lore.data.journal.JournalEntry
import com.gxdevs.lore.utils.BackupEncryptedException
import com.gxdevs.lore.utils.BackupManager
import com.gxdevs.lore.utils.MediaEncryptionManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Instrumented integration tests for BackupManager.exportData / importData.
 *
 * Run on device / emulator with:
 *   .\gradlew connectedAndroidTest
 *
 * Each test wipes the DB and the backup file before/after running.
 */
@RunWith(AndroidJUnit4::class)
class BackupManagerInstrumentedTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: AppDatabase
    private lateinit var backupFile: File
    private val gson = Gson()

    @Before
    fun setUp() = runBlocking {
        db = AppDatabase.getDatabase(context)
        // Wipe all entries before every test so tests don't bleed into each other
        db.journalDao().deleteAllEntries()
        backupFile = File(context.cacheDir, "test_backup_${System.currentTimeMillis()}.aeth")
    }

    @After
    fun tearDown() {
        if (backupFile.exists()) backupFile.delete()
        // Clean up aeth_media and encrypted_media created during tests
        File(context.filesDir, BackupManager.AETH_MEDIA_DIR).listFiles()
            ?.filter { it.name.startsWith("test_") }
            ?.forEach { it.delete() }
        MediaEncryptionManager.encDir(context).listFiles()
            ?.filter { it.name.contains("test") }
            ?.forEach { it.delete() }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 1 — Plain export, plain import (no media encryption, no PIN)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun test1_plainExportImport_noMedia() = runBlocking {
        // Seed two entries
        val dao = db.journalDao()
        val ts = System.currentTimeMillis()
        dao.insertEntry(JournalEntry(timestamp = ts,     content = "Test entry 1"))
        dao.insertEntry(JournalEntry(timestamp = ts + 1, content = "Test entry 2"))

        // Export without media
        val uri = backupFile.toUri()
        val result = BackupManager.exportData(
            context, uri,
            includeMedia = false,
            encryptBackup = false,
            backupPin = null
        )
        assertTrue("Export should succeed", result.isSuccess)

        // The backup file should exist and be a ZIP
        assertTrue("Backup file must exist", backupFile.exists())
        assertTrue("Backup file must be > 0 bytes", backupFile.length() > 0)

        // Verify ZIP contents: entries.json present, no media/ entries
        val zipEntries = mutableListOf<String>()
        ZipInputStream(backupFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                zipEntries.add(entry.name)
                entry = zis.nextEntry
            }
        }
        assertTrue("entries.json must be in ZIP", "entries.json" in zipEntries)
        assertFalse("No media entries expected", zipEntries.any { it.startsWith("media/") })

        // Clear DB and import
        dao.deleteAllEntries()
        val importResult = BackupManager.importData(
            context, uri,
            mergeMode = false,
            reEncryptMedia = false
        )
        assertTrue("Import should succeed: ${importResult.exceptionOrNull()?.message}", importResult.isSuccess)

        // Verify entries restored
        val restored = dao.getAllEntriesSync()
        assertEquals("Should restore 2 entries", 2, restored.size)
        assertTrue("Entry 1 content restored",
            restored.any { it.content == "Test entry 1" })
        assertTrue("Entry 2 content restored",
            restored.any { it.content == "Test entry 2" })
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 2 — Export with plain image attachment; import with encryption OFF
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun test2_exportWithPlainImageAttachment_importPlain() = runBlocking {
        // Create a fake image file in filesDir (2×2 JPEG magic bytes, valid header)
        val fakeImage = File(context.filesDir, "test_image_${System.currentTimeMillis()}.jpg")
        // Write a minimal JPEG header (SOI marker FF D8 FF E0 ... JFIF)
        val jpegHeader = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
            0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01,
            0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00
        ) + ByteArray(100) // padding
        fakeImage.writeBytes(jpegHeader)

        val attachmentsJson = gson.toJson(listOf(
            mapOf("uri" to fakeImage.absolutePath, "type" to "IMAGE", "name" to "test.jpg")
        ))

        val dao = db.journalDao()
        val ts = System.currentTimeMillis()
        val entryId = dao.insertEntry(JournalEntry(
            timestamp = ts,
            content = "Entry with image",
            attachments = attachmentsJson,
            isEncrypted = false
        ))

        val uri = backupFile.toUri()
        val exportResult = BackupManager.exportData(
            context, uri,
            includeMedia = true,
            encryptBackup = false
        )
        assertTrue("Export should succeed", exportResult.isSuccess)

        // Verify ZIP has manifest.json and at least one media/*.jpg entry
        var hasManifest = false
        var hasImageEntry = false
        ZipInputStream(backupFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "manifest.json") hasManifest = true
                if (entry.name.startsWith("media/") && entry.name.endsWith(".jpg")) hasImageEntry = true
                entry = zis.nextEntry
            }
        }
        assertTrue("manifest.json must be written for v2 export", hasManifest)
        assertTrue("Image must be exported as .jpg", hasImageEntry)

        // Clear DB and import with encryption OFF
        dao.deleteAllEntries()
        val importResult = BackupManager.importData(
            context, uri,
            mergeMode = false,
            reEncryptMedia = false
        )
        assertTrue("Import should succeed", importResult.isSuccess)

        val restored = dao.getAllEntriesSync()
        assertEquals("Should restore 1 entry", 1, restored.size)
        val restoredEntry = restored.first()

        // Attachments should point to aeth_media directory (plain copy)
        val att = restoredEntry.attachments ?: ""
        assertTrue("Attachment URI must point to aeth_media", att.contains(BackupManager.AETH_MEDIA_DIR))
        assertFalse("Entry should NOT be encrypted", restoredEntry.isEncrypted)

        fakeImage.delete()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 3 — Export with plain media; import with re-encryption ON
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun test3_importWithReEncryptionOn() = runBlocking {
        // Write a fake JPEG to filesDir
        val fakeImage = File(context.filesDir, "test_reenc_${System.currentTimeMillis()}.jpg")
        val jpegHeader = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
            0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01,
            0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00
        ) + ByteArray(100)
        fakeImage.writeBytes(jpegHeader)

        val attachmentsJson = gson.toJson(listOf(
            mapOf("uri" to fakeImage.absolutePath, "type" to "IMAGE", "name" to "reenc_test.jpg")
        ))

        val dao = db.journalDao()
        dao.insertEntry(JournalEntry(
            timestamp = System.currentTimeMillis(),
            content = "Re-encryption test",
            attachments = attachmentsJson,
            isEncrypted = false
        ))

        val uri = backupFile.toUri()
        BackupManager.exportData(context, uri, includeMedia = true, encryptBackup = false)

        dao.deleteAllEntries()

        // Import with re-encryption enabled
        val importResult = BackupManager.importData(
            context, uri,
            mergeMode = false,
            reEncryptMedia = true
        )
        assertTrue("Import with re-encryption should succeed", importResult.isSuccess)

        val restored = dao.getAllEntriesSync()
        assertEquals("Should restore 1 entry", 1, restored.size)
        val restoredEntry = restored.first()

        // Entry should now be flagged as encrypted
        assertTrue("Entry must be marked isEncrypted=true", restoredEntry.isEncrypted)

        // Attachment URIs must point to encrypted_media/*.enc
        val att = restoredEntry.attachments ?: ""
        assertTrue("Attachment must reference encrypted_media .enc file",
            att.contains("encrypted_media") && att.contains(".enc"))

        fakeImage.delete()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 4 — AES-CBC outer envelope with PIN — correct PIN imports; wrong PIN fails
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun test4_pinEncryptedBackup_correctPinImports_wrongPinFails() = runBlocking {
        val dao = db.journalDao()
        dao.insertEntry(JournalEntry(
            timestamp = System.currentTimeMillis(),
            content = "Encrypted backup entry"
        ))

        val pin = "1234"
        val uri = backupFile.toUri()
        val exportResult = BackupManager.exportData(
            context, uri,
            includeMedia = false,
            encryptBackup = true,
            backupPin = pin
        )
        assertTrue("PIN-encrypted export should succeed", exportResult.isSuccess)

        // The file should NOT start with PK ZIP magic (it's encrypted)
        val magic = backupFile.readBytes().take(4).map { it.toInt() and 0xFF }
        val isPkMagic = magic == listOf(0x50, 0x4B, 0x03, 0x04)
        assertFalse("Encrypted file must NOT be plain ZIP", isPkMagic)

        dao.deleteAllEntries()

        // Try wrong PIN — must throw BackupEncryptedException (can't decrypt)
        val wrongPinResult = BackupManager.importData(
            context, uri,
            mergeMode = false,
            providedPin = "9999"
        )
        assertTrue("Wrong PIN must cause failure", wrongPinResult.isFailure)
        assertTrue("Wrong PIN failure must be BackupEncryptedException",
            wrongPinResult.exceptionOrNull() is BackupEncryptedException)

        // Correct PIN must succeed
        val correctPinResult = BackupManager.importData(
            context, uri,
            mergeMode = false,
            providedPin = pin
        )
        assertTrue("Correct PIN must import successfully", correctPinResult.isSuccess)

        val restored = dao.getAllEntriesSync()
        assertEquals("Should restore 1 entry", 1, restored.size)
        assertEquals("Restored content must match", "Encrypted backup entry", restored[0].content)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 5 — Deduplication: same media referenced twice should appear only once in ZIP
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun test5_deduplication_sameMediaNotZippedTwice() = runBlocking {
        val fakeAudio = File(context.filesDir, "test_audio_${System.currentTimeMillis()}.m4a")
        fakeAudio.writeBytes(ByteArray(200) { 0x00 }) // dummy audio bytes

        // Set the same file as both audioPath AND in the attachments list
        val attachmentsJson = gson.toJson(listOf(
            mapOf("uri" to fakeAudio.absolutePath, "type" to "FILE", "name" to "recording.m4a")
        ))

        val dao = db.journalDao()
        dao.insertEntry(JournalEntry(
            timestamp = System.currentTimeMillis(),
            content = "Duplicate ref entry",
            audioPath = fakeAudio.absolutePath,
            attachments = attachmentsJson,
            isEncrypted = false
        ))

        val uri = backupFile.toUri()
        val exportResult = BackupManager.exportData(
            context, uri,
            includeMedia = true,
            encryptBackup = false
        )
        assertTrue("Export should succeed", exportResult.isSuccess)

        // Count how many media/ entries are in the ZIP
        var mediaCount = 0
        ZipInputStream(backupFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name.startsWith("media/")) mediaCount++
                entry = zis.nextEntry
            }
        }
        assertEquals("Same media file must be zipped only once", 1, mediaCount)

        fakeAudio.delete()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 6 — Manifest type hints: image/video/audio types are preserved
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun test6_manifestTypeHints_correctForImageVideoAudio() = runBlocking {
        val fakeImage = File(context.filesDir, "test_img_${System.currentTimeMillis()}.jpg")
        fakeImage.writeBytes(byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
            0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01
        ) + ByteArray(100))

        val attachmentsJson = gson.toJson(listOf(
            mapOf("uri" to fakeImage.absolutePath, "type" to "IMAGE", "name" to "test.jpg")
        ))
        val dao = db.journalDao()
        dao.insertEntry(JournalEntry(
            timestamp = System.currentTimeMillis(),
            content = "Manifest test entry",
            attachments = attachmentsJson,
            isEncrypted = false
        ))

        val uri = backupFile.toUri()
        BackupManager.exportData(context, uri, includeMedia = true, encryptBackup = false)

        // Parse manifest.json from the ZIP
        var manifestJson: String? = null
        ZipInputStream(backupFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "manifest.json") {
                    manifestJson = zis.readBytes().toString(Charsets.UTF_8)
                }
                entry = zis.nextEntry
            }
        }

        assertNotNull("manifest.json must be present", manifestJson)

        val mapType = object : TypeToken<Map<String, Any>>() {}.type
        val manifest: Map<String, Any> = gson.fromJson(manifestJson, mapType)
        assertEquals("Manifest schema_version must be 2", 2.0, manifest["schema_version"])

        @Suppress("UNCHECKED_CAST")
        val mediaEntries = manifest["media_entries"] as? List<Map<String, Any>>
        assertNotNull("manifest.media_entries must not be null", mediaEntries)
        assertTrue("At least one media entry expected", mediaEntries!!.isNotEmpty())

        val imageEntry = mediaEntries.firstOrNull { it["type"] == "IMAGE" }
        assertNotNull("IMAGE type hint must be present in manifest", imageEntry)

        fakeImage.delete()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 7 — Backward compatibility: v1 backup (no manifest.json)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun test7_backwardCompatibility_v1BackupNoManifest() = runBlocking {
        // Build a minimal v1 ZIP in-memory (only entries.json, no manifest.json)
        val entry1 = JournalEntry(timestamp = System.currentTimeMillis(), content = "V1 entry")
        val entriesJson = gson.toJson(listOf(entry1))

        val baos = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("entries.json"))
            zos.write(entriesJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        backupFile.writeBytes(baos.toByteArray())

        val dao = db.journalDao()
        dao.deleteAllEntries()

        val importResult = BackupManager.importData(
            context,
            backupFile.toUri(),
            mergeMode = false,
            reEncryptMedia = false
        )
        assertTrue("V1 backup import should succeed: ${importResult.exceptionOrNull()?.message}",
            importResult.isSuccess)

        val restored = dao.getAllEntriesSync()
        assertEquals("Should restore 1 v1 entry", 1, restored.size)
        assertEquals("V1 entry content must match", "V1 entry", restored[0].content)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TEST 8 — Merge mode: existing entries are NOT deleted on import
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun test8_mergeMode_doesNotDeleteExistingEntries() = runBlocking {
        val dao = db.journalDao()
        val existing = JournalEntry(timestamp = 1000L, content = "Pre-existing entry")
        dao.insertEntry(existing)

        // Export one different entry to a backup
        val backupEntry = JournalEntry(timestamp = 2000L, content = "New backup entry")
        dao.insertEntry(backupEntry)
        val allBefore = dao.getAllEntriesSync()
        assertEquals("Should have 2 entries before export", 2, allBefore.size)

        val uri = backupFile.toUri()
        BackupManager.exportData(context, uri, includeMedia = false, encryptBackup = false)

        // Now delete one entry to simulate out-of-sync state
        dao.deleteAllEntries()
        dao.insertEntry(existing.copy(id = 0))

        // Import in merge mode
        val importResult = BackupManager.importData(
            context, uri,
            mergeMode = true,
            reEncryptMedia = false
        )
        assertTrue("Merge import should succeed", importResult.isSuccess)

        val afterMerge = dao.getAllEntriesSync()
        assertTrue("Merge must have at least the pre-existing entry",
            afterMerge.any { it.content == "Pre-existing entry" })
        assertTrue("Merge must have imported the backup entry",
            afterMerge.any { it.content == "New backup entry" })
    }
}

