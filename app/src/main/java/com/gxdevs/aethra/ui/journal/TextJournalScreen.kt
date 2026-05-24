package com.gxdevs.aethra.ui.journal

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import com.gxdevs.aethra.LocalSharedTransitionScope
import com.gxdevs.aethra.LocalNavAnimatedVisibilityScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.FormatStrikethrough
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.gxdevs.aethra.ui.theme.MyApplicationTheme
import com.gxdevs.aethra.data.SettingsRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay as kDelay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.AccessTime

private val mainContainerBackground = Color(0xFFF4F1EA)
private val cardBackground          = Color(0xFFEAE7DF)
private val borderColor             = Color(0xFFE0DCD1)
private val textPrimary             = Color(0xFF2E332A)
private val textSecondary           = Color(0xFF828779)
private val textTertiary            = Color(0xFF828779)
private val primaryAccent           = Color(0xFF606F49)
private val accentBackground        = Color(0xFFD9DFCD)
private val darkAccent              = Color(0xFF4A5638)
private val bottomNavBackground     = Color(0xFF2E332A)
private val promptColor             = Color(0xFF606F49)

val promptsList = listOf(
    "What is a heavy thought you need to let go of today?",
    "What was the most beautiful thing you saw today?",
    "Describe a moment you felt completely at peace.",
    "What is something you are looking forward to?",
    "Write about a challenge you overcame recently."
)

enum class FormatType { BOLD, ITALIC, UNDERLINE, STRIKETHROUGH }
enum class ListType   { OFF, NUMBER, BULLET }
enum class AudioState { IDLE, RECORDING, PAUSED, RECORDED }

data class FormatRange(val type: FormatType, var start: Int, var end: Int)

/** Serializable snapshot of a FormatRange â€” used to persist rich-text formatting. */
private data class SavedRange(val type: String, val start: Int, val end: Int)

private fun List<FormatRange>.toFormatJson(): String =
    com.google.gson.Gson().toJson(map { SavedRange(it.type.name, it.start, it.end) })

private fun String.toFormatRanges(): List<FormatRange> = try {
    val listType = object : com.google.gson.reflect.TypeToken<List<SavedRange>>() {}.type
    val saved: List<SavedRange> = com.google.gson.Gson().fromJson(this, listType)
    saved.mapNotNull { r ->
        val type = when (r.type) {
            "BOLD"          -> FormatType.BOLD
            "ITALIC"        -> FormatType.ITALIC
            "UNDERLINE"     -> FormatType.UNDERLINE
            "STRIKETHROUGH" -> FormatType.STRIKETHROUGH
            else            -> return@mapNotNull null
        }
        FormatRange(type, r.start, r.end)
    }
} catch (_: Exception) { emptyList() }

class RichTextState {
    var textFieldValue by mutableStateOf(TextFieldValue(""))
    val formatRanges = mutableStateListOf<FormatRange>()

    var activeBold          by mutableStateOf(false)
    var activeItalic        by mutableStateOf(false)
    var activeUnderline     by mutableStateOf(false)
    var activeStrikethrough by mutableStateOf(false)

