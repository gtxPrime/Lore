package com.gxdevs.lore.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gxdevs.lore.data.SettingsRepository
import kotlinx.coroutines.launch

// ── Palette aligned with Athera Light Sanctuary Theme ───────────────────────────
private val BgPage      = Color(0xFFF4F1EA) // Light warm paper background
private val BgCard      = Color(0xFFEAE7DF) // Warm paper card background
private val Border      = Color(0xFFD6D0C1)
private val TextPri     = Color(0xFF2E332A) // Dark charcoal text
private val TextSec     = Color(0xFF727869) // Sage gray secondary text
private val GreenPrimary= Color(0xFF606F49) // Sage green accent
private val GreenHero   = Color(0xFF425139)
private val GoldAccent  = Color(0xFFB88E10)

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPage)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp)
        ) {
            // ── Top Bar (Skip button) ─────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pagerState.currentPage < 3) {
                    Text(
                        text = "Skip",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSec,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { finishOnboarding() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // ── Pager Pages ──────────────────────────────────────────────────
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> OnboardingPageWelcome()
                    1 -> OnboardingPageCompanions()
                    2 -> OnboardingPageEvolution()
                    3 -> OnboardingPageSecurity()
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Footer (Indicators + Next/Get Started Button) ─────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Page Indicator Dots
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(4) { idx ->
                        val isCurrent = pagerState.currentPage == idx
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(if (isCurrent) 24.dp else 8.dp)
                                .clip(CircleShape)
                                .background(if (isCurrent) GreenPrimary else Border)
                        )
                    }
                }

                // CTA Button
                Button(
                    onClick = {
                        if (pagerState.currentPage < 3) {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        } else {
                            finishOnboarding()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary),
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (pagerState.currentPage == 3) "Begin Journey" else "Next",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Spacer(Modifier.width(6.dp))
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
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(GreenPrimary.copy(alpha = 0.15f))
                .border(1.5.dp, GreenPrimary.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = GreenPrimary,
                modifier = Modifier.size(54.dp)
            )
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

// ── Page 2: Real Sanctuary Companions ──────────────────────────────────────────
@Composable
private fun OnboardingPageCompanions() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Meet Your Companions",
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPri,
            fontFamily = FontFamily.Serif,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Living reflections born from your emotions",
            fontSize = 13.sp,
            color = TextSec,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CompanionCard(
                name = "Solara",
                type = "Bright · Sun Pup",
                desc = "Carries the warmth of golden sunrises and joyful moments.",
                icon = Icons.Rounded.WbSunny,
                accentColor = Color(0xFFB88E10)
            )
            CompanionCard(
                name = "Cappi",
                type = "Calm · Capybara",
                desc = "Unhurried and gentle. Teaches that stillness is strength.",
                icon = Icons.Rounded.Spa,
                accentColor = GreenPrimary
            )
            CompanionCard(
                name = "Pebble",
                type = "Heavy · Penguin",
                desc = "Small but resilient. Knows how to keep going despite weight.",
                icon = Icons.Rounded.Shield,
                accentColor = Color(0xFF4A6B47)
            )
            CompanionCard(
                name = "River",
                type = "Tangled · Otter",
                desc = "Drifts through confusion, turning tangles into calm currents.",
                icon = Icons.Rounded.Water,
                accentColor = Color(0xFF3B82A6)
            )
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = BgCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
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
                    Text(name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPri)
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
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(GoldAccent.copy(alpha = 0.15f))
                .border(1.5.dp, GoldAccent.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Pets, null, tint = GoldAccent, modifier = Modifier.size(48.dp))
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
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = BgCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, Border)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                EvolutionStep(step = "1", title = "Hatch your Egg", desc = "Your first journal awakens a new companion egg.")
                EvolutionStep(step = "2", title = "Nurture Daily", desc = "Reflect daily to grant XP and unlock Kit, Cub & Capy forms.")
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
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(GreenPrimary.copy(alpha = 0.15f))
                .border(1.5.dp, GreenPrimary.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Lock, null, tint = GreenPrimary, modifier = Modifier.size(48.dp))
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
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = BgCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, Border)
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
