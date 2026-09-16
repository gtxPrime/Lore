package com.gxdevs.lore.ui.profile

import com.gxdevs.lore.MainActivity
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.gxdevs.lore.data.SettingsRepository
import com.gxdevs.lore.ui.JournalViewModel
import com.gxdevs.lore.ui.pets.PetViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.gxdevs.lore.utils.DriveBackupWorker
import com.gxdevs.lore.utils.DriveBackupClient
import com.gxdevs.lore.utils.DriveTokenHelper
import com.google.android.gms.auth.api.identity.Identity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.livedata.observeAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private fun android.content.Context.findActivity(): android.app.Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

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
    onNavigateToPremium: () -> Unit = {},
    journalViewModel: JournalViewModel = viewModel(),
    petViewModel: PetViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settingsRepo = remember { SettingsRepository(context) }

    val premiumManager = remember { com.gxdevs.lore.utils.PremiumManager.getInstance(context) }
    val isPremium by premiumManager.isPremium.collectAsState()

    val isDecoy by settingsRepo.isDecoyMode.collectAsState(initial = false)

    val googleLoggedInRaw by settingsRepo.googleLoggedIn.collectAsState(initial = false)
    val googleLoggedIn = googleLoggedInRaw && !isDecoy
    val googleName by settingsRepo.googleAccountName.collectAsState(initial = null)
    val googlePhoto by settingsRepo.googleAccountPhoto.collectAsState(initial = null)

    val gdriveBackupEnabled by settingsRepo.gdriveBackupEnabled.collectAsState(initial = false)
    val gdriveIncludeMedia by settingsRepo.gdriveIncludeMedia.collectAsState(initial = true)
    val gdriveLastSynced by settingsRepo.gdriveLastSynced.collectAsState(initial = "NEVER")
    val gdriveHasPendingChanges by settingsRepo.gdriveHasPendingChanges.collectAsState(initial = false)
    val subscriptionPlan by settingsRepo.subscriptionPlan.collectAsState(initial = "MYSTIC (PRO)")

    val workInfos by WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkLiveData(DriveBackupWorker.WORK_NAME_ONDEMAND)
        .observeAsState()

    val isSyncing = remember(workInfos) {
        workInfos?.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } == true
    }

    // ActivityResultLauncher for Google Drive OAuth Consent Dialog (modern AuthorizationClient).
    // After the user approves, we extract the token from the result intent and kick off backup.
    var pendingPostConsentMode by remember { mutableStateOf<String?>(null) }

    val driveConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val token = DriveTokenHelper.getTokenFromAuthorizationResult(context, result.data)
            if (token != null) {
                coroutineScope.launch {
                    settingsRepo.setGdriveBackupEnabled(true)
                    settingsRepo.setGoogleLoggedIn(true)
                }
                Toast.makeText(context, "Drive permission granted!", Toast.LENGTH_SHORT).show()
                if (pendingPostConsentMode == "BACKUP") {
                    pendingPostConsentMode = "BACKUP_AUTO_RESUME"
                } else if (pendingPostConsentMode == "RESTORE") {
                    pendingPostConsentMode = "RESTORE_AUTO_RESUME"
                }
            } else {
                Toast.makeText(context, "Drive permission granted but token unavailable. Please try again.", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, "Drive permission is required to enable Google Drive backup", Toast.LENGTH_LONG).show()
        }
    }

    val requestDriveBackup: () -> Unit = {
        coroutineScope.launch(Dispatchers.IO) {
            val authState = DriveTokenHelper.authorizeInForeground(context)
            withContext(Dispatchers.Main) {
                when (authState) {
                    is DriveTokenHelper.DriveAuthState.HasToken -> {
                        // Token already available — kick off backup worker immediately & show initial notification
                        com.gxdevs.lore.utils.showDriveBackupNotification(context, "Google Drive Syncing...", "Preparing Sanctuary backup...", 10)
                        val constraints = Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                        val request = OneTimeWorkRequestBuilder<DriveBackupWorker>()
                            .setConstraints(constraints)
                            .addTag(DriveBackupWorker.WORK_TAG)
                            .addTag(DriveBackupWorker.TAG_MANUAL_SYNC)
                            .build()
                        WorkManager.getInstance(context).enqueueUniqueWork(
                            DriveBackupWorker.WORK_NAME_ONDEMAND,
                            ExistingWorkPolicy.REPLACE,
                            request
                        )
                        Toast.makeText(context, "Syncing to Google Drive...", Toast.LENGTH_SHORT).show()
                    }
                    is DriveTokenHelper.DriveAuthState.NeedsConsent -> {
                        // Show Drive consent dialog — result handled by driveConsentLauncher
                        try {
                            pendingPostConsentMode = "BACKUP"
                            driveConsentLauncher.launch(
                                androidx.activity.result.IntentSenderRequest.Builder(authState.pendingIntent.intentSender).build()
                            )
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open Drive permission dialog: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                    is DriveTokenHelper.DriveAuthState.Failed -> {
                        Toast.makeText(context, "Drive access error: ${authState.reason}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var showRestorePinPromptDialog by remember { mutableStateOf(false) }
    var restorePinInput by remember { mutableStateOf("") }
    var restorePinError by remember { mutableStateOf(false) }
    var pendingRestoreTempFile by remember { mutableStateOf<java.io.File?>(null) }

    val ensureLoginAndPremium: (() -> Unit) -> Unit = { action ->
        if (!googleLoggedIn) {
            Toast.makeText(context, "Please sign in with Google to use Google Drive Backup.", Toast.LENGTH_LONG).show()
        } else if (!isPremium) {
            Toast.makeText(context, "Google Drive Backup requires Premium. Please upgrade to unlock.", Toast.LENGTH_LONG).show()
            onNavigateToPremium()
        } else {
            action()
        }
    }

    val restoreFromDrive: () -> Unit = {
        showRestoreConfirmDialog = false
        isRestoring = true
        coroutineScope.launch(Dispatchers.IO) {
            android.util.Log.d("LoreRestore", "=== STARTING RESTORE FROM GOOGLE DRIVE ===")
            val authState = DriveTokenHelper.authorizeInForeground(context)
            android.util.Log.d("LoreRestore", "Auth state result: $authState")

            if (authState !is DriveTokenHelper.DriveAuthState.HasToken) {
                withContext(Dispatchers.Main) {
                    isRestoring = false
                    when (authState) {
                        is DriveTokenHelper.DriveAuthState.NeedsConsent -> {
                            android.util.Log.d("LoreRestore", "Needs user consent — launching consent dialog")
                            try {
                                pendingPostConsentMode = "RESTORE"
                                driveConsentLauncher.launch(
                                    androidx.activity.result.IntentSenderRequest.Builder(authState.pendingIntent.intentSender).build()
                                )
                            } catch (e: Exception) {
                                android.util.Log.e("LoreRestore", "Consent launcher error: ${e.message}", e)
                                Toast.makeText(context, "Could not open Drive permission dialog: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                        is DriveTokenHelper.DriveAuthState.Failed -> {
                            android.util.Log.e("LoreRestore", "Drive auth failed: ${authState.reason}")
                            Toast.makeText(context, "Drive error: ${authState.reason}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                return@launch
            }
            val token = authState.token
            android.util.Log.d("LoreRestore", "Obtained access token (len=${token.length}, prefix=${token.take(10)}...)")

            val downloadResult = DriveBackupClient.downloadBackup(token, context)
            android.util.Log.d("LoreRestore", "Download result isSuccess=${downloadResult.isSuccess}")

            if (downloadResult.isFailure) {
                val downloadException = downloadResult.exceptionOrNull()
                android.util.Log.e("LoreRestore", "Download failed: ${downloadException?.message}", downloadException)
                withContext(Dispatchers.Main) {
                    isRestoring = false
                    Toast.makeText(context, "Restore failed: ${downloadException?.message}", Toast.LENGTH_LONG).show()
                }
                return@launch
            }

            val bytes = downloadResult.getOrThrow()
            android.util.Log.d("LoreRestore", "Downloaded ${bytes.size} bytes from Drive. Writing to temp file...")
            val tempFile = java.io.File(context.cacheDir, "drive_restore_${System.currentTimeMillis()}.lore")
            try {
                tempFile.writeBytes(bytes)
                val encryptMedia = settingsRepo.encryptMedia.first()
                val backupPin = settingsRepo.backupEncryptionKey.first() ?: settingsRepo.appPin.first()
                android.util.Log.d("LoreRestore", "Importing backup file (encryptMedia=$encryptMedia, size=${tempFile.length()} bytes)...")

                val importResult = com.gxdevs.lore.utils.BackupManager.importData(
                    context = context,
                    inputUri = android.net.Uri.fromFile(tempFile),
                    mergeMode = true,
                    reEncryptMedia = encryptMedia,
                    currentAppPin = backupPin
                )

                android.util.Log.d("LoreRestore", "Import result isSuccess=${importResult.isSuccess}")
                if (importResult.isSuccess) {
                    petViewModel.recalculateAndDownloadResourcesSuspend(context)
                }
                withContext(Dispatchers.Main) {
                    isRestoring = false
                    if (importResult.isSuccess) {
                        android.util.Log.d("LoreRestore", "Restore complete!")
                        Toast.makeText(context, "Sanctuary restored from Google Drive successfully!", Toast.LENGTH_LONG).show()
                    } else {
                        val exception = importResult.exceptionOrNull()
                        android.util.Log.e("LoreRestore", "Import failed: ${exception?.message}", exception)
                        if (exception is com.gxdevs.lore.utils.BackupEncryptedException) {
                            pendingRestoreTempFile = tempFile
                            showRestorePinPromptDialog = true
                        } else {
                            Toast.makeText(context, "Failed to restore backup: ${exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isRestoring = false
                    Toast.makeText(context, "Restore error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    LaunchedEffect(pendingPostConsentMode) {
        val mode = pendingPostConsentMode ?: return@LaunchedEffect
        if (mode == "BACKUP_AUTO_RESUME") {
            pendingPostConsentMode = null
            requestDriveBackup()
        } else if (mode == "RESTORE_AUTO_RESUME") {
            pendingPostConsentMode = null
            restoreFromDrive()
        }
    }

    if (showRestoreConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirmDialog = false },
            title = { Text("Restore Sanctuary Data", color = textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "This will download your latest backup from Google Drive and merge entries & media into your Sanctuary.",
                    color = textSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { ensureLoginAndPremium { restoreFromDrive() } }
                ) {
                    Text("RESTORE NOW", color = primaryAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRestoreConfirmDialog = false }
                ) {
                    Text("CANCEL", color = textSecondary)
                }
            },
            containerColor = cardBackground
        )
    }

    if (showRestorePinPromptDialog) {
        AlertDialog(
            onDismissRequest = {
                showRestorePinPromptDialog = false
                restorePinInput = ""
                restorePinError = false
                pendingRestoreTempFile?.delete()
                pendingRestoreTempFile = null
            },
            title = { Text("Encrypted Backup Key Required", color = textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "This backup is protected with a 6-digit security key. Enter your key to decrypt and restore your Sanctuary:",
                        color = textSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = restorePinInput,
                        onValueChange = {
                            restorePinError = false
                            if (it.length <= 8) restorePinInput = it
                        },
                        label = { Text("6-Digit Backup Key", color = textSecondary) },
                        isError = restorePinError,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (restorePinError) {
                        Text(
                            text = "Incorrect backup key. Please try again.",
                            color = Color.Red,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val file = pendingRestoreTempFile ?: return@TextButton
                        coroutineScope.launch(Dispatchers.IO) {
                            val encryptMedia = settingsRepo.encryptMedia.first()
                            val importResult = com.gxdevs.lore.utils.BackupManager.importData(
                                context = context,
                                inputUri = android.net.Uri.fromFile(file),
                                mergeMode = true,
                                reEncryptMedia = encryptMedia,
                                providedPin = restorePinInput,
                                currentAppPin = restorePinInput
                            )
                            if (importResult.isSuccess) {
                                petViewModel.recalculateAndDownloadResourcesSuspend(context)
                            }
                            withContext(Dispatchers.Main) {
                                if (importResult.isSuccess) {
                                    showRestorePinPromptDialog = false
                                    restorePinInput = ""
                                    restorePinError = false
                                    file.delete()
                                    pendingRestoreTempFile = null
                                    Toast.makeText(context, "Sanctuary restored from Google Drive successfully!", Toast.LENGTH_LONG).show()
                                } else {
                                    restorePinError = true
                                }
                            }
                        }
                    }
                ) {
                    Text("UNLOCK & RESTORE", color = primaryAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRestorePinPromptDialog = false
                        restorePinInput = ""
                        restorePinError = false
                        pendingRestoreTempFile?.delete()
                        pendingRestoreTempFile = null
                    }
                ) {
                    Text("CANCEL", color = textSecondary)
                }
            },
            containerColor = cardBackground
        )
    }

    // Dynamic stats
    val entriesRaw by journalViewModel.allEntries.collectAsState(initial = emptyList())
    val entries = if (isDecoy) emptyList() else entriesRaw

    val streakCountRaw by journalViewModel.streakDays.collectAsState(initial = 0)
    val streakCount = if (isDecoy) 0 else streakCountRaw

    val petsState by petViewModel.petsState.collectAsState()
    val spiritsCount = if (isDecoy) 0 else petsState.pets.filter { it.journalCount > 0 }.size

    // Modern Credential Manager setup (100% non-deprecated Android CredentialManager API)
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

    var isSigningIn by remember { mutableStateOf(false) }

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
                            text = "Lore Sanctuary",
                            color = Color.White.copy(alpha = 0.9f),
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
                    .fillMaxHeight(0.62f)
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
                    .background(mainContainerBackground)
                    .padding(horizontal = 24.dp, vertical = 28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    Spacer(modifier = Modifier.height(4.dp))

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Unseal the Archive.",
                            color = textPrimary,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif,
                            lineHeight = 38.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Authenticate to synchronize your lore, preserve your companions, and carry your sanctuary across realms.",
                            color = textSecondary,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // "Continue with Google" Button
                    Button(
                        onClick = {
                            if (isSigningIn) return@Button
                            val activity = context.findActivity()
                            if (activity == null) {
                                android.util.Log.e("CredentialAuth", "Cannot find Activity from context — aborting sign-in")
                                Toast.makeText(context, "Sign-in unavailable in this context", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isSigningIn = true
                            MainActivity.bypassNextLock = true
                            coroutineScope.launch {
                                try {
                                    when (val result = com.gxdevs.lore.auth.GoogleAuthManager.signIn(activity, settingsRepo)) {
                                        is com.gxdevs.lore.auth.GoogleAuthManager.AuthResult.Success -> {
                                            Toast.makeText(context, "Welcome, ${result.displayName}!", Toast.LENGTH_SHORT).show()
                                        }
                                        is com.gxdevs.lore.auth.GoogleAuthManager.AuthResult.Failure -> {
                                            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                        }
                                        is com.gxdevs.lore.auth.GoogleAuthManager.AuthResult.Cancelled -> {
                                            Toast.makeText(context, result.reason, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("CredentialAuth", "Unhandled exception during sign-in", e)
                                    Toast.makeText(context, "Sign-in failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                } finally {
                                    isSigningIn = false
                                    MainActivity.bypassNextLock = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .shadow(4.dp, RoundedCornerShape(27.dp)),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            disabledContainerColor = Color.White.copy(alpha = 0.7f)
                        ),
                        enabled = !isSigningIn,
                        shape = RoundedCornerShape(27.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (isSigningIn) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF4285F4)
                                )
                            } else {
                                GlideImage(
                                    model = "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c1/Google_%22G%22_logo.svg/120px-Google_%22G%22_logo.svg.png",
                                    contentDescription = "Google Logo",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = if (isSigningIn) "Signing in…" else "Continue with Google",
                                color = Color(0xFF1F1F1F),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ─── Lore Sanctuary entry point (Upgrade Banner if Free, Active Badge if Premium) ───
                    if (!isPremium) {
                        CompactPremiumBanner(onClick = onNavigateToPremium)
                    } else {
                        PremiumActiveBadge(onClick = onNavigateToPremium)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

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
                        text = if (!googleName.isNullOrBlank()) googleName!! else "Lore Explorer",
                        color = textPrimary,
                        fontSize = 30.sp,
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

                Spacer(modifier = Modifier.height(24.dp))

                Spacer(modifier = Modifier.height(4.dp))

                // --- Google Drive Backup section ---
                Text(
                    text = "• SECURE BACKUP",
                    color = if (isPremium) textSecondary else textSecondary.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(horizontal = 28.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Observe worker state for live progress feedback (only active while running on-demand sync)
                val workInfoList by WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWorkLiveData(DriveBackupWorker.WORK_NAME_ONDEMAND)
                    .observeAsState(emptyList())
                val isSyncing = workInfoList.any {
                    it.state == WorkInfo.State.RUNNING
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    // Drive backup card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
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
                                        tint = if (isPremium) Color.White else Color.White.copy(alpha = 0.4f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Google Drive",
                                        color = if (isPremium) Color.White else Color.White.copy(alpha = 0.4f),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (isPremium) "🔒 End-to-End Encrypted" else "🔒 Premium feature",
                                        color = Color.White.copy(alpha = 0.4f),
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            // Master Toggle Switch for Google Drive Auto-Backup
                            Switch(
                                checked = gdriveBackupEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        ensureLoginAndPremium {
                                            coroutineScope.launch(Dispatchers.IO) {
                                                val authState = DriveTokenHelper.authorizeInForeground(context)
                                                withContext(Dispatchers.Main) {
                                                    when (authState) {
                                                        is DriveTokenHelper.DriveAuthState.HasToken -> {
                                                            settingsRepo.setGdriveBackupEnabled(true)
                                                            settingsRepo.setGoogleLoggedIn(true)
                                                            Toast.makeText(context, "Google Drive Auto-Backup enabled!", Toast.LENGTH_SHORT).show()
                                                        }
                                                        is DriveTokenHelper.DriveAuthState.NeedsConsent -> {
                                                            try {
                                                                driveConsentLauncher.launch(
                                                                    androidx.activity.result.IntentSenderRequest.Builder(authState.pendingIntent.intentSender).build()
                                                                )
                                                            } catch (e: Exception) {
                                                                Toast.makeText(context, "Could not open Drive permission dialog: ${e.message}", Toast.LENGTH_LONG).show()
                                                            }
                                                        }
                                                        is DriveTokenHelper.DriveAuthState.Failed -> {
                                                            Toast.makeText(context, "Drive authorization error: ${authState.reason}", Toast.LENGTH_LONG).show()
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        coroutineScope.launch {
                                            settingsRepo.setGdriveBackupEnabled(false)
                                            Toast.makeText(context, "Google Drive Auto-Backup disabled", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                enabled = isPremium,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = cardDarkBackground,
                                    checkedTrackColor = Color.White,
                                    uncheckedThumbColor = Color.White.copy(alpha = 0.5f),
                                    uncheckedTrackColor = Color.White.copy(alpha = 0.2f),
                                    checkedBorderColor = Color.Transparent,
                                    uncheckedBorderColor = Color.Transparent,
                                    disabledCheckedThumbColor = Color.White.copy(alpha = 0.2f),
                                    disabledUncheckedThumbColor = Color.White.copy(alpha = 0.15f),
                                    disabledCheckedTrackColor = Color.White.copy(alpha = 0.1f),
                                    disabledUncheckedTrackColor = Color.White.copy(alpha = 0.08f)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (gdriveBackupEnabled && isPremium) {
                            // --- Extended Backup Controls (visible when Auto-Backup is ON) ---
                            // 1. Manual Sync Button Row
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
                                        if (isSyncing) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = Color.White
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.Sync,
                                                contentDescription = "Sync",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Sync Now",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (isSyncing) "Syncing to Google Drive..." else "Back up changes immediately",
                                            color = Color.White.copy(alpha = 0.4f),
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        if (isSyncing) return@Button
                                        ensureLoginAndPremium {
                                            requestDriveBackup()
                                        }
                                    },
                                    enabled = !isSyncing,
                                    modifier = Modifier.height(34.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        disabledContainerColor = Color.White.copy(alpha = 0.3f)
                                    ),
                                    shape = RoundedCornerShape(17.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        text = if (isSyncing) "SYNCING" else "SYNC NOW",
                                        color = cardDarkBackground,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // 2. Media Toggle inside card
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
                                            color = Color.White.copy(alpha = 0.4f),
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

                            Spacer(modifier = Modifier.height(12.dp))

                            // 3. Restore from Drive Row
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
                                        if (isRestoring) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = Color.White
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.CloudDownload,
                                                contentDescription = "Restore",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Restore from Drive",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (isRestoring) "Downloading & restoring..." else "Download & restore latest backup",
                                            color = Color.White.copy(alpha = 0.4f),
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        if (isRestoring || isSyncing) return@Button
                                        // restore confirmation dialog deferred
                                        showRestoreConfirmDialog = false
                                    },
                                    enabled = !isRestoring && !isSyncing,
                                    modifier = Modifier.height(34.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        disabledContainerColor = Color.White.copy(alpha = 0.3f)
                                    ),
                                    shape = RoundedCornerShape(17.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        text = if (isRestoring) "RESTORING" else "RESTORE",
                                        color = cardDarkBackground,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = if (gdriveHasPendingChanges) {
                                    "LAST SYNCED: ${(gdriveLastSynced ?: "NEVER").uppercase()} • CHANGES PENDING"
                                } else {
                                    "LAST SYNCED: ${(gdriveLastSynced ?: "NEVER").uppercase()} • UP TO DATE"
                                },
                                color = if (gdriveHasPendingChanges) Color(0xFFD9A05B) else Color.White.copy(alpha = 0.4f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        } else {
                            // --- Disabled State Explanation ---
                            Text(
                                text = if (isPremium) {
                                    "Auto-backup is disabled. Turn on the switch above to automatically back up your sanctuary entries to Google Drive."
                                } else {
                                    "AVAILABLE WITH LORE SANCTUARY"
                                },
                                color = Color.White.copy(alpha = if (isPremium) 0.5f else 0.25f),
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Premium lock overlay for free users
                    if (!isPremium) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(RoundedCornerShape(32.dp))
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable { onNavigateToPremium() },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = "Premium required",
                                    tint = Color(0xFFD4AF37),
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Lore Sanctuary exclusive",
                                    color = Color(0xFFD4AF37),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Tap to upgrade",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // --- Subscription Section ---
                Text(
                    text = "• SUBSCRIPTION",
                    color = textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(horizontal = 28.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (isPremium) {
                    // ── PREMIUM ACTIVE CARD ──────────────────────────────────────────
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .background(
                                androidx.compose.ui.graphics.Brush.linearGradient(
                                    listOf(Color(0xFF2A3320), Color(0xFF1E2818))
                                )
                            )
                            .border(1.dp, Color(0xFF8FA876).copy(alpha = 0.4f), RoundedCornerShape(32.dp))
                            .padding(24.dp)
                    ) {
                        // Header row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF606F49).copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.WorkspacePremium,
                                    contentDescription = "Premium",
                                    tint = Color(0xFFFFE599),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Lore Sanctuary",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "• ACTIVE MEMBERSHIP",
                                    color = Color(0xFF8FA876),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF606F49).copy(alpha = 0.3f))
                                    .border(1.dp, Color(0xFF8FA876).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "PRO ✓",
                                    color = Color(0xFFFFE599),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Perks list
                        val perks = listOf(
                            Icons.Rounded.Pets to "3× faster companion growth",
                            Icons.Rounded.Mic to "Unlimited voice journaling",
                            Icons.Rounded.Shield to "End-to-end encrypted backups",
                            Icons.Rounded.Star to "Exclusive relic & spirit unlocks",
                            Icons.Rounded.Cloud to "Priority Google Drive sync"
                        )
                        perks.forEach { (icon, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = Color(0xFF8FA876),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = label,
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Manage subscription
                        Button(
                            onClick = {
                                try {
                                    context.findActivity()?.let {
                                        it.startActivity(
                                            android.content.Intent(context, com.gxdevs.lore.ui.premium.PremiumActivity::class.java)
                                        )
                                    } ?: context.startActivity(
                                        android.content.Intent(context, com.gxdevs.lore.ui.premium.PremiumActivity::class.java)
                                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Opening subscription manager…", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF606F49)),
                            shape = RoundedCornerShape(25.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Settings,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "MANAGE MEMBERSHIP",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                } else {
                    // ── FREE / WANDERER CARD ─────────────────────────────────────────
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .background(cardDarkBackground)
                            .padding(24.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Wanderer",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "• FREE PLAN",
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Locked perks (what they're missing)
                        val lockedPerks = listOf(
                            "3× faster companion growth" to false,
                            "Unlimited voice journaling" to false,
                            "Encrypted cloud backups" to false,
                            "Exclusive relic & spirit unlocks" to false,
                            "Basic journaling — always free" to true
                        )
                        lockedPerks.forEach { (label, included) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (included) Icons.Rounded.CheckCircle else Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = if (included) Color(0xFF8FA876) else Color.White.copy(alpha = 0.25f),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = label,
                                    color = if (included) Color.White.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.35f),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Upgrade button — opens PremiumActivity
                        Button(
                            onClick = {
                                onNavigateToPremium()
                                try {
                                    context.startActivity(
                                        android.content.Intent(context, com.gxdevs.lore.ui.premium.PremiumActivity::class.java)
                                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFD4AF37).copy(alpha = 0.15f)
                            ),
                            shape = RoundedCornerShape(25.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .border(1.dp, Color(0xFFD4AF37).copy(alpha = 0.5f), RoundedCornerShape(25.dp))
                                    .fillMaxSize(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.WorkspacePremium,
                                    contentDescription = null,
                                    tint = Color(0xFFD4AF37),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "UNLOCK LORE SANCTUARY",
                                    color = Color(0xFFD4AF37),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                if (googleLoggedIn) {
                    // Google logout button
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                com.gxdevs.lore.auth.GoogleAuthManager.signOut(context, settingsRepo)
                                Toast.makeText(context, "Sanctuary sealed.", Toast.LENGTH_SHORT).show()
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
}

/**
 * Slim single-row banner shown in Profile & Settings when user is NOT premium.
 * Tapping navigates to the dedicated PremiumActivity.
 */
@Composable
fun CompactPremiumBanner(onClick: () -> Unit) {
    val context = LocalContext.current
    val inf = rememberInfiniteTransition(label = "bannerGlow")
    val borderA by inf.animateFloat(
        0.45f, 0.95f,
        infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b"
    )
    val goldGrad = listOf(Color(0xFFB88E10), Color(0xFFD4AF37), Color(0xFF99750C), Color(0xFFB88E10))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFEAE7DF))
            .border(1.5.dp, Brush.linearGradient(goldGrad.map { it.copy(alpha = borderA) }), RoundedCornerShape(18.dp))
            .clickable {
                onClick()
                try {
                    context.startActivity(android.content.Intent(context, com.gxdevs.lore.ui.premium.PremiumActivity::class.java))
                } catch (_: Exception) {}
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFFFAF4E1)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.WorkspacePremium, null, tint = Color(0xFFB88E10), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Lore Sanctuary",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E332A)
            )
            Text(
                "Unlock 3x pet growth & unlimited voice",
                fontSize = 11.sp,
                color = Color(0xFF727869)
            )
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFFAF4E1),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFB88E10).copy(alpha = 0.4f))
        ) {
            Text(
                "50% OFF",
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFB88E10),
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Color(0xFF727869), modifier = Modifier.size(16.dp))
    }
}

/**
 * Active membership badge shown in Profile & Settings when user IS premium.
 * Tapping opens PremiumActivity (where Dev Test Mode toggle is available).
 */
@Composable
fun PremiumActiveBadge(onClick: () -> Unit = {}) {
    val context = LocalContext.current
    val inf = rememberInfiniteTransition(label = "activeGlow")
    val glowAlpha by inf.animateFloat(
        0.35f, 0.75f,
        infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "aGlow"
    )
    val greenGrad = listOf(Color(0xFF606F49), Color(0xFF425139))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(greenGrad))
            .border(1.5.dp, Color(0xFF8FA876).copy(alpha = glowAlpha), RoundedCornerShape(18.dp))
            .clickable {
                onClick()
                try {
                    context.startActivity(android.content.Intent(context, com.gxdevs.lore.ui.premium.PremiumActivity::class.java))
                } catch (_: Exception) {}
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.WorkspacePremium, null, tint = Color(0xFFFFE599), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Lore Sanctuary",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                "Active Membership · All features unlocked",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color.White.copy(alpha = 0.2f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
        ) {
            Text(
                "PRO ✓",
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFFFE599),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
    }
}

