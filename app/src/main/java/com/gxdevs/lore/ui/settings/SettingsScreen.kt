package com.gxdevs.lore.ui.settings

import android.net.Uri
import com.gxdevs.lore.MainActivity
import androidx.biometric.BiometricManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gxdevs.lore.data.AppDatabase
import com.gxdevs.lore.data.SettingsRepository
import com.gxdevs.lore.utils.cancelDailyReminder
import com.gxdevs.lore.utils.scheduleDailyReminder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.fragment.app.FragmentActivity
import android.view.WindowManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gxdevs.lore.R
import com.gxdevs.lore.ui.pets.PetViewModel
import com.gxdevs.lore.ui.profile.CompactPremiumBanner
import com.gxdevs.lore.ui.profile.PremiumActiveBadge

private val mainContainerBackground = Color(0xFFF4F1EA)
private val borderColor = Color(0xFFE0DCD1)
private val cardBackground = Color(0xFFEAE7DF)
private val textPrimary = Color(0xFF2E332A)
private val textSecondary = Color(0xFF828779)
private val primaryAccent = Color(0xFF606F49)
private val accentBackground = Color(0xFFD9DFCD)
private val darkAccent = Color(0xFF4A5638)
private val dangerBackground = Color(0xFFF9EAE6)
private val dangerText = Color(0xFFC06352)
private val dangerCard = Color(0xFFF4E0DB)

