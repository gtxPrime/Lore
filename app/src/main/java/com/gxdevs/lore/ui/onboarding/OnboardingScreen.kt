package com.gxdevs.lore.ui.onboarding

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gxdevs.lore.data.SettingsRepository
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin

// ── Lore Sanctuary Palette — matched exactly to HomeScreen.kt ────────────────
private val BgPage       = Color(0xFFEBE8E0)  // appBackground
private val BgCard       = Color(0xFFEAE7DF)  // cardBackground
private val Border       = Color(0xFFE0DCD1)  // borderColor
private val TextPri      = Color(0xFF2E332A)  // textPrimary
private val TextSec      = Color(0xFF828779)  // textSecondary
private val GreenDeep    = Color(0xFF4A5638)  // darkAccent
private val GreenPrimary = Color(0xFF606F49)  // primaryAccent
private val GreenLight   = Color(0xFF7A8C62)  // midtone
private val GoldAccent   = Color(0xFFC49A1B)
private val GoldWarm     = Color(0xFFD4AF37)
private val AIAccent     = Color(0xFF5B6EA8)  // deep periwinkle — on-device intelligence

// Smooth luxury easing — Apple-style spring feel
private val LuxuryEasing     = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
private val SinusoidalEasing = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val context      = LocalContext.current
    val scope        = rememberCoroutineScope()
    val settingsRepo = remember { SettingsRepository(context) }
    val pagerState   = rememberPagerState(pageCount = { 5 })

    fun finishOnboarding() {
        scope.launch {
            settingsRepo.setHasCompletedOnboarding(true)
            onComplete()
        }
    }

    // Dynamic ambient glow per page
    val targetGlowColor = when (pagerState.currentPage) {
        0    -> GreenPrimary
        1    -> GoldAccent
        2    -> GoldWarm
        3    -> GreenDeep
        else -> AIAccent      // on-device AI page
    }
    val ambientGlowColor by animateColorAsState(
        targetValue   = targetGlowColor,
        animationSpec = tween(900, easing = LuxuryEasing),
        label         = "ambient_glow"
    )

    // ── Global infinite animations ─────────────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "global")
    val breatheScale by infiniteTransition.animateFloat(
        initialValue  = 0.82f,
        targetValue   = 1.18f,
        animationSpec = infiniteRepeatable(tween(4200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "breathe"
    )
    val floatY by infiniteTransition.animateFloat(
        initialValue  = -9f,
        targetValue   = 9f,
        animationSpec = infiniteRepeatable(tween(2800, easing = SinusoidalEasing), RepeatMode.Reverse),
        label         = "float"
    )
    val rotateAngle by infiniteTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = 360f,
        animationSpec = infiniteRepeatable(tween(22000, easing = LinearEasing), RepeatMode.Restart),
        label         = "rotate"
    )
    val rotateReverse by infiniteTransition.animateFloat(
        initialValue  = 360f,
        targetValue   = 0f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Restart),
        label         = "rotate_rev"
    )
    val particlePhase by infiniteTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = (2f * Math.PI.toFloat()),
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label         = "particle"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPage)
    ) {
        // ── Liquid ambient radial glow (dual bloom) ────────────────────────────
        Canvas(modifier = Modifier.fillMaxSize().blur(110.dp)) {
            val cx = size.width / 2f
            val cy = size.height * 0.38f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ambientGlowColor.copy(alpha = 0.28f),
                        ambientGlowColor.copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = size.width * 0.90f * breatheScale
                ),
                radius = size.width * 0.90f * breatheScale,
                center = Offset(cx, cy)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ambientGlowColor.copy(alpha = 0.12f), Color.Transparent),
                    center = Offset(cx * 0.4f, cy * 1.5f),
                    radius = size.width * 0.55f
                ),
                radius = size.width * 0.55f,
                center = Offset(cx * 0.4f, cy * 1.5f)
            )
        }

        // ── Floating ambient particles ─────────────────────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val particles = listOf(
                Triple(0.14f, 0.20f, 3.0f), Triple(0.83f, 0.17f, 2.2f),
                Triple(0.73f, 0.70f, 2.6f), Triple(0.24f, 0.77f, 2.0f),
                Triple(0.56f, 0.10f, 1.6f), Triple(0.91f, 0.44f, 2.4f),
                Triple(0.07f, 0.58f, 1.4f),
            )
            particles.forEachIndexed { i, (rx, ry, radius) ->
                val phase = particlePhase + i * 1.05f
                val xOff  = sin(phase.toDouble()).toFloat() * 11f
                val yOff  = cos(phase.toDouble() * 0.7).toFloat() * 7f
                drawCircle(
                    color  = ambientGlowColor.copy(alpha = 0.08f + i * 0.010f),
                    radius = radius.dp.toPx(),
                    center = Offset(size.width * rx + xOff, size.height * ry + yOff)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 20.dp)
        ) {
            // ── Top Bar ────────────────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth().height(52.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Surface(
                    shape  = RoundedCornerShape(12.dp),
                    color  = GreenDeep.copy(alpha = 0.08f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GreenDeep.copy(alpha = 0.18f))
                ) {
                    Row(
                        modifier          = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = GreenDeep, modifier = Modifier.size(10.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text          = "LORE",
                            fontSize      = 11.sp,
                            fontWeight    = FontWeight.ExtraBold,
                            color         = GreenDeep,
                            letterSpacing = 2.sp
                        )
                    }
                }

                AnimatedVisibility(
                    visible = pagerState.currentPage < 4,
                    enter   = fadeIn(tween(300)),
                    exit    = fadeOut(tween(200))
                ) {
                    Surface(
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication        = null
                        ) { finishOnboarding() },
                        color    = Color.White.copy(alpha = 0.68f),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, Border),
                        shape    = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text       = "Skip",
                            fontSize   = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color      = TextSec,
                            modifier   = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            // ── Pager ─────────────────────────────────────────────────────────
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                HorizontalPager(
                    state    = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val rawOffset  = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                    val pageOffset = rawOffset.absoluteValue
                    val scale      = (1f - pageOffset * 0.13f).coerceIn(0.87f, 1f)
                    val alpha      = (1f - pageOffset * 0.55f).coerceIn(0f, 1f)

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX       = scale
                                scaleY       = scale
                                this.alpha   = alpha
                                rotationY    = rawOffset * 5f
                                translationY = floatY
                                translationX = rawOffset * -28f
                            }
                    ) {
                        val isCurrentPage = pagerState.currentPage == page
                        when (page) {
                            0 -> OnboardingPageWelcome(rotateAngle, rotateReverse, particlePhase, isCurrentPage)
                            1 -> OnboardingPageCompanions(isCurrentPage)
                            2 -> OnboardingPageEvolution(rotateAngle, isCurrentPage)
                            3 -> OnboardingPageSecurity(isCurrentPage)
                            4 -> OnboardingPageAI(rotateAngle, isCurrentPage)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Footer: dots + CTA ─────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    repeat(5) { idx ->
                        val isCurrent = pagerState.currentPage == idx
                        val dotWidth by animateDpAsState(
                            targetValue   = if (isCurrent) 28.dp else 6.dp,
                            animationSpec = spring(dampingRatio = 0.55f, stiffness = 350f),
                            label         = "dot_$idx"
                        )
                        val dotColor by animateColorAsState(
                            targetValue   = if (isCurrent) GreenPrimary else Border,
                            animationSpec = tween(350, easing = LuxuryEasing),
                            label         = "dot_c_$idx"
                        )
                        Box(
                            modifier = Modifier
                                .height(6.dp).width(dotWidth)
                                .clip(CircleShape)
                                .background(dotColor.copy(alpha = if (isCurrent) 1f else 0.6f))
                        )
                    }
                }

                val isLast   = pagerState.currentPage == 4
                val btnScale by animateFloatAsState(
                    targetValue   = if (isLast) 1.06f else 1f,
                    animationSpec = spring(dampingRatio = 0.5f, stiffness = 280f),
                    label         = "btn_s"
                )
                val btnColor by animateColorAsState(
                    targetValue   = if (isLast) GreenDeep else GreenPrimary,
                    animationSpec = tween(500, easing = LuxuryEasing),
                    label         = "btn_c"
                )

                Button(
                    onClick = {
                        if (!isLast) {
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    pagerState.currentPage + 1,
                                    animationSpec = spring(dampingRatio = 0.75f, stiffness = 380f)
                                )
                            }
                        } else {
                            finishOnboarding()
                        }
                    },
                    modifier       = Modifier.graphicsLayer { scaleX = btnScale; scaleY = btnScale },
                    colors         = ButtonDefaults.buttonColors(containerColor = btnColor),
                    shape          = RoundedCornerShape(20.dp),
                    elevation      = ButtonDefaults.buttonElevation(defaultElevation = if (isLast) 10.dp else 6.dp),
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedContent(
                            targetState    = isLast,
                            transitionSpec = {
                                (fadeIn(tween(280)) + slideInVertically { it / 3 })
                                    .togetherWith(fadeOut(tween(180)) + slideOutVertically { -it / 3 })
                            },
                            label          = "btn_label"
                        ) { last ->
                            Text(
                                text          = if (last) "Begin Your Journey" else "Continue",
                                fontSize      = 14.sp,
                                fontWeight    = FontWeight.ExtraBold,
                                color         = Color.White,
                                letterSpacing = 0.3.sp
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector        = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint               = Color.White,
                            modifier           = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// PAGE 1 — Welcome
// ════════════════════════════════════════════════════════════════════
@Composable
private fun OnboardingPageWelcome(
    rotateAngle  : Float,
    rotateReverse: Float,
    particlePhase: Float,
    isCurrentPage: Boolean
) {
    val infiniteT   = rememberInfiniteTransition(label = "welcome")
    val pulseAlpha  by infiniteT.animateFloat(
        initialValue  = 0.28f, targetValue = 0.90f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "pulse"
    )
    val innerGlow by infiniteT.animateFloat(
        initialValue  = 0.50f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "inner_glow"
    )

    // Reset animation every time this page becomes active
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) {
            visible = false
            kotlinx.coroutines.delay(60)
            visible = true
        }
    }

    Column(
        modifier            = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Badge
        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn(tween(500, easing = LuxuryEasing)) +
                      slideInVertically(tween(500, easing = LuxuryEasing)) { it / 2 },
            exit    = fadeOut(tween(200))
        ) {
            Surface(
                shape  = RoundedCornerShape(22.dp),
                color  = GreenPrimary.copy(alpha = 0.10f),
                border = androidx.compose.foundation.BorderStroke(1.dp, GreenPrimary.copy(alpha = 0.26f))
            ) {
                Row(
                    modifier          = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Lock, null, tint = GreenPrimary, modifier = Modifier.size(9.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text          = "PRIVATE  ENCRYPTED  ALIVE",
                        fontSize      = 9.sp,
                        fontWeight    = FontWeight.ExtraBold,
                        color         = GreenPrimary,
                        letterSpacing = 1.6.sp
                    )
                    Spacer(Modifier.width(5.dp))
                    Icon(Icons.Rounded.Lock, null, tint = GreenPrimary, modifier = Modifier.size(9.dp))
                }
            }
        }

        Spacer(Modifier.height(26.dp))

        // Hero orb — dual orbital rings
        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn(tween(700, delayMillis = 100, easing = LuxuryEasing)) +
                      scaleIn(tween(700, delayMillis = 100, easing = LuxuryEasing), initialScale = 0.55f),
            exit    = fadeOut(tween(200))
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
                // Outer counter-rotating halo
                Canvas(modifier = Modifier.size(200.dp).graphicsLayer { rotationZ = rotateReverse }) {
                    drawCircle(
                        brush  = Brush.sweepGradient(
                            listOf(GreenLight.copy(0.30f), GoldAccent.copy(0.16f), Color.Transparent, GreenLight.copy(0.30f))
                        ),
                        style  = Stroke(width = 1.dp.toPx()),
                        radius = size.minDimension / 2f - 1.dp.toPx()
                    )
                }
                // Inner forward-rotating ring with sparkle dots
                Canvas(modifier = Modifier.size(168.dp).graphicsLayer { rotationZ = rotateAngle }) {
                    drawCircle(
                        brush  = Brush.sweepGradient(
                            listOf(GreenPrimary.copy(0.78f), GoldAccent.copy(0.52f), Color.Transparent, GreenPrimary.copy(0.78f))
                        ),
                        style  = Stroke(width = 2.2f.dp.toPx()),
                        radius = size.minDimension / 2f - 1.dp.toPx()
                    )
                    val r = size.minDimension / 2f - 1.dp.toPx()
                    listOf(0.0, 90.0, 180.0, 270.0).forEach { deg ->
                        val rad = Math.toRadians(deg)
                        drawCircle(
                            color  = GoldAccent.copy(0.86f),
                            radius = 3.dp.toPx(),
                            center = Offset(center.x + r * cos(rad).toFloat(), center.y + r * sin(rad).toFloat())
                        )
                    }
                }
                // Orbital particle trail
                Canvas(modifier = Modifier.size(200.dp)) {
                    val r = size.minDimension / 2f - 20.dp.toPx()
                    (0 until 5).forEach { i ->
                        val phase = particlePhase + i * (2f * Math.PI.toFloat() / 5f)
                        drawCircle(
                            color  = GreenPrimary.copy(0.16f),
                            radius = 2.5f.dp.toPx(),
                            center = Offset(center.x + r * cos(phase.toDouble()).toFloat(), center.y + r * sin(phase.toDouble()).toFloat())
                        )
                    }
                }
                // Core orb — using Surface for correct shadow rendering (no shadow+clip chain)
                Surface(
                    modifier        = Modifier.size(130.dp),
                    shape           = CircleShape,
                    color           = Color.White.copy(0.94f),
                    border          = androidx.compose.foundation.BorderStroke(1.8.dp, GreenPrimary.copy(pulseAlpha)),
                    shadowElevation = (innerGlow * 14f).dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        // Inner radial tint
                        Box(
                            modifier = Modifier.fillMaxSize()
                                .background(Brush.radialGradient(listOf(Color.Transparent, GreenPrimary.copy(0.08f))))
                        )
                        Icon(Icons.Rounded.AutoAwesome, null, tint = GreenPrimary, modifier = Modifier.size(56.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        // Title
        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn(tween(600, delayMillis = 200, easing = LuxuryEasing)) +
                      slideInVertically(tween(600, delayMillis = 200, easing = LuxuryEasing)) { it / 3 },
            exit    = fadeOut(tween(150))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text       = "Welcome to Lore",
                    fontSize   = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = TextPri,
                    fontFamily = FontFamily.Serif,
                    textAlign  = TextAlign.Center,
                    lineHeight = 40.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text          = "Your Living Inner Sanctuary",
                    fontSize      = 15.sp,
                    fontWeight    = FontWeight.Bold,
                    color         = GreenPrimary,
                    fontFamily    = FontFamily.Serif,
                    textAlign     = TextAlign.Center,
                    letterSpacing = 0.3.sp
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Description
        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn(tween(700, delayMillis = 340, easing = LuxuryEasing)) +
                      slideInVertically(tween(700, delayMillis = 340, easing = LuxuryEasing)) { it / 3 },
            exit    = fadeOut(tween(150))
        ) {
            Text(
                text       = "Lore is a private journal where your raw emotions don't just sit on a page. They breathe, grow, and hatch into living companions. Write freely. Evolve together.",
                fontSize   = 13.5.sp,
                color      = TextSec,
                textAlign  = TextAlign.Center,
                lineHeight = 22.sp,
                modifier   = Modifier.padding(horizontal = 8.dp)
            )
        }

        Spacer(Modifier.height(20.dp))

        // Three pillars — icons only, no emojis
        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn(tween(700, delayMillis = 480, easing = LuxuryEasing)) +
                      slideInVertically(tween(700, delayMillis = 480, easing = LuxuryEasing)) { it / 3 },
            exit    = fadeOut(tween(150))
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                PillarChip(Icons.Rounded.Edit,     "Express", "Write freely",    GreenPrimary, Modifier.weight(1f))
                PillarChip(Icons.Rounded.Park,     "Evolve",  "Grow companions", GoldAccent,   Modifier.weight(1f))
                PillarChip(Icons.Rounded.Security, "Private", "100% encrypted",  GreenDeep,    Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PillarChip(
    icon    : ImageVector,
    title   : String,
    sub     : String,
    tintCol : Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier        = modifier,
        shape           = RoundedCornerShape(14.dp),
        color           = BgCard,
        border          = androidx.compose.foundation.BorderStroke(1.dp, Border),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier            = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier         = Modifier.size(32.dp).clip(CircleShape).background(tintCol.copy(0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = tintCol, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = TextPri, textAlign = TextAlign.Center)
            Text(sub,   fontSize = 9.sp,  color = TextSec, textAlign = TextAlign.Center, lineHeight = 13.sp)
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// PAGE 2 — Companions
// ════════════════════════════════════════════════════════════════════
@Composable
private fun OnboardingPageCompanions(isCurrentPage: Boolean) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) {
            visible = false
            kotlinx.coroutines.delay(60)
            visible = true
        }
    }

    Column(
        modifier            = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn(tween(450, easing = LuxuryEasing)) +
                      slideInVertically(tween(450, easing = LuxuryEasing)) { it / 2 },
            exit    = fadeOut(tween(150))
        ) {
            Surface(
                shape  = RoundedCornerShape(22.dp),
                color  = GoldAccent.copy(alpha = 0.10f),
                border = androidx.compose.foundation.BorderStroke(1.dp, GoldAccent.copy(alpha = 0.26f))
            ) {
                Row(
                    modifier          = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Mood, null, tint = GoldAccent, modifier = Modifier.size(10.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text          = "EMOTIONAL ARCHETYPES",
                        fontSize      = 9.sp,
                        fontWeight    = FontWeight.ExtraBold,
                        color         = GoldAccent,
                        letterSpacing = 1.8.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn(tween(550, delayMillis = 80, easing = LuxuryEasing)) +
                      slideInVertically(tween(550, delayMillis = 80, easing = LuxuryEasing)) { it / 3 },
            exit    = fadeOut(tween(150))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text       = "Your Feelings Hatch Guardians",
                    fontSize   = 25.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = TextPri,
                    fontFamily = FontFamily.Serif,
                    textAlign  = TextAlign.Center,
                    lineHeight = 31.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text       = "Each emotion you journal awakens a mythic companion egg. Six archetypes, each carrying the essence of a different inner world.",
                    fontSize   = 12.sp,
                    color      = TextSec,
                    textAlign  = TextAlign.Center,
                    lineHeight = 19.sp,
                    modifier   = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn(tween(650, delayMillis = 180, easing = LuxuryEasing)) +
                      slideInVertically(tween(650, delayMillis = 180, easing = LuxuryEasing)) { it / 2 },
            exit    = fadeOut(tween(150))
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompanionCard("Bright",  "Joy and Sunshine",   Icons.Rounded.WbSunny,   Color(0xFFB88E10), "Joyful entries",  Modifier.weight(1f))
                    CompanionCard("Calm",    "Peace and Stillness", Icons.Rounded.Spa,        GreenPrimary,     "Mindful writing", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompanionCard("Heavy",   "Resilience and Depth",Icons.Rounded.Shield,    Color(0xFF4A6B47), "Hard emotions",   Modifier.weight(1f))
                    CompanionCard("Tangled", "Confusion and Flow",  Icons.Rounded.Waves,      Color(0xFF3B82A6), "Uncertain days",  Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompanionCard("Dark",    "Night and Solitude",  Icons.Rounded.NightsStay, Color(0xFF4A4E69), "Quiet darkness",  Modifier.weight(1f))
                    CompanionCard("Blank",   "The Fresh Slate",     Icons.Rounded.Flare,      Color(0xFF6B705C), "Neutral space",   Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn(tween(500, delayMillis = 300, easing = LuxuryEasing)),
            exit    = fadeOut(tween(150))
        ) {
            Surface(
                shape  = RoundedCornerShape(14.dp),
                color  = GreenPrimary.copy(0.08f),
                border = androidx.compose.foundation.BorderStroke(1.dp, GreenPrimary.copy(0.20f))
            ) {
                Row(
                    modifier              = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Rounded.TouchApp, null, tint = GreenPrimary, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text       = "Long press on companion avatar anytime to enable Demo Mode",
                        fontSize   = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color      = GreenPrimary,
                        textAlign  = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun CompanionCard(
    title      : String,
    subtitle   : String,
    icon       : ImageVector,
    accentColor: Color,
    trigger    : String,
    modifier   : Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue   = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.48f, stiffness = 420f),
        label         = "cc_scale"
    )
    LaunchedEffect(pressed) { if (pressed) { kotlinx.coroutines.delay(110); pressed = false } }

    Surface(
        modifier        = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { pressed = true },
        shape           = RoundedCornerShape(16.dp),
        color           = BgCard,
        border          = androidx.compose.foundation.BorderStroke(1.dp, Border),
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier         = Modifier.size(32.dp).clip(CircleShape).background(accentColor.copy(0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = accentColor, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(title,    fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TextPri)
                    Text(subtitle, fontSize = 8.5.sp, color = TextSec, lineHeight = 11.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            Surface(shape = RoundedCornerShape(6.dp), color = accentColor.copy(0.08f)) {
                Text(trigger, fontSize = 8.sp, color = accentColor, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// PAGE 3 — Evolution
// ════════════════════════════════════════════════════════════════════
@Composable
private fun OnboardingPageEvolution(rotateAngle: Float, isCurrentPage: Boolean) {
    val infiniteT   = rememberInfiniteTransition(label = "evo")
    val goldPulse   by infiniteT.animateFloat(
        initialValue  = 0.92f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "gold_pulse"
    )
    val glowAlpha   by infiniteT.animateFloat(
        initialValue  = 0.35f, targetValue = 0.82f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "gold_alpha"
    )

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) {
            visible = false
            kotlinx.coroutines.delay(60)
            visible = true
        }
    }

    Column(
        modifier            = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn(tween(450, easing = LuxuryEasing)) +
                      slideInVertically(tween(450, easing = LuxuryEasing)) { it / 2 },
            exit    = fadeOut(tween(150))
        ) {
            Surface(
                shape  = RoundedCornerShape(22.dp),
                color  = GoldAccent.copy(0.10f),
                border = androidx.compose.foundation.BorderStroke(1.dp, GoldAccent.copy(0.26f))
            ) {
                Row(
                    modifier          = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Whatshot, null, tint = GoldAccent, modifier = Modifier.size(10.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text          = "5 MYTHIC EVOLUTION STAGES",
                        fontSize      = 9.sp,
                        fontWeight    = FontWeight.ExtraBold,
                        color         = GoldAccent,
                        letterSpacing = 1.8.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // Gold evolution orb with rotating ring
        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn(tween(650, delayMillis = 80, easing = LuxuryEasing)) +
                      scaleIn(tween(650, delayMillis = 80, easing = LuxuryEasing), 0.5f),
            exit    = fadeOut(tween(150))
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
                Canvas(modifier = Modifier.size(160.dp).graphicsLayer { rotationZ = rotateAngle }) {
                    drawCircle(
                        brush  = Brush.sweepGradient(listOf(GoldWarm.copy(0.58f), GoldAccent.copy(0.26f), Color.Transparent, GoldWarm.copy(0.58f))),
                        style  = Stroke(width = 2.dp.toPx()),
                        radius = size.minDimension / 2f - 1.dp.toPx()
                    )
                }
                Box(modifier = Modifier.size(130.dp).scale(goldPulse).clip(CircleShape).background(GoldAccent.copy(0.08f)))
                // Orb — Surface handles shadow correctly
                Surface(
                    modifier        = Modifier.size(102.dp),
                    shape           = CircleShape,
                    color           = Color.White.copy(0.94f),
                    border          = androidx.compose.foundation.BorderStroke(1.8.dp, GoldAccent.copy(glowAlpha)),
                    shadowElevation = (glowAlpha * 16f).dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.Transparent, GoldAccent.copy(0.08f)))))
                        Icon(Icons.Rounded.Pets, null, tint = GoldAccent, modifier = Modifier.size(46.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn(tween(600, delayMillis = 160, easing = LuxuryEasing)) +
                      slideInVertically(tween(600, delayMillis = 160, easing = LuxuryEasing)) { it / 3 },
            exit    = fadeOut(tween(150))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text       = "Write, Evolve, Ascend",
                    fontSize   = 27.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = TextPri,
                    fontFamily = FontFamily.Serif,
                    textAlign  = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text       = "Every word channels growth energy into your companion. Witness them evolve through five mythic stages as your journal deepens.",
                    fontSize   = 12.sp,
                    color      = TextSec,
                    textAlign  = TextAlign.Center,
                    lineHeight = 19.sp,
                    modifier   = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn(tween(650, delayMillis = 260, easing = LuxuryEasing)) +
                      slideInVertically(tween(650, delayMillis = 260, easing = LuxuryEasing)) { it / 2 },
            exit    = fadeOut(tween(150))
        ) {
            Surface(
                modifier        = Modifier.fillMaxWidth(),
                shape           = RoundedCornerShape(20.dp),
                color           = BgCard,
                border          = androidx.compose.foundation.BorderStroke(1.dp, Border),
                shadowElevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    EvoStep(1, "Hatch an Egg",      "Your first journal entry awakens a sleeping companion in their egg.",            GreenPrimary)
                    DividerLine()
                    EvoStep(2, "Nurture Daily",      "Each reflection grants XP. Emotions fuel their stage-by-stage transformation.", GoldAccent)
                    DividerLine()
                    EvoStep(3, "Reach Mythic Form", "30 entries unlock their final guardian form with a unique name and legend.",     GoldWarm)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn(tween(500, delayMillis = 360, easing = LuxuryEasing)),
            exit    = fadeOut(tween(150))
        ) {
            Surface(
                shape  = RoundedCornerShape(14.dp),
                color  = GoldAccent.copy(0.08f),
                border = androidx.compose.foundation.BorderStroke(1.dp, GoldAccent.copy(0.20f))
            ) {
                Row(
                    modifier              = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Rounded.TouchApp, null, tint = GoldAccent, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text       = "Tip: Long press avatar to toggle Demo Mode",
                        fontSize   = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color      = GoldAccent,
                        textAlign  = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun DividerLine() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Border.copy(alpha = 0.55f)))
}

@Composable
private fun EvoStep(step: Int, title: String, desc: String, color: Color) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            modifier = Modifier.size(32.dp),
            shape    = CircleShape,
            color    = color.copy(0.12f),
            border   = androidx.compose.foundation.BorderStroke(1.dp, color.copy(0.26f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("$step", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = color)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = TextPri)
            Spacer(Modifier.height(3.dp))
            Text(desc,  fontSize = 10.5.sp, color = TextSec, lineHeight = 16.sp)
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// PAGE 4 — Privacy
// ════════════════════════════════════════════════════════════════════
@Composable
private fun OnboardingPageSecurity(isCurrentPage: Boolean) {
    val infiniteT   = rememberInfiniteTransition(label = "security")
    val shieldScale by infiniteT.animateFloat(
        initialValue  = 0.94f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "shield_s"
    )
    val shieldGlow  by infiniteT.animateFloat(
        initialValue  = 0.35f, targetValue = 0.88f,
        animationSpec = infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "shield_g"
    )

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) {
            visible = false
            kotlinx.coroutines.delay(60)
            visible = true
        }
    }

    Column(
        modifier            = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn(tween(450, easing = LuxuryEasing)) +
                      slideInVertically(tween(450, easing = LuxuryEasing)) { it / 2 },
            exit    = fadeOut(tween(150))
        ) {
            Surface(
                shape  = RoundedCornerShape(22.dp),
                color  = GreenPrimary.copy(0.10f),
                border = androidx.compose.foundation.BorderStroke(1.dp, GreenPrimary.copy(0.26f))
            ) {
                Row(
                    modifier          = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.GppGood, null, tint = GreenPrimary, modifier = Modifier.size(10.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text          = "ZERO KNOWLEDGE  TOTAL CONTROL",
                        fontSize      = 9.sp,
                        fontWeight    = FontWeight.ExtraBold,
                        color         = GreenPrimary,
                        letterSpacing = 1.6.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Pulsing shield orb
        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn(tween(650, delayMillis = 80, easing = LuxuryEasing)) +
                      scaleIn(tween(650, delayMillis = 80, easing = LuxuryEasing), 0.5f),
            exit    = fadeOut(tween(150))
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
                Box(modifier = Modifier.size(138.dp).scale(shieldScale).clip(CircleShape).background(GreenPrimary.copy(0.06f)))
                Surface(
                    modifier        = Modifier.size(110.dp),
                    shape           = CircleShape,
                    color           = Color.White.copy(0.94f),
                    border          = androidx.compose.foundation.BorderStroke(1.8.dp, GreenPrimary.copy(shieldGlow * 0.78f)),
                    shadowElevation = (shieldGlow * 16f).dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.Transparent, GreenPrimary.copy(0.07f)))))
                        Icon(Icons.Rounded.Lock, null, tint = GreenPrimary, modifier = Modifier.size(48.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn(tween(600, delayMillis = 160, easing = LuxuryEasing)) +
                      slideInVertically(tween(600, delayMillis = 160, easing = LuxuryEasing)) { it / 3 },
            exit    = fadeOut(tween(150))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text       = "Total Privacy and Control",
                    fontSize   = 27.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = TextPri,
                    fontFamily = FontFamily.Serif,
                    textAlign  = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text       = "Your thoughts belong only to you. Lore is built with military-grade privacy. No cloud sync, no ads, no tracking. Ever.",
                    fontSize   = 12.sp,
                    color      = TextSec,
                    textAlign  = TextAlign.Center,
                    lineHeight = 19.sp,
                    modifier   = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn(tween(650, delayMillis = 260, easing = LuxuryEasing)) +
                      slideInVertically(tween(650, delayMillis = 260, easing = LuxuryEasing)) { it / 2 },
            exit    = fadeOut(tween(150))
        ) {
            Surface(
                modifier        = Modifier.fillMaxWidth(),
                shape           = RoundedCornerShape(20.dp),
                color           = BgCard,
                border          = androidx.compose.foundation.BorderStroke(1.dp, Border),
                shadowElevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    PrivacyFeature(Icons.Rounded.Fingerprint,   "App Lock and Biometrics", "Secure your sanctuary with PIN or Fingerprint.")
                    DividerLine()
                    PrivacyFeature(Icons.Rounded.VisibilityOff, "Decoy PIN Vault",         "Enter a decoy PIN to reveal a blank stealth journal to prying eyes.")
                    DividerLine()
                    PrivacyFeature(Icons.Rounded.Shield,        "Screenshot Blackout",     "Prevents all screen captures and hides content in the recent apps view.")
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Final promise badge
        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn(tween(500, delayMillis = 400, easing = LuxuryEasing)),
            exit    = fadeOut(tween(150))
        ) {
            Surface(
                shape  = RoundedCornerShape(12.dp),
                color  = GreenDeep.copy(0.07f),
                border = androidx.compose.foundation.BorderStroke(1.dp, GreenDeep.copy(0.16f))
            ) {
                Row(
                    modifier          = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Rounded.VerifiedUser, null, tint = GreenDeep, modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text          = "No cloud  No ads  No data collection  Ever",
                        fontSize      = 10.sp,
                        fontWeight    = FontWeight.Bold,
                        color         = GreenDeep,
                        textAlign     = TextAlign.Center,
                        letterSpacing = 0.4.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacyFeature(icon: ImageVector, title: String, desc: String) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            modifier = Modifier.size(34.dp),
            shape    = RoundedCornerShape(10.dp),
            color    = GreenPrimary.copy(0.10f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = GreenPrimary, modifier = Modifier.size(17.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = TextPri)
            Spacer(Modifier.height(2.dp))
            Text(desc,  fontSize = 10.5.sp, color = TextSec, lineHeight = 16.sp)
        }
    }
}
// ══════════════════════════════════════════════════════════════════
// PAGE 5 — On-Device AI: Your Companion Learns You
// ══════════════════════════════════════════════════════════════════
@Composable
private fun OnboardingPageAI(rotateAngle: Float, isCurrentPage: Boolean) {
    val infiniteT  = rememberInfiniteTransition(label = "ai")
    val brainPulse by infiniteT.animateFloat(
        initialValue  = 0.90f, targetValue = 1.10f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "brain_pulse"
    )
    val brainGlow  by infiniteT.animateFloat(
        initialValue  = 0.30f, targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "brain_glow"
    )
    val scanLine   by infiniteT.animateFloat(
        initialValue  = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Restart),
        label         = "scan_line"
    )

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) {
            visible = false
            kotlinx.coroutines.delay(60)
            visible = true
        }
    }

    Column(
        modifier            = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Badge
        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn(tween(450, easing = LuxuryEasing)) +
                      slideInVertically(tween(450, easing = LuxuryEasing)) { it / 2 },
            exit    = fadeOut(tween(150))
        ) {
            Surface(
                shape  = RoundedCornerShape(22.dp),
                color  = AIAccent.copy(0.10f),
                border = androidx.compose.foundation.BorderStroke(1.dp, AIAccent.copy(0.26f))
            ) {
                Row(
                    modifier          = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Psychology, null, tint = AIAccent, modifier = Modifier.size(10.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text          = "100% ON-DEVICE INTELLIGENCE",
                        fontSize      = 9.sp,
                        fontWeight    = FontWeight.ExtraBold,
                        color         = AIAccent,
                        letterSpacing = 1.6.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Hero: AI brain orb with rotating scan ring
        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn(tween(700, delayMillis = 80, easing = LuxuryEasing)) +
                      scaleIn(tween(700, delayMillis = 80, easing = LuxuryEasing), 0.5f),
            exit    = fadeOut(tween(150))
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(170.dp)) {
                // Outer scan ring
                Canvas(modifier = Modifier.size(170.dp).graphicsLayer { rotationZ = scanLine }) {
                    drawCircle(
                        brush  = Brush.sweepGradient(
                            listOf(AIAccent.copy(0.75f), GoldAccent.copy(0.30f), Color.Transparent, Color.Transparent, AIAccent.copy(0.75f))
                        ),
                        style  = Stroke(width = 2.dp.toPx()),
                        radius = size.minDimension / 2f - 1.dp.toPx()
                    )
                    // Scan tip sparkle dot
                    val r = size.minDimension / 2f - 1.dp.toPx()
                    drawCircle(
                        color  = GoldAccent.copy(0.90f),
                        radius = 4.dp.toPx(),
                        center = Offset(center.x + r, center.y)
                    )
                }
                // Slower counter-rotating halo
                Canvas(modifier = Modifier.size(145.dp).graphicsLayer { rotationZ = -rotateAngle * 0.4f }) {
                    drawCircle(
                        brush  = Brush.sweepGradient(
                            listOf(AIAccent.copy(0.20f), Color.Transparent, AIAccent.copy(0.20f))
                        ),
                        style  = Stroke(width = 1.dp.toPx()),
                        radius = size.minDimension / 2f - 1.dp.toPx()
                    )
                }
                // Pulse halo
                Box(modifier = Modifier.size(130.dp).scale(brainPulse).clip(CircleShape).background(AIAccent.copy(0.07f)))
                // Core orb — no box shadow, glow lives in the orb border
                Surface(
                    modifier = Modifier.size(108.dp),
                    shape    = CircleShape,
                    color    = Color.White.copy(0.94f),
                    border   = androidx.compose.foundation.BorderStroke(1.8.dp, AIAccent.copy(brainGlow * 0.80f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.Transparent, AIAccent.copy(0.10f)))))
                        Icon(Icons.Rounded.EmojiEmotions, null, tint = AIAccent, modifier = Modifier.size(52.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Title
        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn(tween(600, delayMillis = 180, easing = LuxuryEasing)) +
                      slideInVertically(tween(600, delayMillis = 180, easing = LuxuryEasing)) { it / 3 },
            exit    = fadeOut(tween(150))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text       = "Your Companion Learns You",
                    fontSize   = 27.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = TextPri,
                    fontFamily = FontFamily.Serif,
                    textAlign  = TextAlign.Center,
                    lineHeight = 33.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text       = "As you write, your companion silently studies your patterns. It reads your emotions, remembers your rhythms, and adapts to you. Not a generic AI. Your AI.",
                    fontSize   = 12.sp,
                    color      = TextSec,
                    textAlign  = TextAlign.Center,
                    lineHeight = 19.sp,
                    modifier   = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Feature cards
        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn(tween(650, delayMillis = 280, easing = LuxuryEasing)) +
                      slideInVertically(tween(650, delayMillis = 280, easing = LuxuryEasing)) { it / 2 },
            exit    = fadeOut(tween(150))
        ) {
            Surface(
                modifier        = Modifier.fillMaxWidth(),
                shape           = RoundedCornerShape(20.dp),
                color           = BgCard,
                border          = androidx.compose.foundation.BorderStroke(1.dp, Border),
                shadowElevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    AIFeature(
                        icon  = Icons.Rounded.FavoriteBorder,
                        color = Color(0xFFC05A5A),
                        title = "Reads Your Emotions",
                        desc  = "Every entry is analyzed locally for mood patterns. Your companion senses how you truly feel, even when you cannot name it."
                    )
                    DividerLine()
                    AIFeature(
                        icon  = Icons.AutoMirrored.Rounded.TrendingUp,
                        color = GoldAccent,
                        title = "Learns and Evolves With You",
                        desc  = "The more you write, the sharper the understanding. Dynamic stats, mood trends, and growth insights that are uniquely yours."
                    )
                    DividerLine()
                    AIFeature(
                        icon  = Icons.Rounded.AirplanemodeActive,
                        color = AIAccent,
                        title = "Works Fully Offline",
                        desc  = "Zero servers. Zero cloud. The entire AI runs on your device. Open Lore on a plane with no signal. It all works perfectly."
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Final note
        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn(tween(500, delayMillis = 420, easing = LuxuryEasing)),
            exit    = fadeOut(tween(150))
        ) {
            Surface(
                shape  = RoundedCornerShape(12.dp),
                color  = AIAccent.copy(0.07f),
                border = androidx.compose.foundation.BorderStroke(1.dp, AIAccent.copy(0.16f))
            ) {
                Row(
                    modifier              = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Rounded.PhonelinkLock, null, tint = AIAccent, modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text          = "Your data never leaves your device. Ever.",
                        fontSize      = 10.sp,
                        fontWeight    = FontWeight.Bold,
                        color         = AIAccent,
                        textAlign     = TextAlign.Center,
                        letterSpacing = 0.4.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun AIFeature(icon: ImageVector, color: Color, title: String, desc: String) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape    = RoundedCornerShape(10.dp),
            color    = color.copy(0.10f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = TextPri)
            Spacer(Modifier.height(3.dp))
            Text(desc,  fontSize = 10.5.sp, color = TextSec, lineHeight = 16.sp)
        }
    }
}
