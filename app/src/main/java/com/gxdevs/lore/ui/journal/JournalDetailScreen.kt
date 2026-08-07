package com.gxdevs.lore.ui.journal

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.animation.ExperimentalSharedTransitionApi
import com.gxdevs.lore.LocalNavAnimatedVisibilityScope
import com.gxdevs.lore.LocalSharedTransitionScope
import com.gxdevs.lore.MainActivity
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.gxdevs.lore.utils.MediaEncryptionManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.style.TextAlign
import androidx.core.net.toUri
import com.gxdevs.lore.data.SettingsRepository
import com.gxdevs.lore.data.mood.MoodConstants
import com.gxdevs.lore.ui.JournalViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.URLDecoder

private val mainContainerBackground = Color(0xFFF4F1EA)
private val cardBackground          = Color(0xFFEAE7DF)
private val textPrimary             = Color(0xFF2E332A)
private val textSecondary           = Color(0xFF828779)
private val primaryAccent           = Color(0xFF606F49)
private val darkAccent              = Color(0xFF4A5638)
private val yellowAccent            = Color(0xFFD9A05B)
private val borderColor             = Color(0xFFE0DCD1)
private val destructiveRed          = Color(0xFFC75D4E)

private data class DetailSavedRange(val type: String, val start: Int, val end: Int)

