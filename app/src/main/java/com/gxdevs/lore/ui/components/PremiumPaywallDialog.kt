package com.gxdevs.lore.ui.components

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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gxdevs.lore.utils.PremiumManager

// ── Design tokens matching Athera Light Sanctuary theme ──────────────────────────────
private val BgDark        = Color(0xFFF4F1EA) // Light warm cream background
private val BgCard        = Color(0xFFEAE7DF) // Warm paper card background
private val BgCardAlt     = Color(0xFFE2DDD2)
private val BorderSubtle  = Color(0xFFD6D0C1)
private val TextPrimary   = Color(0xFF2E332A)
private val TextSecondary = Color(0xFF727869)
private val AccentGreen   = Color(0xFF606F49)
private val AccentDark    = Color(0xFF425139)
private val GoldPrimary   = Color(0xFFB88E10)
private val GoldLight     = Color(0xFFD4AF37)
private val GoldDim       = Color(0xFF99750C)
private val GoldMid       = Color(0xFFB88E10)

private val goldGrad  = listOf(GoldLight, GoldPrimary, GoldDim)
private val greenGrad = listOf(Color(0xFF606F49), Color(0xFF425139), Color(0xFF333E2B))
private val bgGrad    = listOf(Color(0xFFF4F1EA), Color(0xFFEBE7DF), Color(0xFFF4F1EA))

/**
 * Backward-compatible wrapper for PremiumPaywallScreen.
 */
@Composable
fun PremiumPaywallDialog(
    onDismiss: () -> Unit
) {
    PremiumPaywallScreen(onDismiss = onDismiss)
}

/**
 * Reusable dialog presented when attempting to access a locked Lore Sanctuary feature.
 * The CTA navigates directly to the Premium screen.
 */
