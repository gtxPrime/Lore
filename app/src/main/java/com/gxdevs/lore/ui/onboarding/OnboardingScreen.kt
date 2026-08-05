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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

// ── Palette aligned with Athera Light Sanctuary Theme ───────────────────────────
private val BgPage       = Color(0xFFF4F1EA) // Light warm paper background
private val BgCard       = Color(0xFFEAE7DF) // Warm paper card background
private val Border       = Color(0xFFD6D0C1)
private val TextPri      = Color(0xFF2E332A) // Dark charcoal text
private val TextSec      = Color(0xFF727869) // Sage gray secondary text
private val GreenPrimary = Color(0xFF606F49) // Sage green accent
private val GreenHero    = Color(0xFF425139)
private val GoldAccent   = Color(0xFFB88E10)

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepo = remember { SettingsRepository(context) }

    val pagerState = rememberPagerState(pageCount = { 4 })

    fun finishOnboarding() {
        scope.launch {
            settingsRepo.setHasCompletedOnboarding(true)
            onComplete()
        }
    }

    // Dynamic Ambient Glow Color based on current page
    val targetGlowColor = when (pagerState.currentPage) {
        0 -> Color(0xFF606F49)
        1 -> Color(0xFFB88E10)
        2 -> Color(0xFFD4AF37)
        else -> Color(0xFF425139)
    }
    val ambientGlowColor by animateColorAsState(
        targetValue = targetGlowColor,
        animationSpec = tween(750, easing = FastOutSlowInEasing),
        label = "ambient_glow"
    )

    // Breathing glow animation
    val infiniteTransition = rememberInfiniteTransition(label = "ambient_breathe")
    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue  = 1.12f,
        animationSpec = infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe"
    )
    val floatY by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue  = 6f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "float"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPage)
    ) {
        // ── Ambient Liquid Background Radial Glow Canvas ───────────────────────
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .blur(80.dp)
        ) {
            val centerPx = Offset(size.width / 2f, size.height * 0.38f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        ambientGlowColor.copy(alpha = 0.24f),
                        ambientGlowColor.copy(alpha = 0.09f),
                        Color.Transparent
                    ),
                    center = centerPx,
                    radius = size.width * 0.75f * breatheScale
                ),
                radius = size.width * 0.75f * breatheScale,
                center = centerPx
            )
        }

        // ── Top & Bottom Sanctuary Edge Soft Fades ─────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(BgPage.copy(alpha = 0.95f), BgPage.copy(alpha = 0.0f))
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(BgPage.copy(alpha = 0.0f), BgPage.copy(alpha = 0.95f))
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp)
        ) {
            // ── Top Bar (Skip button with Frosted Glass look) ────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pagerState.currentPage < 3) {
                    Surface(
                        modifier = Modifier
                            .shadow(4.dp, RoundedCornerShape(14.dp), spotColor = Color.Black.copy(alpha = 0.06f))
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { finishOnboarding() },
                        color = Color.White.copy(alpha = 0.65f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Border.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = "Skip",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSec,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            // ── Pager Pages with Bouncy Slide Morphing ───────────────────────
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
                val scaleFactor = (1f - (pageOffset * 0.15f)).coerceIn(0.85f, 1f)
                val alphaFactor = (1f - (pageOffset * 0.5f)).coerceIn(0f, 1f)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scaleFactor
                            scaleY = scaleFactor
                            alpha  = alphaFactor
                            translationY = floatY
                        }
                ) {
                    when (page) {
                        0 -> OnboardingPageWelcome()
                        1 -> OnboardingPageCompanions()
                        2 -> OnboardingPageEvolution()
                        3 -> OnboardingPageSecurity()
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Footer (Liquid Bouncy Indicators + Dynamic CTA Button) ───────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bouncy Liquid Page Indicator Dots
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(4) { idx ->
                        val isCurrent = pagerState.currentPage == idx
                        val dotWidth by animateDpAsState(
                            targetValue = if (isCurrent) 28.dp else 8.dp,
                            animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                            label = "dot_width"
                        )
                        val dotColor by animateColorAsState(
                            targetValue = if (isCurrent) GreenPrimary else Border,
                            animationSpec = tween(300),
                            label = "dot_color"
                        )

                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(dotWidth)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                    }
                }

                // Fluid Bouncy CTA Button with Ambient Shadow
                val buttonScale by animateFloatAsState(
                    targetValue = if (pagerState.currentPage == 3) 1.04f else 1f,
                    animationSpec = spring(dampingRatio = 0.55f, stiffness = 300f),
                    label = "btn_scale"
                )

                Button(
                    onClick = {
                        if (pagerState.currentPage < 3) {
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    pagerState.currentPage + 1,
                                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
                                )
                            }
                        } else {
                            finishOnboarding()
                        }
                    },
                    modifier = Modifier
                        .graphicsLayer { scaleX = buttonScale; scaleY = buttonScale }
                        .shadow(
                            elevation = 16.dp,
                            shape = RoundedCornerShape(20.dp),
                            spotColor = GreenPrimary.copy(alpha = 0.45f),
                            ambientColor = Color.Black.copy(alpha = 0.15f)
                        ),
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 13.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedContent(
                            targetState = pagerState.currentPage == 3,
                            transitionSpec = { (fadeIn(tween(250)) + scaleIn(initialScale = 0.8f)).togetherWith(fadeOut(tween(150))) },
                            label = "btn_text"
                        ) { isLast ->
                            Text(
                                text = if (isLast) "Begin Journey" else "Next",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                letterSpacing = 0.3.sp
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── Page 1: Welcome to Lore ─────────────────────────────────────────────────────
@Composable
private fun OnboardingPageWelcome() {
    val infiniteTransition = rememberInfiniteTransition(label = "ring_glow")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue  = 0.75f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse_alpha"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Glowing outer ring with spot shadow
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .clip(CircleShape)
                    .background(GreenPrimary.copy(alpha = pulseAlpha * 0.22f))
            )
            Box(
                modifier = Modifier
                    .size(108.dp)
                    .shadow(16.dp, CircleShape, spotColor = GreenPrimary.copy(alpha = 0.35f))
                    .clip(CircleShape)
                    .background(GreenPrimary.copy(alpha = 0.15f))
                    .border(1.5.dp, GreenPrimary.copy(alpha = pulseAlpha), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = GreenPrimary,
                    modifier = Modifier.size(52.dp)
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        Text(
            text = "Welcome to Lore",
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPri,
            fontFamily = FontFamily.Serif,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Your Private Inner Sanctuary",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = GreenPrimary,
            fontFamily = FontFamily.Serif,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Lore is a quiet space to express your raw thoughts, process daily emotions, and nurture living companions that grow alongside your journey.",
            fontSize = 14.sp,
            color = TextSec,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

// ── Page 2: 6 Secret Mood Guardians ──────────────────────────────────────────
@Composable
private fun OnboardingPageCompanions() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "6 Emotion Archetypes",
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPri,
            fontFamily = FontFamily.Serif,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Every feeling awakens a secret guardian egg",
            fontSize = 13.sp,
            color = TextSec,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(14.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompanionArchetypeBadge("Bright", "Joy & Sunshine", Icons.Rounded.WbSunny, Color(0xFFB88E10), Modifier.weight(1f))
                CompanionArchetypeBadge("Calm", "Peace & Stillness", Icons.Rounded.Spa, GreenPrimary, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompanionArchetypeBadge("Heavy", "Resilience & Burdens", Icons.Rounded.Shield, Color(0xFF4A6B47), Modifier.weight(1f))
                CompanionArchetypeBadge("Tangled", "Confusion & Flow", Icons.Rounded.Water, Color(0xFF3B82A6), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompanionArchetypeBadge("Dark", "Quiet Night & Solitude", Icons.Rounded.NightsStay, Color(0xFF4A4E69), Modifier.weight(1f))
                CompanionArchetypeBadge("Blank", "Tranquil Fresh Slate", Icons.Rounded.AutoAwesome, Color(0xFF6B705C), Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = "✦ Names, forms & mythic evolutions hatch as you write.",
            fontSize = 11.sp,
            color = GreenPrimary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CompanionArchetypeBadge(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = accentColor.copy(alpha = 0.15f),
                ambientColor = Color.Black.copy(alpha = 0.04f)
            ),
        shape = RoundedCornerShape(16.dp),
        color = BgCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, Border.copy(alpha = 0.8f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accentColor, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPri)
                Text(subtitle, fontSize = 9.sp, color = TextSec, lineHeight = 12.sp)
            }
        }
    }
}

@Composable
private fun CompanionCard(
    name: String,
    type: String,
    desc: String,
    icon: ImageVector,
    accentColor: Color
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "card_press"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(18.dp),
                spotColor = accentColor.copy(alpha = 0.18f),
                ambientColor = Color.Black.copy(alpha = 0.05f)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    isPressed = true
                }
            ),
        shape = RoundedCornerShape(18.dp),
        color = BgCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, Border.copy(alpha = 0.8f))
    ) {
        LaunchedEffect(isPressed) {
            if (isPressed) {
                kotlinx.coroutines.delay(120)
                isPressed = false
            }
        }

        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accentColor, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPri)
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = accentColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            type,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(desc, fontSize = 11.sp, color = TextSec, lineHeight = 15.sp)
            }
        }
    }
}

