package com.gxdevs.lore.ui.pets

import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gxdevs.lore.data.mood.MoodConstants
import com.gxdevs.lore.ui.JournalViewModel
import com.gxdevs.lore.ui.journal.JournalDetailScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─── Mood → icon mapping (no emoji) ──────────────────────────────────────────

private fun moodIcon(moodId: String?): ImageVector = when (moodId?.lowercase()) {
    "bright"  -> Icons.Rounded.WbSunny
    "calm"    -> Icons.Rounded.Spa
    "heavy"   -> Icons.Rounded.WaterDrop
    "tangled" -> Icons.Rounded.Loop
    "dark"    -> Icons.Rounded.DarkMode
    "blank"   -> Icons.Rounded.RadioButtonUnchecked
    else      -> Icons.Rounded.Edit
}

private fun stageIcon(stageIndex: Int, emotion: String?): ImageVector {
    val emo = emotion?.lowercase() ?: "blank"
    if (stageIndex == 0) return Icons.Rounded.Egg
    if (stageIndex >= 4) return Icons.Rounded.AutoAwesome
    
    return when (emo) {
        "bright" -> when (stageIndex) {
            1 -> Icons.Rounded.WbTwilight
            2 -> Icons.Rounded.WbSunny
            else -> Icons.Rounded.Flare
        }
        "calm" -> when (stageIndex) {
            1 -> Icons.Rounded.Spa
            2 -> Icons.Rounded.Eco
            else -> Icons.Rounded.Yard
        }
        "heavy" -> when (stageIndex) {
            1 -> Icons.Rounded.WaterDrop
            2 -> Icons.Rounded.FilterDrama
            else -> Icons.Rounded.Terrain
        }
        "tangled" -> when (stageIndex) {
            1 -> Icons.Rounded.Loop
            2 -> Icons.Rounded.Cyclone
            else -> Icons.Rounded.Air
        }
        "dark" -> when (stageIndex) {
            1 -> Icons.Rounded.Bedtime
            2 -> Icons.Rounded.DarkMode
            else -> Icons.Rounded.NightsStay
        }
        else -> when (stageIndex) { // blank / fallback
            1 -> Icons.Rounded.RadioButtonUnchecked
            2 -> Icons.Rounded.Circle
            else -> Icons.Rounded.BlurOn
        }
    }
}

// ─── Journey Screen ───────────────────────────────────────────────────────────