@Composable
fun LoreSanctuaryFeatureDialog(
    title: String,
    subtitle: String,
    icon: ImageVector = Icons.Rounded.WorkspacePremium,
    onUnlock: () -> Unit,
    onDismiss: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dialogGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dialogGlow"
    )
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -300f, targetValue = 600f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                // Glow icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(GoldPrimary.copy(alpha = glowAlpha * 0.4f), Color.Transparent)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(GoldPrimary.copy(alpha = 0.15f))
                            .border(1.dp, Brush.linearGradient(goldGrad), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, null, tint = GoldPrimary, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Lore Sanctuary",
                    color = GoldPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    title,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.Serif
                )
            }
        },
        text = {
            Text(
                subtitle,
                color = TextSecondary,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 0.dp, vertical = 4.dp)
            ) {
                // Shimmer background
                val shimmerBrush = Brush.linearGradient(
                    colors = listOf(
                        AccentDark,
                        AccentGreen.copy(alpha = 0.85f),
                        AccentDark
                    ),
                    start = Offset(shimmerOffset, 0f),
                    end = Offset(shimmerOffset + 300f, 100f)
                )
                val context = LocalContext.current
                Button(
                    onClick = {
                        onUnlock()
                        try {
                            context.startActivity(android.content.Intent(context, com.gxdevs.lore.ui.premium.PremiumActivity::class.java))
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(shimmerBrush, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.WorkspacePremium, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Unlock Lore Sanctuary",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                letterSpacing = 0.3.sp
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        },
        dismissButton = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = onDismiss) {
                    Text("Maybe Later", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        },
        containerColor = BgCard,
        shape = RoundedCornerShape(28.dp)
    )
}

/**
 * Full-screen Lore Sanctuary paywall — cinematic hero, emotional copy, animated CTA.
 * Navigate to this composable via the "premium_paywall" nav route.
 */
@Composable
fun PremiumPaywallScreen(
    onDismiss: () -> Unit
) {
    val context        = LocalContext.current
    val activity       = context as? Activity
    val premiumManager = remember { PremiumManager.getInstance(context) }
    val isAlreadyPremium by premiumManager.isPremium.collectAsState()
    val productDetailsList by premiumManager.productDetailsList.collectAsState()

    var selectedPlan  by remember { mutableStateOf(PremiumManager.PRODUCT_ANNUAL) }
    var showConfetti  by remember { mutableStateOf(false) }
    var isPurchasing  by remember { mutableStateOf(false) }

    // ── Animations ──────────────────────────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "paywall")

    val crownScale by infiniteTransition.animateFloat(
        initialValue = 0.97f, targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "crownScale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.20f, targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -400f, targetValue = 800f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer"
    )
    val borderGlow by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "borderGlow"
    )

    LaunchedEffect(Unit) {
        premiumManager.queryAvailableProducts()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(bgGrad))
    ) {
        ConfettiEffect(isVisible = showConfetti)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(56.dp))

            // ── Top bar ─────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(BorderSubtle)
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack, null,
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GoldPrimary.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Brush.linearGradient(goldGrad)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Star, null, tint = GoldPrimary, modifier = Modifier.size(11.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "LORE SANCTUARY",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = GoldPrimary,
                            letterSpacing = 1.5.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // ── Cinematic Crown Hero ─────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .scale(crownScale),
                contentAlignment = Alignment.Center
            ) {
                // Outer glow ring
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(GoldPrimary.copy(alpha = glowAlpha * 0.7f), Color.Transparent)
                            )
                        )
                )
                // Mid ring
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(GoldPrimary.copy(alpha = glowAlpha * 0.4f), Color.Transparent)
                            )
                        )
                )
                // Icon container
                Surface(
                    shape = CircleShape,
                    color = Color.Transparent,
                    modifier = Modifier.size(88.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        2.dp,
                        Brush.linearGradient(
                            listOf(
                                GoldLight.copy(alpha = borderGlow),
                                GoldMid.copy(alpha = borderGlow * 0.7f),
                                GoldDim.copy(alpha = borderGlow * 0.5f),
                                GoldLight.copy(alpha = borderGlow)
                            )
                        )
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        GoldPrimary.copy(alpha = 0.18f),
                                        BgDark.copy(alpha = 0.8f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.WorkspacePremium, null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(50.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Hero Copy ─────────────────────────────────────────────────────
            Text(
                text = "Lore Sanctuary",
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                fontFamily = FontFamily.Serif,
                textAlign = TextAlign.Center,
                letterSpacing = (-0.5).sp
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Write without limits. Remember forever.",
                fontSize = 16.sp,
                color = GoldPrimary.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Serif,
                letterSpacing = 0.2.sp
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = "Unlock 3x pet growth, endless voice & media, encrypted cloud backup, and total stealth privacy — everything your sanctuary deserves.",
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(Modifier.height(16.dp))

            // ── Social Proof Row ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgCardAlt)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SocialProofChip(icon = Icons.Rounded.Star, value = "4.9", label = "Rating")
                Box(Modifier.width(1.dp).height(24.dp).background(BorderSubtle))
                SocialProofChip(icon = Icons.Rounded.Edit, value = "10K+", label = "Writers")
                Box(Modifier.width(1.dp).height(24.dp).background(BorderSubtle))
                SocialProofChip(icon = Icons.Rounded.Lock, value = "E2E", label = "Encrypted")
            }

            Spacer(Modifier.height(24.dp))

            // ── Feature Showcase ──────────────────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = BgCard,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Section header
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(GoldPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "WHAT YOU UNLOCK",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = GoldPrimary,
                            letterSpacing = 1.5.sp
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = BorderSubtle)
                    Spacer(Modifier.height(16.dp))

                    FeatureShowcaseItem(
                        icon = Icons.Rounded.Bolt,
                        iconTint = Color(0xFFFFD166),
                        title = "3× Pet Growth Speed",
                        subtitle = "Three journal entries credited daily — watch your companion evolve faster",
                        freeLabel = "1 / day",
                        premiumLabel = "3 / day"
                    )
                    FeatureShowcaseItem(
                        icon = Icons.Rounded.Edit,
                        iconTint = Color(0xFF7CB87A),
                        title = "Custom Pet Nicknames",
                        subtitle = "Give your spirit a name that belongs to you, across all 6 evolution stages",
                        freeLabel = "—",
                        premiumLabel = "✓ Custom"
                    )
                    FeatureShowcaseItem(
                        icon = Icons.Rounded.Mic,
                        iconTint = Color(0xFF9B8FD4),
                        title = "Unlimited Voice Recording",
                        subtitle = "Speak your mind without a timer cutting you off mid-thought",
                        freeLabel = "1 min",
                        premiumLabel = "∞ Unlimited"
                    )
                    FeatureShowcaseItem(
                        icon = Icons.Rounded.Image,
                        iconTint = Color(0xFF5BA8C4),
                        title = "Unlimited Media Attachments",
                        subtitle = "Attach every photo, video and memory — no arbitrary 3-item cap",
                        freeLabel = "3 / entry",
                        premiumLabel = "∞ Unlimited"
                    )
                    FeatureShowcaseItem(
                        icon = Icons.Rounded.VisibilityOff,
                        iconTint = Color(0xFFC88C82),
                        title = "Decoy PIN & Stealth Mode",
                        subtitle = "A second PIN that reveals a blank decoy vault — total deniability",
                        freeLabel = "Basic",
                        premiumLabel = "Full Stealth"
                    )
                    FeatureShowcaseItem(
                        icon = Icons.Rounded.CloudUpload,
                        iconTint = Color(0xFF7CB87A),
                        title = "Encrypted Cloud Backup",
                        subtitle = "Auto-sync to your private Google Drive — never lose a memory",
                        freeLabel = "—",
                        premiumLabel = "✓ Auto-sync"
                    )
                    FeatureShowcaseItem(
                        icon = Icons.Rounded.Widgets,
                        iconTint = Color(0xFFD4AF37),
                        title = "Export & Widgets",
                        subtitle = "Coming soon — your sanctuary, everywhere you are",
                        freeLabel = "—",
                        premiumLabel = "Soon™"
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── Plan Chooser ──────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "CHOOSE YOUR PLAN",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextSecondary,
                    letterSpacing = 1.5.sp
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentDark.copy(alpha = 0.2f)
                ) {
                    Text(
                        "Cancel anytime",
                        fontSize = 10.sp,
                        color = AccentGreen,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Annual Plan (Featured)
            val annualDetails = productDetailsList.find { it.productId == PremiumManager.PRODUCT_ANNUAL }
            val annualPrice = annualDetails?.subscriptionOfferDetails?.firstOrNull()
                ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice ?: "₹199 / year"
            PlanOptionCard(
                title = "Annual Sanctuary",
                price = annualPrice,
                subtitle = "Best deal · Billed annually · Cancel anytime",
                badgeText = "SAVE 50% · MOST POPULAR",
                isFeatured = true,
                isSelected = selectedPlan == PremiumManager.PRODUCT_ANNUAL,
                onClick = { selectedPlan = PremiumManager.PRODUCT_ANNUAL }
            )

            Spacer(Modifier.height(10.dp))

            // Monthly Plan
            val monthlyDetails = productDetailsList.find { it.productId == PremiumManager.PRODUCT_MONTHLY }
            val monthlyPrice = monthlyDetails?.subscriptionOfferDetails?.firstOrNull()
                ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice ?: "₹29 / month"
            PlanOptionCard(
                title = "Monthly Pass",
                price = monthlyPrice,
                subtitle = "Flexible · Cancel anytime",
                badgeText = null,
                isFeatured = false,
                isSelected = selectedPlan == PremiumManager.PRODUCT_MONTHLY,
                onClick = { selectedPlan = PremiumManager.PRODUCT_MONTHLY }
            )

            Spacer(Modifier.height(10.dp))

            // Lifetime Plan
            val lifetimeDetails = productDetailsList.find { it.productId == PremiumManager.PRODUCT_LIFETIME }
            val lifetimePrice = lifetimeDetails?.oneTimePurchaseOfferDetails?.formattedPrice ?: "₹499 one-time"
            PlanOptionCard(
                title = "Lifetime Sanctuary",
                price = lifetimePrice,
                subtitle = "Pay once · Own forever · No renewals",
                badgeText = "BEST VALUE",
                isFeatured = false,
                isSelected = selectedPlan == PremiumManager.PRODUCT_LIFETIME,
                onClick = { selectedPlan = PremiumManager.PRODUCT_LIFETIME }
            )

            Spacer(Modifier.height(28.dp))

            // ── Animated CTA Button ───────────────────────────────────────────
            val ctaShimmerBrush = Brush.linearGradient(
                colors = listOf(
                    AccentDark,
                    AccentGreen.copy(alpha = 0.9f),
                    Color(0xFF5EA85C),
                    AccentDark
                ),
                start = Offset(shimmerOffset * 0.6f, 0f),
                end = Offset(shimmerOffset * 0.6f + 400f, 120f)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isAlreadyPremium)
                            Brush.linearGradient(listOf(AccentDark.copy(alpha = 0.5f), AccentDark.copy(alpha = 0.5f)))
                        else ctaShimmerBrush
                    )
                    .clickable(enabled = !isPurchasing) {
                        if (isAlreadyPremium) {
                            onDismiss()
                            return@clickable
                        }
                        isPurchasing = true
                        if (activity != null) {
                            premiumManager.launchPurchaseFlow(activity, selectedPlan) { success, msg ->
                                isPurchasing = false
                                if (success) {
                                    showConfetti = true
                                    Toast.makeText(context, "🎉 Welcome to Lore Sanctuary!", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            isPurchasing = false
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isPurchasing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = TextPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.WorkspacePremium, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = if (isAlreadyPremium) "LORE SANCTUARY ACTIVE" else "START MY SANCTUARY",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = 0.5.sp
                        )
                        if (!isAlreadyPremium) {
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Trust line
            if (!isAlreadyPremium) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Lock, null, tint = TextSecondary, modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "Secured by Google Play · No hidden fees · Cancel anytime",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Footer links ──────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Restore Purchase",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable {
                        premiumManager.queryExistingPurchases()
                        Toast.makeText(context, "Checking Google Play...", Toast.LENGTH_SHORT).show()
                    }
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isAlreadyPremium) Icons.Rounded.CheckCircle else Icons.Rounded.Lock,
                        null,
                        tint = AccentGreen,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (isAlreadyPremium) "Premium Active" else "Secured by Google Play",
                        fontSize = 11.sp,
                        color = AccentGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

// ── Sub-composables ────────────────────────────────────────────────────────────

@Composable
private fun SocialProofChip(icon: ImageVector, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = GoldPrimary, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
        }
        Text(label, fontSize = 9.sp, color = TextSecondary, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }
}

@Composable
private fun FeatureShowcaseItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    freeLabel: String,
    premiumLabel: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(subtitle, fontSize = 11.sp, color = TextSecondary, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(top = 2.dp)) {
            Text(
                freeLabel,
                fontSize = 10.sp,
                color = TextSecondary.copy(alpha = 0.6f),
                fontWeight = FontWeight.Medium
            )
            Text(
                premiumLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = AccentGreen
            )
        }
    }
}

@Composable
private fun PlanOptionCard(
    title: String,
    price: String,
    subtitle: String,
    badgeText: String?,
    isFeatured: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderBrush = when {
        isSelected && isFeatured -> Brush.linearGradient(goldGrad)
        isSelected               -> Brush.linearGradient(listOf(AccentGreen, AccentGreen))
        else                     -> Brush.linearGradient(listOf(BorderSubtle, BorderSubtle))
    }
    val borderWidth = if (isSelected) 2.dp else 1.dp
    val bgColor = when {
        isSelected && isFeatured -> Color(0xFF1A1800).copy(alpha = 0.6f)
        isSelected               -> AccentDark.copy(alpha = 0.2f)
        else                     -> BgCard
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .border(borderWidth, borderBrush, RoundedCornerShape(18.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (badgeText != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isFeatured) GoldPrimary.copy(alpha = 0.18f) else AccentDark.copy(alpha = 0.25f),
                        modifier = Modifier.padding(bottom = 5.dp)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isFeatured) GoldPrimary else AccentGreen,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            letterSpacing = 0.5.sp
                        )
                    }
                }
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(subtitle, fontSize = 11.sp, color = TextSecondary, lineHeight = 15.sp)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    price,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isFeatured && isSelected) GoldPrimary else AccentGreen
                )
                RadioButton(
                    selected = isSelected,
                    onClick = onClick,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = if (isFeatured) GoldPrimary else AccentGreen,
                        unselectedColor = TextSecondary.copy(alpha = 0.5f)
                    )
                )
            }
        }

        // Featured shimmer line at top
        if (isFeatured) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Brush.linearGradient(goldGrad.map { it.copy(alpha = if (isSelected) 0.8f else 0.3f) }))
                    .align(Alignment.TopCenter)
            )
        }
    }
}