    /** Returns true if the caller should turn off list mode (user pressed Enter on empty list line). */
    fun onValueChange(newValue: TextFieldValue, listType: ListType): Boolean {
        val oldText      = textFieldValue.text
        val newText      = newValue.text
        val oldSelection = textFieldValue.selection

        if (oldText != newText) {
            val lengthDiff = newText.length - oldText.length

            // â”€â”€ Auto-list prefix when user presses Enter â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            if (listType != ListType.OFF && lengthDiff == 1 &&
                newValue.selection.collapsed &&
                newValue.selection.start > 0 &&
                newText[newValue.selection.start - 1] == '\n'
            ) {
                val cursorPos     = newValue.selection.start
                val prevLineStart = newText.lastIndexOf('\n', cursorPos - 2).let { if (it < 0) 0 else it + 1 }
                val prevLine      = newText.substring(prevLineStart, cursorPos - 1)

                val isBarePrefix = prevLine == "â€¢ " || prevLine.matches(Regex("^\\d+\\. $"))
                if (isBarePrefix) {
                    val cleaned    = newText.substring(0, prevLineStart) + newText.substring(cursorPos - 1)
                    textFieldValue = TextFieldValue(cleaned, TextRange(prevLineStart))
                    return true
                }

                val prefix = when (listType) {
                    ListType.BULLET -> "â€¢ "
                    ListType.NUMBER -> {
                        val linesBefore = newText.substring(0, cursorPos).lines()
                        val num = linesBefore.count { it.matches(Regex("^\\d+\\. .*")) } + 1
                        "$num. "
                    }
                    ListType.OFF -> ""
                }
                if (prefix.isNotEmpty()) {
                    val inserted  = newText.substring(0, cursorPos) + prefix + newText.substring(cursorPos)
                    val newCursor = cursorPos + prefix.length
                    adjustRangesForEdit(oldText, newText, oldSelection)
                    shiftRangesFrom(cursorPos, prefix.length)
                    textFieldValue = TextFieldValue(inserted, TextRange(newCursor))
                    return false  // â† do NOT fall through; value already set
                }
            }

            adjustRangesForEdit(oldText, newText, oldSelection)
            if (lengthDiff > 0) {
                val s = oldSelection.min; val e = s + lengthDiff
                if (activeBold)          formatRanges.add(FormatRange(FormatType.BOLD, s, e))
                if (activeItalic)        formatRanges.add(FormatRange(FormatType.ITALIC, s, e))
                if (activeUnderline)     formatRanges.add(FormatRange(FormatType.UNDERLINE, s, e))
                if (activeStrikethrough) formatRanges.add(FormatRange(FormatType.STRIKETHROUGH, s, e))
            }
            textFieldValue = newValue   // only set once, here, for normal edits
        } else {
            // Text unchanged â€” cursor moved / selection changed; sync active-format state
            val pos = newValue.selection.min
            var b = false; var i = false; var u = false; var s = false
            for (r in formatRanges) {
                if (pos > r.start && pos <= r.end) when (r.type) {
                    FormatType.BOLD          -> b = true
                    FormatType.ITALIC        -> i = true
                    FormatType.UNDERLINE     -> u = true
                    FormatType.STRIKETHROUGH -> s = true
                }
            }
            activeBold = b; activeItalic = i; activeUnderline = u; activeStrikethrough = s
            textFieldValue = newValue
        }
        return false
    }

    fun applyListToSelection(listType: ListType) {
        val sel  = textFieldValue.selection
        if (sel.collapsed) return
        val text         = textFieldValue.text
        val selectedText = text.substring(sel.min, sel.max)
        val lines        = selectedText.split("\n")
        val newLines     = lines.mapIndexed { idx, line ->
            val stripped = line.removePrefix("â€¢ ").replace(Regex("^\\d+\\. "), "")
            when (listType) {
                ListType.OFF    -> stripped
                ListType.BULLET -> "â€¢ $stripped"
                ListType.NUMBER -> "${idx + 1}. $stripped"
            }
        }
        val newSelected  = newLines.joinToString("\n")
        val newText      = text.substring(0, sel.min) + newSelected + text.substring(sel.max)
        textFieldValue   = TextFieldValue(newText, TextRange(sel.min, sel.min + newSelected.length))
    }

    private fun shiftRangesFrom(pos: Int, delta: Int) {
        for (r in formatRanges) {
            if (r.start >= pos)      { r.start += delta; r.end += delta }
            else if (r.end > pos)    { r.end += delta }
        }
    }

    private fun adjustRangesForEdit(oldText: String, newText: String, oldSel: TextRange) {
        val diff       = newText.length - oldText.length
        val isInsert   = diff > 0 && oldSel.collapsed  // pure insertion (no selection replaced)
        val iter = formatRanges.iterator()
        while (iter.hasNext()) {
            val r = iter.next()
            when {
                // Range is entirely after the edit â†’ shift both ends
                r.start >= oldSel.max -> { r.start += diff; r.end += diff }
                // Cursor exactly AT the start of a range and inserting â†’ shift range right
                // (new char typed just before range stays outside the range)
                isInsert && r.start == oldSel.min -> { r.start += diff; r.end += diff }
                // Cursor exactly AT the end of a range and inserting â†’ don't extend the range
                // (new char typed just after range is controlled by active-flag, not range expansion)
                isInsert && r.end == oldSel.max -> { /* leave r.end as-is */ }
                // Range strictly contains the edit â†’ extend/shrink proportionally
                r.start < oldSel.min && r.end > oldSel.max -> r.end = (r.end + diff).coerceAtLeast(r.start)
                // Range fully inside replaced selection â†’ collapse
                r.start >= oldSel.min && r.end <= oldSel.max -> r.end = r.start
                // Partial overlap (tail of range clips into edit region)
                r.end > oldSel.min && r.end <= oldSel.max -> r.end = oldSel.min
                // Partial overlap (head of range clips into edit region)
                r.start >= oldSel.min && r.start < oldSel.max -> r.start = (oldSel.max + diff).coerceAtLeast(r.start)
            }
            if (r.start >= r.end) iter.remove()
        }
    }

    /** Prefix the line the cursor is currently on, exactly like Notion/Google Docs. */
    fun applyListToCurrentLine(listType: ListType) {
        val text      = textFieldValue.text
        val cursor    = textFieldValue.selection.min
        val lineStart = text.lastIndexOf('\n', cursor - 1).let { if (it < 0) 0 else it + 1 }
        val lineEnd   = text.indexOf('\n', cursor).let { if (it < 0) text.length else it }
        val line      = text.substring(lineStart, lineEnd)
        val stripped  = line.removePrefix("\u2022 ").replace(Regex("^\\d+\\. "), "")
        val newLine   = when (listType) {
            ListType.OFF    -> stripped
            ListType.BULLET -> "\u2022 $stripped"
            ListType.NUMBER -> {
                val num = text.substring(0, lineStart).lines().count { it.matches(Regex("^\\d+\\. .*")) } + 1
                "$num. $stripped"
            }
        }
        val newText      = text.substring(0, lineStart) + newLine + text.substring(lineEnd)
        val newCursor    = (cursor + newLine.length - line.length).coerceAtLeast(lineStart)
        textFieldValue   = TextFieldValue(newText, TextRange(newCursor))
    }


    fun toggleFormat(type: FormatType) {
        val sel = textFieldValue.selection
        if (sel.collapsed) {
            when (type) {
                FormatType.BOLD -> activeBold = !activeBold
                FormatType.ITALIC -> activeItalic = !activeItalic
                FormatType.UNDERLINE -> activeUnderline = !activeUnderline
                FormatType.STRIKETHROUGH -> activeStrikethrough = !activeStrikethrough
            }
        } else {
            val covered = formatRanges.any { it.type == type && it.start <= sel.min && it.end >= sel.max }
            if (covered) {
                val newRanges = mutableListOf<FormatRange>()
                for (r in formatRanges) {
                    if (r.type == type) {
                        if (r.start < sel.min && r.end > sel.max) {
                            newRanges.add(FormatRange(type, r.start, sel.min))
                            newRanges.add(FormatRange(type, sel.max, r.end))
                        } else if (r.start >= sel.min && r.end <= sel.max) {
                            // fully covered
                        } else if (r.start < sel.min && r.end > sel.min) {
                            r.end = sel.min
                            newRanges.add(r)
                        } else if (r.start < sel.max && r.end > sel.max) {
                            r.start = sel.max
                            newRanges.add(r)
                        } else {
                            newRanges.add(r)
                        }
                    } else {
                        newRanges.add(r)
                    }
                }
                formatRanges.clear()
                formatRanges.addAll(newRanges)
            } else {
                formatRanges.add(FormatRange(type, sel.min, sel.max))
            }
            
            when (type) {
                FormatType.BOLD -> activeBold = !covered
                FormatType.ITALIC -> activeItalic = !covered
                FormatType.UNDERLINE -> activeUnderline = !covered
                FormatType.STRIKETHROUGH -> activeStrikethrough = !covered
            }
        }
        textFieldValue = textFieldValue.copy()
    }

    fun buildAnnotatedString(): AnnotatedString = buildAnnotatedString {
        append(textFieldValue.text)
        for (r in formatRanges) {
            if (r.start >= 0 && r.end <= textFieldValue.text.length && r.start < r.end) {
                addStyle(when (r.type) {
                    FormatType.BOLD          -> SpanStyle(fontWeight = FontWeight.Bold)
                    FormatType.ITALIC        -> SpanStyle(fontStyle = FontStyle.Italic)
                    FormatType.UNDERLINE     -> SpanStyle(textDecoration = TextDecoration.Underline)
                    FormatType.STRIKETHROUGH -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                }, r.start, r.end)
            }
        }
    }
}