private fun String.toFormatRanges(): List<FormatRange> = try {
    val listType = object : TypeToken<List<DetailSavedRange>>() {}.type
    val saved: List<DetailSavedRange> = Gson().fromJson(this, listType)
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

private fun buildAnnotatedStringForPart(
    partText: String,
    startOffset: Int,
    formatRanges: List<FormatRange>
): androidx.compose.ui.text.AnnotatedString {
    return androidx.compose.ui.text.buildAnnotatedString {
        append(partText)
        val endOffset = startOffset + partText.length
        for (r in formatRanges) {
            val intersectionStart = maxOf(r.start, startOffset)
            val intersectionEnd = minOf(r.end, endOffset)
            if (intersectionStart < intersectionEnd) {
                val localStart = intersectionStart - startOffset
                val localEnd = intersectionEnd - startOffset
                addStyle(when (r.type) {
                    FormatType.BOLD          -> androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)
                    FormatType.ITALIC        -> androidx.compose.ui.text.SpanStyle(fontStyle = FontStyle.Italic)
                    FormatType.UNDERLINE     -> androidx.compose.ui.text.SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
                    FormatType.STRIKETHROUGH -> androidx.compose.ui.text.SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
                }, localStart, localEnd)
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun JournalDetailScreen(
    viewModel: JournalViewModel? = null,
    entryId: Long = 0L,
    onBack: () -> Unit = {},
    onEdit: (Long) -> Unit = {},
    onDeleted: () -> Unit = {},
    onEditMood: (Long) -> Unit = {}
) {
    val scrollState = rememberScrollState()
    LocalDensity.current
    val context = LocalContext.current

    // Load dynamic entry
    val allEntries by viewModel?.allEntries?.collectAsState(initial = emptyList())
        ?: remember { mutableStateOf(emptyList()) }
    val entry = allEntries.find { it.id == entryId }

    val showDeleteDialog = remember { mutableStateOf(false) }
    val showRelicDialog  = remember { mutableStateOf(false) }
    var showMenu         by remember { mutableStateOf(false) }

    // --- Delete confirmation ---
    if (showDeleteDialog.value) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog.value = false },
            containerColor = cardBackground,
            title = {
                Text(
                    "Delete this entry?",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    fontSize = 20.sp
                )
            },
            text = {
                Text(
                    "This memory will be permanently erased from your Lore. This action cannot be undone.",
                    color = textSecondary,
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel?.deleteEntry(entryId)
                        showDeleteDialog.value = false
                        onDeleted()
                        onBack()
                    }
                ) {
                    Text("Delete", color = destructiveRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog.value = false }) {
                    Text("Cancel", color = textSecondary)
                }
            }
        )
    }

    // --- Relic confirmation ---
    if (showRelicDialog.value) {
        RelicSealDialog(
            entryTitle = entry?.content?.substringBefore("\n")?.take(40) ?: "This entry",
            onConfirm = { days ->
                viewModel?.sealAsRelic(entryId, days)
                showRelicDialog.value = false
            },
            onDismiss = { showRelicDialog.value = false }
        )
    }

    // If entry is null, show loading
    if (entry == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(primaryAccent),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = mainContainerBackground)
        }
        return
    }

    // Parse Data
    val decodedContent = if (entry.content?.contains("+") == true) {
        runCatching { URLDecoder.decode(entry.content, "UTF-8") }.getOrDefault(entry.content)
    } else { entry.content ?: "" }
    val lines         = decodedContent.split("\n")
    val title         = if (lines.isNotEmpty()) lines.first() else "Untitled"
    val bodyText      = if (lines.size > 1) lines.drop(1).joinToString("\n").trim() else ""
    val bodyWords     = bodyText.split(Regex("\\s+")).filter { it.isNotBlank() }
    val firstWord     = bodyWords.firstOrNull() ?: ""
    val firstLetter   = firstWord.take(1)
    val restOfFirstWordAndPara  = if (firstWord.isNotEmpty()) bodyText.removePrefix(firstLetter).substringBefore("\n\n") else ""
    val remainingParagraphs     = if (firstWord.isNotEmpty()) bodyText.substringAfter("\n\n", missingDelimiterValue = "") else ""
 
    val formatRanges = remember(entry.promptResponses) {
        entry.promptResponses?.toFormatRanges() ?: emptyList()
    }

    val sdf         = SimpleDateFormat("EEEE, MMM dd, yyyy · h:mm a", LocalLocale.current.platformLocale)
    val dateString  = sdf.format(Date(entry.timestamp))
    val tags        = entry.tags?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    val mainTag     = tags.firstOrNull() ?: "MEMO"

    // --- Parse dominant mood from emotions JSON ---
    val dominantMood: String? = remember(entry.emotions) {
        if (entry.emotions.isNullOrBlank()) null
        else try {
            val ems = Gson().fromJson(entry.emotions, Array<Emotion>::class.java)
            val counts = mutableMapOf<String, Int>()
            ems.forEach { em ->
                val cat = MoodConstants.emotionToMood(em.label)
                counts[cat] = (counts[cat] ?: 0) + 1
            }
            counts.maxByOrNull { it.value }?.key
        } catch (_: Exception) { null }
    }
    val moodLabel   = dominantMood?.uppercase() ?: mainTag.uppercase()

    // --- Parse attachments handles both JSON formats ---
    // Format A (AfterJournalViewModel): [{"uri":"...","type":"IMAGE","name":"..."}, ...]
    // Format B (TextJournalScreen edit): ["content://...", "content://..."]
    data class ParsedAttachment(val uri: android.net.Uri, val type: String)
    val parsedAttachments = remember(entry.attachments) {
        if (!entry.attachments.isNullOrBlank()) {
            try {
                val raw = entry.attachments
                val jsonString = if (!raw.contains("{") && !raw.contains("[")) {
                    runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
                } else raw

                val objectListType = object : TypeToken<List<Map<String, Any>>>() {}.type
                val asMaps: List<Map<String, Any>>? = runCatching {
                    Gson().fromJson<List<Map<String, Any>>>(jsonString, objectListType)
                        .also { list -> if (list.isEmpty() || list.any { !it.containsKey("uri") }) throw IllegalArgumentException() }
                }.getOrNull()

                if (asMaps != null) {
                    asMaps.mapNotNull { map ->
                        val uriStr = map["uri"] as? String ?: return@mapNotNull null
                        val fileType = (map["type"] as? String) ?: "IMAGE"
                        runCatching { ParsedAttachment(uriStr.toUri(), fileType) }.getOrNull()
                    }
                } else {
                    val typeToken = object : TypeToken<List<String>>() {}.type
                    val uriStrings: List<String> = Gson().fromJson(jsonString, typeToken)
                    uriStrings.mapNotNull { uriStr ->
                        val uri = uriStr.toUri()
                        val path = uri.path ?: uriStr
                        val fileType = if (MediaEncryptionManager.isEncrypted(path)) {
                            "UNKNOWN"
                        } else {
                            val mimeType = if (uriStr.endsWith(".mp4", ignoreCase = true) || uriStr.endsWith(".mkv", ignoreCase = true) || uriStr.endsWith(".webm", ignoreCase = true)) {
                                "video"
                            } else if (uriStr.endsWith(".jpg", ignoreCase = true) || uriStr.endsWith(".jpeg", ignoreCase = true) || uriStr.endsWith(".png", ignoreCase = true) || uriStr.endsWith(".webp", ignoreCase = true)) {
                                "image"
                            } else null

                            if (mimeType != null) {
                                if (mimeType == "video") "VIDEO" else "IMAGE"
                            } else {
                                "UNKNOWN"
                            }
                        }
                        runCatching { ParsedAttachment(uri, fileType) }.getOrNull()
                    }
                }
            } catch (_: Exception) { emptyList() }
        } else emptyList()
    }



    val mediaTypes = remember(parsedAttachments) {
        parsedAttachments.associate { it.uri to it.type }
    }

    // Pre-compute video flags: use stored type metadata instantly, sniff unknowns on IO thread.
    // This avoids calling isVideoEncrypted() (file I/O) on the main/composition thread.
    var isVideoMap by remember(parsedAttachments) {
        mutableStateOf(
            parsedAttachments.associate { att ->
                att.uri to when (att.type) {
                    "VIDEO" -> true
                    "IMAGE", "FILE", "AUDIO" -> false
                    else -> false // UNKNOWN � will be resolved by the LaunchedEffect below
                }
            }
        )
    }
    LaunchedEffect(parsedAttachments) {
        // Only sniff files whose type is not already known
        parsedAttachments.forEach { att ->
            if (att.type !in listOf("VIDEO", "IMAGE", "FILE")) {
                val uriStr = att.uri.toString()
                val path = att.uri.path ?: uriStr
                if (MediaEncryptionManager.isEncrypted(path)) {
                    val detected = withContext(Dispatchers.IO) {
                        MediaEncryptionManager.isVideoEncrypted(context, path)
                    }
                    isVideoMap = isVideoMap + (att.uri to detected)
                } else {
                    val mimeType = withContext(Dispatchers.IO) {
                        try { context.contentResolver.getType(att.uri) } catch (_: Exception) { null }
                    }
                    val detected = mimeType?.startsWith("video") == true ||
                        uriStr.endsWith(".mp4", ignoreCase = true)
                    isVideoMap = isVideoMap + (att.uri to detected)
                }
            }
        }
    }

    val isVideo = remember(isVideoMap) {
        { uri: android.net.Uri -> isVideoMap[uri] ?: false }
    }

    // Visual media = IMAGE + VIDEO only (FILE/AUDIO = audio, shown in audio pill instead)
    val mediaUris = remember(parsedAttachments) {
        parsedAttachments
            .filter { it.type != "FILE" && it.type != "AUDIO" }
            .map { it.uri }
    }
    // Audio from attachments (FILE or AUDIO type) for entries saved via AfterJournalViewModel
    val attachmentAudioUri = remember(parsedAttachments) {
        parsedAttachments.firstOrNull { it.type == "FILE" || it.type == "AUDIO" }?.uri
    }

    val hasAudio        = (!entry.audioPath.isNullOrBlank() && entry.audioPath != "null") || attachmentAudioUri != null
    val hasVideo        = !entry.videoPath.isNullOrBlank() && entry.videoPath != "null"
    val totalMediaCount = mediaUris.size + (if (hasAudio) 1 else 0) + (if (hasVideo) 1 else 0)

    // Header constants
    val topBarHeight = 64.dp
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topBarTotalHeight = topBarHeight + topPadding

    val sharedScope = LocalSharedTransitionScope.current
    val navAnimScope = LocalNavAnimatedVisibilityScope.current
    
    @OptIn(ExperimentalSharedTransitionApi::class)
    val morphModifier = if (sharedScope != null && navAnimScope != null) {
        with(sharedScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "journal_card_${entryId}"),
                animatedVisibilityScope = navAnimScope,
                boundsTransform = { _, _ -> tween(400, easing = FastOutSlowInEasing) }
            )
        }
    } else Modifier

    Box(
        modifier = Modifier.fillMaxSize().background(mainContainerBackground).then(morphModifier)
    ) {
        // Scrollable Content
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(topBarTotalHeight))

            // Dynamic Green Section that scrolls naturally
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(primaryAccent)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 64.dp) // extra padding so white box overlaps cleanly
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(dateString, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontStyle = FontStyle.Italic)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("EARTH", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 32.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 38.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                // --- Mood chip — tappable to update mood ---
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(darkAccent)
                        .clickable { onEditMood(entryId) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Rounded.Eco, contentDescription = null, tint = yellowAccent, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(moodLabel, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.width(1.dp).height(12.dp).background(Color.White.copy(alpha = 0.3f)))
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = "Change Mood",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            // White Content Box (Overlaps the green section)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-32).dp)
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(mainContainerBackground)
                    .padding(horizontal = 24.dp, vertical = 32.dp)
            ) {
                Column {
                    if (firstLetter.isNotEmpty()) {
                        val annotatedFirstLetter = buildAnnotatedStringForPart(firstLetter, 0, formatRanges)
                        val annotatedRest = buildAnnotatedStringForPart(restOfFirstWordAndPara, 1, formatRanges)
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            Text(
                                text = annotatedFirstLetter,
                                fontSize = 60.sp,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                color = primaryAccent,
                                modifier = Modifier.padding(end = 12.dp).offset(y = (-8).dp),
                                lineHeight = 60.sp
                            )
                            Text(
                                text = annotatedRest,
                                fontSize = 15.sp,
                                color = textPrimary,
                                lineHeight = 24.sp
                            )
                        }
                    }

                    if (remainingParagraphs.isNotBlank()) {
                        val remainingStartOffset = bodyText.length - remainingParagraphs.length
                        val annotatedRemaining = buildAnnotatedStringForPart(remainingParagraphs, remainingStartOffset, formatRanges)
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = annotatedRemaining,
                            fontSize = 15.sp,
                            color = textPrimary,
                            lineHeight = 24.sp
                        )
                    }

                    if (totalMediaCount > 0) {
                        Spacer(modifier = Modifier.height(48.dp))
                        HorizontalDivider(color = borderColor.copy(alpha = 0.5f), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Image, contentDescription = null, tint = textSecondary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("SEALED ARTIFACTS ($totalMediaCount)", color = textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cardBackground).padding(16.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (hasAudio) {
                                    val audioUri = attachmentAudioUri ?: entry.audioPath?.toUri()
                                    if (audioUri != null) {
                                        AudioPlayerCard(uri = audioUri)
                                    }
                                }
                                if (mediaUris.isNotEmpty()) {
                                    DynamicMediaGrid(
                                        uris = mediaUris,
                                        isVideo = isVideo
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(120.dp))
                }
            }
        }

        // Sticky Top Bar (Drawn LAST so it's always on top)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(topBarTotalHeight)
                .background(primaryAccent)
                .padding(top = topPadding)
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)).clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(18.dp))
            }

            Text(
                text = dateString,
                color = Color.White,
                fontSize = 14.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .graphicsLayer {
                        val scrollPx = scrollState.value.toFloat()
                        val fadeStart = 100f
                        val fadeEnd = 300f
                        val calculatedAlpha = ((scrollPx - fadeStart) / (fadeEnd - fadeStart)).coerceIn(0f, 1f)
                        this.alpha = calculatedAlpha
                    },
                textAlign = TextAlign.Center
            )

            // Menu button
            Box(contentAlignment = Alignment.TopEnd) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)).clickable { showMenu = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Menu", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    containerColor = cardBackground
                ) {
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Edit, null, tint = textPrimary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Edit Entry", color = textPrimary, fontSize = 14.sp)
                            }
                        },
                        onClick = { showMenu = false; onEdit(entryId) }
                    )
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Lock, null, tint = yellowAccent, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Seal as Relic", color = textPrimary, fontSize = 14.sp)
                            }
                        },
                        onClick = { showMenu = false; showRelicDialog.value = true }
                    )
                    HorizontalDivider(color = borderColor, thickness = 1.dp, modifier = Modifier.padding(horizontal = 8.dp))
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Delete, null, tint = destructiveRed, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Delete Entry", color = destructiveRed, fontSize = 14.sp)
                            }
                        },
                        onClick = { showMenu = false; showDeleteDialog.value = true }
                    )
                }
            }
        }
    }
}


