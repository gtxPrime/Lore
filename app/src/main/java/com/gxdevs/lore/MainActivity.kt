package com.gxdevs.lore

import android.widget.Toast
import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
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
import com.gxdevs.lore.data.SettingsRepository
import com.gxdevs.lore.pets.PetCatalogRepository
import com.gxdevs.lore.pets.PetCatalogSyncWorker
import com.gxdevs.lore.pets.PetSyncGuard
import com.gxdevs.lore.ui.JournalViewModel
import com.gxdevs.lore.ui.journal.AfterJournalRecordScreen
import com.gxdevs.lore.ui.journal.AfterJournalViewModel
import com.gxdevs.lore.ui.journal.JournalDetailScreen
import com.gxdevs.lore.ui.journal.TextJournalScreen
import com.gxdevs.lore.ui.lock.PinLockScreen
import com.gxdevs.lore.ui.pets.PetViewModel
import com.gxdevs.lore.ui.profile.IdentityScreen
import com.gxdevs.lore.ui.stats.StatsScreen
import com.gxdevs.lore.ui.theme.MyApplicationTheme
import com.gxdevs.lore.utils.cancelDailyReminder
import com.gxdevs.lore.utils.createNotificationChannels
import com.gxdevs.lore.utils.MediaEncryptionManager
import com.gxdevs.lore.utils.scheduleDailyReminder
import com.gxdevs.lore.utils.DriveBackupWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.util.Calendar
import java.util.concurrent.TimeUnit
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Constraints
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }


class MainActivity : FragmentActivity() {

    companion object {
        var bypassNextLock: Boolean = false
        var pauseTimestamp: Long? = null
    }

    lateinit var appUpdateHelper: com.gxdevs.lore.utils.AppUpdateHelper