@Composable
fun SettingsScreen(
    onNavigateToPremium: () -> Unit = {}
) {
    val context      = LocalContext.current
    val settingsRepo = remember { SettingsRepository(context) }
    val db           = remember { AppDatabase.getDatabase(context) }
    val petViewModel: PetViewModel = viewModel()

    val premiumManager = remember { com.gxdevs.lore.utils.PremiumManager.getInstance(context) }
    val isPremium by premiumManager.isPremium.collectAsState()

    val showProgressDialog = remember { mutableStateOf(false) }
    var progressMessage by remember { mutableStateOf("") }
    var showBackupPinBanner by remember { mutableStateOf(false) }
    var triggerPinSetup by remember { mutableStateOf(false) }

    val appLockEnabled    by settingsRepo.appLockEnabled.collectAsState(initial = false)
    val hideMedia         by settingsRepo.hideMediaInGallery.collectAsState(initial = false)
    val decoyPin          by settingsRepo.decoyPin.collectAsState(initial = false)
    val appPin            by settingsRepo.appPin.collectAsState(initial = null)
    val useBiometric      by settingsRepo.useBiometricLock.collectAsState(initial = true)
    val dailyReminder     by settingsRepo.dailyReminder.collectAsState(initial = true)
    val companionAlerts   by settingsRepo.companionAlerts.collectAsState(initial = true)
    val relicAlerts       by settingsRepo.relicAlerts.collectAsState(initial = true)
    val pastPrompts       by settingsRepo.pastPrompts.collectAsState(initial = false)
    val autoSaveFrequency by settingsRepo.autoSaveFrequency.collectAsState(initial = 15f)
    val autoLockDelay     by settingsRepo.autoLockDelay.collectAsState(initial = 0)
    val reminderHour      by settingsRepo.reminderHour.collectAsState(initial = 10)
    val reminderMinute    by settingsRepo.reminderMinute.collectAsState(initial = 0)
    val blurJournals      by settingsRepo.blurJournals.collectAsState(initial = false)
    val encryptMedia      by settingsRepo.encryptMedia.collectAsState(initial = false)
    val backupEncryptionKey by settingsRepo.backupEncryptionKey.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val activity = context as? FragmentActivity

    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var showBackupPinPromptDialog by remember { mutableStateOf(false) }
    var showSetBackupKeyDialog by remember { mutableStateOf(false) }
    var showOldMediaWarningDialog by remember { mutableStateOf(false) }
    var pendingBackupKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(hideMedia) {
        activity?.window?.let { w ->
            if (hideMedia) w.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            else           w.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    val biometricAvailable = remember {
        BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    var exportIncludeMedia by remember { mutableStateOf(false) }
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let {
            scope.launch {
                showProgressDialog.value = true
                progressMessage = "Exporting data... Please wait."
                val result = com.gxdevs.lore.utils.BackupManager.exportData(
                    context = context,
                    outputUri = it,
                    includeMedia = exportIncludeMedia,
                    encryptBackup = encryptMedia,
                    backupPin = backupEncryptionKey ?: appPin
                )
                showProgressDialog.value = false
                if (result.isSuccess) {
                    val missing = result.getOrNull() ?: 0
                    if (missing > 0) {
                        android.widget.Toast.makeText(context, "Exported successfully, but $missing media files were not found on device.", android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        android.widget.Toast.makeText(context, "Backup exported successfully", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    android.widget.Toast.makeText(context, "Failed to export backup", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    var importMergeMode by remember { mutableStateOf(false) }
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                showProgressDialog.value = true
                progressMessage = "Importing data... Please wait."
                val result = com.gxdevs.lore.utils.BackupManager.importData(
                    context = context,
                    inputUri = it,
                    mergeMode = importMergeMode,
                    reEncryptMedia = encryptMedia,
                    currentAppPin = backupEncryptionKey ?: appPin
                )
                if (result.isSuccess) {
                    progressMessage = "Syncing resources... Please wait."
                    petViewModel.recalculateAndDownloadResources(context)
                    showProgressDialog.value = false
                    android.widget.Toast.makeText(context, "Data imported successfully", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    showProgressDialog.value = false
                    val exception = result.exceptionOrNull()
                    if (exception is com.gxdevs.lore.utils.BackupEncryptedException) {
                        pendingImportUri = it
                        showBackupPinPromptDialog = true
                    } else {
                        android.widget.Toast.makeText(context, "Failed to import data: ${exception?.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    if (showProgressDialog.value) {
        AlertDialog(
            onDismissRequest = { /* Cannot dismiss */ },
            title = { Text("Processing", color = textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = primaryAccent, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(progressMessage, color = textSecondary)
                }
            },
            confirmButton = {},
            containerColor = cardBackground
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SettingsScreenUI(
            isPremium            = isPremium,
            onNavigateToPremium  = onNavigateToPremium,
            appLockEnabled       = appLockEnabled,
            screenshotProtection = hideMedia,
            decoyPin             = decoyPin,
            appPin               = appPin,
            useBiometric         = useBiometric,
            biometricAvailable   = biometricAvailable,
            dailyReminder        = dailyReminder,
            reminderHour         = reminderHour,
            reminderMinute       = reminderMinute,
            blurJournals         = blurJournals,
            companionAlerts      = companionAlerts,
            relicAlerts          = relicAlerts,
            pastPrompts          = pastPrompts,
            autoSaveFrequency    = autoSaveFrequency,
            triggerPinSetup      = triggerPinSetup,
            onPinSetupStarted    = { triggerPinSetup = false },
            onAppLockToggle = { enabled ->
                if (enabled) {
                    if (biometricAvailable && useBiometric) {
                        val secMgr = com.gxdevs.lore.utils.SecurityManager(context)
                        secMgr.authenticate(
                            activity = activity ?: return@SettingsScreenUI,
                            title    = "Enable App Lock",
                            subtitle = "Confirm your identity",
                            onSuccess = {
                                scope.launch { settingsRepo.setAppLockEnabled(true) }
                                if (appPin == null) {
                                    showBackupPinBanner = true
                                }
                            },
                            onError   = {}
                        )
                    }
                    // PIN setup is handled inside SettingsScreenUI via onAppPinSave
                } else {
                    scope.launch {
                        settingsRepo.setAppLockEnabled(false)
                        settingsRepo.setAppPin(null)
                        settingsRepo.setRealPin(null)
                    }
                }
            },
            onLockMethodChange = { wantBiometric ->
                scope.launch { settingsRepo.setUseBiometricLock(wantBiometric) }
                if (wantBiometric && biometricAvailable) {
                    val secMgr = com.gxdevs.lore.utils.SecurityManager(context)
                    secMgr.authenticate(
                        activity = activity ?: return@SettingsScreenUI,
                        title    = "Confirm Biometric Lock",
                        subtitle = "Authenticate to switch lock method",
                        onSuccess = {
                            scope.launch { settingsRepo.setUseBiometricLock(true) }
                            // Do not clear appPin here so it remains a backup
                            if (appPin == null) {
                                showBackupPinBanner = true
                            }
                        },
                        onError = { scope.launch { settingsRepo.setUseBiometricLock(false) } }
                    )
                } else if (!wantBiometric) {
                    if (appPin == null) {
                        triggerPinSetup = true
                    }
                }
            },
        onAppPinSave = { pin -> scope.launch {
            settingsRepo.setAppPin(pin)
            settingsRepo.setRealPin(pin)
            settingsRepo.setAppLockEnabled(true)
            if (!biometricAvailable || !useBiometric) {
                settingsRepo.setUseBiometricLock(false)
            }
        }},
        onChangeRealPin = { newPin -> scope.launch {
            settingsRepo.setAppPin(newPin)
            settingsRepo.setRealPin(newPin)
        }},
        onChangeDecoyPin = { newDecoy -> scope.launch {
            settingsRepo.setDecoyPinValue(newDecoy)
        }},
        onScreenshotToggle = { enabled ->
            scope.launch { settingsRepo.setHideMediaInGallery(enabled) }
            activity?.window?.let { w ->
                if (enabled) w.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else         w.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        },
        onDecoyPinToggle = { isEnabled, _, _ ->
            scope.launch {
                if (isEnabled) { settingsRepo.setDecoyPin(true) }
                else {
                    settingsRepo.setDecoyPinValue(null)
                    settingsRepo.setDecoyPin(false)
                }
            }
        },
        onDailyReminderToggle    = { enabled ->
            scope.launch { settingsRepo.setDailyReminder(enabled) }
            if (enabled) scheduleDailyReminder(context, reminderHour, reminderMinute)
            else cancelDailyReminder(context)
        },
        onCompanionAlertsToggle  = { scope.launch { settingsRepo.setCompanionAlerts(it) } },
        onRelicAlertsToggle      = { scope.launch { settingsRepo.setRelicAlerts(it) } },
        onPastPromptsToggle      = { scope.launch { settingsRepo.setPastPrompts(it) } },
        onAutoSaveFrequencyChange = { scope.launch { settingsRepo.setAutoSaveFrequency(it) } },
        onReminderTimeChange     = { h, m ->
            scope.launch { settingsRepo.setReminderTime(h, m) }
            if (dailyReminder) scheduleDailyReminder(context, h, m)
        },
        onDeleteAll = { scope.launch {
            db.journalDao().deleteAllEntries()
            settingsRepo.setRealPin(null)
            settingsRepo.setDecoyPinValue(null)
            settingsRepo.setDecoyPin(false)
            settingsRepo.setIsDecoyMode(false)
            settingsRepo.setAppPin(null)
            settingsRepo.setAppLockEnabled(false)
        }},
        onExportData = { includeMedia ->
            exportIncludeMedia = includeMedia
            MainActivity.bypassNextLock = true
            exportLauncher.launch("aethra_backup.aeth")
        },
        onImportData = { mergeMode ->
            importMergeMode = mergeMode
            MainActivity.bypassNextLock = true
            importLauncher.launch(arrayOf("application/octet-stream", "*/*"))
        },
        onBlurJournalsToggle = { enabled ->
            scope.launch { settingsRepo.setBlurJournals(enabled) }
        },
        encryptMedia      = encryptMedia,
        onEncryptMediaToggle = { enabled ->
            scope.launch {
                if (enabled) {
                    if (backupEncryptionKey.isNullOrBlank()) {
                        showSetBackupKeyDialog = true
                    } else {
                        showOldMediaWarningDialog = true
                    }
                } else {
                    settingsRepo.setEncryptMedia(false)
                }
            }
        },
        autoLockDelay = autoLockDelay,
        onAutoLockDelayChange = { scope.launch { settingsRepo.setAutoLockDelay(it) } }
    )

        // ── Backup PIN animated banner (slides up from bottom) ─────────────────
        BackupPinBanner(
            visible   = showBackupPinBanner,
            onSetPin  = { showBackupPinBanner = false; triggerPinSetup = true },
            onDismiss = { showBackupPinBanner = false },
            modifier  = Modifier.align(Alignment.BottomCenter)
        )

        // ─── Dialogs for Encrypted Backup / Set 6-Digit Encryption Key ───────
        var backupPinInput by remember { mutableStateOf("") }
        var backupPinError by remember { mutableStateOf(false) }

        if (showBackupPinPromptDialog) {
            AlertDialog(
                onDismissRequest = {
                    showBackupPinPromptDialog = false
                    pendingImportUri = null
                    backupPinInput = ""
                    backupPinError = false
                },
                title = { Text("Backup is Encrypted", color = textPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("This backup file is encrypted and password-protected. Please enter the 6-digit backup key or App PIN used to secure it.", color = textSecondary)
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = backupPinInput,
                            onValueChange = {
                                backupPinError = false
                                if (it.length <= 8) backupPinInput = it
                            },
                            label = { Text("6-Digit Backup Key / PIN", color = textSecondary) },
                            singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                            ),
                            isError = backupPinError,
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedTextColor = textPrimary,
                                unfocusedTextColor = textPrimary,
                                focusedIndicatorColor = primaryAccent,
                                unfocusedIndicatorColor = borderColor,
                                focusedLabelColor = primaryAccent,
                                unfocusedLabelColor = textSecondary
                            )
                        )
                        if (backupPinError) {
                            Text("Incorrect key/password. Please try again.", color = dangerText, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val uri = pendingImportUri
                        if (uri != null) {
                            scope.launch {
                                showProgressDialog.value = true
                                progressMessage = "Importing data... Please wait."
                                val result = com.gxdevs.lore.utils.BackupManager.importData(
                                    context = context,
                                    inputUri = uri,
                                    mergeMode = importMergeMode,
                                    reEncryptMedia = encryptMedia,
                                    providedPin = backupPinInput,
                                    currentAppPin = backupEncryptionKey ?: appPin
                                )
                                if (result.isSuccess) {
                                    progressMessage = "Syncing resources... Please wait."
                                    petViewModel.recalculateAndDownloadResources(context)
                                    showProgressDialog.value = false
                                    showBackupPinPromptDialog = false
                                    pendingImportUri = null
                                    backupPinInput = ""
                                    android.widget.Toast.makeText(context, "Data imported successfully", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    showProgressDialog.value = false
                                    val exception = result.exceptionOrNull()
                                    if (exception is com.gxdevs.lore.utils.BackupEncryptedException) {
                                        backupPinError = true
                                    } else {
                                        showBackupPinPromptDialog = false
                                        pendingImportUri = null
                                        backupPinInput = ""
                                        android.widget.Toast.makeText(context, "Failed to import data: ${exception?.message}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }) { Text("Decrypt & Import", color = primaryAccent, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showBackupPinPromptDialog = false
                        pendingImportUri = null
                        backupPinInput = ""
                        backupPinError = false
                    }) { Text("Cancel", color = textSecondary) }
                },
                containerColor = cardBackground
            )
        }

        var setKeyInput by remember { mutableStateOf("") }
        var setKeyError by remember { mutableStateOf(false) }

        if (showSetBackupKeyDialog) {
            AlertDialog(
                onDismissRequest = {
                    showSetBackupKeyDialog = false
                    setKeyInput = ""
                    setKeyError = false
                },
                title = { Text("Set 6-Digit Encryption Key", color = textPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            "Create a 6-digit security key to encrypt your backups. All database records and media inside your backup files will be fully secure.",
                            color = textSecondary,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // WARNING NOTICE
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(dangerBackground)
                                .border(1.dp, dangerCard, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                "\u26A0\uFE0F WARNING: Losing this key will lead to permanent loss of your backup data! Keep it safe. It is NOT changeable by any means.",
                                color = dangerText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = setKeyInput,
                            onValueChange = {
                                setKeyError = false
                                if (it.all { char -> char.isDigit() } && it.length <= 6) {
                                    setKeyInput = it
                                }
                            },
                            label = { Text("6-Digit Encryption Key", color = textSecondary) },
                            singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                            ),
                            isError = setKeyError,
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedTextColor = textPrimary,
                                unfocusedTextColor = textPrimary,
                                focusedIndicatorColor = primaryAccent,
                                unfocusedIndicatorColor = borderColor,
                                focusedLabelColor = primaryAccent,
                                unfocusedLabelColor = textSecondary
                            )
                        )
                        if (setKeyError) {
                            Text("The key must be exactly 6 digits.", color = dangerText, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (setKeyInput.length == 6) {
                            pendingBackupKey = setKeyInput
                            showSetBackupKeyDialog = false
                            setKeyInput = ""
                            showOldMediaWarningDialog = true
                        } else {
                            setKeyError = true
                        }
                    }) { Text("Confirm & Enable", color = primaryAccent, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showSetBackupKeyDialog = false
                        setKeyInput = ""
                        setKeyError = false
                    }) { Text("Cancel", color = textSecondary) }
                },
                containerColor = cardBackground
            )
        }

        if (showOldMediaWarningDialog) {
            AlertDialog(
                onDismissRequest = {
                    showOldMediaWarningDialog = false
                    pendingBackupKey = null
                },
                title = { Text("Encryption Info", color = textPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        text = "Your old media (if any exists) is not automatically encrypted; only new media will be encrypted.\n\n" +
                               "To encrypt older media, you must edit the old journal entry, remove the media items, and re-add them.",
                        color = textSecondary,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            val key = pendingBackupKey
                            if (key != null) {
                                settingsRepo.setBackupEncryptionKey(key)
                            }
                            settingsRepo.setEncryptMedia(true)
                            showOldMediaWarningDialog = false
                            pendingBackupKey = null
                            android.widget.Toast.makeText(context, "Media encryption enabled. Only new media will be encrypted.", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }) { Text("Got it", color = primaryAccent, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showOldMediaWarningDialog = false
                        pendingBackupKey = null
                    }) { Text("Cancel", color = textSecondary) }
                },
                containerColor = cardBackground
            )
        }
    }
}

// PIN dialog stage for the unified state machine
enum class PinStage {
    NONE, SETUP_NEW, SETUP_CONFIRM, VERIFY,
    CHANGE_REAL, CHANGE_REAL_CONFIRM, CHANGE_DECOY, SETUP_DECOY
}

@Composable
fun SettingsScreenUI(
    isPremium: Boolean            = false,
    onNavigateToPremium: () -> Unit = {},
    appLockEnabled: Boolean       = false,
    screenshotProtection: Boolean = false,
    decoyPin: Boolean             = false,
    appPin: String?               = null,
    useBiometric: Boolean         = true,
    biometricAvailable: Boolean   = true,
    dailyReminder: Boolean        = true,
    reminderHour: Int             = 10,
    reminderMinute: Int           = 0,
    companionAlerts: Boolean      = true,
    relicAlerts: Boolean          = true,
    pastPrompts: Boolean          = false,
    autoSaveFrequency: Float      = 15f,
    onAppLockToggle: (Boolean) -> Unit                    = {},
    onLockMethodChange: (Boolean) -> Unit                 = {},
    onAppPinSave: (String) -> Unit                        = {},
    onChangeRealPin: (String) -> Unit                     = {},
    onChangeDecoyPin: (String) -> Unit                    = {},
    onScreenshotToggle: (Boolean) -> Unit                 = {},
    onDecoyPinToggle: (Boolean, String?, String?) -> Unit = { _, _, _ -> },
    onDailyReminderToggle: (Boolean) -> Unit              = {},
    onCompanionAlertsToggle: (Boolean) -> Unit            = {},
    onRelicAlertsToggle: (Boolean) -> Unit                = {},
    onPastPromptsToggle: (Boolean) -> Unit                = {},
    onAutoSaveFrequencyChange: (Float) -> Unit            = {},
    onReminderTimeChange: (Int, Int) -> Unit              = { _, _ -> },
    onDeleteAll: () -> Unit                               = {},
    onExportData: (Boolean) -> Unit                       = {},
    onImportData: (Boolean) -> Unit                       = {},
    blurJournals: Boolean                                 = false,
    onBlurJournalsToggle: (Boolean) -> Unit               = {},
    encryptMedia: Boolean                                 = false,
    onEncryptMediaToggle: (Boolean) -> Unit               = {},
    triggerPinSetup: Boolean                              = false,
    onPinSetupStarted: () -> Unit                         = {},
    autoLockDelay: Int                                    = 0,
    onAutoLockDelayChange: (Int) -> Unit                  = {}
) {
    var saveFrequency by remember(autoSaveFrequency) { mutableFloatStateOf(autoSaveFrequency) }
    var lockDelay     by remember(autoLockDelay)     { mutableIntStateOf(autoLockDelay) }
    LocalContext.current

    var showFeatureDialog by remember { mutableStateOf(false) }
    var featureTitle      by remember { mutableStateOf("") }
    var featureSubtitle   by remember { mutableStateOf("") }
    var featureIcon       by remember { mutableStateOf(Icons.Rounded.WorkspacePremium) }

    if (showFeatureDialog) {
        com.gxdevs.lore.ui.components.LoreScanturyFeatureDialog(
            title = featureTitle,
            subtitle = featureSubtitle,
            icon = featureIcon,
            onUnlock = {
                showFeatureDialog = false
                onNavigateToPremium()
            },
            onDismiss = { showFeatureDialog = false }
        )
    }

    var pinStage  by remember { mutableStateOf(PinStage.NONE) }
    var pinInput      by remember { mutableStateOf("") }
    val pinError      = remember { mutableStateOf(false) }
    var pinBuffer     by remember { mutableStateOf("") } // stores first-entry for confirm steps

    LaunchedEffect(triggerPinSetup) {
        if (triggerPinSetup) {
            pinStage = PinStage.SETUP_NEW
            pinInput = ""
            pinError.value = false
            onPinSetupStarted()
        }
    }

    fun resetPin() { pinStage = PinStage.NONE; pinInput = ""; pinError.value = false; pinBuffer = "" }

    fun handlePinDigit(d: String) {
        if (pinInput.length >= 4) return
        pinError.value = false
        pinInput += d
        if (pinInput.length < 4) return
        when (pinStage) {
            PinStage.SETUP_NEW -> { pinBuffer = pinInput; pinInput = ""; pinStage = PinStage.SETUP_CONFIRM }
            PinStage.SETUP_CONFIRM -> {
                if (pinInput == pinBuffer) { onAppPinSave(pinInput); resetPin() }
                else { pinError.value = true; pinInput = "" }
            }
            PinStage.VERIFY -> {
                if (pinInput == appPin) {
                    // After verification, decide what comes next based on what was pending
                    val wasSetupDecoy = pinBuffer == "DECOY"
                    val wasChangeReal = pinBuffer == "REAL"
                    pinBuffer = ""; pinInput = ""
                    pinStage = when { wasSetupDecoy -> PinStage.CHANGE_DECOY; wasChangeReal -> PinStage.CHANGE_REAL; else -> PinStage.NONE }
                } else { pinError.value = true; pinInput = "" }
            }
            PinStage.CHANGE_REAL -> { pinBuffer = pinInput; pinInput = ""; pinStage = PinStage.CHANGE_REAL_CONFIRM }
            PinStage.CHANGE_REAL_CONFIRM -> {
                if (pinInput == pinBuffer) { onChangeRealPin(pinInput); resetPin() }
                else { pinError.value = true; pinInput = "" }
            }
            PinStage.CHANGE_DECOY -> {
                if (pinInput == appPin) { pinError.value = true; pinInput = "" } // decoy can't equal real
                else { 
                    onChangeDecoyPin(pinInput)
                    onDecoyPinToggle(true, null, null)
                    resetPin() 
                }
            }
            PinStage.SETUP_DECOY -> {
                if (pinInput == appPin) { pinError.value = true; pinInput = "" }
                else { onChangeDecoyPin(pinInput); onDecoyPinToggle(true, null, null); resetPin() }
            }
            PinStage.NONE -> {}
        }
    }

    val pinDialogVisible = pinStage != PinStage.NONE
    val pinDialogTitle = when (pinStage) {
        PinStage.SETUP_NEW          -> "Create App PIN"
        PinStage.SETUP_CONFIRM      -> "Confirm App PIN"
        PinStage.VERIFY             -> "Verify Current PIN"
        PinStage.CHANGE_REAL        -> "New Main PIN"
        PinStage.CHANGE_REAL_CONFIRM -> "Confirm New PIN"
        PinStage.CHANGE_DECOY, PinStage.SETUP_DECOY -> "Set Decoy PIN"
        PinStage.NONE               -> ""
    }
    val pinDialogSubtitle = when (pinStage) {
        PinStage.SETUP_NEW          -> "Enter a 4-digit PIN to lock the app"
        PinStage.SETUP_CONFIRM      -> "Re-enter your PIN to confirm"
        PinStage.VERIFY             -> "Enter your current PIN to continue"
        PinStage.CHANGE_REAL        -> "Enter your new main PIN"
        PinStage.CHANGE_REAL_CONFIRM -> "Re-enter to confirm the new PIN"
        PinStage.CHANGE_DECOY, PinStage.SETUP_DECOY -> "This PIN shows a blank journal"
        PinStage.NONE               -> ""
    }

    if (pinDialogVisible) {
        PinSetupDialog(
            title     = pinDialogTitle,
            subtitle  = pinDialogSubtitle,
            input     = pinInput,
            isError   = pinError.value,
            onDigit   = { handlePinDigit(it) },
            onBackspace = { if (pinInput.isNotEmpty()) pinInput = pinInput.dropLast(1); pinError.value = false },
            onDismiss = { resetPin() }
        )
    }

    // ── Delete dialog ──────────────────────────────────────────────────
    val deleteTextInput = remember { mutableStateOf("") }
    val showDeleteConfirmDialog = remember { mutableStateOf(false) }

    if (showDeleteConfirmDialog.value) {
        AlertDialog(
            onDismissRequest = { 
                showDeleteConfirmDialog.value = false 
                deleteTextInput.value = ""
            },
            title = { Text("Confirm Deletion", color = dangerText, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("This action cannot be undone. To proceed, please type 'DELETE' below:", color = textPrimary, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = deleteTextInput.value,
                        onValueChange = { deleteTextInput.value = it },
                        singleLine = true,
                        placeholder = { Text("DELETE", color = textSecondary) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary,
                            focusedIndicatorColor = dangerText,
                            unfocusedIndicatorColor = dangerText.copy(alpha = 0.5f),
                            cursorColor = dangerText
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (deleteTextInput.value == "DELETE") {
                            onDeleteAll()
                            showDeleteConfirmDialog.value = false
                            deleteTextInput.value = ""
                        }
                    }
                ) { Text("Delete", color = dangerText, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showDeleteConfirmDialog.value = false 
                    deleteTextInput.value = ""
                }) { Text("Cancel", color = textSecondary) }
            },
            containerColor = dangerCard
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(mainContainerBackground)
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Settings.",
            color = textPrimary,
            fontSize = 36.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Serif,
            letterSpacing = (-1).sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Mold your sanctuary to your rhythm and privacy needs.",
            color = textSecondary,
            fontSize = 15.sp,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(22.dp))

        // ── Lore Scantury Banner / Active Badge ─────────────────────────────────────
        if (!isPremium) {
            CompactPremiumBanner(onClick = onNavigateToPremium)
        } else {
            PremiumActiveBadge(onClick = onNavigateToPremium)
        }
        Spacer(modifier = Modifier.height(22.dp))

        // PRIVACY & SECURITY
        SettingsSection(
            title = "PRIVACY & SECURITY",
            icon  = Icons.Outlined.Lock
        ) {
            // ── App Lock toggle ────────────────────────────────────────────
            val pinMode = !useBiometric || !biometricAvailable
            val lockSub = when {
                !appLockEnabled   -> "Tap to enable app lock."
                !pinMode          -> "Biometric lock active."
                appPin != null    -> "PIN lock active."
                else              -> "Tap to set up a PIN."
            }
            SettingsSwitchItem(
                icon     = if (!pinMode) Icons.Rounded.Fingerprint else Icons.Outlined.Lock,
                title    = "App Lock",
                subtitle = lockSub,
                checked  = appLockEnabled,
                onCheckedChange = { enabled ->
                    if (enabled && pinMode && appPin == null) {
                        pinStage = PinStage.SETUP_NEW; pinInput = ""
                    } else {
                        onAppLockToggle(enabled)
                    }
                }
            )

            // ── Lock method selector (only when biometric is available) ──────────
            AnimatedVisibility(
                visible = appLockEnabled && biometricAvailable,
                enter = expandVertically(tween(220)),
                exit  = shrinkVertically(tween(180))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accentBackground.copy(alpha = 0.5f))
                        .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    // Biometric option
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (!pinMode) primaryAccent else Color.Transparent)
                            .clickable { onLockMethodChange(true) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.Fingerprint, null,
                                tint = if (!pinMode) mainContainerBackground else textSecondary,
                                modifier = Modifier.size(16.dp))
                            Text("Biometric",
                                color = if (!pinMode) mainContainerBackground else textSecondary,
                                fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    // PIN option
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (pinMode) primaryAccent else Color.Transparent)
                            .clickable { onLockMethodChange(false) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Outlined.Lock, null,
                                tint = if (pinMode) mainContainerBackground else textSecondary,
                                modifier = Modifier.size(16.dp))
                            Text("PIN",
                                color = if (pinMode) mainContainerBackground else textSecondary,
                                fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ── Custom PIN section (visible when PIN mode is active) ──────────
            AnimatedVisibility(
                visible = appLockEnabled && pinMode,
                enter = expandVertically(tween(250)),
                exit  = shrinkVertically(tween(200))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(cardBackground)
                        .border(1.dp, borderColor, RoundedCornerShape(18.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                        Icon(Icons.Outlined.Key, null, tint = primaryAccent, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("CUSTOM PIN", color = primaryAccent, fontSize = 11.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }

                    // Main PIN card
                    PinActionCard(
                        icon     = Icons.Outlined.Lock,
                        title    = "Main PIN",
                        subtitle = if (appPin != null) "Tap to change your unlock PIN" else "Tap to set up your PIN",
                        onClick  = {
                            if (appPin != null) {
                                // Verify first, then change
                                pinBuffer = "REAL"; pinInput = ""; pinError.value = false
                                pinStage = PinStage.VERIFY
                            } else {
                                pinStage = PinStage.SETUP_NEW; pinInput = ""; pinError.value = false
                            }
                        }
                    )

                    // Decoy PIN toggle + card
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.VisibilityOff, null, tint = textSecondary, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Decoy PIN", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Shows blank journal on fake PIN", color = textSecondary, fontSize = 11.sp)
                        }
                        Switch(
                            checked = decoyPin && isPremium,
                            onCheckedChange = {
                                if (!isPremium) {
                                    featureTitle = "Decoy PIN Vault"
                                    featureSubtitle = "Decoy PIN displays a stealth blank journal when entered. Upgrade to Lore Scantury to activate decoy mode."
                                    featureIcon = Icons.Rounded.VisibilityOff
                                    showFeatureDialog = true
                                } else if (it) {
                                    if (appPin != null) {
                                        // verify before setup
                                        pinBuffer = "DECOY"; pinInput = ""; pinError.value = false
                                        pinStage = PinStage.VERIFY
                                    } else {
                                        pinStage = PinStage.SETUP_DECOY
                                    }
                                } else { onDecoyPinToggle(false, null, null) }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor   = mainContainerBackground,
                                checkedTrackColor   = primaryAccent,
                                uncheckedThumbColor = textSecondary,
                                uncheckedTrackColor = borderColor,
                                uncheckedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier.scale(0.8f)
                        )
                    }

                    // Decoy PIN change card (only when enabled)
                    AnimatedVisibility(
                        visible = decoyPin,
                        enter = expandVertically(tween(220)),
                        exit  = shrinkVertically(tween(180))
                    ) {
                        PinActionCard(
                            icon     = Icons.Outlined.VisibilityOff,
                            title    = "Decoy PIN",
                            subtitle = "Tap to change the decoy PIN",
                            onClick  = {
                                pinBuffer = "DECOY"; pinInput = ""; pinError.value = false
                                pinStage = PinStage.VERIFY
                            }
                        )
                    }
                }
            }

            // -- Backup PIN card (biometric mode only, optional) ------------------
            AnimatedVisibility(
                visible = appLockEnabled && !pinMode,
                enter = expandVertically(tween(240)),
                exit  = shrinkVertically(tween(200))
            ) {
                val hasBackupPin = appPin != null
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(cardBackground)
                        .border(
                            1.dp,
                            if (hasBackupPin) borderColor else primaryAccent.copy(alpha = 0.4f),
                            RoundedCornerShape(18.dp)
                        )
                        .clickable {
                            if (appPin != null) {
                                pinBuffer = "REAL"; pinInput = ""; pinError.value = false
                                pinStage = PinStage.VERIFY
                            } else {
                                pinStage = PinStage.SETUP_NEW; pinInput = ""; pinError.value = false
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (hasBackupPin) accentBackground else primaryAccent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Key, null, tint = primaryAccent, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Backup PIN", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (hasBackupPin) "Active - used if biometrics fail or device lock is removed"
                            else "Optional - set one as a safety fallback",
                            color = if (hasBackupPin) textSecondary else primaryAccent,
                            fontSize = 11.sp
                        )
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = textSecondary, modifier = Modifier.size(18.dp))
                }
            }

            // -- Auto-Lock Delay slider ------------------------------------------
            AnimatedVisibility(
                visible = appLockEnabled,
                enter = expandVertically(tween(240)),
                exit  = shrinkVertically(tween(200))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(accentBackground.copy(alpha = 0.4f))
                        .border(1.dp, borderColor, RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                        Icon(Icons.Outlined.Timer, null, tint = primaryAccent, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("AUTO-LOCK DELAY", color = primaryAccent, fontSize = 10.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(primaryAccent.copy(alpha = 0.18f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (lockDelay == 0) "Instant" else "${lockDelay}s",
                                color = primaryAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Instant", color = textSecondary, fontSize = 11.sp)
                        Spacer(Modifier.width(8.dp))
                        Slider(
                            value = lockDelay.toFloat(),
                            onValueChange = { lockDelay = it.toInt() },
                            onValueChangeFinished = { onAutoLockDelayChange(lockDelay) },
                            valueRange = 0f..15f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor         = primaryAccent,
                                activeTrackColor   = primaryAccent,
                                inactiveTrackColor = borderColor,
                                activeTickColor    = Color.Transparent,
                                inactiveTickColor  = Color.Transparent
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("15s", color = textSecondary, fontSize = 11.sp)
                    }
                    Text(
                        text = "Lock the app after being in the background for this long.",
                        color = textSecondary.copy(alpha = 0.7f), fontSize = 11.sp, lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }


            // ── Screenshot Protection ──────────────────────────────────────────
            SettingsSwitchItem(
                icon     = Icons.Outlined.Shield,
                title    = "Screenshot Protection",
                subtitle = if (isPremium) "Prevent screenshots and hide preview in app switcher." else "Prevent screenshots & hide app preview in recent switcher",
                checked  = screenshotProtection && isPremium,
                isProFeature = true,
                onCheckedChange = { enabled ->
                    if (!isPremium) {
                        onNavigateToPremium()
                    } else {
                        onScreenshotToggle(enabled)
                    }
                }
            )
            
            // ── Blur Journals ───────────────────────────────────────────────
            SettingsSwitchItem(
                icon     = Icons.Outlined.VisibilityOff,
                title    = "Blur Journals on Home",
                subtitle = "Hide text and replace titles with dates on the home screen.",
                checked  = blurJournals,
                onCheckedChange = onBlurJournalsToggle
            )

            // ── Encrypt Media ──────────────────────────────────────────────────
            SettingsSwitchItem(
                icon     = Icons.Outlined.EnhancedEncryption,
                title    = "Encrypt Media",
                subtitle = if (encryptMedia)
                    "Media is encrypted in app storage. Safe to delete originals from gallery."
                else
                    "Copy & encrypt journal media inside the app for safe keeping.",
                checked  = encryptMedia,
                onCheckedChange = onEncryptMediaToggle
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        // RHYTHM & FLOW
        SettingsSection(
            title = "RHYTHM & FLOW",
            icon = Icons.Rounded.AutoAwesome
        ) {
            SettingsSwitchItem(
                icon = Icons.Outlined.Notifications,
                title = "Daily Writing Reminder",
                subtitle = if (dailyReminder) {
                    val amPm = if (reminderHour < 12) "AM" else "PM"
                    val displayHour = when {
                        reminderHour == 0  -> 12
                        reminderHour > 12  -> reminderHour - 12
                        else               -> reminderHour
                    }
                    "Reminder set for %d:%02d %s".format(displayHour, reminderMinute, amPm)
                } else null,
                checked = dailyReminder,
                onCheckedChange = onDailyReminderToggle
            )
            // ── Reminder time picker (shown only when reminder is on) ──────
            AnimatedVisibility(
                visible = dailyReminder,
                enter = expandVertically(tween(220)),
                exit  = shrinkVertically(tween(180))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(accentBackground.copy(alpha = 0.5f))
                        .border(1.dp, borderColor, RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                        Icon(Icons.Outlined.Notifications, null, tint = primaryAccent, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("NOTIFICATION TIME", color = primaryAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // ── Hour stepper ──
                        TimeStepperBlock(
                            label = "Hour",
                            value = "%02d".format(if (reminderHour == 0) 12 else if (reminderHour > 12) reminderHour - 12 else reminderHour),
                            onDecrease = {
                                val newH = (reminderHour - 1).let { if (it < 0) 23 else it }
                                onReminderTimeChange(newH, reminderMinute)
                            },
                            onIncrease = {
                                val newH = (reminderHour + 1) % 24
                                onReminderTimeChange(newH, reminderMinute)
                            }
                        )
                        Text(":", color = textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp))
                        // ── Minute stepper ──
                        TimeStepperBlock(
                            label = "Min",
                            value = "%02d".format(reminderMinute),
                            onDecrease = {
                                val newM = (reminderMinute - 5).let { if (it < 0) 55 else it }
                                onReminderTimeChange(reminderHour, newM)
                            },
                            onIncrease = {
                                val newM = (reminderMinute + 5) % 60
                                onReminderTimeChange(reminderHour, newM)
                            }
                        )
                        Spacer(Modifier.width(16.dp))
                        // ── AM/PM toggle ──
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val isAm = reminderHour < 12
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isAm) primaryAccent else cardBackground)
                                    .clickable {
                                        if (!isAm) onReminderTimeChange(reminderHour - 12, reminderMinute)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text("AM", color = if (isAm) Color.White else textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (!isAm) primaryAccent else cardBackground)
                                    .clickable {
                                        if (isAm) onReminderTimeChange(reminderHour + 12, reminderMinute)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text("PM", color = if (!isAm) Color.White else textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            SettingsSwitchItem(
                icon = Icons.Outlined.CrueltyFree,
                title = "Companion Evolution Alerts",
                subtitle = "Get notified when your pets reach a new growth stage.",
                checked = companionAlerts,
                onCheckedChange = onCompanionAlertsToggle
            )
            SettingsSwitchItem(
                icon = Icons.Outlined.Diamond,
                title = "Relic Discovery Alerts",
                subtitle = "Alerts for discovering hidden relics in your sanctuary.",
                checked = relicAlerts,
                onCheckedChange = onRelicAlertsToggle
            )
            var showWidgetComingSoon by remember { mutableStateOf(false) }

            if (showWidgetComingSoon) {
                AlertDialog(
                    onDismissRequest = { showWidgetComingSoon = false },
                    title = { Text("Home Screen Widgets 📱", color = textPrimary, fontWeight = FontWeight.Bold) },
                    text = {
                        Text("Keep your spirit companion and current journaling streak right on your home screen with customizable Glance widgets.\n\nComing Soon for Lore Scantury members!", color = textSecondary)
                    },
                    confirmButton = {
                        TextButton(onClick = { showWidgetComingSoon = false }) {
                            Text("Got it", color = primaryAccent, fontWeight = FontWeight.Bold)
                        }
                    },
                    containerColor = cardBackground
                )
            }

            SettingsSwitchItem(
                icon = Icons.Outlined.History,
                title = "Past Memory Prompts",
                subtitle = "Occasionally resurface entries from exactly a year ago.",
                checked = pastPrompts,
                onCheckedChange = onPastPromptsToggle
            )
            SettingsActionItem(
                icon = Icons.Outlined.Widgets,
                title = "Home Screen Widgets",
                subtitle = "Coming Soon • Display pet & streak on your home screen.",
                onClick = { showWidgetComingSoon = true }
            )
            
            // Slider Item
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(accentBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Save,
                            contentDescription = null,
                            tint = primaryAccent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Auto-save Journal", color = textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = "Save frequency while writing.", color = textSecondary, fontSize = 13.sp)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentBackground)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${saveFrequency.toInt()}s",
                            color = darkAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 36.dp)) {
                    Text("3s", color = textSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    @OptIn(ExperimentalMaterial3Api::class)
                    Slider(
                        value = saveFrequency,
                        onValueChange = { saveFrequency = it },
                        onValueChangeFinished = { onAutoSaveFrequencyChange(saveFrequency) },
                        valueRange = 3f..30f,
                        steps = 26,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = primaryAccent,
                            activeTrackColor = primaryAccent,
                            inactiveTrackColor = borderColor,
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("30s", color = textSecondary, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // SANCTUARY VAULT
        var showExportDialog by remember { mutableStateOf(false) }
        var showImportDialog by remember { mutableStateOf(false) }
        var showExportJourneyComingSoon by remember { mutableStateOf(false) }

        if (showExportJourneyComingSoon) {
            AlertDialog(
                onDismissRequest = { showExportJourneyComingSoon = false },
                title = { Text("Export Journal Journey 📤", color = textPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Text("Export your entire sanctuary timeline — including mood charts, pet evolution stages, and formatted journal entries into a beautiful PDF book.\n\nComing Soon for Lore Scantury members!", color = textSecondary)
                },
                confirmButton = {
                    TextButton(onClick = { showExportJourneyComingSoon = false }) {
                        Text("Got it", color = primaryAccent, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = cardBackground
            )
        }

        SettingsSection(
            title = "SANCTUARY VAULT",
            icon = Icons.Outlined.Lock
        ) {
            SettingsActionItem(
                icon = Icons.Outlined.Book,
                title = "Export Journal Journey (PDF)",
                subtitle = "Coming Soon • Download complete mood & pet evolution story.",
                onClick = { showExportJourneyComingSoon = true }
            )
            SettingsActionItem(
                icon = Icons.Outlined.Download,
                title = "Export Complete Data",
                subtitle = "Download all text, images, and audio as a secure .aeth file.",
                onClick = { showExportDialog = true }
            )
            SettingsActionItem(
                icon = Icons.Outlined.Upload,
                title = "Import Data",
                subtitle = "Merge or overwrite your sanctuary with an existing backup.",
                onClick = { showImportDialog = true }
            )
            val localContext = LocalContext.current
            SettingsActionItem(
                icon = Icons.Rounded.AutoAwesome,
                title = "Replay Sanctuary Guide",
                subtitle = "Review the onboarding guide & meet companion characters.",
                onClick = {
                    val repo = SettingsRepository(localContext)
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        repo.setHasCompletedOnboarding(false)
                    }
                    onNavigateToPremium() // Triggers navigation refresh
                }
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        // DANGER ZONE
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, dangerCard, RoundedCornerShape(24.dp))
                .background(dangerBackground)
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = dangerText,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "DANGER ZONE",
                    color = dangerText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Eradicate Sanctuary",
                color = textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Permanently delete all journal entries, companions, media, and data. This action is irreversible.",
                color = dangerText.copy(alpha = 0.8f),
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, dangerCard, RoundedCornerShape(16.dp))
                    .clickable { 
                        showDeleteConfirmDialog.value = true
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.trash),
                    contentDescription = null,
                    tint = dangerText,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Delete Everything",
                    color = dangerText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(140.dp))
    }
}

@Composable
fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(cardBackground)
            .padding(vertical = 20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = primaryAccent,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                color = primaryAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

// ─── Time stepper for notification time picker ────────────────────────────────
@Composable
private fun TimeStepperBlock(
    label: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(cardBackground)
                .border(1.dp, borderColor, CircleShape)
                .clickable { onIncrease() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.KeyboardArrowUp, null, tint = primaryAccent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(value, color = textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(label, color = textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(cardBackground)
                .border(1.dp, borderColor, CircleShape)
                .clickable { onDecrease() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = primaryAccent, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isProFeature: Boolean = false
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isProFeature && !checked) {
                    try {
                        context.startActivity(android.content.Intent(context, com.gxdevs.lore.ui.premium.PremiumActivity::class.java))
                    } catch (_: Exception) {}
                }
                onCheckedChange(!checked)
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (isProFeature && !checked) Color(0xFFD4AF37).copy(alpha = 0.15f) else accentBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isProFeature && !checked) Color(0xFFD4AF37) else primaryAccent,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = title, color = textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (isProFeature && !checked) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFD4AF37).copy(alpha = 0.18f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD4AF37).copy(alpha = 0.4f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Rounded.Lock, null, tint = Color(0xFFD4AF37), modifier = Modifier.size(10.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("PRO", color = Color(0xFFD4AF37), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = if (isProFeature && !checked) textSecondary.copy(alpha = 0.75f) else textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = mainContainerBackground,
                checkedTrackColor = primaryAccent,
                uncheckedThumbColor = textSecondary,
                uncheckedTrackColor = borderColor,
                uncheckedBorderColor = Color.Transparent
            ),
            modifier = Modifier.scale(0.85f)
        )
    }
}

@Composable
fun SettingsActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isElevated: Boolean = false
) {
    val modifier = if (isElevated) {
        Modifier
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(mainContainerBackground)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(accentBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = primaryAccent,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, color = textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

// ─── Small tappable PIN action card ───────────────────────────────────────────
@Composable
fun PinActionCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(mainContainerBackground)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(accentBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = primaryAccent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = textSecondary, fontSize = 11.sp)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = textSecondary, modifier = Modifier.size(18.dp))
    }
}

// ─── Keypad-style PIN Setup Dialog ──────────────────────────────────────────────
@Composable
fun PinSetupDialog(
    title: String,
    subtitle: String,
    input: String,
    isError: Boolean,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor  = cardBackground,
        shape           = RoundedCornerShape(28.dp),
        title = null,
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Icon
                Box(
                    modifier = Modifier.size(56.dp).clip(CircleShape).background(accentBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Lock, null, tint = primaryAccent, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(title, color = textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(20.dp))

                // Dot indicators
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    repeat(4) { idx ->
                        val filled = idx < input.length
                        Box(
                            modifier = Modifier.size(14.dp).clip(CircleShape)
                                .background(
                                    when {
                                        isError -> dangerText
                                        filled  -> primaryAccent
                                        else    -> borderColor
                                    }
                                )
                        )
                    }
                }
                if (isError) {
                    Spacer(Modifier.height(6.dp))
                    Text("PINs don't match", color = dangerText, fontSize = 11.sp)
                }

                Spacer(Modifier.height(24.dp))

                // Number pad
                val rows = listOf(listOf("1","2","3"), listOf("4","5","6"), listOf("7","8","9"), listOf("","0","\u232B"))
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { key ->
                            when (key) {
                                "" -> Spacer(Modifier.size(64.dp))
                                "\u232B" -> Box(
                                    modifier = Modifier.size(64.dp).clip(CircleShape)
                                        .background(borderColor).clickable { onBackspace() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.Backspace, null, tint = textSecondary, modifier = Modifier.size(20.dp))
                                }
                                else -> Box(
                                    modifier = Modifier.size(64.dp).clip(CircleShape)
                                        .background(accentBackground).clickable { onDigit(key) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(key, color = textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = textSecondary) }
        }
    )
}

@Preview
@Composable
fun SettingsScreenPreview() {
    MaterialTheme {
        SettingsScreenUI()
    }
}

/**
 * Animated bottom banner that slides up to ask the user if they want to set an optional backup PIN.
 * Auto-dismisses after 5 seconds. Replaces the old SnackbarHost approach for better visibility.
 */
@Composable
fun BackupPinBanner(
    visible: Boolean,
    onSetPin: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Auto-dismiss after 5 seconds
    LaunchedEffect(visible) {
        if (visible) {
            delay(5000)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(320)) { it } + fadeIn(tween(320)),
        exit  = slideOutVertically(tween(260)) { it } + fadeOut(tween(260)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardBackground)
        ) {
            // Top separator line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(borderColor)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // Content row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Icon badge
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(primaryAccent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Key, null, tint = primaryAccent, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Set a Backup PIN?",
                            color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Protects you if biometrics fail or device lock is removed.",
                            color = textSecondary, fontSize = 11.sp, lineHeight = 15.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Skip
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                            .clickable { onDismiss() }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Skip", color = textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    // Set PIN
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(primaryAccent)
                            .clickable { onSetPin() }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Set PIN", color = mainContainerBackground, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}


/**
 * Cinematic full-width Lore Scantury upgrade card for Settings screen.
 * Shown only when the user is NOT premium.
 */
@Composable
fun SettingsPremiumUpgradeCard(onNavigateToPremium: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "settingsPremGlow")

    val borderGlow by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "border"
    )
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -400f, targetValue = 800f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.993f, targetValue = 1.007f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    val goldGrad  = listOf(Color(0xFFF3C042), Color(0xFFD4AF37), Color(0xFF8B7420), Color(0xFFF3C042))
    val accentGreen = Color(0xFF7CB87A)
    val accentDark  = Color(0xFF4A6B47)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(pulseScale)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF161D11), Color(0xFF1E2918), Color(0xFF141A0F))
                )
            )
            .border(
                2.dp,
                Brush.linearGradient(goldGrad.map { it.copy(alpha = borderGlow) }),
                RoundedCornerShape(24.dp)
            )
            .clickable { onNavigateToPremium() }
            .padding(18.dp)
    ) {
        Column {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(Color(0xFFD4AF37).copy(alpha = 0.35f), Color(0xFFD4AF37).copy(alpha = 0.1f))
                            )
                        )
                        .border(1.dp, Brush.linearGradient(goldGrad.map { it.copy(alpha = 0.7f) }), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.WorkspacePremium, null,
                        tint = Color(0xFFF3C042),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "LORE SCANTURY",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFF3C042),
                        letterSpacing = 1.8.sp
                    )
                    Text(
                        "Your sanctuary, unlocked",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF2EDE4)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFD4AF37).copy(alpha = 0.18f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD4AF37).copy(alpha = 0.5f))
                ) {
                    Text(
                        "50% OFF",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFF3C042),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Feature chips row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                PremiumFeatureChip(icon = Icons.Rounded.Bolt, label = "3x XP")
                PremiumFeatureChip(icon = Icons.Rounded.Mic, label = "Unlimited Voice")
                PremiumFeatureChip(icon = Icons.Rounded.CloudUpload, label = "Backup")
                PremiumFeatureChip(icon = Icons.Rounded.VisibilityOff, label = "Stealth")
            }

            Spacer(Modifier.height(14.dp))

            // Animated shimmer CTA
            val shimmerBrush = Brush.linearGradient(
                colors = listOf(accentDark, accentGreen.copy(alpha = 0.9f), Color(0xFF5EA85C), accentDark),
                start = Offset(shimmerOffset * 0.6f, 0f),
                end = Offset(shimmerOffset * 0.6f + 380f, 80f)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(shimmerBrush),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.WorkspacePremium, null, tint = Color.White, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "GET LORE SCANTURY",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = 0.6.sp
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(15.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                "Cancel anytime · No hidden fees · Secured by Google Play",
                fontSize = 10.sp,
                color = Color(0xFF7A8870),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PremiumFeatureChip(icon: ImageVector, label: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF252E1F))
            .border(1.dp, Color(0xFF2E3828), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color(0xFF7CB87A), modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF2EDE4))
    }
}
