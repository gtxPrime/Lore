package com.gxdevs.nurtale.ui.components

import android.app.Activity
import android.widget.Toast
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gxdevs.nurtale.utils.PremiumManager

private val cardBackground     = Color(0xFFFAF8F5)
private val textPrimary        = Color(0xFF2C3224)
private val textSecondary      = Color(0xFF7A8370)
private val primaryAccent      = Color(0xFF606F49)
private val accentBackground   = Color(0xFFD9DFCD)
private val borderColor        = Color(0xFFE5DFC9)
private val goldAccent         = Color(0xFFD4AF37)
private val goldGradient       = listOf(Color(0xFFF3C042), Color(0xFFD4AF37), Color(0xFFB8860B))

/**
 * Full Premium Feel Paywall & Subscription Popup Dialog.
 *
 * Provides a glassmorphic paywall UI with gold/sage accents, glowing CTA button,
 * feature comparison list, plan selectors (Annual 50% OFF, Monthly, Lifetime),
 * and Play Billing v7/v9 integration + Confetti burst upon purchase.
 */
@Composable
fun PremiumPaywallDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val premiumManager = remember { PremiumManager.getInstance(context) }
    val isAlreadyPremium by premiumManager.isPremium.collectAsState()

    var selectedPlan by remember { mutableStateOf(PremiumManager.PRODUCT_ANNUAL) }
    var showConfetti by remember { mutableStateOf(false) }

    // Pulsing crown animation
    val infiniteTransition = rememberInfiniteTransition(label = "CrownPulse")
    val crownScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CrownScale"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Confetti Burst Layer on Purchase
            ConfettiEffect(isVisible = showConfetti)

            Surface(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 24.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .clip(RoundedCornerShape(32.dp))
                    .border(2.dp, Brush.linearGradient(goldGradient), RoundedCornerShape(32.dp)),
                color = cardBackground,
                shadowElevation = 24.dp
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Top Header (Close Button)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = goldAccent.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, goldAccent.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.Star, null, tint = goldAccent, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("NURTALE PREMIUM", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = goldAccent, letterSpacing = 1.sp)
                                }
                            }

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(32.dp).clip(CircleShape).background(borderColor.copy(alpha = 0.4f))
                            ) {
                                Icon(Icons.Rounded.Close, null, tint = textSecondary, modifier = Modifier.size(18.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Glowing Crown Icon
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .scale(crownScale)
                                .clip(CircleShape)
                                .background(Brush.radialGradient(listOf(goldAccent.copy(alpha = 0.35f), Color.Transparent))),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = goldAccent.copy(alpha = 0.15f),
                                modifier = Modifier.size(72.dp),
                                border = androidx.compose.foundation.BorderStroke(2.dp, goldAccent)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("👑", fontSize = 38.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Main Title
                        Text(
                            text = "Nurtale Sanctuary Pass",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            fontFamily = FontFamily.Serif,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Unlock 100% on-device AI self-learning, all 25 companion spirits & voice note intelligence.",
                            fontSize = 13.sp,
                            color = textSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Premium Features List
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FeatureRow("🧠 99% AI Self-Learning Accuracy", "Adapts to your personal vocabulary, Hinglish & style")
                            FeatureRow("🐾 Unlock All 25 Companion Pet Stages", "Grow Sol, Sage, Moss, Knot, Nox & Echo to Mythic forms")
                            FeatureRow("🎙️ On-Device Voice-to-Words AI", "Instant offline speech transcription & acoustic sentiment")
                            FeatureRow("📊 Advanced Emotional Pattern Stats", "Weekly warmest days, peak hours & self-care guidance")
                            FeatureRow("🔒 100% Private & Zero-Cloud", "Your private thoughts never leave your phone")
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Pricing Plan Options
                        Text(
                            text = "CHOOSE YOUR SANCTUARY PASS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = textSecondary,
                            letterSpacing = 1.2.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Annual Plan (Featured)
                        PlanOptionCard(
                            title = "Annual Sanctuary Pass",
                            price = "$19.99 / year",
                            subtitle = "$1.66 / month • Billed annually",
                            badgeText = "SAVE 50% • MOST POPULAR",
                            isSelected = selectedPlan == PremiumManager.PRODUCT_ANNUAL,
                            onClick = { selectedPlan = PremiumManager.PRODUCT_ANNUAL }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Monthly Plan
                        PlanOptionCard(
                            title = "Monthly Pass",
                            price = "$2.99 / month",
                            subtitle = "Cancel anytime",
                            badgeText = null,
                            isSelected = selectedPlan == PremiumManager.PRODUCT_MONTHLY,
                            onClick = { selectedPlan = PremiumManager.PRODUCT_MONTHLY }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Lifetime Plan
                        PlanOptionCard(
                            title = "Lifetime Sanctuary Pass",
                            price = "$49.99 one-time",
                            subtitle = "Pay once, own forever",
                            badgeText = "BEST VALUE",
                            isSelected = selectedPlan == PremiumManager.PRODUCT_LIFETIME,
                            onClick = { selectedPlan = PremiumManager.PRODUCT_LIFETIME }
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Primary Action Button (Glowing)
                        Button(
                            onClick = {
                                if (activity != null) {
                                    premiumManager.launchPurchaseFlow(activity, selectedPlan) { success, msg ->
                                        if (success) {
                                            showConfetti = true
                                            Toast.makeText(context, "🎉 Welcome to Nurtale Premium!", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryAccent)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isAlreadyPremium) "PREMIUM UNLOCKED ✓" else "UNLOCK SANCTUARY PASS",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Restore Purchases & Dev Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Restore Purchases",
                                fontSize = 12.sp,
                                color = textSecondary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable {
                                    premiumManager.queryExistingPurchases()
                                    Toast.makeText(context, "Checking Google Play purchases...", Toast.LENGTH_SHORT).show()
                                }
                            )

                            Text(
                                text = if (isAlreadyPremium) "⚡ Premium Active" else "⚡ Test Unlock",
                                fontSize = 11.sp,
                                color = primaryAccent,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    premiumManager.grantTestPremium(!isAlreadyPremium)
                                    showConfetti = !isAlreadyPremium
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureRow(title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = CircleShape,
            color = primaryAccent.copy(alpha = 0.12f),
            modifier = Modifier.size(22.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, null, tint = primaryAccent, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textPrimary)
            Text(subtitle, fontSize = 11.sp, color = textSecondary, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun PlanOptionCard(
    title: String,
    price: String,
    subtitle: String,
    badgeText: String?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) primaryAccent.copy(alpha = 0.08f) else cardBackground,
        border = androidx.compose.foundation.BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) primaryAccent else borderColor
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (badgeText != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = goldAccent.copy(alpha = 0.2f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = goldAccent,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                Text(subtitle, fontSize = 11.sp, color = textSecondary)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(price, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = primaryAccent)
                RadioButton(
                    selected = isSelected,
                    onClick = onClick,
                    colors = RadioButtonDefaults.colors(selectedColor = primaryAccent)
                )
            }
        }
    }
}
