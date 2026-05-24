package com.gxdevs.aethra

import android.os.Build
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.gson.Gson
import com.gxdevs.aethra.data.SettingsRepository
import com.gxdevs.aethra.pets.PetCatalogRepository
import com.gxdevs.aethra.pets.PetCatalogSyncWorker
import com.gxdevs.aethra.pets.PetSyncGuard
import com.gxdevs.aethra.ui.JournalViewModel
import com.gxdevs.aethra.ui.journal.AfterJournalRecordScreen
import com.gxdevs.aethra.ui.journal.AfterJournalViewModel
import com.gxdevs.aethra.ui.journal.JournalDetailScreen
import com.gxdevs.aethra.ui.journal.TextJournalScreen
import com.gxdevs.aethra.ui.lock.PinLockScreen
import com.gxdevs.aethra.ui.pets.PetViewModel
import com.gxdevs.aethra.ui.profile.IdentityScreen
import com.gxdevs.aethra.ui.stats.StatsScreen
import com.gxdevs.aethra.ui.theme.MyApplicationTheme
import com.gxdevs.aethra.utils.cancelDailyReminder
import com.gxdevs.aethra.utils.createNotificationChannels
import com.gxdevs.aethra.utils.MediaEncryptionManager
import com.gxdevs.aethra.utils.scheduleDailyReminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.net.URLEncoder
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }


class MainActivity : FragmentActivity() {

    private lateinit var appUpdateManager: AppUpdateManager
    private val UPDATE_REQUEST_CODE = 17362

    // Android 13+ notification permission launcher
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* granted or not */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appUpdateManager = AppUpdateManagerFactory.create(this)
        checkForUpdate()
        createNotificationChannels(this)

