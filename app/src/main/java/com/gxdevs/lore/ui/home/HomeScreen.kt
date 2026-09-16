package com.gxdevs.lore.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.animation.*
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalContext
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.gxdevs.lore.data.journal.JournalEntry
import com.gxdevs.lore.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.gxdevs.lore.data.mood.MoodConstants
import com.gxdevs.lore.ui.pets.PetStageVisual
import com.gxdevs.lore.ui.pets.PetUiState
import com.gxdevs.lore.ui.pets.getOrLoadPetPalette
import com.gxdevs.lore.ui.pets.LoadedPetImage
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Brush
import com.gxdevs.lore.ui.settings.SettingsScreen
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import com.bumptech.glide.integration.compose.GlideImage
import com.gxdevs.lore.LocalNavAnimatedVisibilityScope
import com.gxdevs.lore.LocalSharedTransitionScope
import com.gxdevs.lore.R
import com.gxdevs.lore.data.SettingsRepository
import com.gxdevs.lore.ui.chronicles.ChronicleScreen
import com.gxdevs.lore.ui.pets.PetViewModel
import com.gxdevs.lore.ui.pets.PetsScreen
import com.gxdevs.lore.ui.pets.PetJourneyScreen
import com.gxdevs.lore.ui.stats.InsightsTab
import com.gxdevs.lore.ui.stats.StatsViewModel

// ===================== BEIGE / EARTH / SAGE THEME =====================
private val appBackground = Color(0xFFEBE8E0)
private val mainContainerBackground = Color(0xFFF4F1EA)
private val borderColor = Color(0xFFE0DCD1)
private val cardBackground = Color(0xFFEAE7DF)
private val textPrimary = Color(0xFF2E332A)
private val textSecondary = Color(0xFF828779)
private val textTertiary = Color(0xFF828779)
private val primaryAccent = Color(0xFF606F49)
private val accentBackground = Color(0xFFD9DFCD)
private val darkAccent = Color(0xFF4A5638)
private val bottomNavBackground = Color(0xFF2E332A)

