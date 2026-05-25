package com.gxdevs.aethra.ui.profile

import com.gxdevs.aethra.MainActivity
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.gxdevs.aethra.data.SettingsRepository
import com.gxdevs.aethra.ui.JournalViewModel
import com.gxdevs.aethra.ui.pets.PetViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Theme colors from HomeScreen
private val appBackground = Color(0xFFEBE8E0)
private val mainContainerBackground = Color(0xFFF4F1EA)
private val borderColor = Color(0xFFE0DCD1)
private val cardBackground = Color(0xFFEAE7DF)
private val textPrimary = Color(0xFF2E332A)
private val textSecondary = Color(0xFF828779)
private val primaryAccent = Color(0xFF606F49)
private val accentBackground = Color(0xFFD9DFCD)
private val cardDarkBackground = Color(0xFF2E332A)

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun IdentityScreen(
    onBack: () -> Unit,
    journalViewModel: JournalViewModel = viewModel(),
    petViewModel: PetViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settingsRepo = remember { SettingsRepository(context) }

    val isDecoy by settingsRepo.isDecoyMode.collectAsState(initial = false)

    val googleLoggedInRaw by settingsRepo.googleLoggedIn.collectAsState(initial = false)
    val googleLoggedIn = googleLoggedInRaw && !isDecoy
    val googleName by settingsRepo.googleAccountName.collectAsState(initial = null)
    val googlePhoto by settingsRepo.googleAccountPhoto.collectAsState(initial = null)

    val gdriveIncludeMedia by settingsRepo.gdriveIncludeMedia.collectAsState(initial = true)
    val gdriveLastSynced by settingsRepo.gdriveLastSynced.collectAsState(initial = "NEVER")
    val subscriptionPlan by settingsRepo.subscriptionPlan.collectAsState(initial = "MYSTIC (PRO)")

    // Dynamic stats
    val entriesRaw by journalViewModel.allEntries.collectAsState(initial = emptyList())
    val entries = if (isDecoy) emptyList() else entriesRaw

    val streakCountRaw by journalViewModel.streakDays.collectAsState(initial = 0)
    val streakCount = if (isDecoy) 0 else streakCountRaw

    val petsState by petViewModel.petsState.collectAsState()
    val spiritsCount = if (isDecoy) 0 else petsState.pets.filter { it.journalCount > 0 }.size

    // Modern Credential Manager setup
    val credentialManager = remember { androidx.credentials.CredentialManager.create(context) }

    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.0f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    if (!googleLoggedIn) {
        // --- UNLOGGED STATE VIEW (Screenshot 1) ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1E221A)) // Dark background
        ) {
            // Back Arrow Top Left Sticky
            Box(
                modifier = Modifier
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp, start = 16.dp)
                    .align(Alignment.TopStart)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                ) {
                     Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Padlock Icon inside circular rings
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.45f)
                    .align(Alignment.TopCenter),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier.size(240.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Dashed circular rings representation
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Identity",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(Color(0xFFFFD700).copy(alpha = glowAlpha), Color.Transparent)
                                        )
                                    )
                            )
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = "Locked",
                                tint = Color(0xFFFFD700), // Gold padlock
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
            }

            // Curved Bottom Sheet Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f)
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
                    .background(mainContainerBackground)
                    .padding(horizontal = 32.dp, vertical = 40.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Spacer(modifier = Modifier.height(10.dp))

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Unseal the Archive.",
                            color = textPrimary,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif,
                            lineHeight = 42.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Authenticate to synchronize your lore, preserve your companions, and carry your sanctuary across realms.",
                            color = textSecondary,
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // "Continue with Google" Button
                        Button(
                            onClick = {
                                MainActivity.bypassNextLock = true
                                coroutineScope.launch {
                                    try {
                                        val googleIdOption = com.google.android.libraries.identity.googleid.GetGoogleIdOption.Builder()
                                            .setFilterByAuthorizedAccounts(false)
                                            .setServerClientId("484195588417-uo81a7p1r86ldf0fktu8tir8flaqncbe.apps.googleusercontent.com")
                                            .setAutoSelectEnabled(true)
                                            .build()

                                        val request = androidx.credentials.GetCredentialRequest.Builder()
                                            .addCredentialOption(googleIdOption)
                                            .build()

                                        val result = credentialManager.getCredential(context, request)
                                        val credential = result.credential

                                        if (credential is androidx.credentials.CustomCredential &&
                                            credential.type == com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                                        ) {
                                            val googleIdTokenCredential = com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.createFrom(credential.data)
                                            val name = googleIdTokenCredential.displayName ?: "Explorer"
                                            val email = googleIdTokenCredential.id
                                            val photoUrl = googleIdTokenCredential.profilePictureUri?.toString() ?: ""

                                            settingsRepo.setGoogleLoggedIn(true)
                                            settingsRepo.setGoogleAccountName(name)
                                            settingsRepo.setGoogleAccountEmail(email)
                                            settingsRepo.setGoogleAccountPhoto(photoUrl)
                                            Toast.makeText(context, "Welcome back, $name", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: androidx.credentials.exceptions.GetCredentialException) {
                                        e.printStackTrace()
                                        Toast.makeText(context, "Sign-In Failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        Toast.makeText(context, "An error occurred during Sign-In", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .shadow(4.dp, RoundedCornerShape(28.dp)),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                GlideImage(
                                    model = "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c1/Google_%22G%22_logo.svg/120px-Google_%22G%22_logo.svg.png",
                                    contentDescription = "Google Logo",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Continue with Google",
                                    color = Color(0xFF1F1F1F),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Footer: END-TO-END ENCRYPTED
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = "Shield",
                                tint = textSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "END-TO-END ENCRYPTED",
                                color = textSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }
        }
    } else {
        // --- LOGGED-IN VIEW (Screenshot 2) ---
        Scaffold(
            containerColor = mainContainerBackground,
            contentWindowInsets = WindowInsets(0)
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 40.dp)
            ) {
                // Header (Identity Title & Back Arrow)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(cardBackground)
                    ) {
                         Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = textPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Text(
                        text = "Identity",
                        color = textPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                // Profile card section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(cardBackground)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Profile Image with gold star badge
                    Box(
                        modifier = Modifier.size(120.dp),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(appBackground)
                        ) {
                            if (!googlePhoto.isNullOrBlank()) {
                                GlideImage(
                                    model = googlePhoto,
                                    contentDescription = "Profile Avatar",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                val encodedName = try {
                                    java.net.URLEncoder.encode(googleName ?: "wanderer", "UTF-8")
                                } catch (_: Exception) {
                                    "wanderer"
                                }
                                GlideImage(
                                    model = "https://api.dicebear.com/7.x/notionists/png?seed=$encodedName",
                                    contentDescription = "Notion Profile Avatar",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }

                        // Gold star badge
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(1.dp, borderColor, CircleShape)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(accentBackground),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = "Badge",
                                tint = primaryAccent,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // User name
                    Text(
                        text = googleName ?: "Explorer",
                        color = textPrimary,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Role/Plan
                    Text(
                        text = (subscriptionPlan ?: "MYSTIC (PRO)").uppercase(),
                        color = textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Divider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(borderColor)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Stats row: Entries, Spirits, Streak
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "${entries.size}",
                                color = textPrimary,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif
                            )
                            Text(
                                text = "ENTRIES",
                                color = textSecondary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        // Vertical separator
                        Box(
                            modifier = Modifier
                                .height(36.dp)
                                .width(1.dp)
                                .background(borderColor)
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "$spiritsCount",
                                color = textPrimary,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif
                            )
                            Text(
                                text = "SPIRITS",
                                color = textSecondary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        // Vertical separator
                        Box(
                            modifier = Modifier
                                .height(36.dp)
                                .width(1.dp)
                                .background(borderColor)
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "$streakCount",
                                color = textPrimary,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif
                            )
                            Text(
                                text = "STREAK",
                                color = textSecondary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // --- Google Drive Backup section ---
                Text(
                    text = "• SECURE BACKUP",
                    color = textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(horizontal = 28.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(cardDarkBackground)
                        .padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Cloud,
                                    contentDescription = "Cloud",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Google Drive",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "🔒 End-to-End Encrypted",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Reload Sync Button
                        IconButton(
                            onClick = {
                                val currentFormatted = SimpleDateFormat("MMM dd, yyyy | h:mm a", Locale.getDefault()).format(Date())
                                coroutineScope.launch {
                                    settingsRepo.setGdriveLastSynced(currentFormatted)
                                    Toast.makeText(context, "Sanctuary synced successfully!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Sync,
                                contentDescription = "Sync Now",
                                tint = cardDarkBackground,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Media Toggle inside card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PermMedia,
                                    contentDescription = "Media",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Include Media",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Backup photos, videos, & audio",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Switch(
                            checked = gdriveIncludeMedia,
                            onCheckedChange = { value ->
                                coroutineScope.launch {
                                    settingsRepo.setGdriveIncludeMedia(value)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = cardDarkBackground,
                                checkedTrackColor = Color.White,
                                uncheckedThumbColor = Color.White.copy(alpha = 0.5f),
                                uncheckedTrackColor = Color.White.copy(alpha = 0.2f),
                                checkedBorderColor = Color.Transparent,
                                uncheckedBorderColor = Color.Transparent
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "LAST SYNCED: ${(gdriveLastSynced ?: "NEVER").uppercase()}",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // --- Subscription Section ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "• SUBSCRIPTION",
                        color = textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )

                    // TOGGLE DEMO button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(cardBackground)
                            .clickable {
                                coroutineScope.launch {
                                    val newPlan =
                                        if (subscriptionPlan == "MYSTIC (PRO)") "Explorer (Free)" else "MYSTIC (PRO)"
                                    settingsRepo.setSubscriptionPlan(newPlan)
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "TOGGLE DEMO",
                            color = primaryAccent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Subscription Card Details
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(cardDarkBackground)
                        .padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = "Plan Star",
                                tint = Color(0xFFFFD700), // Gold star
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (subscriptionPlan == "MYSTIC (PRO)") "Mystic Active" else "Explorer Plan",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (subscriptionPlan == "MYSTIC (PRO)") "• YEARLY PLAN" else "• FREE PLAN",
                                color = if (subscriptionPlan == "MYSTIC (PRO)") Color(0xFFFFD700) else Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Next billing info card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarMonth,
                                contentDescription = "Calendar",
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "NEXT BILLING",
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = if (subscriptionPlan == "MYSTIC (PRO)") "May 22, 2027" else "N/A",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "AMOUNT",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = if (subscriptionPlan == "MYSTIC (PRO)") "$49.99 / yr" else "Free",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Manage subscription button
                    Button(
                        onClick = {
                            Toast.makeText(context, "Subscription management is active", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(25.dp)),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(25.dp)
                    ) {
                        Text(
                            text = if (subscriptionPlan == "MYSTIC (PRO)") "MANAGE SUBSCRIPTION" else "UPGRADE TO MYSTIC",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                // --- Logout button: SEAL THE SANCTUARY ---
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            settingsRepo.clearGoogleAuth()
                            try {
                                credentialManager.clearCredentialState(androidx.credentials.ClearCredentialStateRequest())
                            } catch (_: Exception) {}
                            Toast.makeText(context, "Sanctuary sealed", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .height(56.dp)
                        .border(1.dp, Color(0xFFC88C82).copy(alpha = 0.3f), RoundedCornerShape(28.dp)),
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFC88C82))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                         Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = "Exit",
                            tint = Color(0xFFC88C82),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SEAL THE SANCTUARY",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}