        // ── Pet catalog: seed demo data on first launch + schedule 24h sync ──
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            PetCatalogRepository(applicationContext).seedDemoCatalogIfEmpty()
        }
        PetCatalogSyncWorker.schedule(this)

        // Clean up stale decrypted temp files from previous sessions
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            MediaEncryptionManager.cleanUpTempFiles(applicationContext)
        }

        // Request POST_NOTIFICATIONS on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                Color.TRANSPARENT,
                Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                Color.TRANSPARENT,
                Color.TRANSPARENT
            )
        )
        setContent {
            MyApplicationTheme {
                val settingsRepo = remember { SettingsRepository(this@MainActivity) }
                val appLockEnabled by settingsRepo.appLockEnabled.collectAsState(initial = false)
                val hideMedia      by settingsRepo.hideMediaInGallery.collectAsState(initial = false)
                val decoyPin       by settingsRepo.decoyPin.collectAsState(initial = false)
                val realPin        by settingsRepo.realPin.collectAsState(initial = null)
                val decoyPinValue  by settingsRepo.decoyPinValue.collectAsState(initial = null)
                val appPin         by settingsRepo.appPin.collectAsState(initial = null)
                val useBiometric   by settingsRepo.useBiometricLock.collectAsState(initial = true)
                val autoLockDelay  by settingsRepo.autoLockDelay.collectAsState(initial = 0)

                // Apply screenshot protection globally
                LaunchedEffect(hideMedia) {
                    if (hideMedia) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else           window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }

                var isUnlocked    by remember { mutableStateOf(false) }
                // Guards against firing biometric prompt multiple times during recomposition
                var authAttempted by remember { mutableStateOf(false) }
                // Timestamp recorded when app goes to background
                var pauseTimestamp by remember { mutableStateOf<Long?>(null) }
                // Biometric availability is a mutable var so it can be refreshed on every resume
                // (handles the case where the user removes the device lock while the app is running)
                var biometricAvailable by remember {
                    mutableStateOf(
                        BiometricManager.from(this@MainActivity).canAuthenticate(
                            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            BiometricManager.Authenticators.DEVICE_CREDENTIAL
                        ) == BiometricManager.BIOMETRIC_SUCCESS
                    )
                }

                val scope = rememberCoroutineScope()

                // Snapshot refs — always read the latest value inside the lifecycle observer
                // without causing the DisposableEffect to restart
                val currentAppLockEnabled by rememberUpdatedState(appLockEnabled)
                val currentIsUnlocked     by rememberUpdatedState(isUnlocked)
                val currentAutoLockDelay  by rememberUpdatedState(autoLockDelay)

                val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        when (event) {
                            androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                                // Record when the app went to background (only while unlocked)
                                if (currentAppLockEnabled && currentIsUnlocked) {
                                    pauseTimestamp = System.currentTimeMillis()
                                }
                            }
                            androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                                // Re-evaluate biometric availability — detects device lock removal
                                biometricAvailable = BiometricManager.from(this@MainActivity).canAuthenticate(
                                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
                                ) == BiometricManager.BIOMETRIC_SUCCESS

                                val pt = pauseTimestamp
                                if (pt != null && currentAppLockEnabled) {
                                    val elapsedMs = System.currentTimeMillis() - pt
                                    // 0 = Instant: use 200ms grace to avoid locking on screen rotation
                                    val thresholdMs = if (currentAutoLockDelay == 0) 200L
                                                      else currentAutoLockDelay * 1000L
                                    if (elapsedMs >= thresholdMs) {
                                        scope.launch { settingsRepo.setIsDecoyMode(false) }
                                        isUnlocked = false
                                        authAttempted = false
                                    }
                                }
                                pauseTimestamp = null
                            }
                            else -> {}
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                // ── Core lock state machine ───────────────────────────────────────────
                // Runs whenever lock state, auth state, or biometric availability changes.
                LaunchedEffect(isUnlocked, appLockEnabled, authAttempted, useBiometric, biometricAvailable) {
                    // App lock disabled → always unlock
                    if (!appLockEnabled) {
                        isUnlocked = true
                        authAttempted = false
                        return@LaunchedEffect
                    }
                    // Already unlocked — nothing to do
                    if (isUnlocked) return@LaunchedEffect
                    // Auth already in progress — wait for its callback
                    if (authAttempted) return@LaunchedEffect

                    when {
                        // Decoy PIN mode → PIN screen handles auth
                        decoyPin -> { /* shown via showPinLock below */ }

                        // Biometric lock preferred and hardware available
                        biometricAvailable && useBiometric -> {
                            authAttempted = true
                            com.gxdevs.aethra.utils.SecurityManager(this@MainActivity)
                                .authenticate(
                                    activity  = this@MainActivity,
                                    onSuccess = {
                                        scope.launch { settingsRepo.setIsDecoyMode(false) }
                                        isUnlocked = true
                                        authAttempted = false
                                    },
                                    onError = { finish() }
                                )
                        }

                        // PIN-only lock (biometrics unavailable or disabled) with backup PIN set
                        (!biometricAvailable || !useBiometric) && appPin != null -> {
                            /* shown via showPinLock below */
                        }

                        // Safety fallback: no biometrics AND no backup PIN → do not lock out
                        else -> { isUnlocked = true }
                    }
                }

                // Show PIN lock for decoy mode OR PIN-only app lock
                val showPinLock = appLockEnabled && !isUnlocked &&
                    (decoyPin || ((!biometricAvailable || !useBiometric) && appPin != null))
                if (showPinLock) {
                    PinLockScreen(
                        onUnlockNormal = {
                            scope.launch { settingsRepo.setIsDecoyMode(false) }
                            isUnlocked = true
                        },
                        onUnlockDecoy  = { scope.launch { settingsRepo.setIsDecoyMode(true) }; isUnlocked = true },
                        realPin        = if (decoyPin) realPin else appPin,
                        decoyPinValue  = if (decoyPin) decoyPinValue else null
                    )
                } else if (isUnlocked) {

                    val navController = rememberNavController()
                    val viewModel: JournalViewModel = viewModel()
                    val petViewModel: PetViewModel = viewModel()

                    // Re-apply daily reminder on cold start if enabled
                    val dailyReminderOn by settingsRepo.dailyReminder.collectAsState(initial = true)
                    val reminderHour    by settingsRepo.reminderHour.collectAsState(initial = 10)
                    val reminderMinute  by settingsRepo.reminderMinute.collectAsState(initial = 0)
                    LaunchedEffect(dailyReminderOn, reminderHour, reminderMinute) {
                        if (dailyReminderOn) scheduleDailyReminder(this@MainActivity, reminderHour, reminderMinute)
                        else cancelDailyReminder(this@MainActivity)
                    }

                    SharedTransitionLayout {
                        CompositionLocalProvider(
                            LocalSharedTransitionScope provides this
                        ) {
                            NavHost(
                                navController = navController,
                                startDestination = "home",
                                modifier = Modifier.fillMaxSize(),
                                enterTransition = { fadeIn(animationSpec = tween(150)) },
                                exitTransition = { fadeOut(animationSpec = tween(150)) },
                                popEnterTransition = { fadeIn(animationSpec = tween(150)) },
                                popExitTransition = { fadeOut(animationSpec = tween(150)) }
                            ) {
                    composable(
                        route = "home",
                        exitTransition = {
                            if (targetState.destination.route?.startsWith("text_journal") == true) {
                                fadeOut(tween(350)) + scaleOut(
                                    targetScale = 0.95f,
                                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1.0f),
                                    animationSpec = tween(350)
                                )
                            } else {
                                fadeOut(animationSpec = tween(150))
                            }
                        },
                        popEnterTransition = {
                            if (initialState.destination.route?.startsWith("text_journal") == true) {
                                fadeIn(tween(300)) + scaleIn(
                                    initialScale = 0.95f,
                                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1.0f),
                                    animationSpec = tween(300)
                                )
                            } else {
                                fadeIn(animationSpec = tween(150))
                            }
                        }
                    ) {
                        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                            com.gxdevs.aethra.ui.home.HomeScreen(
                                onNavigateToText = { navController.navigate("text_journal") },
                                onNavigateToSettings = { navController.navigate("settings") },
                                onNavigateToJournals = { navController.navigate("my_journals") },
                                onNavigateToStats = { navController.navigate("stats") },
                                onEntryClick = { id -> navController.navigate("journal_detail/$id") },
                                onNavigateToProfile = { navController.navigate("profile") }
                            )
                        }
                    }
                    composable("settings") {
                        com.gxdevs.aethra.ui.settings.SettingsScreen(
                        )
                    }
                    composable("profile") {
                        IdentityScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(
                        route = "text_journal?editId={editId}",
                        enterTransition = {
                            if (initialState.destination.route == "home") {
                                slideIntoContainer(
                                    towards = androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Up,
                                    animationSpec = tween(350, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                                ) + fadeIn(tween(350)) + scaleIn(
                                    initialScale = 0.8f,
                                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1.0f),
                                    animationSpec = tween(350, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                                )
                            } else {
                                fadeIn(animationSpec = tween(150))
                            }
                        },
                        popExitTransition = {
                            if (targetState.destination.route == "home") {
                                slideOutOfContainer(
                                    towards = androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Down,
                                    animationSpec = tween(300, easing = androidx.compose.animation.core.FastOutLinearInEasing)
                                ) + fadeOut(tween(300)) + scaleOut(
                                    targetScale = 0.8f,
                                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1.0f),
                                    animationSpec = tween(300, easing = androidx.compose.animation.core.FastOutLinearInEasing)
                                )
                            } else {
                                fadeOut(animationSpec = tween(150))
                            }
                        }
                    ) { backStackEntry ->
                        val editId = backStackEntry.arguments?.getString("editId")?.toLongOrNull()
                        // Block pet catalog sync while user is writing
                        androidx.compose.runtime.DisposableEffect(Unit) {
                            PetSyncGuard.isJournalingActive = true
                            onDispose { PetSyncGuard.isJournalingActive = false }
                        }
                        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                            TextJournalScreen(
                                    editId = editId,
                                    viewModel = viewModel,
                                    onBack = { navController.popBackStack() },
                                    onSave = { text, tags, spiritEnergy, audioPath, mediaUris, timeSpent, formatJson, isRelic ->
                                        val encodedText = URLEncoder.encode(text, "UTF-8")
                                        val encodedTags = URLEncoder.encode(tags, "UTF-8")
                                        val encodedAudio = audioPath?.let { URLEncoder.encode(it, "UTF-8") } ?: "null"
                                        
                                        val urisJson = Gson().toJson(mediaUris.map { it.toString() })
                                        val encodedMedia = URLEncoder.encode(urisJson, "UTF-8")
                                        val encodedFormat = URLEncoder.encode(formatJson, "UTF-8")
                                        
                                        navController.navigate(
                                                "after_journal/text?content=$encodedText&tags=$encodedTags&audioPath=$encodedAudio&mediaUris=$encodedMedia&timeSpent=$timeSpent&formatRanges=$encodedFormat&isRelic=$isRelic"
                                        )
                                    }
                            )
                        }
                    }
                    composable(
                            "after_journal/{type}?content={content}&tags={tags}&filePath={filePath}&audioPath={audioPath}&mediaUris={mediaUris}&timeSpent={timeSpent}&editMoodId={editMoodId}&formatRanges={formatRanges}&isRelic={isRelic}"
                    ) { backStackEntry ->
                        val type = backStackEntry.arguments?.getString("type") ?: "text"
                        val content = backStackEntry.arguments?.getString("content")?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                        val tags = backStackEntry.arguments?.getString("tags")?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                        val filePath = backStackEntry.arguments?.getString("filePath")?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                        val audioPath = backStackEntry.arguments?.getString("audioPath")?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                        val mediaUrisJson = backStackEntry.arguments?.getString("mediaUris")?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                        val timeSpent = backStackEntry.arguments?.getString("timeSpent")?.toLongOrNull() ?: 0L
                        val editMoodId = backStackEntry.arguments?.getString("editMoodId")?.toLongOrNull()
                        val formatRangesJson = backStackEntry.arguments?.getString("formatRanges")?.let { java.net.URLDecoder.decode(it, "UTF-8") }
                        val isRelic = backStackEntry.arguments?.getString("isRelic")?.toBoolean() ?: false
 
                        // Initialize ViewModel with data
                        val afVM: AfterJournalViewModel = viewModel()
 
                        androidx.compose.runtime.LaunchedEffect(Unit) {
                            afVM.setEditMoodEntryId(editMoodId)
                            if (editMoodId == null) {
                                afVM.setInitialData(
                                        type = type,
                                        content = content,
                                        tags = tags,
                                        filePath = filePath,
                                        audioPath = audioPath,
                                        mediaUrisJson = mediaUrisJson,
                                        timeSpent = timeSpent,
                                        formatRangesJson = formatRangesJson
                                )
                            }
                        }
 
                        AfterJournalRecordScreen(
                                onSave = {
                                    val emotionsJson = afVM.selectedEmotions.value
                                        .let { Gson().toJson(it) }
                                    petViewModel.onJournalSaved()
                                    if (editMoodId != null) {
                                        // Return to the detail screen after mood update
                                        navController.popBackStack()
                                    } else {
                                        scope.launch { settingsRepo.clearDraft() }
                                        navController.navigate("home") {
                                            popUpTo("home") { inclusive = true }
                                        }
                                    }
                                },
                                onDiscard = { navController.popBackStack() },
                                viewModel = afVM,
                                isRelic = isRelic
                        )
                    }
                    composable("stats") {
                        StatsScreen(
                            onNavigateToHome = {
                                    navController.navigate("home") {
                                        popUpTo("home") { inclusive = true }
                                    }
                                },
                                onNavigateToJournals = { navController.navigate("my_journals") },
                                onNavigateToSettings = { navController.navigate("settings") }
                        )
                    }
                    composable("journal_detail/{entryId}") { backStackEntry ->
                        val entryId = backStackEntry.arguments?.getString("entryId")?.toLongOrNull() ?: 0L
                        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                            JournalDetailScreen(
                                    viewModel = viewModel,
                                    entryId = entryId,
                                    onBack = { navController.popBackStack() },
                                onEdit = { id -> navController.navigate("text_journal?editId=$id") },
                                onDeleted = { petViewModel.recalculateFromHistory() },
                                onEditMood = { id ->
                                    navController.navigate("after_journal/text?content=&tags=&audioPath=null&mediaUris=null&timeSpent=0&editMoodId=$id")
                                }
                            )
                        }
                    }
                }
                }
            }
        }
    }
    }
    }

    private fun checkForUpdate() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            ) {
                try {
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        this,
                        AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE),
                        UPDATE_REQUEST_CODE
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::appUpdateManager.isInitialized) {
            appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
                if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                    try {
                        appUpdateManager.startUpdateFlowForResult(
                            appUpdateInfo,
                            this,
                            AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE),
                            UPDATE_REQUEST_CODE
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == UPDATE_REQUEST_CODE) {
            if (resultCode != RESULT_OK) {
                checkForUpdate()
            }
        }
    }
}