// ─── Audio Player Card ──────────────────────────────────────────────────────
@Composable
private fun AudioPlayerCard(uri: android.net.Uri) {
    val context = LocalContext.current
    val uriString = uri.toString()
    val isEncFile = MediaEncryptionManager.isEncrypted(uriString)
        || (uri.scheme == null && MediaEncryptionManager.isEncrypted(uri.path ?: uriString))

    // For encrypted files, decrypt to a temp file first
    var tempDecryptedFile by remember { mutableStateOf<File?>(null) }
    var decryptError by remember { mutableStateOf(false) }
    rememberCoroutineScope()

    LaunchedEffect(uriString) {
        if (isEncFile) {
            val encPath = uri.path ?: uriString
            val decrypted = withContext(Dispatchers.IO) {
                // Always decrypt audio with the m4a hint so the temp file has a proper extension
                MediaEncryptionManager.decryptToTemp(context, encPath, "m4a")
            }
            if (decrypted != null) tempDecryptedFile = decrypted
            else decryptError = true
        }
    }

    DisposableEffect(uriString) {
        onDispose { tempDecryptedFile?.delete() }
    }

    // Only build the player once we have the right source
    val playableUri: android.net.Uri? = when {
        !isEncFile                        -> uri
        tempDecryptedFile != null         -> tempDecryptedFile!!.toUri()
        else                              -> null // still decrypting
    }

    var isPlaying    by remember { mutableStateOf(false) }
    var currentMs    by remember { mutableLongStateOf(0L) }
    var durationMs   by remember { mutableLongStateOf(0L) }

    val player = remember(playableUri) {
        if (playableUri == null) return@remember null
        android.media.MediaPlayer().apply {
            runCatching {
                val path = tempDecryptedFile?.absolutePath
                if (path != null) {
                    setDataSource(path)
                } else if (playableUri.scheme == null) {
                    setDataSource(playableUri.path ?: playableUri.toString())
                } else {
                    setDataSource(context, playableUri)
                }
                setOnPreparedListener { mp -> durationMs = mp.duration.toLong() }
                setOnCompletionListener { isPlaying = false; seekTo(0); currentMs = 0L }
                prepareAsync()
            }
        }
    }
    DisposableEffect(playableUri) { onDispose { player?.runCatching { stop(); release() } } }

    // Tick progress while playing
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentMs = player?.runCatching { currentPosition.toLong() }?.getOrDefault(0L) ?: 0L
            delay(200)
        }
    }

    val progress = if (durationMs > 0) currentMs.toFloat() / durationMs else 0f
    fun formatMs(ms: Long): String {
        val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60)
    }
    rememberInfiniteTransition(label = "wave")
    remember { listOf(0.3f, 0.7f, 0.5f, 0.9f, 0.4f, 0.8f, 0.35f, 0.6f, 0.5f, 0.85f, 0.4f, 0.7f) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(mainContainerBackground)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Play/Pause button
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (decryptError) Color(0xFFC75D4E) else primaryAccent)
                .clickable(enabled = player != null && !decryptError) {
                    if (isPlaying) { player?.pause(); isPlaying = false }
                    else           { player?.start(); isPlaying = true  }
                },
            contentAlignment = Alignment.Center
        ) {
            when {
                decryptError -> Icon(Icons.Rounded.ErrorOutline, "Error", tint = Color.White, modifier = Modifier.size(22.dp))
                player == null -> CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else -> Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text("Audio Memo", color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)

            // Seek slider
            Slider(
                value = progress,
                onValueChange = { v ->
                    val seekTo = (v * durationMs).toLong()
                    player?.runCatching { seekTo(seekTo.toInt()) }
                    currentMs = seekTo
                },
                modifier = Modifier.fillMaxWidth().height(20.dp),
                colors = SliderDefaults.colors(
                    thumbColor = primaryAccent,
                    activeTrackColor = primaryAccent,
                    inactiveTrackColor = textSecondary.copy(alpha = 0.3f)
                )
            )

            // Time labels
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatMs(currentMs), color = textSecondary, fontSize = 10.sp)
                Text(formatMs(durationMs), color = textSecondary, fontSize = 10.sp)
            }
        }
    }
}