    private val updateLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            // Force update: re-trigger update check if user cancelled or flow didn't finish
            checkForUpdate()
        }
    }

    // Android 13+ notification permission launcher
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* granted or not */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appUpdateHelper = com.gxdevs.lore.utils.AppUpdateHelper(this)
        appUpdateHelper.checkForUpdateOnStart(this, updateLauncher)
        createNotificationChannels(this)

        // ── Google Play Billing: re-verify active subscription ──
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            android.util.Log.i("MainActivity", "[Billing] Re-checking purchases on app start")
            com.gxdevs.lore.utils.PremiumManager.getInstance(applicationContext).queryExistingPurchases()
        }

        // ── Pet catalog: schedule 24-hour remote sync ─────────────────────────
        PetCatalogSyncWorker.schedule(this)

        // ── Google Drive backup: cancel legacy daily periodic schedule (backups run exclusively on journal changes) ──
        androidx.work.WorkManager.getInstance(applicationContext)
            // periodic cancel deferred

        // Clean up stale decrypted temp files from previous sessions
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            MediaEncryptionManager.cleanUpTempFiles(applicationContext)
        }

        // NOTE: Notification permission is requested contextually when the user enables
        // a reminder in Settings — not eagerly at app launch.
        
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
                val securitySettingsState by remember { settingsRepo.securitySettings }.collectAsState(initial = null)

                var isUnlocked    by rememberSaveable { mutableStateOf(false) }
                var authAttempted by rememberSaveable { mutableStateOf(false) }
                
                var biometricAvailable by remember {
                    mutableStateOf(
                        BiometricManager.from(this@MainActivity).canAuthenticate(
                            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            BiometricManager.Authenticators.DEVICE_CREDENTIAL
                        ) == BiometricManager.BIOMETRIC_SUCCESS
                    )
                }

                val scope = rememberCoroutineScope()

                // Reset decoy mode on cold start
                LaunchedEffect(Unit) {
                    settingsRepo.setIsDecoyMode(false)
                }

                // Apply screenshot protection globally
                LaunchedEffect(securitySettingsState?.hideMedia) {
                    val hide = securitySettingsState?.hideMedia ?: false
                    if (hide) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else           window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }

                val currentAppLockEnabled by rememberUpdatedState(securitySettingsState?.appLockEnabled ?: false)
                val currentIsUnlocked     by rememberUpdatedState(isUnlocked)
                val currentAutoLockDelay  by rememberUpdatedState(securitySettingsState?.autoLockDelay ?: 0)

                val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        when (event) {
                            androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                                if (bypassNextLock) {
                                    return@LifecycleEventObserver
                                }
                                if (currentAppLockEnabled && currentIsUnlocked) {
                                    pauseTimestamp = System.currentTimeMillis()
                                }
                            }
                            androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                                biometricAvailable = BiometricManager.from(this@MainActivity).canAuthenticate(
                                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
                                ) == BiometricManager.BIOMETRIC_SUCCESS

                                if (bypassNextLock) {
                                    bypassNextLock = false
                                    pauseTimestamp = null
                                    return@LifecycleEventObserver
                                }

                                val pt = pauseTimestamp
                                if (pt != null && currentAppLockEnabled) {
                                    val elapsedMs = System.currentTimeMillis() - pt
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
                LaunchedEffect(isUnlocked, securitySettingsState, authAttempted, biometricAvailable) {
                    val settings = securitySettingsState ?: return@LaunchedEffect
                    val appLockEnabled = settings.appLockEnabled
                    val decoyPin = settings.decoyPin
                    val useBiometric = settings.useBiometric
                    val appPin = settings.appPin

                    if (!appLockEnabled) {
                        isUnlocked = true
                        authAttempted = false
                        return@LaunchedEffect
                    }
                    if (isUnlocked) return@LaunchedEffect
                    if (authAttempted) return@LaunchedEffect

                    when {
                        decoyPin -> { /* shown via showPinLock below */ }
                        biometricAvailable && useBiometric -> {
                            authAttempted = true
                            com.gxdevs.lore.utils.SecurityManager(this@MainActivity)
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
                        appPin != null -> {
                            /* shown via showPinLock below */
                        }
                        else -> { isUnlocked = true }
                    }
                }

                val settings = securitySettingsState
                if (settings != null) {
                    val appLockEnabled = settings.appLockEnabled
                    val decoyPin = settings.decoyPin
                    val useBiometric = settings.useBiometric
                    val appPin = settings.appPin
                    val realPin = settings.realPin
                    val decoyPinValue = settings.decoyPinValue

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

                        val localLifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                        DisposableEffect(localLifecycleOwner) {
                            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                                    val target = this@MainActivity.intent?.getStringExtra("navigate_to")
                                    if (target == "text_journal") {
                                        this@MainActivity.intent?.removeExtra("navigate_to")
                                        navController.navigate("text_journal")
                                    }
                                }
                            }
                            localLifecycleOwner.lifecycle.addObserver(observer)
                            onDispose { localLifecycleOwner.lifecycle.removeObserver(observer) }
                        }

                        // Re-apply daily reminder on cold start if enabled
                        val dailyReminderOn by settingsRepo.dailyReminder.collectAsState(initial = true)
                        val reminderHour    by settingsRepo.reminderHour.collectAsState(initial = 10)
                        val reminderMinute  by settingsRepo.reminderMinute.collectAsState(initial = 0)
                        LaunchedEffect(dailyReminderOn, reminderHour, reminderMinute) {
                            if (dailyReminderOn) scheduleDailyReminder(this@MainActivity, reminderHour, reminderMinute)
                            else cancelDailyReminder(this@MainActivity)
                        }

                        val hasCompletedOnboardingState by settingsRepo.hasCompletedOnboarding.collectAsState(initial = null)

                        if (hasCompletedOnboardingState == null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(androidx.compose.ui.graphics.Color(0xFFEBE8E0))
                            )
                        } else {
                            val startDest = if (hasCompletedOnboardingState == false) "onboarding" else "home"

                            SharedTransitionLayout {
                                CompositionLocalProvider(
                                    LocalSharedTransitionScope provides this
                                ) {
                                    NavHost(
                                        navController = navController,
                                        startDestination = startDest,
                                        modifier = Modifier.fillMaxSize(),
                                        enterTransition = { fadeIn(animationSpec = tween(150)) },
                                        exitTransition = { fadeOut(animationSpec = tween(150)) },
                                        popEnterTransition = { fadeIn(animationSpec = tween(150)) },
                                        popExitTransition = { fadeOut(animationSpec = tween(150)) }
                                    ) {
                                    composable("onboarding") {
                                        com.gxdevs.lore.ui.onboarding.OnboardingScreen(
                                            onComplete = {
                                                navController.navigate("home") {
                                                    popUpTo("onboarding") { inclusive = true }
                                                }
                                            }
                                        )
                                    }
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
                                            com.gxdevs.lore.ui.home.HomeScreen(
                                                onNavigateToText = { navController.navigate("text_journal") },
                                                onEntryClick = { id -> navController.navigate("journal_detail/$id") },
                                                onNavigateToProfile = { navController.navigate("profile") },
                                                onNavigateToPremium = { startActivity(Intent(this@MainActivity, com.gxdevs.lore.ui.premium.PremiumActivity::class.java)) }
                                            )
                                        }
                                    }
                                    composable(
                                        "settings",
                                        enterTransition = {
                                            slideIntoContainer(
                                                towards = androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Start,
                                                animationSpec = tween(350, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                                            ) + fadeIn(tween(350))
                                        },
                                        popExitTransition = {
                                            slideOutOfContainer(
                                                towards = androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.End,
                                                animationSpec = tween(300, easing = androidx.compose.animation.core.FastOutLinearInEasing)
                                            ) + fadeOut(tween(300))
                                        }
                                    ) {
                                        com.gxdevs.lore.ui.settings.SettingsScreen(
                                            onNavigateToPremium = { startActivity(Intent(this@MainActivity, com.gxdevs.lore.ui.premium.PremiumActivity::class.java)) }
                                        )
                                    }
                                    composable(
                                        "profile",
                                        enterTransition = {
                                            slideIntoContainer(
                                                towards = androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Start,
                                                animationSpec = tween(350, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                                            ) + fadeIn(tween(350))
                                        },
                                        popExitTransition = {
                                            slideOutOfContainer(
                                                towards = androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.End,
                                                animationSpec = tween(300, easing = androidx.compose.animation.core.FastOutLinearInEasing)
                                            ) + fadeOut(tween(300))
                                        }
                                    ) {
                                        IdentityScreen(
                                            onBack = { navController.popBackStack() },
                                            onNavigateToPremium = { startActivity(Intent(this@MainActivity, com.gxdevs.lore.ui.premium.PremiumActivity::class.java)) }
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
                                                    onNavigateToPremium = { startActivity(Intent(this@MainActivity, com.gxdevs.lore.ui.premium.PremiumActivity::class.java)) },
                                                    onSave = { text, tags, _, audioPath, mediaUris, timeSpent, formatJson, isRelic ->
                                                         val encodedText = encodeRouteParam(text)
                                                         val encodedTags = encodeRouteParam(tags)
                                                         val encodedAudio = encodeRouteParam(audioPath)
                                                         
                                                         val urisJson = Gson().toJson(mediaUris.map { it.toString() })
                                                         val encodedMedia = encodeRouteParam(urisJson)
                                                         val encodedFormat = encodeRouteParam(formatJson)
                                                         
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
                                        val content = decodeRouteParam(backStackEntry.arguments?.getString("content"))
                                        val tags = decodeRouteParam(backStackEntry.arguments?.getString("tags"))
                                        val filePath = decodeRouteParam(backStackEntry.arguments?.getString("filePath"))
                                        val audioPath = decodeRouteParam(backStackEntry.arguments?.getString("audioPath"))
                                        val mediaUrisJson = decodeRouteParam(backStackEntry.arguments?.getString("mediaUris"))
                                        val timeSpent = backStackEntry.arguments?.getString("timeSpent")?.toLongOrNull() ?: 0L
                                        val editMoodId = backStackEntry.arguments?.getString("editMoodId")?.toLongOrNull()
                                        val formatRangesJson = decodeRouteParam(backStackEntry.arguments?.getString("formatRanges"))
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
 
                        val encryptMediaState by settingsRepo.encryptMedia.collectAsState(initial = false)
 
                        AfterJournalRecordScreen(
                                onSave = {
                                    afVM.selectedEmotions.value
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
                                isRelic = isRelic,
                                encryptMedia = encryptMediaState
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
                    composable("my_journals") {
                        // Redirect to home; the Chronicle tab (page 1) is the journal archive
                        navController.navigate("home") {
                            popUpTo("home") { inclusive = true }
                        }
                    }
                    composable(
                        "journal_detail/{entryId}",
                        enterTransition = {
                            slideIntoContainer(
                                towards = androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Start,
                                animationSpec = tween(350, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                            ) + fadeIn(tween(350))
                        },
                        popExitTransition = {
                            slideOutOfContainer(
                                towards = androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.End,
                                animationSpec = tween(300, easing = androidx.compose.animation.core.FastOutLinearInEasing)
                            ) + fadeOut(tween(300))
                        }
                    ) { backStackEntry ->
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
                } else {
                    // Branded loading state while security settings initialise
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(androidx.compose.ui.graphics.Color(0xFF0E1108)),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            color = androidx.compose.ui.graphics.Color(0xFF606F49)
                        )
                    }
                }
            }
        }
    }

    fun checkForUpdate() {
        if (::appUpdateHelper.isInitialized) {
            appUpdateHelper.checkForUpdateOnStart(this, updateLauncher)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::appUpdateHelper.isInitialized) {
            appUpdateHelper.onResumeCheck(this, updateLauncher)
        }
    }



    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

private fun encodeRouteParam(value: String?): String {
    if (value.isNullOrEmpty()) return "null"
    return try {
        android.util.Base64.encodeToString(value.toByteArray(Charsets.UTF_8), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
    } catch (_: Exception) {
        "null"
    }
}

private fun decodeRouteParam(encoded: String?): String? {
    if (encoded.isNullOrEmpty() || encoded == "null") return null
    return try {
        String(android.util.Base64.decode(encoded, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP), Charsets.UTF_8)
    } catch (_: Exception) {
        try { java.net.URLDecoder.decode(encoded, "UTF-8") } catch (_: Exception) { encoded }
    }
}


