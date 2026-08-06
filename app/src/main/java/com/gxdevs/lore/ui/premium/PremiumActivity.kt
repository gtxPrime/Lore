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

    val googleLoggedIn by settingsRepo.googleLoggedIn.collectAsState(initial = false)

    var selectedPlan          by remember { mutableStateOf(PremiumManager.PRODUCT_ANNUAL) }
    var isPurchasing          by remember { mutableStateOf(false) }
    var showGoogleLoginDialog by remember { mutableStateOf(false) }

    // ── Google Sign-In Helper ───────────────────────────────────────────────
    fun performGoogleSignIn(onSuccess: () -> Unit) {
        val act: android.content.Context = activity ?: context.findActivity() ?: context
        MainActivity.bypassNextLock = true
        scope.launch {
            try {
                val signInWithGoogleOption = com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption.Builder(
                    serverClientId = SettingsRepository.WEB_CLIENT_ID
                ).build()

                val request = androidx.credentials.GetCredentialRequest.Builder()
                    .addCredentialOption(signInWithGoogleOption)
                    .build()

                val result = credentialManager.getCredential(act, request)
                val credential = result.credential

                if (credential is androidx.credentials.CustomCredential &&
                    credential.type == com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdTokenCredential = com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.createFrom(credential.data)
                    val name = googleIdTokenCredential.displayName
                        ?: googleIdTokenCredential.givenName
                        ?: googleIdTokenCredential.id.substringBefore("@")
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                    val email = googleIdTokenCredential.id
                    val photoUrl = googleIdTokenCredential.profilePictureUri?.toString() ?: ""

                    settingsRepo.setGoogleLoggedIn(true)
                    settingsRepo.setGoogleAccountName(name)
                    settingsRepo.setGoogleAccountEmail(email)
                    settingsRepo.setGoogleAccountPhoto(photoUrl)
                    Toast.makeText(context, "Signed in as $name", Toast.LENGTH_SHORT).show()
                    onSuccess()
                }
            } catch (e: androidx.credentials.exceptions.GetCredentialCancellationException) {
                // User cancelled sign in, dismiss silently
            } catch (e: androidx.credentials.exceptions.NoCredentialException) {
                Toast.makeText(context, "No Google accounts found", Toast.LENGTH_SHORT).show()
            } catch (e: androidx.credentials.exceptions.GetCredentialException) {
                e.printStackTrace()
                Toast.makeText(context, "Google Sign-In failed: ${e.message}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Sign-In error occurred", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun initiatePurchase() {
        if (!googleLoggedIn) {
            showGoogleLoginDialog = true
            return
        }
        isPurchasing = true
        val act = activity
        if (act != null) {
            pm.launchPurchaseFlow(act, selectedPlan) { ok, msg ->
                isPurchasing = false
                if (!ok) Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
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
                    "To complete your Lore Scantury purchase and lock your entitlement securely across all your devices, please sign in with your Google account.",
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
                        "LORE SCANTURY",
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
                        "Lore Scantury",
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
                    .clip(RoundedCornerShape(14.dp))
                    .background(BgCard)
                    .border(1.dp, Border, RoundedCornerShape(14.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ProofChip(Icons.Rounded.Star, "4.9", "Rating")
                Box(Modifier.width(1.dp).height(24.dp).background(Border))
                ProofChip(Icons.Rounded.Edit, "10K+", "Writers")
                Box(Modifier.width(1.dp).height(24.dp).background(Border))
                ProofChip(Icons.Rounded.Lock, "E2E", "Encrypted")
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
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Spacer(Modifier.weight(1f))
                        Text("FREE", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = TextSec, letterSpacing = 1.sp, modifier = Modifier.width(56.dp), textAlign = TextAlign.Center)
                        Text("PRO", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = GreenHero1, letterSpacing = 1.sp, modifier = Modifier.width(56.dp), textAlign = TextAlign.Center)
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

            // ── Plan Chooser ──────────────────────────────────────────────────
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

            val annualPrice = products.find { it.productId == PremiumManager.PRODUCT_ANNUAL }
                ?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice ?: "₹199 / year"
            val monthlyPrice = products.find { it.productId == PremiumManager.PRODUCT_MONTHLY }
                ?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice ?: "₹29 / month"
            val lifetimePrice = products.find { it.productId == PremiumManager.PRODUCT_LIFETIME }
                ?.oneTimePurchaseOfferDetails?.formattedPrice ?: "₹499 one-time"

            PlanCard("Annual", annualPrice, "Best deal · billed yearly", "SAVE 50%", gold = true,
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
                        Icon(Icons.Rounded.WorkspacePremium, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (isAlreadyPro) "LORE SCANTURY ACTIVE" else "START MY SANCTUARY",
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

            // Footer
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Restore Purchase",
                    fontSize = 12.sp, color = TextSec, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable {
                        pm.queryExistingPurchases()
                        Toast.makeText(context, "Checking Google Play...", Toast.LENGTH_SHORT).show()
                    }
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        pm.grantTestPremium(!isAlreadyPro)
                    }
                ) {
                    Icon(
                        if (isAlreadyPro) Icons.Rounded.CheckCircle else Icons.Rounded.Science,
                        null, tint = GreenHero1, modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (isAlreadyPro) "Premium Active" else "Dev Test Unlock", fontSize = 11.sp, color = GreenHero1, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(44.dp))
        }
    }
}

// ── Sub-composables ─────────────────────────────────────────────────────────────

@Composable
private fun ProofChip(icon: ImageVector, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = GoldHi, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = TextPri)
        }
        Text(label, fontSize = 9.sp, color = TextSec, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)).background(iconTint.copy(0.12f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = iconTint, modifier = Modifier.size(14.dp)) }
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextPri, modifier = Modifier.weight(1f))
        Text(free, fontSize = 11.sp, color = TextSec.copy(0.7f), modifier = Modifier.width(56.dp), textAlign = TextAlign.Center)
        Text(pro, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GreenHero1, modifier = Modifier.width(56.dp), textAlign = TextAlign.Center)
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

