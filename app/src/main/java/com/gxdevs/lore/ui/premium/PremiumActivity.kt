package com.gxdevs.lore.ui.premium

import android.os.Bundle
import android.widget.Toast
import java.util.Locale
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.fragment.app.FragmentActivity
import com.gxdevs.lore.MainActivity
import com.gxdevs.lore.data.SettingsRepository
import com.gxdevs.lore.utils.PremiumManager
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlinx.coroutines.launch

// ===================== ATHERA LIGHT SANCTUARY THEME =====================
private val BgPage      = Color(0xFFF4F1EA) // Light warm cream background matching Home Page
private val BgCard      = Color(0xFFEAE7DF) // Warm paper card background
private val BgCardAlt   = Color(0xFFE2DDD2)
private val Border      = Color(0xFFD6D0C1)
private val TextPri     = Color(0xFF2E332A) // Dark slate primary text
private val TextSec     = Color(0xFF727869) // Sage gray secondary text
private val GoldHi      = Color(0xFFB88E10) // Rich warm gold
private val GoldMid     = Color(0xFFD4AF37)
private val GoldBg      = Color(0xFFFAF4E1)
private val GreenHero1  = Color(0xFF606F49) // Matching Home Page "Awaiting Spark" hero banner
private val GreenHero2  = Color(0xFF425139)
private val GreenLight  = Color(0xFFD9DFCD)

private val goldGrad  = listOf(GoldHi, GoldMid, Color(0xFF99750C), GoldHi)
private val greenGrad = listOf(GreenHero1, GreenHero2)

class PremiumActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PremiumScreen(onBack = { finish() })
        }
    }
}

