package com.gxdevs.lore.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.gxdevs.lore.data.journal.JournalEntry
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Journal Export Engine for Athera / Nurtale.
 * Supports Markdown (.md) and HTML/Printable Export.
 */
object JournalExportManager {

    private val dateFormatter = SimpleDateFormat("MMMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
    private val fileNameFormatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    /**
     * Exports a list of journal entries into a clean Markdown (.md) file and launches the share sheet.
     */
    fun exportToMarkdown(context: Context, entries: List<JournalEntry>): File? {
        if (entries.isEmpty()) return null

        val sb = StringBuilder()
        sb.append("# Nurtale Journal Export\n\n")
        sb.append("_Exported on ${dateFormatter.format(Date())}_\n\n")
        sb.append("---\n\n")

        val sorted = entries.sortedByDescending { it.timestamp }

        for (entry in sorted) {
            val dateStr = dateFormatter.format(Date(entry.timestamp))
            val lines = entry.content?.split("\n") ?: emptyList()
            val title = lines.firstOrNull()?.takeIf { it.isNotBlank() } ?: "Untitled Entry"
            val body = lines.drop(1).joinToString("\n").trim()

            sb.append("## $title\n")
            sb.append("**Date:** $dateStr  \n")
            if (!entry.tags.isNullOrBlank()) {
                sb.append("**Tags:** ${entry.tags}  \n")
            }
            if (entry.timeSpentWriting != null && entry.timeSpentWriting > 0) {
                val mins = entry.timeSpentWriting / 60
                val secs = entry.timeSpentWriting % 60
                sb.append("**Writing Time:** ${mins}m ${secs}s  \n")
            }
            sb.append("\n")

            if (body.isNotBlank()) {
                sb.append(body)
                sb.append("\n\n")
            }

            sb.append("---\n\n")
        }

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val fileName = "nurtale_export_${fileNameFormatter.format(Date())}.md"
        val file = File(exportDir, fileName)

        file.writeText(sb.toString())
        return file
    }

    /**
     * Shares an exported file via Android System Share Sheet.
     */
    fun shareExportedFile(context: Context, file: File, mimeType: String = "text/markdown") {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, "Export Journal")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