// --- Dynamic Media Grid ---
// Layout rules (no empty half-spaces):
//   1 item  at full-width 16:9 only
//   2 items at hero 16:9 stacked above second item 16:9 (both full-width)
//   3 items at hero 16:9 + two 1:1 squares side-by-side
//   4+      at hero 16:9 + one 1:1 thumbnail + "+N MORE" tile (50/50 row)
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun DynamicMediaGrid(
    uris: List<android.net.Uri>,
    isVideo: (android.net.Uri) -> Boolean
) {
    if (uris.isEmpty()) return
    val firstMedia     = uris.first()
    val remainingMedia = uris.drop(1)
    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository(context) }
    val encryptMediaState = settingsRepo.encryptMedia.collectAsState(initial = false)

    fun open(uri: android.net.Uri) {
        val intent = android.content.Intent(context, MediaViewerActivity::class.java).apply {
            putExtra("media_uri", uri.toString())
            putExtra("is_video", isVideo(uri))
            putExtra("encryption_enabled", encryptMediaState.value)
        }
        MainActivity.bypassNextLock = true
        context.startActivity(intent)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Hero always full-width 16:9
        MediaThumbnail(
            uri = firstMedia,
            isVideo = isVideo(firstMedia),
            playIconSize = 26.dp,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            onClick = { open(firstMedia) }
        )

        when {
            // 2 total: second item also full-width 16:9, no gap
            remainingMedia.size == 1 -> {
                val uri = remainingMedia[0]
                MediaThumbnail(uri = uri, isVideo = isVideo(uri), playIconSize = 22.dp,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                    onClick = { open(uri) }
                )
            }
            // 3 total: two equal squares side-by-side
            remainingMedia.size == 2 -> {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(remainingMedia[0], remainingMedia[1]).forEach { uri ->
                        MediaThumbnail(uri = uri, isVideo = isVideo(uri), playIconSize = 16.dp,
                            modifier = Modifier.weight(1f).aspectRatio(1f),
                            onClick = { open(uri) }
                        )
                    }
                }
            }
            // 4+ total: one thumbnail + overflow tile (50/50)
            remainingMedia.size >= 3 -> {
                val overflowCount = remainingMedia.size - 1
                val uri = remainingMedia[0]
                val overflowUri = remainingMedia[1]  // tapping "+N" opens the second overflow item
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MediaThumbnail(uri = uri, isVideo = isVideo(uri), playIconSize = 16.dp,
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        onClick = { open(uri) }
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(primaryAccent.copy(alpha = 0.50f))
                            .clickable { open(overflowUri) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("+$overflowCount", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Text("MORE", color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun MediaThumbnail(
    uri: android.net.Uri,
    isVideo: Boolean,
    playIconSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val uriString = uri.toString()
    val encPath: String? = when {
        MediaEncryptionManager.isEncrypted(uriString) -> uri.path ?: uriString
        uri.scheme == null && MediaEncryptionManager.isEncrypted(uri.path ?: "") -> uri.path
        else -> null
    }

    // For encrypted files, decrypt to a temp file so Glide can load it.
    // displayModel starts as the URI (or a File for schemaless paths) for instant non-null model.
    var displayModel: Any by remember(uriString) {
        mutableStateOf(if (uri.scheme == null && encPath == null) File(uri.path ?: uriString) else uri)
    }
    var tempFile by remember(uriString) { mutableStateOf<File?>(null) }
    var isLoading by remember(uriString) { mutableStateOf(encPath != null) }

    // Sniff type once on IO thread, then decrypt exactly once
    LaunchedEffect(uriString) {
        if (encPath != null) {
            isLoading = true
            val dec = withContext(Dispatchers.IO) {
                // Determine video/audio/image by sniffing first, so we decrypt once
                // with the right extension hint
                val sniffedIsVideo = MediaEncryptionManager.isVideoEncrypted(context, encPath)
                val extHint = if (sniffedIsVideo) "mp4" else "jpg"
                MediaEncryptionManager.decryptToTemp(context, encPath, extHint)
            }
            if (dec != null) {
                // Clean up old temp before switching
                tempFile?.delete()
                tempFile = dec
                displayModel = dec
            }
            isLoading = false
        }
    }

    DisposableEffect(uriString) { onDispose { tempFile?.delete() } }

    Box(modifier = modifier.clip(RoundedCornerShape(16.dp)).clickable { onClick() }) {
        GlideImage(
            model = displayModel,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().background(Color(0xFFCDCCC7))
        )
        // Shimmer/loading overlay while decrypting
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFCDCCC7).copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = primaryAccent,
                    strokeWidth = 2.dp
                )
            }
        }
        if (isVideo && !isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(playIconSize * 1.85f)
                        .clip(CircleShape)
                        .background(primaryAccent.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(playIconSize))
                }
            }
        }
    }
}