val RichTextStateSaver = Saver<RichTextState, Map<String, Any>>(
    save = { state ->
        mapOf(
            "text" to state.textFieldValue.text,
            "sel_start" to state.textFieldValue.selection.start,
            "sel_end" to state.textFieldValue.selection.end,
            "ranges" to state.formatRanges.toFormatJson()
        )
    },
    restore = { savedMap ->
        val state = RichTextState()
        val text = savedMap["text"] as? String ?: ""
        val start = savedMap["sel_start"] as? Int ?: 0
        val end = savedMap["sel_end"] as? Int ?: 0
        state.textFieldValue = TextFieldValue(text, TextRange(start, end))
        val rangesJson = savedMap["ranges"] as? String ?: ""
        state.formatRanges.clear()
        state.formatRanges.addAll(rangesJson.toFormatRanges())
        state
    }
)

val FileStateSaver = Saver<MutableState<File?>, String>(
    save = { state -> state.value?.absolutePath ?: "" },
    restore = { restored -> mutableStateOf(if (restored.isEmpty()) null else File(restored)) }
)

val SelectedMediaStateSaver = Saver<MutableState<List<Uri>>, List<String>>(
    save = { state -> state.value.map { it.toString() } },
    restore = { restored -> mutableStateOf(restored.map { Uri.parse(it) }) }
)