@Composable
fun PetJourneyScreen(
    pet: PetUiState,
    journeyData: PetJourneyData?,
    isLoading: Boolean,
    forceEmoji: Boolean,
    journalViewModel: JournalViewModel? = null,
    onDismiss: () -> Unit
) {
    val moodColor = MoodConstants.colorOf[pet.moodId]  ?: Color(0xFF606F49)
    val moodBg    = MoodConstants.bgColorOf[pet.moodId] ?: Color(0xFFD9DFCD)

    val loadedImage = remember(pet.localImagePath, pet.moodId) {
        getOrLoadPetPalette(pet.localImagePath, pet.moodId)
    }
    val accentColor = if (forceEmoji) moodColor else loadedImage.dominantColor
    val bgTint      = if (forceEmoji) moodBg    else loadedImage.lightBgColor

    val animatedAccent by animateColorAsState(accentColor, tween(500), label = "j_accent")

    val topBgColor = remember(bgTint) {
        Color(
            red   = 0.97f * 0.45f + bgTint.red   * 0.55f,
            green = 0.97f * 0.45f + bgTint.green * 0.55f,
            blue  = 0.97f * 0.45f + bgTint.blue  * 0.55f,
            alpha = 1f
        )
    }

    // Single shared InfiniteTransition — no per-item animations
    val sharedAnim = rememberInfiniteTransition(label = "j_shared")

    val floatY by sharedAnim.animateFloat(
        initialValue = -6f, targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "j_float"
    )
    val glowAlpha by sharedAnim.animateFloat(
        initialValue = 0.15f, targetValue = 0.38f,
        animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Reverse),
        label = "j_glow"
    )
    val rotation by sharedAnim.animateFloat(
        initialValue = -2.5f, targetValue = 2.5f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "j_rotation"
    )
    val breatheScale by sharedAnim.animateFloat(
        initialValue = 0.96f, targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "j_breathe"
    )
    val shimmerX by sharedAnim.animateFloat(
        initialValue = -300f, targetValue = 900f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "j_shimmer"
    )
    val pulseAlpha by sharedAnim.animateFloat(
        initialValue = 0.28f, targetValue = 0.65f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "j_pulse_a"
    )
    val pulseScale by sharedAnim.animateFloat(
        initialValue = 0.90f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "j_pulse_s"
    )
    val btnScale by sharedAnim.animateFloat(
        initialValue = 1f, targetValue = 1.020f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "j_btn_s"
    )

    // Journal detail overlay state
    var openJournalId by remember { mutableStateOf<Long?>(null) }

    val lazyState = rememberLazyListState()

    // Dynamic top shadow when scrolled
    val isScrolled = remember { derivedStateOf { lazyState.firstVisibleItemIndex > 0 || lazyState.firstVisibleItemScrollOffset > 0 } }
    val topGlowAlpha by animateFloatAsState(if (isScrolled.value) 0.95f else 0.0f, label = "top_glow")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        topBgColor,
                        Color(0xFFFAF8F5)
                    )
                )
            )
    ) {
        LazyColumn(
            state          = lazyState,
            modifier       = Modifier
                .fillMaxSize()
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
            contentPadding = PaddingValues(bottom = 200.dp)
        ) {

            // ── Hero ─────────────────────────────────────────────────────────
            item(key = "hero") {
                Box(
                    modifier         = Modifier.fillMaxWidth().height(280.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Outer Soft Aura Glow
                    Box(
                        modifier = Modifier
                            .size(260.dp)
                            .graphicsLayer { alpha = glowAlpha * 0.75f }
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(animatedAccent.copy(0.35f), Color.Transparent)
                                )
                            )
                    )
                    // Inner Bright Glow that breathes
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .graphicsLayer { 
                                alpha = glowAlpha * 1.1f
                                scaleX = breatheScale
                                scaleY = breatheScale
                            }
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(animatedAccent.copy(0.55f), Color.Transparent)
                                )
                            )
                    )
                    // Centered pet image with breathing/pulsing effects
                    Box(
                        modifier         = Modifier
                            .size(180.dp)
                            .graphicsLayer { 
                                scaleX = breatheScale
                                scaleY = breatheScale
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (!forceEmoji && loadedImage.imageBitmap != null) {
                            Image(
                                bitmap             = loadedImage.imageBitmap,
                                contentDescription = pet.name,
                                modifier           = Modifier.fillMaxSize().padding(4.dp),
                                contentScale       = ContentScale.Fit
                            )
                        } else {
                            Icon(
                                imageVector        = stageIcon(pet.stageIndex.coerceAtLeast(0), pet.emotion),
                                contentDescription = pet.stageName,
                                tint               = animatedAccent,
                                modifier           = Modifier.size(80.dp)
                            )
                        }
                    }
                }
            }

            // ── Pet name + summary pill ───────────────────────────────────────
            item(key = "header") {
                Column(
                    modifier            = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text       = pet.name,
                        fontSize   = 34.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        color      = Color(0xFF2C3224)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text          = pet.emotion.uppercase(),
                        fontSize      = 10.sp,
                        fontWeight    = FontWeight.Bold,
                        letterSpacing = 2.5.sp,
                        color         = animatedAccent.copy(0.7f)
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier              = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(animatedAccent.copy(0.12f))
                            .border(1.dp, animatedAccent.copy(0.28f), RoundedCornerShape(50))
                            .padding(horizontal = 16.dp, vertical = 7.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector        = Icons.Outlined.AutoStories,
                            contentDescription = null,
                            tint               = animatedAccent,
                            modifier           = Modifier.size(13.dp)
                        )
                        Text(
                            text       = if (journeyData != null)
                                "${journeyData.totalJournalCount} journals · ${journeyData.days.size} day${if (journeyData.days.size == 1) "" else "s"}"
                            else "Loading journey...",
                            fontSize   = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = animatedAccent
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text          = "JOURNEY TIMELINE",
                        fontSize      = 10.sp,
                        fontWeight    = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color         = Color(0xFF7A8370)
                    )
                    Spacer(Modifier.height(20.dp))
                }
            }

            // ── Loading / empty ──────────────────────────────────────────────
            if (isLoading || journeyData == null) {
                item(key = "loading") {
                    Box(
                        modifier         = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = animatedAccent, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Gathering memories...", fontSize = 13.sp, color = Color(0xFF7A8370))
                        }
                    }
                }
            } else if (journeyData.days.isEmpty()) {
                item(key = "empty") {
                    Box(
                        modifier         = Modifier.fillMaxWidth().padding(32.dp, 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector        = Icons.Outlined.AutoStories,
                                contentDescription = null,
                                tint               = animatedAccent.copy(0.4f),
                                modifier           = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text      = "No journey entries yet.\nStart writing journals to grow ${pet.name}.",
                                fontSize  = 14.sp,
                                color     = Color(0xFF7A8370),
                                textAlign = TextAlign.Center,
                                lineHeight = 22.sp
                            )
                        }
                    }
                }
            } else {
                // ── Timeline Day Nodes ──────────────────────────────────────
                val futureStages = journeyData.allStages.filter { it.stageIndex > journeyData.currentStageIndex }

                // ── Timeline Day Nodes ──────────────────────────────────────
                itemsIndexed(journeyData.days, key = { _, day -> day.dateMillis }) { index, day ->
                    TimelineDayNode(
                        day         = day,
                        accentColor = animatedAccent,
                        bgTint      = bgTint,
                        isFirst     = index == 0,
                        isLast      = index == journeyData.days.lastIndex && futureStages.isEmpty(),
                        shimmerX    = shimmerX,
                        pulseAlpha  = pulseAlpha,
                        pulseScale  = pulseScale,
                        forceEmoji  = forceEmoji,
                        petEmotion  = pet.emotion,
                        onJournalTap = { id -> openJournalId = id }
                    )
                }

                // ── Upcoming locked stages ──────────────────────────────────
                if (futureStages.isNotEmpty()) {
                    item(key = "upcoming_header") {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier          = Modifier.padding(horizontal = 24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.width(2.dp).height(20.dp).background(animatedAccent.copy(0.18f)))
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text          = "UPCOMING STAGES",
                                fontSize      = 9.sp,
                                fontWeight    = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                color         = Color(0xFF7A8370).copy(0.55f)
                            )
                        }
                    }
                    futureStages.forEachIndexed { idx, stage ->
                        item(key = "locked_${stage.stageIndex}") {
                            LockedStageNode(
                                stage       = stage,
                                accentColor = animatedAccent,
                                isLast      = idx == futureStages.lastIndex
                            )
                        }
                    }
                }

                // ── Footer ──────────────────────────────────────────────────
                if (!pet.isFullyGrown) {
                    item(key = "footer_enc") {
                        Spacer(Modifier.height(16.dp))
                        EncouragementFooter(
                            journalsLeft = pet.journalsToNext,
                            petName      = pet.name,
                            accentColor  = animatedAccent,
                            bgTint       = bgTint,
                            pulseScale   = btnScale
                        )
                    }
                } else {
                    item(key = "footer_myth") {
                        Spacer(Modifier.height(16.dp))
                        MythicCelebrationFooter(
                            petName     = pet.name,
                            accentColor = animatedAccent,
                            glowAlpha   = glowAlpha
                        )
                    }
                }
            }
        }

        // ── Close button ─────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(16.dp)
                .size(40.dp)
                .shadow(8.dp, CircleShape, spotColor = Color.Black.copy(0.10f))
                .clip(CircleShape)
                .background(Color.White.copy(0.92f))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication        = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = Icons.Rounded.Close,
                contentDescription = "Close Journey",
                tint               = Color(0xFF2C3224),
                modifier           = Modifier.size(20.dp)
            )
        }

        // ── Journal detail overlay ────────────────────────────────────────────
        openJournalId?.let { jId ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF4F1EA))
            ) {
                JournalDetailScreen(
                    viewModel = journalViewModel,
                    entryId   = jId,
                    onBack    = { openJournalId = null },
                    onEdit    = { openJournalId = null },
                    onDeleted = { openJournalId = null }
                )
            }
        }

        // Spotify-like fade-in edge at the top of the timeline screen
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            // Solid opaque status bar area
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                    .background(topBgColor.copy(alpha = topGlowAlpha))
            )
            // Gradient fade out edge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                topBgColor.copy(alpha = topGlowAlpha),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }
}