@Composable
fun PremiumScreen(onBack: () -> Unit) {
    val context        = LocalContext.current
    val activity       = context as? FragmentActivity
    val scope          = rememberCoroutineScope()
    val settingsRepo   = remember { SettingsRepository(context) }
    val credentialManager = remember { androidx.credentials.CredentialManager.create(context) }

    val pm             = remember { PremiumManager.getInstance(context) }
    val isAlreadyPro   by pm.isPremium.collectAsState()
    val products       by pm.productDetailsList.collectAsState()
    val activePlanName by settingsRepo.subscriptionPlan.collectAsState(initial = "LORE SANCTUARY (PRO)")

    val googleLoggedIn by settingsRepo.googleLoggedIn.collectAsState(initial = false)

    var selectedPlan          by remember { mutableStateOf(PremiumManager.PRODUCT_ANNUAL) }
    var isPurchasing          by remember { mutableStateOf(false) }
    var showGoogleLoginDialog by remember { mutableStateOf(false) }
    var showCelebration       by remember { mutableStateOf(false) }
    var showRestoreDialog     by remember { mutableStateOf(false) }
    var restoreStatus         by remember { mutableStateOf<RestoreStatus>(RestoreStatus.Idle) }

    // Observe real purchase success (fires only after onPurchasesUpdated confirms a PURCHASED state)
    val purchaseSuccess by pm.purchaseSuccess.collectAsState()
    LaunchedEffect(purchaseSuccess) {
        if (purchaseSuccess) {
            isPurchasing = false
            // Only show celebration after genuine purchase confirmation
            showCelebration = true
            pm.resetPurchaseSuccess()
        }
    }

    // ── Google Sign-In Helper ───────────────────────────────────────────────
    fun performGoogleSignIn(onSuccess: () -> Unit) {
        val act = activity ?: context.findActivity() ?: return
        MainActivity.bypassNextLock = true
        scope.launch {
            try {
                when (val res = com.gxdevs.lore.auth.GoogleAuthManager.signIn(act, settingsRepo)) {
                    is com.gxdevs.lore.auth.GoogleAuthManager.AuthResult.Success -> {
                        Toast.makeText(context, "Signed in as ${res.displayName}", Toast.LENGTH_SHORT).show()
                        // Re-verify subscription now that Google login is confirmed
                        pm.refreshIfLoggedIn()
                        onSuccess()
                    }
                    is com.gxdevs.lore.auth.GoogleAuthManager.AuthResult.Failure -> {
                        Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                    }
                    is com.gxdevs.lore.auth.GoogleAuthManager.AuthResult.Cancelled -> {
                        // User cancelled, do nothing
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Sign-In error occurred", Toast.LENGTH_LONG).show()
            } finally {
                MainActivity.bypassNextLock = false
            }
        }
    }

    fun initiatePurchase() {
        isPurchasing = true
        val act = activity
        if (act != null) {
            pm.launchPurchaseFlow(act, selectedPlan) { ok, msg ->
                if (!ok) {
                    isPurchasing = false
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
                // On success: isPurchasing stays true until onPurchasesUpdated fires purchaseSuccess
            }
        } else { isPurchasing = false }
    }

    // ── Smooth non-jittery Animations ──────────────────────────────────────
    val inf = rememberInfiniteTransition(label = "prem")

    val crownPulse by inf.animateFloat(
        0.97f, 1.03f,
        infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "crown"
    )
    val glowA by inf.animateFloat(
        0.30f, 0.70f,
        infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )
    val borderA by inf.animateFloat(
        0.50f, 1.0f,
        infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "border"
    )
    val shimmerPhase by inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer"
    )

    LaunchedEffect(Unit) { pm.queryAvailableProducts() }

    // ── Mandatory Google Login Dialog ─────────────────────────────────────────
    if (showGoogleLoginDialog) {
        AlertDialog(
            onDismissRequest = { showGoogleLoginDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AccountCircle, null, tint = GreenHero1, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Google Sign-In Required", fontWeight = FontWeight.Bold, color = TextPri, fontSize = 17.sp)
                }
            },
            text = {
                Text(
                    "To complete your Lore Sanctuary purchase and lock your entitlement securely across all your devices, please sign in with your Google account.",
                    color = TextSec, fontSize = 13.sp, lineHeight = 19.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showGoogleLoginDialog = false
                        performGoogleSignIn {
                            initiatePurchase()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenHero1),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Sign In & Purchase", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoogleLoginDialog = false }) {
                    Text("Cancel", color = TextSec)
                }
            },
            containerColor = BgCard,
            shape = RoundedCornerShape(20.dp)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgPage)
    ) {
        if (showCelebration) {
            ConfettiOverlay()
            CelebrationDialog(
                onDismiss = {
                    showCelebration = false
                    onBack()
                }
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(56.dp))

            // ── Top Bar (Back + Badge) ───────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(BgCard)
                        .border(1.dp, Border, CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = TextPri, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GoldBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldMid.copy(alpha = 0.5f))
                ) {
                    Text(
                        "LORE SANCTUARY",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = GoldHi,
                        letterSpacing = 1.8.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── Hero Banner Card (Matching Home Page Hero Aesthetic) ──────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(Brush.linearGradient(greenGrad))
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    // Crown Icon Ring
                    Box(modifier = Modifier.size(90.dp).scale(crownPulse), contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier.size(90.dp).clip(CircleShape)
                                .background(Brush.radialGradient(listOf(GoldMid.copy(alpha = glowA * 0.5f), Color.Transparent)))
                        )
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                                .border(1.5.dp, Brush.linearGradient(goldGrad.map { it.copy(alpha = borderA) }), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.WorkspacePremium, null, tint = Color(0xFFFFE599), modifier = Modifier.size(40.dp))
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Text(
                        "Lore Sanctuary",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        fontFamily = FontFamily.Serif,
                        textAlign = TextAlign.Center,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Write without limits. Remember forever.",
                        fontSize = 14.sp,
                        color = Color(0xFFFFE599),
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Serif,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Unlock 3x pet growth, endless voice & media, encrypted cloud backup, and total stealth privacy.",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Social Proof Strip ───────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(BgCard)
                    .border(1.dp, Border, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ProofChip(Icons.Rounded.Star, "4.9", "Rating", modifier = Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(24.dp).background(Border))
                ProofChip(Icons.Rounded.Edit, "10K+", "Writers", modifier = Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(24.dp).background(Border))
                ProofChip(Icons.Rounded.Lock, "E2E", "Encrypted", modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(20.dp))

            // ── Feature Comparison Table ─────────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = BgCard,
                border = androidx.compose.foundation.BorderStroke(1.dp, Border)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.weight(1f))
                        Text("FREE", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = TextSec, letterSpacing = 1.sp, modifier = Modifier.width(74.dp), textAlign = TextAlign.Center)
                        Text("PRO", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = GreenHero1, letterSpacing = 1.sp, modifier = Modifier.width(78.dp), textAlign = TextAlign.Center)
                    }
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = Border)
                    Spacer(Modifier.height(6.dp))

                    CompareRow(Icons.Rounded.Bolt,          Color(0xFFB88E10), "Pet Growth",         "1/day",     "3/day")
                    CompareRow(Icons.Rounded.Edit,          GreenHero1,        "Pet Nicknames",      "—",         "Custom")
                    CompareRow(Icons.Rounded.Mic,           Color(0xFF7A64B8), "Voice Recording",    "1 min",     "Unlimited")
                    CompareRow(Icons.Rounded.Image,         Color(0xFF3B82A6), "Media Attachments",  "3/entry",   "Unlimited")
                    CompareRow(Icons.Rounded.VisibilityOff, Color(0xFFC05244), "Decoy PIN",        "—",         "Stealth")
                    CompareRow(Icons.Rounded.CloudUpload,   GreenHero1,        "Cloud Backup",       "—",         "Auto-sync")
                    CompareRow(Icons.Rounded.Widgets,       Color(0xFFB88E10), "Widgets & Export",   "—",         "Included")
                }
            }

            Spacer(Modifier.height(24.dp))

            if (isAlreadyPro) {
                // ── Active Pro Membership Status Card ──────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(BgCard)
                        .border(1.5.dp, GoldMid.copy(alpha = 0.6f), RoundedCornerShape(22.dp))
                        .padding(20.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(GoldBg)
                                    .border(1.dp, GoldMid, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.CheckCircle, null, tint = GoldHi, modifier = Modifier.size(22.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "LORE SANCTUARY PRO ACTIVE",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = GoldHi,
                                    letterSpacing = 1.2.sp
                                )
                                Spacer(Modifier.height(3.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = GoldBg,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldMid.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = activePlanName ?: "LORE SANCTUARY (PRO)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GoldHi,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = Border)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Your subscription is active and managed through Google Play.",
                            fontSize = 11.sp,
                            color = TextSec,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // ── Plan Chooser (Shown only when NOT premium) ────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("CHOOSE YOUR PLAN", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = TextSec, letterSpacing = 1.5.sp)
                    Surface(shape = RoundedCornerShape(8.dp), color = GreenLight) {
                        Text("Cancel anytime", fontSize = 9.sp, color = GreenHero1, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))

                val annualDetails = products.find { it.productId == PremiumManager.PRODUCT_ANNUAL }
                val monthlyDetails = products.find { it.productId == PremiumManager.PRODUCT_MONTHLY }
                val lifetimeDetails = products.find { it.productId == PremiumManager.PRODUCT_LIFETIME }

                val annualPhase = annualDetails?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()
                val monthlyPhase = monthlyDetails?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()
                val annualPrice = annualPhase?.formattedPrice ?: "₹199 / year"
                val monthlyPrice = monthlyPhase?.formattedPrice ?: "₹29 / month"
                val rawLifetimePrice = lifetimeDetails?.oneTimePurchaseOfferDetails?.formattedPrice
                    ?: lifetimeDetails?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                val lifetimePrice = rawLifetimePrice ?: "₹2,499"

                val savingsBadgeText = remember(annualPhase, monthlyPhase) {
                    val monthlyMicros = monthlyPhase?.priceAmountMicros ?: 0L
                    val annualMicros = annualPhase?.priceAmountMicros ?: 0L
                    if (monthlyMicros > 0L && annualMicros > 0L) {
                        val fullYearMonthlyCost = monthlyMicros * 12.0
                        val savings = (((fullYearMonthlyCost - annualMicros) / fullYearMonthlyCost) * 100).toInt()
                        if (savings > 0) "SAVE ${savings}%" else "SAVE 50%"
                    } else {
                        "SAVE 50%"
                    }
                }

                PlanCard("Annual", annualPrice, "Best deal · billed yearly", savingsBadgeText, gold = true,
                    selected = selectedPlan == PremiumManager.PRODUCT_ANNUAL, borderA = borderA,
                    onClick = { selectedPlan = PremiumManager.PRODUCT_ANNUAL })
                Spacer(Modifier.height(8.dp))
                PlanCard("Monthly", monthlyPrice, "Flexible · cancel anytime", null, gold = false,
                    selected = selectedPlan == PremiumManager.PRODUCT_MONTHLY, borderA = borderA,
                    onClick = { selectedPlan = PremiumManager.PRODUCT_MONTHLY })
                Spacer(Modifier.height(8.dp))
                PlanCard("Lifetime", lifetimePrice, "Pay once · own forever", "BEST VALUE", gold = false,
                    selected = selectedPlan == PremiumManager.PRODUCT_LIFETIME, borderA = borderA,
                    onClick = { selectedPlan = PremiumManager.PRODUCT_LIFETIME })
            }

            Spacer(Modifier.height(24.dp))

            // ── Fluid Non-Jittery Shimmer CTA Button ─────────────────────────
            val shimmerStart = Offset(shimmerPhase * 800f - 300f, 0f)
            val shimmerEnd = Offset(shimmerPhase * 800f + 300f, 100f)

            val ctaBrush = if (isAlreadyPro)
                Brush.linearGradient(listOf(GreenHero2, GreenHero2))
            else
                Brush.linearGradient(
                    colors = listOf(GreenHero1, GreenHero2, GreenHero1),
                    start = shimmerStart,
                    end = shimmerEnd
                )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(ctaBrush)
                    .clickable(
                        enabled = !isPurchasing,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        if (isAlreadyPro) { onBack(); return@clickable }
                        initiatePurchase()
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isPurchasing) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (isAlreadyPro) Icons.Rounded.CheckCircle else Icons.Rounded.WorkspacePremium, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (isAlreadyPro) "SANCTUARY UNLOCKED ✓" else "START MY SANCTUARY",
                            fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, letterSpacing = 0.4.sp
                        )
                        if (!isAlreadyPro) {
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Color.White.copy(0.85f), modifier = Modifier.size(17.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Trust row
            if (!isAlreadyPro) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Lock, null, tint = TextSec, modifier = Modifier.size(10.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Secured by Google Play · No hidden fees · Cancel anytime", fontSize = 10.sp, color = TextSec, textAlign = TextAlign.Center)
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Restore + Trust Footer ─────────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Restore Purchase",
                    fontSize = 12.sp, color = TextSec, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable {
                        restoreStatus = RestoreStatus.Checking
                        showRestoreDialog = true
                        pm.queryExistingPurchases()
                        scope.launch {
                            kotlinx.coroutines.delay(3000)
                            if (restoreStatus == RestoreStatus.Checking) {
                                restoreStatus = if (isAlreadyPro) RestoreStatus.Success else RestoreStatus.NotFound
                            }
                        }
                    }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isAlreadyPro) Icons.Rounded.CheckCircle else Icons.Rounded.Lock,
                        null, tint = GreenHero1, modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (isAlreadyPro) "Premium Active" else "Secured by Google Play", fontSize = 11.sp, color = GreenHero1, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(44.dp))
        }
    }

    // ── Restore Purchase Dialog ──────────────────────────────────────────────
    LaunchedEffect(isAlreadyPro, showRestoreDialog) {
        if (showRestoreDialog && isAlreadyPro && restoreStatus == RestoreStatus.Checking) {
            restoreStatus = RestoreStatus.Success
        }
    }

    if (showRestoreDialog) {
        RestoreDialog(
            status = restoreStatus,
            onDismiss = {
                showRestoreDialog = false
                restoreStatus = RestoreStatus.Idle
            }
        )
    }
}

// ── Restore Purchase Status ──────────────────────────────────────────────────

private sealed class RestoreStatus {
    data object Idle     : RestoreStatus()
    data object Checking : RestoreStatus()
    data object Success  : RestoreStatus()
    data object NotFound : RestoreStatus()
}

@Composable
private fun RestoreDialog(status: RestoreStatus, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (status != RestoreStatus.Checking) onDismiss() },
        containerColor = BgCard,
        shape = RoundedCornerShape(24.dp),
        title = null,
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                when (status) {
                    is RestoreStatus.Checking -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = GreenHero1,
                            strokeWidth = 3.dp
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Checking Google Play…",
                            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPri,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Verifying your purchase with Google Play. This only takes a moment.",
                            fontSize = 12.sp, color = TextSec, lineHeight = 18.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    is RestoreStatus.Success -> {
                        Box(
                            modifier = Modifier.size(64.dp).clip(CircleShape)
                                .background(GreenLight)
                                .border(2.dp, GreenHero1.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.CheckCircle, null, tint = GreenHero1, modifier = Modifier.size(36.dp))
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Subscription Restored!",
                            fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = TextPri,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Your Lore Sanctuary subscription is active. All premium features are unlocked.",
                            fontSize = 12.sp, color = TextSec, lineHeight = 18.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    is RestoreStatus.NotFound -> {
                        Box(
                            modifier = Modifier.size(64.dp).clip(CircleShape)
                                .background(BgCardAlt)
                                .border(2.dp, Border, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.SearchOff, null, tint = TextSec, modifier = Modifier.size(34.dp))
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No Active Subscription Found",
                            fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = TextPri,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "No active subscription was found on this Google account. If you purchased on a different account, please switch and try again.",
                            fontSize = 12.sp, color = TextSec, lineHeight = 18.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    else -> {}
                }
            }
        },
        confirmButton = {
            if (status != RestoreStatus.Checking) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (status is RestoreStatus.Success) GreenHero1 else BgCardAlt
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (status is RestoreStatus.Success) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Enjoy Sanctuary",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            "Got It",
                            color = TextSec,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    )
}

// ── Celebratory Confetti & Success Overlay ─────────────────────────────────────

private enum class ConfettiShape { RECT, CIRCLE, DIAMOND, STAR }

private class ConfettiParticle(
    var xRatio: Float,
    var yRatio: Float,
    var speedY: Float,
    val speedX: Float,
    val wobbleSpeed: Float,
    val wobbleAmp: Float,
    val size: Float,
    val color: Color,
    val shape: ConfettiShape,
    var rotation: Float,
    val rotationSpeed: Float,
    var wobblePhase: Float,
    val gravity: Float         // simulated gravity pull down
)

@Composable
private fun ConfettiOverlay(
    modifier: Modifier = Modifier
) {
    val colors = listOf(
        Color(0xFFD4AF37), // Rich Gold
        Color(0xFFF3C042), // Bright Gold
        Color(0xFFFFE082), // Pale Gold
        Color(0xFF606F49), // Forest Green
        Color(0xFF7CB87A), // Sage Green
        Color(0xFFAED581), // Light Green
        Color(0xFFFFFFFF), // White
        Color(0xFFE8C15A), // Champagne
        Color(0xFFFF9966), // Warm Coral
        Color(0xFFFF7043), // Deep Orange
        Color(0xFFB388FF), // Soft Violet
        Color(0xFF9C27B0), // Purple
        Color(0xFF4FC3F7), // Sky Blue
        Color(0xFF29B6F6), // Bright Blue
        Color(0xFFFF4081), // Pink
    )
    val shapes = ConfettiShape.entries.toTypedArray()

    val particles = remember {
        List(160) {
            val shape = shapes[(Math.random() * shapes.size).toInt()]
            val size = when (shape) {
                ConfettiShape.RECT    -> (8f..20f).randomFloat()
                ConfettiShape.CIRCLE  -> (4f..9f).randomFloat()
                ConfettiShape.DIAMOND -> (8f..16f).randomFloat()
                ConfettiShape.STAR    -> (7f..13f).randomFloat()
            }
            // Burst spawn: start from top-center area spreading out
            val burstX = (0.3f..0.7f).randomFloat()
            val burstY = ((-0.4f)..(-0.05f)).randomFloat()
            // Burst velocities: fan outward
            val angle = ((-45f)..(225f)).randomFloat() * Math.PI.toFloat() / 180f
            val speed = (0.003f..0.012f).randomFloat()
            ConfettiParticle(
                xRatio       = burstX,
                yRatio       = burstY,
                speedY       = -speed * kotlin.math.sin(angle) * 0.6f,  // initial upward burst
                speedX       = speed * kotlin.math.cos(angle) * 0.8f,
                wobbleSpeed  = (0.03f..0.08f).randomFloat(),
                wobbleAmp    = (0.003f..0.012f).randomFloat(),
                size         = size,
                color        = colors[(Math.random() * colors.size).toInt()],
                shape        = shape,
                rotation     = (0f..360f).randomFloat(),
                rotationSpeed = ((-6f)..6f).randomFloat(),
                wobblePhase  = (0f..6.28f).randomFloat(),
                gravity      = (0.00018f..0.00045f).randomFloat()
            )
        }
    }

    var tick by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < 8000) {
            kotlinx.coroutines.android.awaitFrame()
            tick = System.currentTimeMillis()
            particles.forEach { p ->
                p.speedY     += p.gravity          // gravity accelerates downward
                p.yRatio     += p.speedY
                p.wobblePhase += p.wobbleSpeed
                p.xRatio     += p.speedX + p.wobbleAmp * kotlin.math.sin(p.wobblePhase.toDouble()).toFloat()
                p.rotation   += p.rotationSpeed
            }
        }
    }

    androidx.compose.foundation.Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        particles.forEach { p ->
            if (p.yRatio in -1.5f..1.4f) {
                val cx = p.xRatio * w
                val cy = p.yRatio * h
                val s  = p.size
                withTransform({
                    translate(cx, cy)
                    rotate(p.rotation)
                }) {
                    when (p.shape) {
                        ConfettiShape.RECT -> drawRect(
                            color   = p.color,
                            topLeft = androidx.compose.ui.geometry.Offset(-s * 0.4f, -s * 0.9f),
                            size    = androidx.compose.ui.geometry.Size(s * 0.8f, s * 1.8f)
                        )
                        ConfettiShape.CIRCLE -> drawCircle(
                            color  = p.color,
                            radius = s,
                            center = Offset.Zero
                        )
                        ConfettiShape.DIAMOND -> {
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(0f, -s)
                                lineTo(s * 0.55f, 0f)
                                lineTo(0f, s)
                                lineTo(-s * 0.55f, 0f)
                                close()
                            }
                            drawPath(path = path, color = p.color)
                        }
                        ConfettiShape.STAR -> {
                            val path = androidx.compose.ui.graphics.Path().apply {
                                val outerR = s
                                val innerR = s * 0.45f
                                val points = 5
                                for (i in 0 until points * 2) {
                                    val r = if (i % 2 == 0) outerR else innerR
                                    val a = (i * Math.PI / points - Math.PI / 2).toFloat()
                                    val x = (r * kotlin.math.cos(a))
                                    val y = (r * kotlin.math.sin(a))
                                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                                }
                                close()
                            }
                            drawPath(path = path, color = p.color)
                        }
                    }
                }
            }
        }
    }
}