private fun isUriValidAndExists(context: android.content.Context, uri: Uri): Boolean {
    return try {
        if (uri.scheme == "file") {
            val path = uri.path
            if (path != null) {
                File(path).exists()
            } else {
                false
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { true } ?: false
        }
    } catch (e: Exception) {
        false
    }
}

class RichTextVisualTransformation(val state: RichTextState) : VisualTransformation {
    override fun filter(text: AnnotatedString) = TransformedText(state.buildAnnotatedString(), OffsetMapping.Identity)
}

@Composable
fun FormatButton(active: Boolean, icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) accentBackground else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) textPrimary else Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun AudioRecordingPill(
    audioState: AudioState,
    durationSec: Int,
    onPlay:   () -> Unit,
    onPause:  () -> Unit,
    onResume: () -> Unit,
    onStop:   () -> Unit,
    onRemove: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "wavePhase"
    )
    val staticHeights = remember { listOf(0.3f,0.7f,0.5f,0.9f,0.4f,0.8f,0.35f,0.6f,0.5f,0.85f,0.4f,0.7f) }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(bottomNavBackground)
            .border(1.dp, primaryAccent.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Status dot â€“ pulsing accent when recording, solid when paused
        val dotColor by animateColorAsState(
            if (audioState == AudioState.RECORDING) primaryAccent else accentBackground,
            label = "dot"
        )
        Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))

        // Timer
        val mm = durationSec / 60; val ss = durationSec % 60
        Text("%02d:%02d".format(mm, ss), color = accentBackground, fontSize = 12.sp, fontWeight = FontWeight.Bold)

        // Waveform (12 bars)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.width(52.dp)
        ) {
            repeat(12) { idx ->
                val frac = if (audioState == AudioState.RECORDING) {
                    val v = kotlin.math.sin((wavePhase + idx * 0.55f).toDouble()).toFloat()
                    0.25f + 0.75f * ((v + 1f) / 2f)
                } else staticHeights[idx]
                val barColor = if (audioState == AudioState.RECORDING)
                    primaryAccent.copy(alpha = 0.85f) else accentBackground.copy(alpha = 0.6f)
                Box(
                    Modifier.width(2.dp)
                        .height((22.dp * frac).coerceAtLeast(4.dp))
                        .clip(CircleShape)
                        .background(barColor)
                )
            }
        }

        // Controls
        when (audioState) {
            AudioState.RECORDING -> {
                IconButton(onClick = onPause, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Rounded.Pause,
                        null,
                        tint = accentBackground,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(onClick = onStop, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.Stop, null, tint = primaryAccent, modifier = Modifier.size(16.dp))
                }
            }
            AudioState.PAUSED -> {
                IconButton(onClick = onResume, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.Mic, null, tint = primaryAccent, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onStop, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Rounded.Stop, null, tint = primaryAccent, modifier = Modifier.size(16.dp))
                }
            }
            else -> {
                IconButton(onClick = onPlay, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        null,
                        tint = primaryAccent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Rounded.Close, null, tint = textSecondary, modifier = Modifier.size(14.dp))
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextJournalScreen(
    onBack: () -> Unit,
    onSave: (String, String, Int, String?, List<Uri>, Long, String, Boolean) -> Unit,
    editId: Long? = null,
    viewModel: com.gxdevs.aethra.ui.JournalViewModel? = null
) {
    val context       = LocalContext.current
    var title         by rememberSaveable { mutableStateOf("") }
    val richTextState = rememberSaveable(saver = RichTextStateSaver) { RichTextState() }
    var currentPrompt by rememberSaveable { mutableStateOf<String?>(null) }
    var activeList    by rememberSaveable { mutableStateOf(ListType.OFF) }

    // Edit Initialization
    var initialized by rememberSaveable { mutableStateOf(false) }
    val allEntries by viewModel?.allEntries?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList()) }
    var selectedMedia by rememberSaveable(saver = SelectedMediaStateSaver) { mutableStateOf(listOf<Uri>()) }
    
    LaunchedEffect(editId, allEntries) {
        if (editId != null && !initialized && allEntries.isNotEmpty()) {
            val entry = allEntries.find { it.id == editId }
            if (entry != null) {
                val lines = entry.content?.split("\n") ?: emptyList()
                if (lines.isNotEmpty()) {
                    title = lines.first()
                    richTextState.textFieldValue = TextFieldValue(lines.drop(1).joinToString("\n"))
                }

                // Restore rich-text format ranges saved in promptResponses
                if (!entry.promptResponses.isNullOrBlank()) {
                    try {
                        richTextState.formatRanges.clear()
                        richTextState.formatRanges.addAll(entry.promptResponses.toFormatRanges())
                    } catch (_: Exception) {}
                }

                // Parse media URIs â€” handles both storage formats:
                // Format A (AfterJournalViewModel): [{"uri":"...","type":"IMAGE","name":"..."}, ...]
                // Format B (plain URI list):         ["content://...", "content://..."]
                if (!entry.attachments.isNullOrBlank()) {
                    try {
                        val raw = entry.attachments
                        // Try object-list format first
                        val objectListType = object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type
                        val asMaps: List<Map<String, Any>>? = kotlin.runCatching {
                            com.google.gson.Gson().fromJson<List<Map<String, Any>>>(raw, objectListType)
                                .also { list ->
                                    if (list.isEmpty() || list.any { !it.containsKey("uri") })
                                        throw IllegalArgumentException("not object format")
                                }
                        }.getOrNull()

                        selectedMedia = if (asMaps != null) {
                            // Extract only visual media (not audio FILE entries)
                            asMaps.mapNotNull { map ->
                                val uriStr  = map["uri"] as? String ?: return@mapNotNull null
                                val type    = (map["type"] as? String) ?: "IMAGE"
                                if (type == "FILE") return@mapNotNull null // skip audio
                                kotlin.runCatching { Uri.parse(uriStr) }.getOrNull()
                            }
                        } else {
                            // Fallback: plain string list
                            val typeToken = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
                            val uriStrings: List<String> = com.google.gson.Gson().fromJson(raw, typeToken)
                            uriStrings.mapNotNull { kotlin.runCatching { Uri.parse(it) }.getOrNull() }
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }

                initialized = true
            }
        }
    }

    // â”€â”€ Audio state (one recording at a time) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    var audioState     by rememberSaveable { mutableStateOf(AudioState.IDLE) }
    var durationSec    by rememberSaveable { mutableIntStateOf(0) }
    var recordingFile  by rememberSaveable(saver = FileStateSaver) { mutableStateOf<File?>(null) }
    val mediaRecorder  = remember { mutableStateOf<android.media.MediaRecorder?>(null) }
    val mediaPlayer    = remember { mutableStateOf<android.media.MediaPlayer?>(null) }

    // Timer: only ticks while actively recording
    val isActivelyRecording = audioState == AudioState.RECORDING
    LaunchedEffect(isActivelyRecording) {
        if (isActivelyRecording) {
            while (true) { kotlinx.coroutines.delay(1000L); durationSec++ }
        }
    }
    // Release resources when screen leaves composition
    DisposableEffect(Unit) {
        onDispose {
            mediaRecorder.value?.runCatching { stop(); release() }
            mediaRecorder.value = null
            mediaPlayer.value?.runCatching { stop(); release() }
            mediaPlayer.value = null
        }
    }

    fun startRecording() {
        // Stop any existing recording first
        mediaRecorder.value?.runCatching { stop(); release() }
        mediaPlayer.value?.runCatching { stop(); release() }
        mediaPlayer.value = null
        val file = File(context.cacheDir, "lore_rec_${System.currentTimeMillis()}.m4a")
        recordingFile = file
        durationSec = 0
        @Suppress("DEPRECATION")
        val rec = android.media.MediaRecorder().apply {
            setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
            setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        mediaRecorder.value = rec
        audioState = AudioState.RECORDING
    }

    fun stopRecording() {
        mediaRecorder.value?.runCatching { stop(); release() }
        mediaRecorder.value = null
        audioState = AudioState.RECORDED
    }

    fun pauseRecording() {
        mediaRecorder.value?.pause()
        audioState = AudioState.PAUSED
    }

    fun resumeRecording() {
        mediaRecorder.value?.resume()
        audioState = AudioState.RECORDING
    }

    fun playRecording() {
        val file = recordingFile ?: return
        if (!file.exists()) return
        try {
            mediaPlayer.value?.runCatching { stop(); release() }
            val player = android.media.MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnPreparedListener {
                    it.start()
                    audioState = AudioState.RECORDED
                }
                setOnCompletionListener { audioState = AudioState.RECORDED }
                prepareAsync() // prevent main thread block which causes crashes
            }
            mediaPlayer.value = player
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // â”€â”€ Media (multi-select, images + videos only) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    val imagePicker = rememberLauncherForActivityResult(PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) { e.printStackTrace() }
            }
            selectedMedia = (selectedMedia + uris).distinct()
        }
    }
    val currentTime = remember { SimpleDateFormat("MMM dd, yyyy | h:mm a", Locale.getDefault()).format(Date()) }

    val recordAudioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording()
        else Toast.makeText(context, "Microphone permission required", Toast.LENGTH_SHORT).show()
    }

    val hasMedia  = selectedMedia.isNotEmpty()
    val showAudio = audioState != AudioState.IDLE
    val textContent  = richTextState.textFieldValue.text
    val wordCount    = if (textContent.isBlank()) 0 else textContent.trim().split(Regex("\\s+")).count { it.isNotBlank() }
    val spiritEnergy = wordCount / 3
    val bottomPad = if (hasMedia) 200.dp else 148.dp

    // ─── Settings & Draft ─────────────────────────────────────────
    val settingsRepo      = remember { SettingsRepository(context) }
    val autoSaveFrequency by settingsRepo.autoSaveFrequency.collectAsState(initial = 15f)
    val draftTitle        by settingsRepo.draftTitle.collectAsState(initial = null)
    val draftContent      by settingsRepo.draftContent.collectAsState(initial = null)
    val draftTimestamp    by settingsRepo.draftTimestamp.collectAsState(initial = 0L)
    val draftAttachments  by settingsRepo.draftAttachments.collectAsState(initial = null)
    val coroutineScope    = rememberCoroutineScope()

    // Draft snackbar state
    var showDraftSnackbar    by remember { mutableStateOf(false) }
    var draftSnackbarSeconds by remember { mutableIntStateOf(5) }
    var draftLoaded          by rememberSaveable { mutableStateOf(false) }

    // ─── Time Tracking ─────────────────────────────────────────────
    val timeSpentWritingSec  by settingsRepo.draftTimeSpent.collectAsState(initial = 0L)
    var currentSessionTime   by remember { mutableLongStateOf(0L) }
    
    // Timer to track time spent
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000L)
            currentSessionTime++
        }
    }

    // On first open: if a draft exists & is <1 day old, offer to restore
    LaunchedEffect(Unit) {
        if (draftLoaded) return@LaunchedEffect
        val titleDraft = settingsRepo.draftTitle.first()
        val contentDraft = settingsRepo.draftContent.first()
        val ts = settingsRepo.draftTimestamp.first()
        
        val oneDayMs = 24L * 60 * 60 * 1000
        if (!titleDraft.isNullOrBlank() || !contentDraft.isNullOrBlank()) {
            if (ts > 0 && System.currentTimeMillis() - ts < oneDayMs) {
                if (title.isBlank() && richTextState.textFieldValue.text.isBlank()) {
                    showDraftSnackbar    = true
                    draftSnackbarSeconds = 5
                    // Count down
                    repeat(5) {
                        kotlinx.coroutines.delay(1000L)
                        draftSnackbarSeconds--
                    }
                    showDraftSnackbar = false
                }
            } else {
                if (ts > 0) {
                    // Draft is older than 1 day — silently clear
                    settingsRepo.clearDraft()
                }
                draftLoaded = true
            }
        } else {
            draftLoaded = true
        }
    }

    // Auto-save to DataStore every N seconds
    var lastSavedContent by remember { mutableStateOf("") }
    LaunchedEffect(autoSaveFrequency) {
        while (true) {
            kotlinx.coroutines.delay((autoSaveFrequency * 1000L).toLong())
            val t = title
            val c = richTextState.textFieldValue.text
            val combined = "$t\n$c"
            val totalTime = timeSpentWritingSec + currentSessionTime
            val mediaJson = com.google.gson.Gson().toJson(selectedMedia.map { it.toString() })
            if ((t.isNotBlank() || c.isNotBlank()) && combined != lastSavedContent) {
                lastSavedContent = combined
                settingsRepo.saveDraft(t, c, totalTime, mediaJson)
            } else if (t.isNotBlank() || c.isNotBlank()) {
                // Keep updating time even if content hasn't changed if they are writing
                settingsRepo.saveDraft(t, c, totalTime, mediaJson)
            }
        }
    }

    // Discard old draft the moment user starts typing NEW content (without restoring)
    // This ensures only 1 draft ever exists — new writing wins immediately.
    val currentEditorText = richTextState.textFieldValue.text
    LaunchedEffect(title, currentEditorText) {
        if ((title.isNotBlank() || currentEditorText.isNotBlank()) && !draftLoaded) {
            // User is writing fresh content — kill the old draft immediately
            if (showDraftSnackbar) showDraftSnackbar = false
            settingsRepo.clearDraft()
            draftLoaded = true
        }
    }

    Scaffold(
        containerColor = mainContainerBackground,
        contentWindowInsets = WindowInsets(0)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {


            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                    .padding(bottom = bottomPad)
                    .imePadding() // push content up when keyboard opens
            ) {
                // â”€â”€ Top Bar: Close + Save â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(cardBackground)
                            .border(1.dp, borderColor, CircleShape)
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = textSecondary, modifier = Modifier.size(20.dp))
                    }

                    val canSave = textContent.isNotBlank() || title.isNotBlank()
                    val saveBg by animateColorAsState(if (canSave) darkAccent else cardBackground, label = "saveBg")
                    val saveText by animateColorAsState(if (canSave) Color.White else textSecondary, label = "saveText")

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (editId == null) {
                            val relicSaveBg by animateColorAsState(if (canSave) primaryAccent else cardBackground, label = "relicSaveBg")
                            val relicSaveText by animateColorAsState(if (canSave) Color.White else textSecondary, label = "relicSaveText")
                            
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(relicSaveBg)
                                    .border(1.dp, if (canSave) primaryAccent else borderColor, RoundedCornerShape(22.dp))
                                    .clickable(enabled = canSave, onClick = {
                                        val totalTime = timeSpentWritingSec + currentSessionTime
                                        val contentStr = title + "\n" + textContent
                                        val formatJson = richTextState.formatRanges.toFormatJson()
                                        onSave(contentStr, "", spiritEnergy, recordingFile?.absolutePath, selectedMedia, totalTime, formatJson, true)
                                    })
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Lock, "Relic", tint = relicSaveText, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("RELIC", color = relicSaveText, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            }
                        }

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(22.dp))
                                .background(saveBg)
                                .border(1.dp, if (canSave) darkAccent else borderColor, RoundedCornerShape(22.dp))
                                .clickable(enabled = canSave, onClick = {
                                    val totalTime = timeSpentWritingSec + currentSessionTime
                                    val contentStr = title + "\n" + textContent
                                    
                                    if (editId != null && viewModel != null && initialized) {
                                        coroutineScope.launch { settingsRepo.clearDraft() }
                                        val entry = allEntries.find { it.id == editId }
                                        if (entry != null) {
                                            val formatJson = richTextState.formatRanges.toFormatJson()

                                            // Build new attachments JSON preserving [{uri,type,name}] format
                                            // for existing encrypted entries; plain list for unencrypted
                                            val newAttachmentsJson: String = run {
                                                val gson = com.google.gson.Gson()
                                                val selectedUriStrings = selectedMedia.map { it.toString() }
                                                if (entry.isEncrypted) {
                                                    // Preserve object format so type metadata survives
                                                    val listType = object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type
                                                    val existingList: List<Map<String, Any>> = try {
                                                        val raw = entry.attachments ?: "[]"
                                                        if (raw.trimStart().startsWith("[{"))
                                                            gson.fromJson(raw, listType)
                                                        else emptyList()
                                                    } catch (_: Exception) { emptyList() }
                                                    val existingUris = existingList.mapNotNull { it["uri"] as? String }.toSet()
                                                    // Keep existing entries that are still selected
                                                    val kept = existingList.filter { item ->
                                                        val u = item["uri"] as? String ?: return@filter false
                                                        selectedUriStrings.any { it == u }
                                                    }.toMutableList()
                                                    // Add newly picked URIs (not already in existing list)
                                                    selectedUriStrings.forEach { uriStr ->
                                                        if (uriStr !in existingUris) {
                                                            val mimeType = try {
                                                                context.contentResolver.getType(android.net.Uri.parse(uriStr)) ?: ""
                                                            } catch (_: Exception) { "" }
                                                            val typeStr = when {
                                                                mimeType.startsWith("video") -> "VIDEO"
                                                                mimeType.startsWith("audio") -> "FILE"
                                                                else -> "IMAGE"
                                                            }
                                                            kept.add(mapOf("uri" to uriStr, "type" to typeStr, "name" to "Attached Media"))
                                                        }
                                                    }
                                                    gson.toJson(kept)
                                                } else {
                                                    gson.toJson(selectedUriStrings)
                                                }
                                            }

                                            val updatedEntry = entry.copy(
                                                content = contentStr,
                                                audioPath = recordingFile?.absolutePath ?: entry.audioPath,
                                                attachments = newAttachmentsJson,
                                                promptResponses = formatJson,
                                                timeSpentWriting = (entry.timeSpentWriting ?: 0L) + totalTime
                                            )
                                            // Use diff-aware update: deletes removed .enc files,
                                            // re-encrypts newly added plain URIs
                                            viewModel.updateEntryWithMediaDiff(entry, updatedEntry)
                                            onBack()
                                        }

                                    } else {
                                        val formatJson = richTextState.formatRanges.toFormatJson()
                                        onSave(contentStr, "", spiritEnergy, recordingFile?.absolutePath, selectedMedia, totalTime, formatJson, false)
                                    }
                                })
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("SAVE", color = saveText, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Save", tint = saveText, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Date Time
                Text(
                    text = currentTime,
                    color = textTertiary,
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Seek Inspiration Button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                        .clickable { currentPrompt = promptsList.random() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.AutoAwesome, 
                        contentDescription = null, 
                        tint = textSecondary, 
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (currentPrompt == null) "SEEK INSPIRATION" else "CHANGE PROMPT",
                        color = if (currentPrompt == null) textSecondary else promptColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                // Prompt Card
                AnimatedVisibility(visible = currentPrompt != null) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(cardBackground.copy(alpha = 0.5f))
                                .padding(start = 2.dp) // for left border
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .fillMaxHeight()
                                    .background(promptColor)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = currentPrompt ?: "",
                                    color = promptColor,
                                    fontFamily = FontFamily.Serif,
                                    fontStyle = FontStyle.Italic,
                                    fontSize = 16.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Remove Prompt",
                                    tint = textSecondary,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { currentPrompt = null }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Title Editor
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    textStyle = TextStyle(
                        color = textPrimary.copy(alpha = 0.8f),
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    cursorBrush = SolidColor(primaryAccent),
                    decorationBox = { innerTextField ->
                        if (title.isEmpty()) {
                            Text(
                                text = "Give it a title..",
                                style = TextStyle(
                                    color = textSecondary.copy(alpha = 0.5f),
                                    fontSize = 42.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Serif
                                )
                            )
                        }
                        innerTextField()
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Content Editor
                BasicTextField(
                    value = richTextState.textFieldValue,
                    onValueChange = { newVal ->
                        val shouldTurnOffList = richTextState.onValueChange(newVal, activeList)
                        if (shouldTurnOffList) activeList = ListType.OFF
                    },
                    visualTransformation = RichTextVisualTransformation(richTextState),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    textStyle = TextStyle(
                        color = textSecondary,
                        fontSize = 18.sp,
                        lineHeight = 28.sp
                    ),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                    cursorBrush = SolidColor(primaryAccent),
                    decorationBox = { innerTextField ->
                        if (richTextState.textFieldValue.text.isEmpty()) {
                            Text(
                                text = "What's on your mind today? Write as much as you need...",
                                style = TextStyle(
                                    color = textSecondary.copy(alpha = 0.5f),
                                    fontSize = 18.sp,
                                    lineHeight = 28.sp
                                )
                            )
                        }
                        innerTextField()
                    }
                )
            }

            // â”€â”€ Bottom overlay â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding() // nav-bar inset when keyboard is hidden
                    .imePadding(),           // pushes toolbar above keyboard when open
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // â”€â”€ Audio recording pill â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                AnimatedVisibility(
                    visible = showAudio,
                    enter = slideInVertically { it } + fadeIn(),
                    exit  = slideOutVertically { it } + fadeOut()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AudioRecordingPill(
                            audioState  = audioState,
                            durationSec = durationSec,
                            onPlay   = { playRecording() },
                            onPause  = { pauseRecording() },
                            onResume = { resumeRecording() },
                            onStop   = { stopRecording() },
                            onRemove = {
                                mediaRecorder.value?.runCatching { stop(); release() }
                                mediaRecorder.value = null
                                mediaPlayer.value?.runCatching { stop(); release() }
                                mediaPlayer.value = null
                                recordingFile = null
                                durationSec = 0
                                audioState = AudioState.IDLE
                            }
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }

                // â”€â”€ Word count and Spirit energy pills â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Word count pill
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(cardBackground.copy(alpha = 0.92f))
                            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("$wordCount WORDS", color = textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }

                    // Spirit energy pill
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(cardBackground.copy(alpha = 0.92f))
                            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = "Spirit Energy",
                            tint = Color(0xFFF3C042),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("$spiritEnergy SP", color = textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }

                Spacer(Modifier.height(10.dp))

                // â”€â”€ Floating toolbar (media + format + mic) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                val sharedScope = LocalSharedTransitionScope.current
                val navAnimScope = LocalNavAnimatedVisibilityScope.current
                
                @OptIn(ExperimentalSharedTransitionApi::class)
                val morphModifier = if (sharedScope != null && navAnimScope != null) {
                    with(sharedScope) {
                        Modifier.sharedBounds(
                            sharedContentState = rememberSharedContentState(key = "bottom_bar_morph"),
                            animatedVisibilityScope = navAnimScope,
                            boundsTransform = { _, _ -> androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 400f) }
                        )
                    }
                } else {
                    Modifier
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 16.dp)
                        .then(morphModifier)
                        .defaultMinSize(minHeight = 70.dp)
                        .shadow(24.dp, RoundedCornerShape(32.dp))
                        .clip(RoundedCornerShape(32.dp))
                        .background(bottomNavBackground)
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    // Draft restore indicator inside the bottom toolbar
                    AnimatedVisibility(
                        visible = showDraftSnackbar && !draftLoaded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // Load draft into editor
                                        title = draftTitle ?: ""
                                        val content = draftContent ?: ""
                                        richTextState.textFieldValue = TextFieldValue(content)
                                        
                                        // Restore media attachments
                                        val mediaList = try {
                                            val raw = draftAttachments
                                            if (!raw.isNullOrBlank()) {
                                                val typeToken = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
                                                val uriStrings: List<String> = com.google.gson.Gson().fromJson(raw, typeToken)
                                                uriStrings.mapNotNull { Uri.parse(it) }
                                            } else {
                                                emptyList()
                                            }
                                        } catch (e: Exception) {
                                            emptyList()
                                        }
                                        
                                        var hasMissingMedia = false
                                        val validMedia = mediaList.filter { uri ->
                                            val exists = isUriValidAndExists(context, uri)
                                            if (!exists) {
                                                hasMissingMedia = true
                                            }
                                            exists
                                        }
                                        selectedMedia = validMedia
                                        if (hasMissingMedia) {
                                            Toast.makeText(context, "Some draft media files were deleted or moved and could not be restored.", Toast.LENGTH_LONG).show()
                                        }
                                        
                                        draftLoaded       = true
                                        showDraftSnackbar = false
                                        coroutineScope.launch { settingsRepo.clearDraft() }
                                    }
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.AutoAwesome, null, tint = primaryAccent, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Draft found", color = Color(0xFFF4F1EA), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text("Tap to restore — closing in ${draftSnackbarSeconds}s", color = Color(0xFF828779), fontSize = 11.sp)
                                }
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Close",
                                    tint = Color(0xFF828779),
                                    modifier = Modifier.size(16.dp).clickable {
                                        showDraftSnackbar = false
                                    }
                                )
                            }
                            // Divider
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color.White.copy(alpha = 0.1f))
                            )
                        }
                    }

                    // Media thumbnails row (only when media selected)
                    AnimatedVisibility(visible = hasMedia) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(selectedMedia) { uri ->
                                val mimeType = context.contentResolver.getType(uri) ?: ""
                                val isVideo  = mimeType.startsWith("video")
                                Box(modifier = Modifier
                                    .size(64.dp)
                                    .clickable {
                                        try {
                                            val validMimeType = if (mimeType.isNotEmpty()) mimeType else if (isVideo) "video/*" else "image/*"
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, validMimeType)
                                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            Toast.makeText(context, "No app found to open this media", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    @OptIn(ExperimentalGlideComposeApi::class)
                                    GlideImage(
                                        model = uri,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    // Video overlay icon
                                    if (isVideo) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(3.dp)
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.55f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Rounded.VideoFile, null, tint = Color.White, modifier = Modifier.size(10.dp))
                                        }
                                    }
                                    // Remove button
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 4.dp, y = (-4).dp)
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(bottomNavBackground)
                                            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                                            .clickable { selectedMedia = selectedMedia - uri },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Rounded.Close, null, tint = Color.White, modifier = Modifier.size(10.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Format buttons row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.weight(1f)
                        ) {
                            FormatButton(active = richTextState.activeBold,          icon = Icons.Rounded.FormatBold,          onClick = { richTextState.toggleFormat(FormatType.BOLD) })
                            FormatButton(active = richTextState.activeItalic,         icon = Icons.Rounded.FormatItalic,        onClick = { richTextState.toggleFormat(FormatType.ITALIC) })
                            FormatButton(active = richTextState.activeUnderline,      icon = Icons.Rounded.FormatUnderlined,    onClick = { richTextState.toggleFormat(FormatType.UNDERLINE) })
                            FormatButton(active = richTextState.activeStrikethrough,  icon = Icons.Rounded.FormatStrikethrough, onClick = { richTextState.toggleFormat(FormatType.STRIKETHROUGH) })
                            FormatButton(
                                active = activeList != ListType.OFF,
                                icon   = if (activeList == ListType.NUMBER) Icons.Rounded.FormatListNumbered else Icons.AutoMirrored.Rounded.FormatListBulleted,
                                onClick = {
                                    val sel = richTextState.textFieldValue.selection
                                    val newList = when (activeList) {
                                        ListType.OFF    -> ListType.NUMBER
                                        ListType.NUMBER -> ListType.BULLET
                                        ListType.BULLET -> ListType.OFF
                                    }
                                    activeList = newList
                                    // Apply to selection OR current line (Notion-style)
                                    if (!sel.collapsed) {
                                        richTextState.applyListToSelection(newList)
                                    } else {
                                        richTextState.applyListToCurrentLine(newList)
                                    }
                                }
                            )
                        }

                        Box(Modifier.width(1.dp).height(24.dp).background(Color.White.copy(alpha = 0.2f)))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            IconButton(onClick = {
                                imagePicker.launch(
                                    PickVisualMediaRequest(PickVisualMedia.ImageAndVideo)
                                )
                            }) {
                                Icon(Icons.Rounded.Image, "Media", tint = Color.White)
                            }
                            // Mic button: starts new recording, replacing any existing one
                            Box(
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(if (audioState == AudioState.RECORDING) darkAccent else accentBackground)
                                    .clickable {
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                            startRecording()
                                        } else {
                                            recordAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Mic, "Record",
                                    tint = if (audioState == AudioState.RECORDING) accentBackground else bottomNavBackground
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TextJournalScreenPreview() {
    MyApplicationTheme { TextJournalScreen(onBack = {}, onSave = { _, _, _, _, _, _, _, _ -> }) }
}