// ─── Timeline Day Node ────────────────────────────────────────────────────────

@Composable
fun TimelineDayNode(
    day: PetJourneyDay,
    accentColor: Color,
    bgTint: Color,
    isFirst: Boolean,
    isLast: Boolean,
    shimmerX: Float,
    pulseAlpha: Float,
    pulseScale: Float,
    forceEmoji: Boolean,
    petEmotion: String,
    onJournalTap: (Long) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max)
            .padding(start = 24.dp, end = 24.dp)
    ) {
        // ── Continuous vertical line + dot ────────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier
                .width(22.dp)
                .fillMaxHeight()
        ) {
            if (!isFirst) {
                Box(Modifier.width(2.dp).height(14.dp).background(accentColor.copy(0.20f)))
            } else {
                Spacer(Modifier.height(14.dp))
            }
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .shadow(3.dp, CircleShape, spotColor = accentColor.copy(0.22f))
                    .clip(CircleShape)
                    .background(accentColor.copy(0.80f))
            )
            if (isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .defaultMinSize(minHeight = 24.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(accentColor.copy(0.20f), Color.Transparent)
                            )
                        )
                )
            } else {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .defaultMinSize(minHeight = 16.dp)
                        .background(accentColor.copy(0.20f))
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        // ── Day content ───────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 8.dp, bottom = 16.dp)
        ) {
            // Date label with a small calendar icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier          = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    imageVector        = Icons.Outlined.CalendarToday,
                    contentDescription = null,
                    tint               = accentColor,
                    modifier           = Modifier.size(11.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text          = day.dateLabel,
                    fontSize      = 11.sp,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 0.4.sp,
                    color         = accentColor
                )
            }

            day.journalSnippets.forEach { snippet ->
                JournalSnippetCard(
                    snippet      = snippet,
                    accentColor  = accentColor,
                    onTap        = { onJournalTap(snippet.id) }
                )
                Spacer(Modifier.height(6.dp))
            }

            day.stageReachedAfterThis?.let { stage ->
                Spacer(Modifier.height(6.dp))
                EvolutionBurstCard(
                    stage      = stage,
                    accentColor = accentColor,
                    bgTint      = bgTint,
                    shimmerX    = shimmerX,
                    pulseAlpha  = pulseAlpha,
                    pulseScale  = pulseScale,
                    forceEmoji  = forceEmoji,
                    petEmotion  = petEmotion
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ─── Journal Snippet Card ─────────────────────────────────────────────────────

@Composable
fun JournalSnippetCard(
    snippet: JournalSnippet,
    accentColor: Color,
    onTap: () -> Unit
) {
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val icon = moodIcon(snippet.moodId)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(0.78f))
            .border(1.dp, accentColor.copy(0.10f), RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication        = null
            ) { onTap() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = accentColor.copy(0.72f),
                modifier           = Modifier.size(16.dp).padding(top = 1.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                // Max 2 lines of content
                Text(
                    text       = snippet.snippet.ifBlank { "Journal entry" },
                    fontSize   = 12.sp,
                    color      = Color(0xFF2C3224),
                    maxLines   = 2,
                    overflow   = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(5.dp))
                // Time + "Tap to read" hint on one row
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        text       = timeFormatter.format(Date(snippet.timestamp)),
                        fontSize   = 10.sp,
                        color      = Color(0xFF7A8370),
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text       = "Tap to read",
                            fontSize   = 9.sp,
                            color      = accentColor.copy(0.55f),
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.3.sp
                        )
                        Icon(
                            imageVector        = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint               = accentColor.copy(0.45f),
                            modifier           = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─── Evolution Burst Card ─────────────────────────────────────────────────────

@Composable
fun EvolutionBurstCard(
    stage: PetJourneyStage,
    accentColor: Color,
    bgTint: Color,
    shimmerX: Float,
    pulseAlpha: Float,
    pulseScale: Float,
    forceEmoji: Boolean,
    petEmotion: String
) {
    var stageBitmap by remember(stage.localImagePath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(stage.localImagePath) {
        stage.localImagePath?.let { path ->
            if (File(path).exists()) {
                val bmp = withContext(Dispatchers.IO) {
                    try { BitmapFactory.decodeFile(path)?.asImageBitmap() } catch (_: Exception) { null }
                }
                stageBitmap = bmp
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(bgTint.copy(0.88f), accentColor.copy(0.15f), bgTint.copy(0.88f))
                )
            )
            .border(
                1.5.dp,
                Brush.linearGradient(listOf(accentColor.copy(0.52f), accentColor.copy(0.14f))),
                RoundedCornerShape(20.dp)
            )
    ) {
        // Shared shimmer sweep
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(0.18f), Color.Transparent),
                        start  = Offset(shimmerX, 0f),
                        end    = Offset(shimmerX + 200f, 160f)
                    )
                )
        )

        Row(
            modifier          = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier         = Modifier.size(90.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .graphicsLayer { alpha = pulseAlpha; scaleX = pulseScale; scaleY = pulseScale }
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(accentColor.copy(0.48f), Color.Transparent)))
                )
                if (!forceEmoji && stageBitmap != null) {
                    Image(
                        bitmap             = stageBitmap!!,
                        contentDescription = stage.stageName,
                        modifier           = Modifier.size(76.dp).padding(4.dp),
                        contentScale       = ContentScale.Fit
                    )
                } else {
                    Icon(
                        imageVector        = stageIcon(stage.stageIndex, petEmotion),
                        contentDescription = stage.stageName,
                        tint               = accentColor,
                        modifier           = Modifier.size(46.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // "EVOLVED" badge
                Row(
                    modifier          = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(accentColor.copy(0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector        = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint               = accentColor,
                        modifier           = Modifier.size(9.dp)
                    )
                    Text(
                        text          = "EVOLVED",
                        fontSize      = 8.sp,
                        fontWeight    = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color         = accentColor
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text       = stage.stageName,
                    fontSize   = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    color      = Color(0xFF2C3224)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text       = "${stage.journalsRequired} journals reached",
                    fontSize   = 11.sp,
                    color      = Color(0xFF7A8370),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ─── Locked Stage Node ────────────────────────────────────────────────────────

@Composable
fun LockedStageNode(stage: PetJourneyStage, accentColor: Color, isLast: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 3.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.width(22.dp)
        ) {
            Box(Modifier.width(2.dp).height(12.dp).background(accentColor.copy(0.10f)))
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(0.16f))
                    .border(1.dp, accentColor.copy(0.26f), CircleShape)
            )
            if (isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(14.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(accentColor.copy(0.10f), Color.Transparent)
                            )
                        )
                )
            } else {
                Box(Modifier.width(2.dp).height(14.dp).background(accentColor.copy(0.10f)))
            }
        }

        Spacer(Modifier.width(14.dp))

        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(0.30f))
                .border(1.dp, accentColor.copy(0.07f), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector        = Icons.Rounded.Lock,
                contentDescription = null,
                tint               = accentColor.copy(0.35f),
                modifier           = Modifier.size(16.dp)
            )
            Column {
                Text(
                    text       = stage.stageName,
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = Color(0xFF2C3224).copy(0.38f)
                )
                Text(
                    text     = "${stage.journalsRequired} journals needed",
                    fontSize = 10.sp,
                    color    = Color(0xFF7A8370).copy(0.48f)
                )
            }
        }
    }
}

