package com.gxdevs.aethra.ui.chronicles

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gxdevs.aethra.Relic
import com.gxdevs.aethra.ui.theme.MyApplicationTheme

private val mainContainerBackground = Color(0xFFF4F1EA)
private val borderColor = Color(0xFFE0DCD1)
private val cardBackground = Color(0xFFEAE7DF)
private val textPrimary = Color(0xFF2E332A)
private val textSecondary = Color(0xFF828779)
private val primaryAccent = Color(0xFF606F49)
private val accentBackground = Color(0xFFD9DFCD)
private val redAccent = Color(0xFFC75D4E)
private val yellowAccent = Color(0xFFD9A05B)
private val darkOverlay = Color(0xFF2D3229)

@Composable
fun ChronicleScreen(viewModel: ChronicleViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var echoState by remember { mutableStateOf("LOADING") } // LOADING, WAITING, UNVEILED, EMPTY
    var showUnsealedOverlay by remember { mutableStateOf(false) }
    var selectedRelic by remember { mutableStateOf<Relic?>(null) }
    val scrollState = rememberScrollState()

    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsRepo = remember { com.gxdevs.aethra.data.SettingsRepository(context) }
    val relicAlerts by settingsRepo.relicAlerts.collectAsState(initial = true)
    val pastPrompts by settingsRepo.pastPrompts.collectAsState(initial = false)

    // Once data loads, set echo state
    LaunchedEffect(uiState.isLoading, uiState.echoEntry) {
        if (!uiState.isLoading) {
            echoState = if (uiState.echoEntry != null) "WAITING" else "EMPTY"
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(mainContainerBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                .verticalScroll(scrollState)
                .padding(bottom = 120.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Spacer(modifier = Modifier.height(24.dp))
                Text("Chronicles.", fontSize = 40.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = textPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Echoes and artifacts from your past self.", fontSize = 14.sp, color = textSecondary, fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.History, contentDescription = null, tint = redAccent, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("THE ECHO", color = redAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                }
                Spacer(modifier = Modifier.height(16.dp))

                if (pastPrompts) {
                    AnimatedContent(
                        targetState = echoState,
                        label = "echo_state",
                        transitionSpec = {
                            (fadeIn(tween(400)) + scaleIn(tween(400), initialScale = 0.92f))
                                .togetherWith(fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 1.04f))
                        }
                    ) { state ->
                        when (state) {
                            "LOADING" -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = primaryAccent, modifier = Modifier.size(28.dp))
                            }
                            "WAITING" -> EchoWaitingCard(
                                onUnveil = { echoState = "UNVEILED" },
                                onDismiss = { echoState = "EMPTY" }
                            )
                            "UNVEILED" -> EchoUnveiledCard(
                                entry = uiState.echoEntry,
                                onClose = { echoState = "WAITING" }
                            )
                            else -> EchoEmptyCard()
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .border(1.dp, borderColor, RoundedCornerShape(24.dp))
                            .padding(vertical = 32.dp, horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Past memory prompts are disabled in Settings.",
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            color = textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = textSecondary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("THE RELIQUARY", color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    }
                    Text("SEAL NEW >", color = textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                        modifier = Modifier.clickable { /* navigate to journals to pick one */ })
                }
                Spacer(modifier = Modifier.height(16.dp))

                if (!relicAlerts) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(cardBackground)
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.Lock, null, tint = textSecondary.copy(0.4f), modifier = Modifier.size(32.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Reliquary is disabled.", color = textSecondary, fontSize = 14.sp, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(4.dp))
                            Text("Enable Relic Alerts in Settings to view sealed and surfaced relics.", color = textSecondary.copy(0.6f), fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
                        }
                    }
                } else if (uiState.isLoading) {
                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = primaryAccent, modifier = Modifier.size(28.dp))
                    }
                } else if (uiState.surfacedRelics.isEmpty() && uiState.lockedRelics.isEmpty()) {
                    // No relics yet — empty state
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(cardBackground)
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.LockOpen, null, tint = textSecondary.copy(0.4f), modifier = Modifier.size(32.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No relics sealed yet.", color = textSecondary, fontSize = 14.sp, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(4.dp))
                            Text("Open a journal entry and tap\n\"Seal as Relic\" to begin.", color = textSecondary.copy(0.6f), fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
                        }
                    }
                } else {
                    // 2-column staggered grid
                    val allRelics = uiState.surfacedRelics + uiState.lockedRelics
                    val leftColumn = allRelics.filterIndexed { i, _ -> i % 2 == 0 }
                    val rightColumn = allRelics.filterIndexed { i, _ -> i % 2 == 1 }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            leftColumn.forEach { relic ->
                                val isSurfaced = uiState.surfacedRelics.any { it.id == relic.id }
                                if (isSurfaced) {
                                    RelicSurfacedCard(onClick = { selectedRelic = relic; showUnsealedOverlay = true })
                                } else {
                                    RelicSealedCard(
                                        days = viewModel.daysRemaining(relic),
                                        date = viewModel.relicUnsealDateStr(relic)
                                    )
                                }
                            }
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            rightColumn.forEach { relic ->
                                val isSurfaced = uiState.surfacedRelics.any { it.id == relic.id }
                                if (isSurfaced) {
                                    RelicSurfacedCard(onClick = { selectedRelic = relic; showUnsealedOverlay = true })
                                } else {
                                    RelicSealedCard(
                                        days = viewModel.daysRemaining(relic),
                                        date = viewModel.relicUnsealDateStr(relic)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showUnsealedOverlay,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
        ) {
            UnsealedRelicOverlay(relic = selectedRelic, onClose = { showUnsealedOverlay = false })
        }
    }
}

@Composable
fun EchoWaitingCard(onUnveil: () -> Unit, onDismiss: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "unveil_pulse")
    val btnScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "btn_scale"
    )
    val eyeGlow by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "eye_glow"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(cardBackground)
            .padding(24.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center
            ) {
                // Subtle pulsing glow behind eye
                Box(
                    modifier = Modifier
                        .requiredSize(80.dp)
                        .blur(20.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(redAccent.copy(alpha = eyeGlow * 0.4f), Color.Transparent)
                            ),
                            CircleShape
                        )
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(borderColor.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.VisibilityOff, contentDescription = null, tint = textSecondary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("An echo is waiting.", fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = textPrimary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Something from this exact day last year is here.\nDo you wish to hear it?",
                fontSize = 13.sp,
                color = textSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NOT TODAY",
                    color = textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.clickable { onDismiss() }.padding(8.dp)
                )
                Box(
                    modifier = Modifier
                        .scale(btnScale)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(listOf(textPrimary, darkOverlay))
                        )
                        .clickable { onUnveil() }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Visibility, contentDescription = null, tint = mainContainerBackground, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("UNVEIL", color = mainContainerBackground, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun EchoUnveiledCard(entry: com.gxdevs.aethra.JournalEntry?, onClose: () -> Unit) {
    val dateSdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.US)
    val dateStr = entry?.let { dateSdf.format(java.util.Date(it.timestamp)).uppercase() } ?: "A YEAR AGO"
    val content = entry?.content?.take(280)?.let {
        // Show just first meaningful paragraph
        it.substringBefore("\n\n").ifBlank { it }
    } ?: "No content found."

    // Parse dominant mood for display
    val moodStr = entry?.emotions?.let { emotionsJson ->
        try {
            val ems = com.google.gson.Gson().fromJson(emotionsJson, Array<com.gxdevs.aethra.ui.journal.Emotion>::class.java)
            val counts = mutableMapOf<String, Int>()
            ems.forEach { em ->
                val mood = com.gxdevs.aethra.MoodConstants.emotionToMood(em.label)
                counts[mood] = (counts[mood] ?: 0) + 1
            }
            counts.maxByOrNull { it.value }?.key
        } catch (_: Exception) { null }
    }
    val moodColor = moodStr?.let { com.gxdevs.aethra.MoodConstants.moodColor(it) } ?: yellowAccent

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(cardBackground)
            .border(1.dp, borderColor, RoundedCornerShape(24.dp))
            .padding(24.dp)
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(dateStr, color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Close",
                    tint = textSecondary,
                    modifier = Modifier.size(20.dp).clickable { onClose() }
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "\"$content\"",
                fontSize = 18.sp,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                color = textPrimary,
                lineHeight = 28.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            if (moodStr != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(moodColor))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("FELT $moodStr".uppercase(), color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        }
    }
}


@Composable
fun EchoEmptyCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, borderColor, RoundedCornerShape(24.dp))
            .padding(vertical = 32.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "The past rests quietly for today.",
            fontSize = 16.sp,
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            color = textSecondary
        )
    }
}

@Composable
fun RelicSurfacedCard(onClick: () -> Unit) {
    // Ambient pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "relic_pulse")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow_scale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f, targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow_alpha"
    )
    val sparkleRotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "sparkle_rot"
    )
    val iconFloat by infiniteTransition.animateFloat(
        initialValue = -3f, targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "icon_float"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.8f)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(cardBackground, yellowAccent.copy(alpha = 0.08f))
                )
            )
            .border(1.dp, yellowAccent.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
            .clickable { onClick() }
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                // Outer soft ambient glow — requiredSize overflows clip
                Box(
                    modifier = Modifier
                        .requiredSize(200.dp)
                        .scale(glowScale)
                        .blur(32.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    yellowAccent.copy(alpha = glowAlpha),
                                    yellowAccent.copy(alpha = glowAlpha * 0.3f),
                                    Color.Transparent
                                )
                            ),
                            CircleShape
                        )
                )
                // Inner tighter glow ring
                Box(
                    modifier = Modifier
                        .requiredSize(100.dp)
                        .blur(16.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(yellowAccent.copy(alpha = glowAlpha * 0.9f), Color.Transparent)
                            ),
                            CircleShape
                        )
                )
                // Floating icon
                Icon(
                    Icons.Rounded.LockOpen,
                    contentDescription = null,
                    tint = yellowAccent,
                    modifier = Modifier
                        .size(36.dp)
                        .graphicsLayer { translationY = iconFloat }
                )
                // Orbiting sparkle
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = yellowAccent.copy(alpha = 0.8f),
                    modifier = Modifier
                        .size(14.dp)
                        .graphicsLayer {
                            rotationZ = sparkleRotation
                            translationX = 28.dp.toPx()
                            translationY = -28.dp.toPx()
                        }
                )
                Icon(
                    Icons.Rounded.StarOutline,
                    contentDescription = null,
                    tint = yellowAccent.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(10.dp)
                        .graphicsLayer {
                            rotationZ = -sparkleRotation * 0.7f
                            translationX = -20.dp.toPx()
                            translationY = 24.dp.toPx()
                        }
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "A Relic has\nsurfaced.",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    color = textPrimary,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "TAP TO OPEN",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = yellowAccent,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
fun RelicSealedCard(days: String, date: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "sealed_pulse")
    val sealedGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.0f, targetValue = 0.18f,
        animationSpec = infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sealed_glow"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.8f)
            .clip(RoundedCornerShape(24.dp))
            .background(cardBackground.copy(alpha = 0.5f))
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                // Subtle sealed glow
                Box(
                    modifier = Modifier
                        .requiredSize(120.dp)
                        .blur(24.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(textSecondary.copy(alpha = sealedGlowAlpha), Color.Transparent)
                            ),
                            CircleShape
                        )
                )
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(borderColor.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = textSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Schedule, contentDescription = null, tint = textPrimary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(days, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("SEALED: $date", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = textSecondary, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
fun UnsealedRelicOverlay(relic: Relic?, onClose: () -> Unit) {
    val scrollState = rememberScrollState()

    // Date & Content
    val dateSdf = remember { java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.US) }
    val dateStr = relic?.let { "FROM " + dateSdf.format(java.util.Date(it.sealedAtTimestamp)).uppercase() } ?: "UNKNOWN DATE"
    val contentStr = relic?.contentSnapshot?.ifBlank { null } ?: "No text captured."

    // Phase: 0=lid rising, 1=particles, 2=content revealed
    var phase by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300)
        phase = 1
        kotlinx.coroutines.delay(600)
        phase = 2
    }

    // Lid animation (box opens upward)
    val lidOffsetY by animateFloatAsState(
        targetValue = if (phase >= 1) -180f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "lid_offset"
    )
    val lidRotationX by animateFloatAsState(
        targetValue = if (phase >= 1) -60f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "lid_rot"
    )
    val lidAlpha by animateFloatAsState(
        targetValue = if (phase >= 1) 0f else 1f,
        animationSpec = tween(500),
        label = "lid_alpha"
    )

    // Content reveal
    val contentAlpha by animateFloatAsState(
        targetValue = if (phase >= 2) 1f else 0f,
        animationSpec = tween(600),
        label = "content_alpha"
    )
    val contentOffsetY by animateFloatAsState(
        targetValue = if (phase >= 2) 0f else 60f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "content_slide"
    )

    // Particle glow burst
    val infiniteTransition = rememberInfiniteTransition(label = "relic_open")
    val particleGlow by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "particle_glow"
    )
    val particleScale by infiniteTransition.animateFloat(
        initialValue = 0.9f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "particle_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF1A1E16), darkOverlay, Color(0xFF1A1E16)))
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                .verticalScroll(scrollState)
                .padding(24.dp)
                .padding(bottom = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = mainContainerBackground, modifier = Modifier.size(20.dp))
                }
                Text("RELIC UNSEALED", color = mainContainerBackground.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Spacer(modifier = Modifier.width(40.dp))
            }

            Spacer(modifier = Modifier.height(40.dp))

            // --- BOX OPENING ANIMATION ---
            Box(
                modifier = Modifier.size(120.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                // Ambient glow burst (always visible when open)
                if (phase >= 1) {
                    Box(
                        modifier = Modifier
                            .requiredSize(240.dp)
                            .scale(particleScale)
                            .blur(40.dp)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        yellowAccent.copy(alpha = particleGlow * 0.7f),
                                        yellowAccent.copy(alpha = 0.1f),
                                        Color.Transparent
                                    )
                                ),
                                CircleShape
                            )
                    )
                }

                // Box base
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .width(90.dp)
                        .height(55.dp)
                        .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp, topStart = 8.dp, topEnd = 8.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFFB8860B), Color(0xFF8B6914))
                            )
                        )
                        .border(1.dp, yellowAccent.copy(0.6f),
                            RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp, topStart = 8.dp, topEnd = 8.dp))
                )

                // Box lid (animates upward)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .width(96.dp)
                        .height(24.dp)
                        .graphicsLayer {
                            translationY = lidOffsetY
                            rotationX = lidRotationX
                            alpha = lidAlpha.coerceAtLeast(0.1f)
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                        }
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFFDAA520), Color(0xFFB8860B))
                            )
                        )
                        .border(1.dp, yellowAccent.copy(0.8f),
                            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                )

                // Inner glow from box
                if (phase >= 1) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .width(80.dp)
                            .height(40.dp)
                            .blur(16.dp)
                            .background(
                                Brush.radialGradient(
                                    listOf(yellowAccent.copy(alpha = particleGlow), Color.Transparent)
                                ),
                                RoundedCornerShape(50)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // --- REVEALED CONTENT ---
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        alpha = contentAlpha
                        translationY = contentOffsetY
                    }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .border(1.dp, yellowAccent.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(dateStr, color = yellowAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = "\"$contentStr\"",
                        fontSize = 22.sp,
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        color = mainContainerBackground,
                        textAlign = TextAlign.Center,
                        lineHeight = 34.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(48.dp))
                    Box(modifier = Modifier.width(1.dp).height(48.dp).background(mainContainerBackground.copy(alpha = 0.2f)))
                    Spacer(modifier = Modifier.height(48.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(mainContainerBackground)
                            .padding(32.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier.size(40.dp).clip(CircleShape).background(accentBackground),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Eco, contentDescription = null, tint = primaryAccent, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                            Text("Who were you\nthen?", fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = textPrimary, textAlign = TextAlign.Center, lineHeight = 36.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Reflect on the person who wrote those words. How have you grown? What has changed?",
                                fontSize = 14.sp, color = textSecondary, textAlign = TextAlign.Center, lineHeight = 22.sp
                            )
                            Spacer(modifier = Modifier.height(40.dp))
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(borderColor))
                            Spacer(modifier = Modifier.height(20.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("LINKS TO ORIGINAL", color = textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                Row(
                                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(cardBackground).clickable { }.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.Check, contentDescription = null, tint = textSecondary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("SEAL CONNECTION", color = textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                }
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
fun ChronicleScreenPreview() {
    MyApplicationTheme {
        ChronicleScreen()
    }
}

