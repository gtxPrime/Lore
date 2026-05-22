package com.gxdevs.athera.ui.settings

import androidx.biometric.BiometricManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gxdevs.athera.AppDatabase
import com.gxdevs.athera.data.SettingsRepository
import com.gxdevs.athera.utils.cancelDailyReminder
import com.gxdevs.athera.utils.scheduleDailyReminder
import kotlinx.coroutines.launch
import androidx.fragment.app.FragmentActivity
import android.view.WindowManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace

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
) {
    val context      = LocalContext.current
    val settingsRepo = remember { SettingsRepository(context) }
    val db           = remember { AppDatabase.getDatabase(context) }
    val petViewModel: com.gxdevs.athera.ui.pets.PetViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

    var showProgressDialog by remember { mutableStateOf(false) }
    var progressMessage by remember { mutableStateOf("") }

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
    val reminderHour      by settingsRepo.reminderHour.collectAsState(initial = 10)
    val reminderMinute    by settingsRepo.reminderMinute.collectAsState(initial = 0)
    val blurJournals      by settingsRepo.blurJournals.collectAsState(initial = false)
    val scope = rememberCoroutineScope()
    val activity = context as? FragmentActivity

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
                showProgressDialog = true
                progressMessage = "Exporting data... Please wait."
                val result = com.gxdevs.athera.utils.BackupManager.exportData(context, it, exportIncludeMedia)
                showProgressDialog = false
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
                showProgressDialog = true
                progressMessage = "Importing data... Please wait."
                val result = com.gxdevs.athera.utils.BackupManager.importData(context, it, importMergeMode)
                if (result.isSuccess) {
                    progressMessage = "Syncing resources... Please wait."
                    petViewModel.recalculateAndDownloadResources(context)
                    showProgressDialog = false
                    android.widget.Toast.makeText(context, "Data imported successfully", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    showProgressDialog = false
                    android.widget.Toast.makeText(context, "Failed to import data", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (showProgressDialog) {
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

    SettingsScreenUI(
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
        onAppLockToggle = { enabled ->
            if (enabled) {
                if (biometricAvailable && useBiometric) {
                    val secMgr = com.gxdevs.athera.utils.SecurityManager(context)
                    secMgr.authenticate(
                        activity = activity ?: return@SettingsScreenUI,
                        title    = "Enable App Lock",
                        subtitle = "Confirm your identity",
                        onSuccess = { scope.launch { settingsRepo.setAppLockEnabled(true) } },
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
                val secMgr = com.gxdevs.athera.utils.SecurityManager(context)
                secMgr.authenticate(
                    activity = activity ?: return@SettingsScreenUI,
                    title    = "Confirm Biometric Lock",
                    subtitle = "Authenticate to switch lock method",
                    onSuccess = {
                        scope.launch {
                            settingsRepo.setUseBiometricLock(true)
                            settingsRepo.setAppPin(null)
                            settingsRepo.setRealPin(null)
                        }
                    },
                    onError = { scope.launch { settingsRepo.setUseBiometricLock(false) } }
                )
            }
        },
        onAppPinSave = { pin -> scope.launch {
            settingsRepo.setAppPin(pin)
            settingsRepo.setRealPin(pin)
            settingsRepo.setAppLockEnabled(true)
            settingsRepo.setUseBiometricLock(false)
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
            exportLauncher.launch("lore_backup.Athera")
        },
        onImportData = { mergeMode ->
            importMergeMode = mergeMode
            importLauncher.launch(arrayOf("application/octet-stream", "*/*"))
        },
        onBlurJournalsToggle = { enabled ->
            scope.launch { settingsRepo.setBlurJournals(enabled) }
        }
    )
}

// PIN dialog stage for the unified state machine
enum class PinStage {
    NONE, SETUP_NEW, SETUP_CONFIRM, VERIFY,
    CHANGE_REAL, CHANGE_REAL_CONFIRM, CHANGE_DECOY, SETUP_DECOY
}

@Composable
fun SettingsScreenUI(
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
    onBlurJournalsToggle: (Boolean) -> Unit               = {}
) {
    var saveFrequency by remember(autoSaveFrequency) { mutableFloatStateOf(autoSaveFrequency) }
    LocalContext.current

    var pinStage  by remember { mutableStateOf(PinStage.NONE) }
    var pinInput      by remember { mutableStateOf("") }
    var pinError      by remember { mutableStateOf(false) }
    var pinBuffer     by remember { mutableStateOf("") } // stores first-entry for confirm steps

    fun resetPin() { pinStage = PinStage.NONE; pinInput = ""; pinError = false; pinBuffer = "" }

    fun handlePinDigit(d: String) {
        if (pinInput.length >= 4) return
        pinError = false
        pinInput += d
        if (pinInput.length < 4) return
        when (pinStage) {
            PinStage.SETUP_NEW -> { pinBuffer = pinInput; pinInput = ""; pinStage = PinStage.SETUP_CONFIRM }
            PinStage.SETUP_CONFIRM -> {
                if (pinInput == pinBuffer) { onAppPinSave(pinInput); resetPin() }
                else { pinError = true; pinInput = "" }
            }
            PinStage.VERIFY -> {
                if (pinInput == appPin) {
                    // After verification, decide what comes next based on what was pending
                    val wasSetupDecoy = pinBuffer == "DECOY"
                    val wasChangeReal = pinBuffer == "REAL"
                    pinBuffer = ""; pinInput = ""
                    pinStage = when { wasSetupDecoy -> PinStage.CHANGE_DECOY; wasChangeReal -> PinStage.CHANGE_REAL; else -> PinStage.NONE }
                } else { pinError = true; pinInput = "" }
            }
            PinStage.CHANGE_REAL -> { pinBuffer = pinInput; pinInput = ""; pinStage = PinStage.CHANGE_REAL_CONFIRM }
            PinStage.CHANGE_REAL_CONFIRM -> {
                if (pinInput == pinBuffer) { onChangeRealPin(pinInput); resetPin() }
                else { pinError = true; pinInput = "" }
            }
            PinStage.CHANGE_DECOY -> {
                if (pinInput == appPin) { pinError = true; pinInput = "" } // decoy can't equal real
                else { onChangeDecoyPin(pinInput); resetPin() }
            }
            PinStage.SETUP_DECOY -> {
                if (pinInput == appPin) { pinError = true; pinInput = "" }
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
            isError   = pinError,
            onDigit   = { handlePinDigit(it) },
            onBackspace = { if (pinInput.isNotEmpty()) pinInput = pinInput.dropLast(1); pinError = false },
            onDismiss = { resetPin() }
        )
    }

    // â”€â”€ Delete dialog â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    var deleteTextInput        by remember { mutableStateOf("") }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { 
                showDeleteConfirmDialog = false 
                deleteTextInput = ""
            },
            title = { Text("Confirm Deletion", color = dangerText, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("This action cannot be undone. To proceed, please type 'DELETE' below:", color = textPrimary, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = deleteTextInput,
                        onValueChange = { deleteTextInput = it },
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
                        if (deleteTextInput == "DELETE") {
                            onDeleteAll()
                            showDeleteConfirmDialog = false
                            deleteTextInput = ""
                        }
                    }
                ) { Text("Delete", color = dangerText, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showDeleteConfirmDialog = false 
                    deleteTextInput = ""
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
            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
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

        // PRIVACY & SECURITY
        SettingsSection(
            title = "PRIVACY & SECURITY",
            icon  = Icons.Outlined.Lock
        ) {
            // â”€â”€ App Lock toggle â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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
                        pinStage = PinStage.SETUP_NEW; pinInput = ""; pinError = false
                    } else {
                        onAppLockToggle(enabled)
                    }
                }
            )

            // â”€â”€ Lock method selector (only when biometric is available) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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

            // â”€â”€ Custom PIN section (visible when PIN mode is active) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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
                                pinBuffer = "REAL"; pinInput = ""; pinError = false
                                pinStage = PinStage.VERIFY
                            } else {
                                pinStage = PinStage.SETUP_NEW; pinInput = ""; pinError = false
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
                            checked = decoyPin,
                            onCheckedChange = {
                                if (it) {
                                    if (appPin != null) {
                                        // verify before setup
                                        pinBuffer = "DECOY"; pinInput = ""; pinError = false
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
                                pinBuffer = "DECOY"; pinInput = ""; pinError = false
                                pinStage = PinStage.VERIFY
                            }
                        )
                    }
                }
            }

            // â”€â”€ Screenshot Protection â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            SettingsSwitchItem(
                icon     = Icons.Outlined.Shield,
                title    = "Screenshot Protection",
                subtitle = "Prevent screenshots and hide preview in app switcher.",
                checked  = screenshotProtection,
                onCheckedChange = onScreenshotToggle
            )
            
            // â”€â”€ Blur Journals â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            SettingsSwitchItem(
                icon     = Icons.Outlined.VisibilityOff,
                title    = "Blur Journals on Home",
                subtitle = "Hide text and replace titles with dates on the home screen.",
                checked  = blurJournals,
                onCheckedChange = onBlurJournalsToggle
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
            // â”€â”€ Reminder time picker (shown only when reminder is on) â”€â”€â”€â”€â”€â”€
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
                        // â”€â”€ Hour stepper â”€â”€
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
                        // â”€â”€ Minute stepper â”€â”€
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
                        // â”€â”€ AM/PM toggle â”€â”€
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
            SettingsSwitchItem(
                icon = Icons.Outlined.History,
                title = "Past Memory Prompts",
                subtitle = "Occasionally resurface entries from exactly a year ago.",
                checked = pastPrompts,
                onCheckedChange = onPastPromptsToggle
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

        if (showExportDialog) {
            AlertDialog(
                onDismissRequest = { showExportDialog = false },
                title = { Text("Export Data", color = textPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Text("Do you want to include media (images, videos, voices) in your export? The file size will be significantly larger if you include media.", color = textSecondary)
                },
                confirmButton = {
                    TextButton(onClick = {
                        onExportData(true)
                        showExportDialog = false
                    }) { Text("With Media", color = primaryAccent, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        onExportData(false)
                        showExportDialog = false
                    }) { Text("Without Media", color = textSecondary) }
                },
                containerColor = cardBackground
            )
        }

        if (showImportDialog) {
            AlertDialog(
                onDismissRequest = { showImportDialog = false },
                title = { Text("Import Data", color = textPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Text("How would you like to import this backup? \n\nâ€¢ Merge: Fill empty slots only (keeps existing data).\nâ€¢ Overwrite: Replace ALL current data.", color = textSecondary)
                },
                confirmButton = {
                    TextButton(onClick = {
                        onImportData(false) // Overwrite
                        showImportDialog = false
                    }) { Text("Overwrite", color = dangerText, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        onImportData(true) // Merge
                        showImportDialog = false
                    }) { Text("Merge", color = primaryAccent) }
                },
                containerColor = cardBackground
            )
        }

        SettingsSection(
            title = "SANCTUARY VAULT",
            icon = Icons.Outlined.Lock
        ) {
            SettingsActionItem(
                icon = Icons.Outlined.Download,
                title = "Export Complete Data",
                subtitle = "Download all text, images, and audio as a secure .Athera file.",
                onClick = { showExportDialog = true }
            )
            SettingsActionItem(
                icon = Icons.Outlined.Upload,
                title = "Import Data",
                subtitle = "Merge or overwrite your sanctuary with an existing backup.",
                onClick = { showImportDialog = true }
                // Removed isElevated = true to remove the white card background
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
                        showDeleteConfirmDialog = true
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
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

// â”€â”€â”€ Time stepper for notification time picker â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, color = textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
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

// â”€â”€â”€ Small tappable PIN action card â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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

// â”€â”€â”€ Keypad-style PIN Setup Dialog â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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
                val rows = listOf(listOf("1","2","3"), listOf("4","5","6"), listOf("7","8","9"), listOf("","0","âŒ«"))
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { key ->
                            when (key) {
                                "" -> Spacer(Modifier.size(64.dp))
                                "âŒ«" -> Box(
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