// ─── Encouragement Footer ─────────────────────────────────────────────────────

@Composable
fun EncouragementFooter(
    journalsLeft: Int,
    petName: String,
    accentColor: Color,
    bgTint: Color,
    pulseScale: Float
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(accentColor.copy(0.13f), bgTint.copy(0.58f))))
            .border(1.dp, accentColor.copy(0.26f), RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector        = Icons.Outlined.Spa,
                contentDescription = null,
                tint               = accentColor.copy(0.7f),
                modifier           = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text       = "$journalsLeft more journal${if (journalsLeft == 1) "" else "s"}",
                fontSize   = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                color      = Color(0xFF2C3224),
                textAlign  = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text      = "until ${petName}'s next evolution",
                fontSize  = 13.sp,
                color     = Color(0xFF7A8370),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─── Mythic Celebration Footer ────────────────────────────────────────────────

@Composable
fun MythicCelebrationFooter(petName: String, accentColor: Color, glowAlpha: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.radialGradient(listOf(accentColor.copy(glowAlpha * 0.52f), accentColor.copy(0.06f))))
            .border(
                1.5.dp,
                Brush.linearGradient(listOf(accentColor.copy(0.62f), accentColor.copy(0.16f))),
                RoundedCornerShape(20.dp)
            )
            .padding(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector        = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint               = accentColor,
                modifier           = Modifier.size(34.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text       = "$petName is Fully Grown",
                fontSize   = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                color      = Color(0xFF2C3224),
                textAlign  = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text       = "A mythic spirit has awakened.\nThis journey is complete.",
                fontSize   = 13.sp,
                color      = Color(0xFF7A8370),
                textAlign  = TextAlign.Center,
                lineHeight = 20.sp
            )
        }
    }
}