// --- Relic Seal Dialog ---
@Composable
fun RelicSealDialog(
    entryTitle: String,
    onConfirm: (days: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var lockDays by remember { mutableIntStateOf(365) }
    val infiniteTransition = rememberInfiniteTransition(label = "relic_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )
    val rotationAnim by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
        label = "spin"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF2D3229), Color(0xFF1E2218)))
                )
                .padding(32.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Animated seal icon
                Box(
                    modifier = Modifier.size(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Outer glow ring
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(
                                    yellowAccent.copy(alpha = glowAlpha * 0.6f),
                                    yellowAccent.copy(alpha = 0f)
                                )
                            )
                        )
                    }
                    // Rotating border
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .graphicsLayer { rotationZ = rotationAnim }
                            .clip(CircleShape)
                            .background(
                                Brush.sweepGradient(
                                    listOf(
                                        yellowAccent.copy(0.0f),
                                        yellowAccent.copy(0.5f),
                                        yellowAccent.copy(0.0f)
                                    )
                                )
                            )
                    )
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2D3229))
                            .clickable {},
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Lock, null, tint = yellowAccent, modifier = Modifier.size(28.dp))
                    }
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    "Seal as Relic",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    color = Color.White
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "\"$entryTitle\"",
                    fontSize = 13.sp,
                    color = yellowAccent.copy(0.8f),
                    fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    "This memory will be sealed in the Reliquary. It will resurface after the chosen time — a message to your future self.",
                    fontSize = 14.sp,
                    color = Color.White.copy(0.7f),
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(32.dp))

                // Lock duration chips
                Text("UNSEAL AFTER", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Color.White.copy(0.4f))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30 to "1 mo", 90 to "3 mo", 180 to "6 mo", 365 to "1 yr").forEach { (days, label) ->
                        val isSelected = lockDays == days
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) yellowAccent else Color.White.copy(0.08f))
                                .clickable { lockDays = days }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color(0xFF2D3229) else Color.White.copy(0.6f))
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(0.08f))
                            .clickable { onDismiss() }
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("CANCEL", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Color.White.copy(0.6f))
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(listOf(yellowAccent, Color(0xFFF0B042))))
                            .clickable { onConfirm(lockDays) }
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Lock, null, tint = Color(0xFF2D3229), modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("SEAL IT", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Color(0xFF2D3229))
                        }
                    }
                }
            }
        }
    }
}