// ===================== MAIN SCREEN =====================
@Composable
fun HomeContent(
    userName: String?,
    entries: List<JournalEntry>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onNavigateToText: () -> Unit,
    onSaveUserName: (String) -> Unit,
    onEntryClick: (Long) -> Unit = {},
    onNavigateToProfile: () -> Unit,
    onNavigateToPremium: () -> Unit = {}
) {
    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository(context) }
    val blurJournals by settingsRepo.blurJournals.collectAsState(initial = false)
    val betaWelcomeState = settingsRepo.betaWelcomeShown.collectAsState(initial = null)
    val googleLoggedIn by settingsRepo.googleLoggedIn.collectAsState(initial = false)
    val googleName by settingsRepo.googleAccountName.collectAsState(initial = null)
    val displayName = when {
        !userName.isNullOrBlank() -> userName
        googleLoggedIn && !googleName.isNullOrBlank() -> googleName!!
        else -> "Explorer"
    }

    LaunchedEffect(displayName, entries.size, googleLoggedIn) {
        android.util.Log.d("HomeScreen", "HomeScreen state: displayName='$displayName', entriesCount=${entries.size}, googleLoggedIn=$googleLoggedIn")
    }
    val coroutineScope = rememberCoroutineScope()

    // Auto-dismiss welcome flag for Google-signed-in users (they already have a name)
    LaunchedEffect(betaWelcomeState.value, googleLoggedIn) {
        if (betaWelcomeState.value == false && googleLoggedIn) {
            settingsRepo.setBetaWelcomeShown(true)
        }
    }

    // Only show name prompt for first-time users who are NOT signed in with Google
    if (betaWelcomeState.value == false && !googleLoggedIn) {
        FirstTimeNameDialog(
            initialName = displayName,
            onSaveName = onSaveUserName,
            onComplete = { coroutineScope.launch { settingsRepo.setBetaWelcomeShown(true) } }
        )
    }

    val pagerState = rememberPagerState(initialPage = 2, pageCount = { 5 })
    val petViewModel: PetViewModel = viewModel()
    val petsState by petViewModel.petsState.collectAsState()
    val journeyData by petViewModel.petJourneyState.collectAsState()

    var journeyPetForOverlay by remember { mutableStateOf<PetUiState?>(null) }

    LaunchedEffect(journeyPetForOverlay) {
        if (journeyPetForOverlay != null) {
            petViewModel.loadJourney(journeyPetForOverlay!!)
        } else {
            petViewModel.clearJourney()
        }
    }

    androidx.activity.compose.BackHandler(enabled = journeyPetForOverlay != null) {
        journeyPetForOverlay = null
    }

    Scaffold(
        containerColor = mainContainerBackground,
        contentWindowInsets = WindowInsets(0)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = true
            ) { page ->
                // Remove alpha and scale for a pure liquid bounce slide
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val position = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                            val pageOffset = position.absoluteValue
                            this.translationX = position * size.width * 0.06f
                            this.alpha = (1f - pageOffset * 0.25f).coerceIn(0f, 1f)
                        }
                ) {
                    when (page) {
                        0 -> {
                            val statsViewModel: StatsViewModel = viewModel()
                            val stats by statsViewModel.statsState.collectAsState()
                            InsightsTab(stats = stats, pets = petsState.pets, onWriteJournal = onNavigateToText)
                        }
                        1 -> ChronicleScreen(onNavigateToJournal = onNavigateToText)
                        2 -> {
                            HomeTabContent(
                                displayName = displayName,
                                searchQuery = searchQuery,
                                onSearchQueryChange = onSearchQueryChange,
                                onNameLongClick = {},
                                entries = entries,
                                pets = petsState.pets,
                                isDemoMode = petsState.isDemoMode,
                                demoPetIndex = petsState.demoPetIndex,
                                onToggleDemoMode = { petViewModel.toggleDemoMode() },
                                onCycleDemoStage = { petViewModel.cycleDemoStage() },
                                onCycleDemoPet = { petViewModel.cycleDemoPet() },
                                blurJournals = blurJournals,
                                onEntryClick = onEntryClick,
                                onViewAllClick = { coroutineScope.launch { pagerState.animateScrollToPage(3) } },
                                onWriteJournal = onNavigateToText,
                                onNavigateToProfile = onNavigateToProfile,
                                onOpenJourney = { pet -> journeyPetForOverlay = pet }
                            )
                        }
                        3 -> PetsScreen(onOpenJourney = { pet -> journeyPetForOverlay = pet })
                        4 -> SettingsTabPlaceholder(onNavigateToPremium = onNavigateToPremium)
                    }
                }
            }

            // Bottom Nav overlaid on top of scrolling content
            BottomDockedArea(
                modifier = Modifier.align(Alignment.BottomCenter),
                currentPage = pagerState.currentPage,
                onNavigatePage = { 
                    coroutineScope.launch { 
                        pagerState.animateScrollToPage(
                            it,
                            animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
                        ) 
                    } 
                },
                onWriteJournal = onNavigateToText
            )

            // Pet Journey Timeline Overlay
            AnimatedVisibility(
                visible = journeyPetForOverlay != null,
                enter   = fadeIn(tween(450)) + slideInVertically(
                    animationSpec = tween(650, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f))
                ) { it } + scaleIn(initialScale = 0.90f, animationSpec = tween(650, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f))),
                exit    = fadeOut(tween(350)) + slideOutVertically(
                    animationSpec = tween(550, easing = CubicBezierEasing(0.7f, 0f, 0.84f, 0f))
                ) { it } + scaleOut(targetScale = 0.90f, animationSpec = tween(550, easing = CubicBezierEasing(0.7f, 0f, 0.84f, 0f)))
            ) {
                journeyPetForOverlay?.let { pet ->
                    val journalViewModel: com.gxdevs.lore.ui.JournalViewModel = viewModel()
                    PetJourneyScreen(
                        pet              = pet,
                        journeyData      = journeyData,
                        isLoading        = journeyData == null,
                        forceEmoji       = petsState.useEmojiVisuals,
                        journalViewModel = journalViewModel,
                        onDismiss        = {
                            journeyPetForOverlay = null
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun HomeTabContent(
    displayName: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onNameLongClick: () -> Unit,
    entries: List<JournalEntry>,
    pets: List<PetUiState>,
    isDemoMode: Boolean = false,
    demoPetIndex: Int = 0,
    onToggleDemoMode: () -> Unit = {},
    onCycleDemoStage: () -> Unit = {},
    onCycleDemoPet: () -> Unit = {},
    blurJournals: Boolean = false,
    onEntryClick: (Long) -> Unit = {},
    onViewAllClick: () -> Unit = {},
    onWriteJournal: () -> Unit = {},
    onNavigateToProfile: () -> Unit,
    onOpenJourney: (PetUiState) -> Unit
) {
    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository(context) }
    val hasLongPressed by settingsRepo.hasLongPressedJournal.collectAsState(initial = true)
    val coroutineScope = rememberCoroutineScope()
    val petViewModel: PetViewModel = viewModel()
    val pendingLevelUpPet by petViewModel.pendingLevelUpPet.collectAsState()

    // Trigger pet progress recalculation & level-up check whenever a new journal is saved
    var prevEntryCount by remember { mutableIntStateOf(entries.size) }
    LaunchedEffect(entries.size) {
        if (entries.size > prevEntryCount) {
            petViewModel.onJournalSaved()
        }
        prevEntryCount = entries.size
    }

    var selectedPetForDialog by remember { mutableStateOf<PetUiState?>(null) }
    var showPaywallDialog by remember { mutableStateOf(false) }

    // Main Content Container
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
            .background(mainContainerBackground)
    ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp) // space to scroll past the floating nav
                ) {
                    item {
                        TopAppBarSection(
                            displayName = displayName,
                            searchQuery = searchQuery,
                            onSearchQueryChange = onSearchQueryChange,
                            onNameLongClick = onNameLongClick,
                            onOpenPaywall = { showPaywallDialog = true },
                            onNavigateToProfile = onNavigateToProfile,
                            isDemoMode = isDemoMode,
                            onToggleDemoMode = onToggleDemoMode
                        )
                    }

                    if (isDemoMode) {
                        item {
                            DemoHomeBanner(
                                onCyclePet = onCycleDemoPet,
                                onCycleStage = onCycleDemoStage,
                                onToggleDemo = onToggleDemoMode
                            )
                        }
                    }

                    // Rank unlocked pets (or all pets in demo mode)
                    val unlockedPets = pets.filter { it.stageIndex >= 0 && (it.journalCount > 0 || isDemoMode) }
                        .sortedByDescending { if (isDemoMode) it.stageIndex else it.journalCount }
                    
                    val demoShiftedPets = if (isDemoMode && unlockedPets.isNotEmpty()) {
                        val offset = demoPetIndex % unlockedPets.size
                        unlockedPets.drop(offset) + unlockedPets.take(offset)
                    } else unlockedPets

                    if (demoShiftedPets.isEmpty()) {
                        item { HeroCard(null, onWriteJournal) }
                    } else {
                        val heroPet = demoShiftedPets.getOrNull(0)
                        val incubating2nd = demoShiftedPets.getOrNull(1)
                        val incubating3rd = demoShiftedPets.getOrNull(2)

                        item {
                            Box(modifier = Modifier.clickable { heroPet?.let { onOpenJourney(it) } }) {
                                HeroCard(heroPet, onWriteJournal)
                            }
                        }
                        
                        // Show incubating section only if there's a 2nd pet
                        if (incubating2nd != null) {
                            item { Spacer(modifier = Modifier.height(24.dp)) }
                            item { IncubatingSection(listOfNotNull(incubating2nd, incubating3rd), onViewAllClick, onOpenJourney) }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }

                    // TODAY'S ENTRIES & TIMELINE
                    if (entries.isNotEmpty()) {
                        val isAllToday = entries.all { e ->
                            val c = Calendar.getInstance().apply { timeInMillis = e.timestamp }
                            val now = Calendar.getInstance()
                            now.get(Calendar.YEAR) == c.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR)
                        }
                        val headerTitle = if (isAllToday) "TODAY'S ENTRIES" else "JOURNAL TIMELINE"

                        item {
                            Text(
                                text = headerTitle,
                                color = textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp,
                                modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp)
                            )
                        }
                        itemsIndexed(entries) { index, entry ->
                            val title = entry.content?.substringBefore("\n")?.take(40)?.ifBlank { "Journal Entry" } ?: "Journal Entry"
                            val content = entry.content?.substringAfter("\n")?.take(120) ?: ""
                            
                            val entryDate = Date(entry.timestamp)
                            val now = Calendar.getInstance()
                            val entryCal = Calendar.getInstance().apply { timeInMillis = entry.timestamp }

                            val formattedTime = when {
                                now.get(Calendar.YEAR) == entryCal.get(Calendar.YEAR) && 
                                now.get(Calendar.DAY_OF_YEAR) == entryCal.get(Calendar.DAY_OF_YEAR) -> {
                                    "Today · " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(entryDate)
                                }
                                now.get(Calendar.YEAR) == entryCal.get(Calendar.YEAR) && 
                                now.get(Calendar.DAY_OF_YEAR) - entryCal.get(Calendar.DAY_OF_YEAR) == 1 -> {
                                    "Yesterday · " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(entryDate)
                                }
                                else -> {
                                    SimpleDateFormat("MMM dd, yyyy · h:mm a", Locale.getDefault()).format(entryDate)
                                }
                            }
                            
                            val displayTitle = if (blurJournals) {
                                SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(entryDate)
                            } else {
                                title
                            }

                            val calendar = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
                            val dayKey = "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.DAY_OF_YEAR)}"

                            val isLocked = entry.isTimeCapsule && System.currentTimeMillis() < (entry.unlockDate ?: 0L)

                            MockTimelineEntry(
                                title = displayTitle,
                                time = formattedTime,
                                content = content.takeIf { it.isNotBlank() } ?: title,
                                isFirst = index == 0,
                                isLast = index == entries.size - 1,
                                entryId = entry.id,
                                blurContent = blurJournals,
                                isTimeCapsule = entry.isTimeCapsule,
                                unlockDate = entry.unlockDate,
                                onClick = {
                                    if (isLocked) {
                                        val dateStr = SimpleDateFormat("MMM dd, yyyy | h:mm a", Locale.getDefault()).format(Date(entry.unlockDate ?: 0L))
                                        android.widget.Toast.makeText(context, "Locked until $dateStr", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        onEntryClick(entry.id)
                                    }
                                },
                                onLongClick = {
                                    if (isLocked) {
                                        android.widget.Toast.makeText(context, "Locked relics cannot be used for daily pet growth", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        coroutineScope.launch {
                                            settingsRepo.setDailySelectedJournal(dayKey, entry.id)
                                            settingsRepo.setHasLongPressedJournal()
                                            android.widget.Toast.makeText(context, "Selected for daily pet growth", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                showLongPressHint = !hasLongPressed && index == 0 && !isLocked
                            )
                        }
                    } else {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 8.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(cardBackground)
                                    .padding(horizontal = 24.dp, vertical = 28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("\u2726", fontSize = 28.sp, color = primaryAccent.copy(alpha = 0.4f))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Your Sanctuary is Peaceful",
                                        color = textPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Tap the quill below to share your heart and awaken your secret companion.",
                                        color = textSecondary,
                                        fontSize = 13.sp,
                                        lineHeight = 20.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

        val activeDialogPet = selectedPetForDialog ?: pendingLevelUpPet

        if (activeDialogPet != null) {
            val p = activeDialogPet
            com.gxdevs.lore.ui.components.PetLevelUpDialog(
                petName = p.name,
                stageName = p.stageName,
                stageIndex = p.stageIndex,
                moodId = p.moodId,
                localImagePath = p.localImagePath,
                dialogueQuote = MoodConstants.descriptionOf[p.moodId],
                onDismiss = {
                    if (selectedPetForDialog != null) selectedPetForDialog = null
                    petViewModel.clearPendingLevelUpPet()
                }
            )
        }

        if (showPaywallDialog) {
            com.gxdevs.lore.ui.components.PremiumPaywallDialog(
                onDismiss = { showPaywallDialog = false }
            )
        }
    }
}

@Composable
fun DemoHomeBanner(
    onCyclePet: () -> Unit,
    onCycleStage: () -> Unit,
    onToggleDemo: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(cardBackground)
            .border(1.dp, primaryAccent.copy(0.3f), RoundedCornerShape(24.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(6.dp).clip(CircleShape)
                .background(primaryAccent)
        )
        Text("DEMO", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = primaryAccent, letterSpacing = 1.2.sp)
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            Icons.Rounded.Pets,
            contentDescription = "Switch Pet",
            tint = textPrimary,
            modifier = Modifier.size(18.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(accentBackground)
                .clickable { onCyclePet() }
                .padding(3.dp)
        )
        Icon(
            Icons.Rounded.Sync,
            contentDescription = "Cycle Stage",
            tint = textPrimary,
            modifier = Modifier.size(18.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(accentBackground)
                .clickable { onCycleStage() }
                .padding(3.dp)
        )
        Icon(
            Icons.Rounded.Close,
            contentDescription = "Exit Demo",
            tint = textSecondary,
            modifier = Modifier.size(18.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(borderColor.copy(0.5f))
                .clickable { onToggleDemo() }
                .padding(3.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalGlideComposeApi::class)
@Composable
private fun TopAppBarSection(
    displayName: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onNameLongClick: () -> Unit,
    onOpenPaywall: () -> Unit = {},
    onNavigateToProfile: () -> Unit,
    isDemoMode: Boolean = false,
    onToggleDemoMode: () -> Unit = {}
) {
    var isSearchExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) {
            focusRequester.requestFocus()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp)
            .heightIn(min = 56.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val containerWidth = maxWidth

        // Target width of the search bar (collapsed: 48.dp, expanded: full width)
        val searchBarWidth by animateDpAsState(
            targetValue = if (isSearchExpanded) containerWidth else 48.dp,
            animationSpec = spring(
                dampingRatio = 0.55f, // Fluid bounce feel
                stiffness = 250f
            ),
            label = "search_width"
        )

        // Target offset of the search bar
        // Profile is 48.dp, spacer is 12.dp. So search starts at: containerWidth - 48.dp - 12.dp - 48.dp = containerWidth - 108.dp
        val searchBarOffset by animateDpAsState(
            targetValue = if (isSearchExpanded) 0.dp else (containerWidth - 108.dp),
            animationSpec = spring(
                dampingRatio = 0.55f,
                stiffness = 250f
            ),
            label = "search_offset"
        )

        // Alpha of non-search contents
        val contentAlpha by animateFloatAsState(
            targetValue = if (isSearchExpanded) 0f else 1f,
            animationSpec = tween(durationMillis = 150),
            label = "content_alpha"
        )

        // Name column (fade out/in)
        if (contentAlpha > 0f) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(end = 112.dp)
                    .graphicsLayer { alpha = contentAlpha }
                    .combinedClickable(
                        onClick = {},
                        onLongClick = onNameLongClick
                    )
            ) {
                Text(
                    text = "Welcome back",
                    color = textSecondary,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = displayName,
                    color = textPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Profile button (fade out/in)
        if (contentAlpha > 0f) {
            val settingsRepo = remember { SettingsRepository(context) }
            val googleLoggedIn by settingsRepo.googleLoggedIn.collectAsState(initial = false)
            val googlePhoto by settingsRepo.googleAccountPhoto.collectAsState(initial = null)

            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically
            ) {


                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer { alpha = contentAlpha }
                        .then(
                            if (isDemoMode) Modifier.border(
                                2.dp,
                                Brush.linearGradient(listOf(primaryAccent, darkAccent)),
                                CircleShape
                            ) else Modifier
                        )
                        .clip(CircleShape)
                        .background(appBackground)
                        .combinedClickable(
                            onClick = { onNavigateToProfile() },
                            onLongClick = { onToggleDemoMode() }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val encodedName = try {
                        java.net.URLEncoder.encode(displayName, "UTF-8")
                    } catch (_: Exception) {
                        "wanderer"
                    }
                    val avatarUrl = if (googleLoggedIn && !googlePhoto.isNullOrBlank()) {
                        googlePhoto
                    } else {
                        "https://api.dicebear.com/7.x/notionists/png?seed=$encodedName"
                    }
                    GlideImage(
                        model = avatarUrl,
                        contentDescription = "Profile",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                    // Small demo indicator dot
                    if (isDemoMode) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(primaryAccent)
                                .border(1.dp, mainContainerBackground, CircleShape)
                        )
                    }
                }
            }
        }

        // Search Bar/Button container
        Box(
            modifier = Modifier
                .offset { IntOffset(x = searchBarOffset.roundToPx(), y = 0) }
                .size(width = searchBarWidth, height = 48.dp)
                .align(Alignment.CenterStart)
        ) {
            // Background container that is always a rounded shape
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(appBackground)
            )

            if (searchBarWidth > 64.dp) {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(focusRequester),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(color = textPrimary, fontSize = 15.sp),
                    cursorBrush = SolidColor(primaryAccent),
                    decorationBox = { innerTextField ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Search",
                                tint = textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search journals...",
                                        color = textSecondary,
                                        fontSize = 15.sp,
                                        maxLines = 1
                                    )
                                }
                                innerTextField()
                            }
                            IconButton(
                                onClick = { 
                                    isSearchExpanded = false
                                    onSearchQueryChange("")
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Close Search",
                                    tint = textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { isSearchExpanded = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = "Search", tint = textSecondary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun HeroCard(topPet: PetUiState?, onWriteJournal: () -> Unit = {}) {
    val loadedImage = remember(topPet?.localImagePath, topPet?.moodId) {
        if (topPet != null) {
            getOrLoadPetPalette(topPet.localImagePath, topPet.moodId)
        } else null
    }

    val fallbackMoodColor = topPet?.let { MoodConstants.colorOf[it.moodId] } ?: primaryAccent
    val fallbackMoodBg = topPet?.let { MoodConstants.bgColorOf[it.moodId] } ?: accentBackground

    val heroCardColor by animateColorAsState(
        targetValue = loadedImage?.dominantColor ?: fallbackMoodColor,
        animationSpec = tween(600),
        label = "hero_card_color"
    )
    val heroBgColor by animateColorAsState(
        targetValue = loadedImage?.lightBgColor ?: fallbackMoodBg,
        animationSpec = tween(600),
        label = "hero_bg_color"
    )

    // Derive the rich gradient palette from the dominant colour — three stops for YT-Music depth
    val gradientMid = remember(heroCardColor) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(heroCardColor.toArgb(), hsv)
        hsv[0] = (hsv[0] + 18f) % 360f          // hue-rotate mid stop
        hsv[1] = (hsv[1] * 1.10f).coerceIn(0f, 1f)
        hsv[2] = (hsv[2] * 0.92f).coerceIn(0.30f, 0.95f)
        Color(android.graphics.Color.HSVToColor(hsv))
    }
    val gradientEnd = remember(heroCardColor) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(heroCardColor.toArgb(), hsv)
        hsv[0] = (hsv[0] + 38f) % 360f          // hue-rotate end stop further
        hsv[1] = (hsv[1] * 0.85f).coerceIn(0f, 1f)
        hsv[2] = (hsv[2] * 0.72f).coerceIn(0.20f, 0.75f)
        Color(android.graphics.Color.HSVToColor(hsv))
    }

    // ── Infinite animation driver ────────────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "hero_infinite")

    // YT-Music style: gradient origin drifts continuously across the card
    val gradientShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradient_shift"
    )

    // Shimmer sweep across card
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )

    // Pulsing glow orb behind the pet
    val orb1Pulse by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.90f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb1_pulse"
    )
    val orb2Pulse by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(2700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb2_pulse"
    )

    // Pet float
    val floatOffsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -9f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "y_float"
    )

    // Glowing arrow pulse for CTA (opacity oscillation)
    val arrowGlow by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrow_glow"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .shadow(
                elevation = 28.dp,
                shape = RoundedCornerShape(32.dp),
                spotColor = heroCardColor.copy(alpha = 0.80f),
                ambientColor = heroCardColor.copy(alpha = 0.45f)
            )
            .clip(RoundedCornerShape(32.dp)),
        contentAlignment = Alignment.CenterStart
    ) {
        // ── Layer 0: YT-Music animated base gradient ────────────────────────
        Canvas(modifier = Modifier.matchParentSize()) {
            // Diagonal start/end drift creates the "living" gradient effect
            val driftX = size.width  * gradientShift
            val driftY = size.height * (1f - gradientShift) * 0.6f
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(heroCardColor, gradientMid, gradientEnd),
                    start = Offset(driftX * 0.15f, driftY * 0.1f),
                    end   = Offset(size.width - driftX * 0.05f, size.height - driftY * 0.05f)
                )
            )
        }

        // ── Layer 1: Radial depth vignette (corners darker) ─────────────────
        Canvas(modifier = Modifier.matchParentSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.28f)
                    ),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = size.width * 0.85f
                )
            )
        }

        // ── Layer 2: Shimmer sweep ───────────────────────────────────────────
        Canvas(modifier = Modifier.matchParentSize()) {
            val sweepX = size.width * shimmerOffset
            val bandWidth = size.width * 0.45f
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.09f),
                        Color.White.copy(alpha = 0.15f),
                        Color.White.copy(alpha = 0.09f),
                        Color.Transparent
                    ),
                    start = Offset(sweepX - bandWidth, 0f),
                    end   = Offset(sweepX + bandWidth, size.height)
                )
            )
        }

        // ── Layer 3: Pulsing orb 1 (large, behind pet) ──────────────────────
        Canvas(
            modifier = Modifier
                .size(240.dp)
                .align(Alignment.CenterEnd)
                .offset(x = 20.dp, y = (floatOffsetY * 0.4f).dp)
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.38f * orb1Pulse),
                        heroBgColor.copy(alpha = 0.22f * orb1Pulse),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.width / 1.9f
                )
            )
        }

        // ── Layer 4: Pulsing orb 2 (accent colour, lower-left of pet) ───────
        Canvas(
            modifier = Modifier
                .size(140.dp)
                .align(Alignment.BottomEnd)
                .offset(x = (-10).dp, y = 30.dp)
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        gradientMid.copy(alpha = 0.50f * orb2Pulse),
                        Color.Transparent
                    ),
                    center = center,
                    radius = size.width / 1.8f
                )
            )
        }

        // ── Layer 5: Glassmorphic inner-rim border ───────────────────────────
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(
                    width = 1.2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.55f),
                            Color.White.copy(alpha = 0.10f),
                            Color.Transparent,
                            Color.White.copy(alpha = 0.08f)
                        )
                    ),
                    shape = RoundedCornerShape(32.dp)
                )
        )

        // ── Text Column on left side ─────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth(if (topPet == null) 0.65f else 0.54f)
                .padding(start = 22.dp, top = 16.dp, bottom = 16.dp, end = 4.dp),
            verticalArrangement = Arrangement.Center
        ) {
            if (topPet != null) {
                // Stage badge (compact height)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.25f),
                                    Color.White.copy(alpha = 0.12f)
                                )
                            )
                        )
                        .border(
                            1.dp,
                            Brush.linearGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.45f),
                                    Color.White.copy(alpha = 0.18f)
                                )
                            ),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 1.5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val icon = if (topPet.isFullyGrown) Icons.Rounded.AutoAwesome else Icons.Rounded.Egg
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(8.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        "STAGE ${topPet.stageIndex.coerceAtLeast(0) + 1}",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.6.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = topPet.displayName,
                    color = Color.White,
                    fontSize = if (topPet.displayName.length > 12) 22.sp else 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = "${topPet.stageName} · ${topPet.journalCount} entries",
                    color = Color.White.copy(alpha = 0.80f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Progress label row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (topPet.isFullyGrown) "FULLY GROWN" else "NURTURING",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "${((topPet.progressInStage) * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Spacer(modifier = Modifier.height(5.dp))

                // Premium glowing progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.20f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(topPet.progressInStage.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.90f),
                                        Color.White
                                    )
                                )
                            )
                            .shadow(
                                elevation = 6.dp,
                                shape = CircleShape,
                                spotColor = Color.White
                            )
                    )
                }

                if (!topPet.isFullyGrown && topPet.journalsToNext > 0) {
                    Spacer(modifier = Modifier.height(7.dp))
                    Text(
                        text = "${topPet.journalsToNext} more to evolve",
                        color = Color.White.copy(alpha = 0.70f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                // ── Empty state ──────────────────────────────────────────────
                Text(
                    "Your Sanctuary\nAwaits",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 30.sp
                )
                Spacer(modifier = Modifier.height(7.dp))
                Text(
                    "Write your daily reflection\nto awaken your secret companion.",
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                // Glowing CTA button with animated arrows
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.28f),
                                    Color.White.copy(alpha = 0.14f)
                                )
                            )
                        )
                        .border(
                            1.5.dp,
                            Brush.linearGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.70f),
                                    Color.White.copy(alpha = 0.25f)
                                )
                            ),
                            RoundedCornerShape(22.dp)
                        )
                        .shadow(elevation = 0.dp, shape = RoundedCornerShape(22.dp))
                        .clickable { onWriteJournal() }
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Write First Entry",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "»",
                            color = Color.White.copy(alpha = arrowGlow),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        // ── Companion Character Artwork & Ground Contact Shadow ───────────────
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(160.dp),
            contentAlignment = Alignment.Center
        ) {
            // Ground contact shadow — uses dynamic hero color for warm color-matched glow
            Canvas(
                modifier = Modifier
                    .size(width = 160.dp, height = 40.dp)
                    .align(Alignment.BottomCenter)
                    .offset(y = (-8).dp)
            ) {
                // Pass 1: Wide ambient soft bloom (color-tinted)
                val bloomRadius = 60.dp.toPx()
                withTransform({
                    scale(scaleX = 1.0f, scaleY = 0.18f, pivot = center)
                }) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                heroCardColor.copy(alpha = 0.35f),
                                Color.Black.copy(alpha = 0.18f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = bloomRadius
                        ),
                        radius = bloomRadius,
                        center = center
                    )
                }

                // Pass 2: Tighter dense core
                val coreRadius = 38.dp.toPx()
                withTransform({
                    scale(scaleX = 1.0f, scaleY = 0.12f, pivot = center)
                }) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.45f),
                                Color.Black.copy(alpha = 0.18f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = coreRadius
                        ),
                        radius = coreRadius,
                        center = center
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = floatOffsetY.dp)
            ) {
                if (topPet != null) {
                    PetStageVisual(
                        pet = topPet,
                        moodColor = heroCardColor,
                        isCenter = true,
                        loadedImage = loadedImage
                    )
                } else {
                    // Multi-layer glowing aura behind egg
                    Box(contentAlignment = Alignment.Center) {
                        // Outer ambient aura — slow pulse, color-matched
                        Canvas(modifier = Modifier.size(120.dp)) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        heroBgColor.copy(alpha = 0.45f * orb2Pulse),
                                        heroCardColor.copy(alpha = 0.18f * orb2Pulse),
                                        Color.Transparent
                                    ),
                                    center = center,
                                    radius = size.width / 1.8f
                                )
                            )
                        }
                        // Inner bright glow — faster pulse
                        Canvas(modifier = Modifier.size(80.dp)) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.50f * orb1Pulse),
                                        heroBgColor.copy(alpha = 0.20f * orb1Pulse),
                                        Color.Transparent
                                    ),
                                    center = center,
                                    radius = size.width / 1.5f
                                )
                            )
                        }
                        Icon(
                            Icons.Rounded.Egg,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.95f),
                            modifier = Modifier.size(72.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IncubatingSection(
    pets: List<PetUiState>,
    onViewAllClick: () -> Unit,
    onPetClick: (PetUiState) -> Unit
) {
    if (pets.isEmpty()) return
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("NURTURING IN SANCTUARY", color = textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text("VIEW ALL >", color = primaryAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onViewAllClick() })
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IncubatingCard(
                pet = pets[0],
                onPetClick = onPetClick,
                modifier = Modifier.weight(1f)
            )
            if (pets.size >= 2) {
                IncubatingCard(
                    pet = pets[1],
                    onPetClick = onPetClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun IncubatingCard(
    pet: PetUiState,
    onPetClick: (PetUiState) -> Unit,
    modifier: Modifier = Modifier
) {
    val loadedImage = remember(pet.localImagePath, pet.moodId) {
        getOrLoadPetPalette(pet.localImagePath, pet.moodId)
    }



    val moodColor by animateColorAsState(
        targetValue = loadedImage.dominantColor,
        animationSpec = tween(350),
        label = "incubating_mood_color"
    )

    val moodBgColor by animateColorAsState(
        targetValue = loadedImage.lightBgColor,
        animationSpec = tween(350),
        label = "incubating_mood_bg"
    )

    Column(
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = moodColor.copy(alpha = 0.28f),
                ambientColor = moodColor.copy(alpha = 0.1f)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(moodBgColor.copy(alpha = 0.35f), mainContainerBackground.copy(alpha = 0.25f))
                )
            )
            .border(1.5.dp, moodColor.copy(alpha = 0.26f), RoundedCornerShape(24.dp))
            .clickable { onPetClick(pet) }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pet image / visual
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(moodBgColor.copy(alpha = 0.7f))
                    .border(1.dp, moodColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                PetStageVisual(
                    pet = pet,
                    moodColor = moodColor,
                    isCenter = false,
                    loadedImage = loadedImage
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pet.name,
                    color = textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = pet.stageName,
                    color = textSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            // Level badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(moodColor.copy(alpha = 0.12f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    "LV ${pet.journalCount}",
                    color = moodColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    maxLines = 1
                )
            }
        }
        LinearProgressIndicator(
            progress = { pet.progressInStage },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = moodColor,
 trackColor = borderColor.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun MockTimelineEntry(
    title: String,
    time: String,
    content: String,
    isFirst: Boolean,
    isLast: Boolean,
    entryId: Long? = null,
    blurContent: Boolean = false,
    isTimeCapsule: Boolean = false,
    unlockDate: Long? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    showLongPressHint: Boolean = false
) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(IntrinsicSize.Min)) {
        // Timeline line + dot
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(16.dp)) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (isFirst) primaryAccent else borderColor)
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(borderColor)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title, 
                    color = textPrimary, 
                    fontSize = 16.sp, 
                    fontWeight = FontWeight.Bold, 
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isTimeCapsule && System.currentTimeMillis() < (unlockDate ?: 0L)) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "Locked Relic",
                        tint = primaryAccent,
                        modifier = Modifier.size(16.dp)
                    )
                } else if (isTimeCapsule) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Rounded.LockOpen,
                        contentDescription = "Unlocked Relic",
                        tint = textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(time, color = textTertiary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            val sharedScope = LocalSharedTransitionScope.current
            val navAnimScope = LocalNavAnimatedVisibilityScope.current
            
            @OptIn(ExperimentalSharedTransitionApi::class)
            val morphModifier = if (sharedScope != null && navAnimScope != null && entryId != null) {
                with(sharedScope) {
                    Modifier.sharedBounds(
                        sharedContentState = rememberSharedContentState(key = "journal_card_$entryId"),
                        animatedVisibilityScope = navAnimScope,
                        boundsTransform = { _, _ -> tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing) }
                    )
                }
            } else Modifier
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(morphModifier)
                    .clip(RoundedCornerShape(topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 24.dp))
                    .background(cardBackground)
                    .then(
                        if (onClick != null || onLongClick != null) {
                            Modifier.combinedClickable(
                                onClick = { onClick?.invoke() },
                                onLongClick = { onLongClick?.invoke() }
                            )
                        } else Modifier
                    )
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = content,
                        color = textSecondary,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (blurContent) Modifier.blur(8.dp) else Modifier
                    )
                    if (onClick != null || showLongPressHint) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            if (onClick != null) {
                                Text(
                                    text = "Tap to read →",
                                    color = primaryAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (showLongPressHint) {
                                Text(
                                    text = "Hold to grow companion",
                                    color = textSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ===================== BOTTOM DOCKED AREA =====================
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun BottomDockedArea(
    modifier: Modifier = Modifier,
    currentPage: Int,
    onNavigatePage: (Int) -> Unit,
    onWriteJournal: () -> Unit
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Gradient and Opaque System Nav Background
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Gradient fade-in layer above the navigation bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp) // Taller fade so it starts well above the pill
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0.0f to mainContainerBackground.copy(alpha = 0f), // Starts transparent at the top
                            0.5f to mainContainerBackground, // Becomes fully solid exactly at the top of the 80dp pill!
                            1.0f to mainContainerBackground // Remains solid behind the pill
                        )
                    )
            )
            // Solid opaque background for the system navigation bar area
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsBottomHeight(WindowInsets.navigationBars)
                    .background(mainContainerBackground)
            )
        }
        // Floating Pill Bottom Nav
        val sharedScope = LocalSharedTransitionScope.current
        val navAnimScope = LocalNavAnimatedVisibilityScope.current
        
        val morphModifier = if (sharedScope != null && navAnimScope != null) {
            with(sharedScope) {
                Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState(key = "bottom_bar_morph"),
                    animatedVisibilityScope = navAnimScope,
                    boundsTransform = { _, _ -> spring(dampingRatio = 0.8f, stiffness = 400f) }
                )
            }
        } else {
            Modifier
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .widthIn(max = 380.dp)
                .padding(horizontal = 24.dp)
                .padding(bottom = 10.dp)
                .then(morphModifier),
            contentAlignment = Alignment.BottomCenter
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp)
                    .shadow(
                        elevation = 20.dp, 
                        shape = RoundedCornerShape(35.dp), 
                        spotColor = Color.White.copy(alpha = 0.35f),
                        ambientColor = Color.White.copy(alpha = 0.15f)
                    )
                    .clip(RoundedCornerShape(35.dp))
                    .background(bottomNavBackground)
                    .border(1.dp, borderColor.copy(alpha = 0.1f), RoundedCornerShape(35.dp))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                    BottomNavItem(iconResId = R.drawable.chart_line, isSelected = currentPage == 0, onClick = { onNavigatePage(0) })
                    BottomNavItem(iconResId = R.drawable.scroll_text, isSelected = currentPage == 1, onClick = { onNavigatePage(1) })
                }
                
                Spacer(modifier = Modifier.width(60.dp)) // Space for overlapping center button
                
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                    BottomNavItem(iconResId = R.drawable.paw_print, isSelected = currentPage == 3, onClick = { onNavigatePage(3) })
                    BottomNavItem(iconResId = R.drawable.settings, isSelected = currentPage == 4, onClick = { onNavigatePage(4) })
                }
            }
            
            // Floating Circular Write Journal Button
            val isHomeTab = currentPage == 2
            Box(
                modifier = Modifier
                    .offset(y = (-30).dp)
                    .size(60.dp),
                contentAlignment = Alignment.Center
            ) {
                // Premium Glow
                Canvas(
                    modifier = Modifier
                        .requiredSize(160.dp) // Bypasses the parent's 60dp constraint to prevent square clipping!
                        .offset(y = 12.dp)
                        .blur(24.dp) // Softer, wider blur
                ) {
                    val radiusPx = 35.dp.toPx() // The actual glow diameter is 100dp
                    val glowBrush = androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(accentBackground.copy(alpha = 1.0f), accentBackground.copy(alpha = 0.4f), Color.Transparent),
                        center = center,
                        radius = radiusPx
                    )
                    drawCircle(brush = glowBrush, radius = radiusPx, center = center)
                }

                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                // Button
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .shadow(
                            elevation = 8.dp,
                            shape = CircleShape
                        )
                        .clip(CircleShape)
                        .background(accentBackground)
                        .combinedClickable(
                            onClick = {
                                if (isHomeTab) onWriteJournal() else onNavigatePage(2)
                            },
                            onLongClick = {
                                val repo = com.gxdevs.lore.data.SettingsRepository(context)
                                scope.launch {
                                    repo.setHasCompletedOnboarding(false)
                                }
                                android.widget.Toast.makeText(context, "Replaying Onboarding Guide (Demo)", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = isHomeTab,
                        label = "center_icon"
                    ) { isHome ->
                        if (isHome) {
                            Icon(
                                painter = painterResource(id = R.drawable.pencil), // Write journal icon
                                contentDescription = "Write Journal",
                                tint = textPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.house), // Home icon
                                contentDescription = "Home",
                                tint = textPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    iconResId: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val animatedBgColor = if (isSelected) accentBackground.copy(alpha = 0.15f) else Color.Transparent
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1.0f,
        animationSpec = spring(stiffness = 3000f),
        label = "nav_scale"
    )
    val iconColor by animateColorAsState(
        targetValue = if (isSelected) cardBackground else accentBackground,
        animationSpec = tween(150),
        label = "nav_color"
    )
    Box(
        modifier = Modifier
            .height(40.dp)
            .width(42.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(animatedBgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconResId),
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(24.dp).graphicsLayer {
                scaleX = iconScale
                scaleY = iconScale
            }
        )
    }
}

@Composable
fun FirstTimeNameDialog(
    initialName: String,
    onSaveName: (String) -> Unit,
    onComplete: () -> Unit
) {
    var nameInput by remember { mutableStateOf(if (initialName != "Explorer" && initialName != "User") initialName else "") }

    AlertDialog(
        onDismissRequest = {
            onSaveName(nameInput.trim().ifBlank { "Explorer" })
            onComplete()
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Person, contentDescription = null, tint = primaryAccent, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Welcome to Lore", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        },
        text = {
            Column {
                Text(
                    "What should we call you?",
                    color = textSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    placeholder = { Text("e.g. Phoenix", color = textSecondary.copy(alpha = 0.6f)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryAccent,
                        unfocusedBorderColor = borderColor,
                        focusedTextColor = textPrimary,
                        unfocusedTextColor = textPrimary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveName(nameInput.trim().ifBlank { "Explorer" })
                    onComplete()
                },
                colors = ButtonDefaults.buttonColors(containerColor = primaryAccent),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Continue", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = cardBackground,
        shape = RoundedCornerShape(24.dp)
    )
}


@Composable
fun SettingsTabPlaceholder(onNavigateToPremium: () -> Unit = {}) {
    SettingsScreen(onNavigateToPremium = onNavigateToPremium)
}

// ===================== VIEWMODEL CONNECTOR =====================
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel = viewModel(),
    onNavigateToText: () -> Unit,
    onEntryClick: (Long) -> Unit = {},
    onNavigateToProfile: () -> Unit,
    onNavigateToPremium: () -> Unit = {}
) {
    val userName by homeViewModel.userName.collectAsState()
    val entries by homeViewModel.filteredEntries.collectAsState()
    val searchQuery by homeViewModel.searchQuery.collectAsState()

    HomeContent(
        userName = userName,
        entries = entries,
        searchQuery = searchQuery,
        onSearchQueryChange = { homeViewModel.updateSearchQuery(it) },
        onNavigateToText = onNavigateToText,
        onSaveUserName = { homeViewModel.saveUserName(it) },
        onEntryClick = onEntryClick,
        onNavigateToProfile = onNavigateToProfile,
        onNavigateToPremium = onNavigateToPremium
    )
}

// ===================== PREVIEW =====================
@Preview(showBackground = true, backgroundColor = 0xFF080908)
@Composable
private fun HomePreview() {
    MyApplicationTheme {
        HomeContent(
            userName = "Garvit",
            entries = emptyList(),
            searchQuery = "",
            onSearchQueryChange = {},
            onNavigateToText = {},
            onSaveUserName = {},
            onEntryClick = {},
            onNavigateToProfile = {}
        )
    }
}