// ── Page 3: Watch Them Evolve ───────────────────────────────────────────────────
@Composable
private fun OnboardingPageEvolution() {
    val infiniteTransition = rememberInfiniteTransition(label = "gold_pulse")
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue  = 1.05f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow_scale"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(118.dp)
                    .clip(CircleShape)
                    .background(GoldAccent.copy(alpha = 0.15f))
                    .scale(glowScale)
            )
            Box(
                modifier = Modifier
                    .size(98.dp)
                    .shadow(16.dp, CircleShape, spotColor = GoldAccent.copy(alpha = 0.35f))
                    .clip(CircleShape)
                    .background(GoldAccent.copy(alpha = 0.15f))
                    .border(1.5.dp, GoldAccent.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Pets, null, tint = GoldAccent, modifier = Modifier.size(46.dp))
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Write & Evolve",
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPri,
            fontFamily = FontFamily.Serif,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Every entry grants growth energy",
            fontSize = 14.sp,
            color = GreenPrimary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(18.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(12.dp, RoundedCornerShape(20.dp), spotColor = GreenPrimary.copy(alpha = 0.15f)),
            shape = RoundedCornerShape(20.dp),
            color = BgCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, Border.copy(alpha = 0.8f))
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                EvolutionStep(step = "1", title = "Hatch your Egg", desc = "Your first journal awakens a new companion egg.")
                EvolutionStep(step = "2", title = "Nurture Daily", desc = "Reflect daily to grant XP and evolve through progressive stages.")
                EvolutionStep(step = "3", title = "Reach Mythic Stage", desc = "Complete 30 entries to unlock their final Mythic guardian form.")
            }
        }
    }
}