private fun ClosedRange<Float>.randomFloat(): Float =
    (start + (endInclusive - start) * Math.random()).toFloat()

@Composable
private fun CelebrationDialog(
    onDismiss: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 280f),
        label = "dialog_scale"
    )

    val inf = rememberInfiniteTransition(label = "cel")
    val crownPulse by inf.animateFloat(
        0.92f, 1.08f,
        infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "cel_crown"
    )
    val glowPulse by inf.animateFloat(
        0.3f, 0.8f,
        infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "cel_glow"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        shape = RoundedCornerShape(26.dp),
        title = null,
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp)
            ) {
                // Animated Crown Badge with pulsing glow rings
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(crownPulse),
                    contentAlignment = Alignment.Center
                ) {
                    // Outer glow ring
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        GoldMid.copy(alpha = 0.40f * glowPulse),
                                        GoldHi.copy(alpha = 0.12f * glowPulse),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    // Inner badge
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(GoldBg, GoldBg.copy(alpha = 0.85f))
                                )
                            )
                            .border(
                                2.dp,
                                Brush.linearGradient(listOf(GoldHi, GoldMid, GoldHi)),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.WorkspacePremium,
                            contentDescription = "Success",
                            tint = GoldHi,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                Text(
                    text = "SANCTUARY UNLOCKED!",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPri,
                    fontFamily = FontFamily.Serif,
                    letterSpacing = (-0.5).sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Welcome to Lore Sanctuary. All premium features including 3x XP, Unlimited Voice, Decoy Mode, and Google Drive Auto-Sync are now active across all your devices.",
                    fontSize = 13.sp,
                    color = TextSec,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(18.dp))

                // Feature Chips — icon + label, no emoji
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 3x XP chip
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = GreenLight.copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GreenHero1.copy(alpha = 0.4f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Bolt,
                                contentDescription = null,
                                tint = GreenHero1,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "3x XP",
                                fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = GreenHero1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    // Unlimited Voice chip
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = GoldBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, GoldMid.copy(alpha = 0.4f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Mic,
                                contentDescription = null,
                                tint = GoldHi,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Voice",
                                fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = GoldHi,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    // Drive Sync chip
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = GreenLight.copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GreenHero1.copy(alpha = 0.4f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.CloudDone,
                                contentDescription = null,
                                tint = GreenHero1,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Backup",
                                fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = GreenHero1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = GreenHero1),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(
                    text = "EXPLORE SANCTUARY",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    letterSpacing = 0.5.sp
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    )
}

// ── Sub-composables ─────────────────────────────────────────────────────────────

@Composable
private fun ProofChip(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = GoldHi, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = TextPri, maxLines = 1)
        }
        Text(label, fontSize = 9.sp, color = TextSec, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, maxLines = 1)
    }
}