@Composable
private fun EvolutionStep(step: String, title: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(GreenPrimary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(step, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = GreenPrimary)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPri)
            Text(desc, fontSize = 11.sp, color = TextSec, lineHeight = 16.sp)
        }
    }
}

// ── Page 4: Security & Total Privacy ───────────────────────────────────────────
@Composable
private fun OnboardingPageSecurity() {
    val infiniteTransition = rememberInfiniteTransition(label = "shield_breathe")
    val shieldPulse by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue  = 1.04f,
        animationSpec = infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "shield_pulse"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(118.dp)
                    .clip(CircleShape)
                    .background(GreenPrimary.copy(alpha = 0.15f))
                    .scale(shieldPulse)
            )
            Box(
                modifier = Modifier
                    .size(98.dp)
                    .shadow(16.dp, CircleShape, spotColor = GreenPrimary.copy(alpha = 0.35f))
                    .clip(CircleShape)
                    .background(GreenPrimary.copy(alpha = 0.15f))
                    .border(1.5.dp, GreenPrimary.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Lock, null, tint = GreenPrimary, modifier = Modifier.size(46.dp))
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Total Privacy & Control",
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPri,
            fontFamily = FontFamily.Serif,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Your thoughts belong to you alone",
            fontSize = 14.sp,
            color = GreenPrimary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(18.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(12.dp, RoundedCornerShape(20.dp), spotColor = GreenPrimary.copy(alpha = 0.15f)),
            shape = RoundedCornerShape(20.dp),
            color = BgCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, Border.copy(alpha = 0.8f))
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SecurityFeature(Icons.Rounded.Fingerprint, "App Lock & Biometrics", "Secure your sanctuary with PIN or Fingerprint.")
                SecurityFeature(Icons.Rounded.VisibilityOff, "Decoy PIN Vault", "Enter a fake PIN to show a blank stealth journal.")
                SecurityFeature(Icons.Rounded.Shield, "Screenshot Protection", "Prevents screen grabs and hides recent app previews.")
            }
        }
    }
}

@Composable
private fun SecurityFeature(icon: ImageVector, title: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(GreenPrimary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = GreenPrimary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPri)
            Text(desc, fontSize = 11.sp, color = TextSec, lineHeight = 15.sp)
        }
    }
}