@Composable
private fun CompareRow(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    free: String,
    pro: String
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)).background(iconTint.copy(0.12f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = iconTint, modifier = Modifier.size(14.dp)) }
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = TextPri,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Text(
            free,
            fontSize = 11.sp,
            color = TextSec.copy(0.7f),
            modifier = Modifier.width(74.dp),
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false
        )
        Text(
            pro,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = GreenHero1,
            modifier = Modifier.width(78.dp),
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun PlanCard(
    title: String,
    price: String,
    subtitle: String,
    badge: String?,
    gold: Boolean,
    selected: Boolean,
    borderA: Float,
    onClick: () -> Unit
) {
    val borderBrush = when {
        selected && gold -> Brush.linearGradient(listOf(GoldHi, GoldMid, GoldHi))
        selected         -> Brush.linearGradient(listOf(GreenHero1, GreenHero1))
        else             -> Brush.linearGradient(listOf(Border, Border))
    }
    val bg = when {
        selected && gold -> GoldBg
        selected         -> GreenLight
        else             -> BgCard
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(if (selected) 2.dp else 1.dp, borderBrush, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        if (gold) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Brush.linearGradient(listOf(GoldHi.copy(if (selected) 0.9f else 0.4f), GoldMid.copy(if (selected) 0.5f else 0.2f))))
                    .align(Alignment.TopCenter)
            )
        }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (badge != null) {
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = if (gold) GoldHi.copy(0.18f) else GreenHero1.copy(0.18f)
                    ) {
                        Text(
                            badge,
                            fontSize = 8.sp, fontWeight = FontWeight.ExtraBold,
                            color = if (gold) GoldHi else GreenHero1,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            letterSpacing = 0.5.sp
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPri)
                Text(subtitle, fontSize = 11.sp, color = TextSec)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(price, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = if (gold && selected) GoldHi else GreenHero1)
                RadioButton(
                    selected = selected, onClick = onClick,
                    colors = RadioButtonDefaults.colors(
                        selectedColor   = if (gold) GoldHi else GreenHero1,
                        unselectedColor = TextSec.copy(0.4f)
                    ),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun android.content.Context.findActivity(): android.app.Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

