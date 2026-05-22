package com.gxdevs.athera.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalContext
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.gxdevs.athera.JournalEntry
import com.gxdevs.athera.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.platform.LocalLocale

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
    onEntryClick: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val settingsRepo = remember { com.gxdevs.athera.data.SettingsRepository(context) }
    val blurJournals by settingsRepo.blurJournals.collectAsState(initial = false)
    val betaWelcomeState = settingsRepo.betaWelcomeShown.collectAsState(initial = null)

    val displayName = if (userName.isNullOrBlank()) "User" else userName
    var showNameDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Track whether the ViewModel has delivered its first value (avoids flashing during initial load)
    var userNameLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(userName, betaWelcomeState.value) {
        if (userName != null && betaWelcomeState.value != null) {
            userNameLoaded = true
            // Only show the dialog on genuine first-run (name is blank) and AFTER beta welcome
            if (userName.isBlank() && !showNameDialog && betaWelcomeState.value == true) {
                showNameDialog = true
            }
        }
    }

    if (betaWelcomeState.value == false) {
        BetaWelcomeDialog(
            onComplete = { coroutineScope.launch { settingsRepo.setBetaWelcomeShown(true) } }
        )
    } else if (showNameDialog) {
        var newName by remember { mutableStateOf(userName ?: "") }
        AlertDialog(
            onDismissRequest = { 
                if (!userName.isNullOrBlank()) {
                    showNameDialog = false 
                }
            },
            title = { 
                Text(
                    if (userName.isNullOrBlank()) "Welcome to Athera" else "Edit Name", 
                    color = textPrimary, 
                    fontWeight = FontWeight.Bold
                ) 
            },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { input -> 
                        newName = input.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                    },
                    label = { Text("Your Name", color = textSecondary) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = primaryAccent,
                        unfocusedIndicatorColor = borderColor,
                        focusedLabelColor = primaryAccent,
                        unfocusedLabelColor = textSecondary,
                        cursorColor = primaryAccent,
                        focusedTextColor = textPrimary,
                        unfocusedTextColor = textPrimary
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = { 
                        onSaveUserName(newName)
                        showNameDialog = false 
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryAccent),
                    enabled = newName.isNotBlank()
                ) {
                    Text(if (userName.isNullOrBlank()) "Start Journey" else "Save", color = Color.White)
                }
            },
            dismissButton = {
                if (!userName.isNullOrBlank()) {
                    TextButton(onClick = { showNameDialog = false }) {
                        Text("Cancel", color = textSecondary)
                    }
                }
            },
            containerColor = cardBackground
        )
    }

    val pagerState = rememberPagerState(initialPage = 2, pageCount = { 5 })

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
                val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
                // Remove alpha and scale for a pure liquid bounce slide
                val slideOffset = pageOffset * 0.1f // Very slight parallax effect
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            this.translationX = slideOffset * size.width
                        }
                ) {
                    when (page) {
                        0 -> {
                            val statsViewModel: com.gxdevs.athera.ui.stats.StatsViewModel = viewModel()
                            val stats by statsViewModel.statsState.collectAsState()
                            com.gxdevs.athera.ui.stats.InsightsTab(stats = stats)
                        }
                        1 -> com.gxdevs.athera.ui.chronicles.ChronicleScreen()
                        2 -> {
                            val petViewModel: com.gxdevs.athera.ui.pets.PetViewModel = viewModel()
                            val petsState by petViewModel.petsState.collectAsState()
                            HomeTabContent(
                                displayName = displayName,
                                searchQuery = searchQuery,
                                onSearchQueryChange = onSearchQueryChange,
                                onNameLongClick = { showNameDialog = true },
                                entries = entries,
                                pets = petsState.pets,
                                blurJournals = blurJournals,
                                onEntryClick = onEntryClick,
                                onViewAllClick = { coroutineScope.launch { pagerState.animateScrollToPage(3) } }
                            )
                        }
                        3 -> com.gxdevs.athera.ui.pets.PetsScreen()
                        4 -> SettingsTabPlaceholder()
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
                            animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 400f)
                        ) 
                    } 
                },
                onWriteJournal = onNavigateToText
            )
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
    pets: List<com.gxdevs.athera.ui.pets.PetUiState>,
    blurJournals: Boolean = false,
    onEntryClick: (Long) -> Unit = {},
    onViewAllClick: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsRepo = remember { com.gxdevs.athera.data.SettingsRepository(context) }
    val hasLongPressed by settingsRepo.hasLongPressedJournal.collectAsState(initial = true)
    val coroutineScope = rememberCoroutineScope()

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
                            onNameLongClick = onNameLongClick
                        )
                    }
                    // Rank only unlocked pets with at least 1 journal entry
                    val unlockedPets = pets.filter { it.stageIndex >= 0 && it.journalCount > 0 }.sortedByDescending { it.journalCount }
                    
                    if (unlockedPets.isEmpty()) {
                        item { HeroCard(null) }
                    } else {
                        val heroPet = unlockedPets.getOrNull(0)
                        val incubating2nd = unlockedPets.getOrNull(1)
                        val incubating3rd = unlockedPets.getOrNull(2)

                        item { HeroCard(heroPet) }
                        
                        // Show incubating section only if there's a 2nd pet
                        if (incubating2nd != null) {
                            item { Spacer(modifier = Modifier.height(24.dp)) }
                            item { IncubatingSection(listOfNotNull(incubating2nd, incubating3rd), onViewAllClick) }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }

                    // TODAY'S ENTRIES
                    if (entries.isNotEmpty()) {
                        item {
                            Text(
                                text = "TODAY'S ENTRIES",
                                color = textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp,
                                modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp)
                            )
                        }
                        itemsIndexed(entries) { index, entry ->
                            val title = entry.content?.substringBefore("\n")?.take(40) ?: "Journal Entry"
                            val content = entry.content?.substringAfter("\n")?.take(120) ?: ""
                            val time = SimpleDateFormat("hh:mm a", LocalLocale.current.platformLocale).format(Date(entry.timestamp))
                            
                            val displayTitle = if (blurJournals) {
                                SimpleDateFormat("MMMM dd, yyyy", LocalLocale.current.platformLocale).format(Date(entry.timestamp))
                            } else {
                                title
                            }

                            val calendar = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
                            val dayKey = "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.DAY_OF_YEAR)}"

                            MockTimelineEntry(
                                title = displayTitle,
                                time = time,
                                content = content.takeIf { it.isNotBlank() } ?: title,
                                isFirst = index == 0,
                                isLast = index == entries.size - 1,
                                entryId = entry.id,
                                blurContent = blurJournals,
                                onClick = { onEntryClick(entry.id) },
                                onLongClick = {
                                    coroutineScope.launch {
                                        settingsRepo.setDailySelectedJournal(dayKey, entry.id)
                                        settingsRepo.setHasLongPressedJournal()
                                        android.widget.Toast.makeText(context, "Selected for daily pet growth", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                showLongPressHint = !hasLongPressed && index == 0
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
                                    Text("âœ¦", fontSize = 28.sp, color = primaryAccent.copy(alpha = 0.4f))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Nothing written today",
                                        color = textPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Tap the pen below to begin your first entry.",
                                        color = textSecondary,
                                        fontSize = 13.sp,
                                        lineHeight = 20.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TopAppBarSection(
    displayName: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onNameLongClick: () -> Unit
) {
    var isSearchExpanded by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSearchExpanded) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search journals...", color = textSecondary) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = appBackground,
                    unfocusedContainerColor = appBackground,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = primaryAccent,
                    focusedTextColor = textPrimary,
                    unfocusedTextColor = textPrimary
                ),
                shape = CircleShape,
                trailingIcon = {
                    IconButton(onClick = { 
                        isSearchExpanded = false
                        onSearchQueryChange("")
                    }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close Search", tint = textSecondary)
                    }
                }
            )
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
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
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(appBackground)
                        .clickable { isSearchExpanded = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = "Search", tint = textSecondary, modifier = Modifier.size(20.dp))
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(appBackground)
                        .clickable { android.widget.Toast.makeText(context, "Profiles coming soon", android.widget.Toast.LENGTH_SHORT).show() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Person, contentDescription = "Profile", tint = textSecondary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun HeroCard(topPet: com.gxdevs.athera.ui.pets.PetUiState?) {
    // Always use the signature green color for the hero card
    val heroCardColor = primaryAccent
    val heroBgColor = accentBackground

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            // Green Card â€” always green
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(32.dp),
                        spotColor = Color.Black.copy(alpha = 0.2f),
                        ambientColor = Color.Black.copy(alpha = 0.1f)
                    )
                    .clip(RoundedCornerShape(32.dp))
                    .background(heroCardColor)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    if (topPet != null) {
                        // Badge
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isFullyGrown = topPet.isFullyGrown
                            val icon = if (isFullyGrown) Icons.Rounded.AutoAwesome else Icons.Rounded.Egg
                            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("STAGE ${topPet.stageIndex.coerceAtLeast(0)}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(topPet.name, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("${topPet.stageName} â€¢ Lv ${topPet.journalCount}", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)

                        Spacer(modifier = Modifier.weight(1f))

                        Row(
                            modifier = Modifier.fillMaxWidth(0.55f),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (topPet.isFullyGrown) "FULLY GROWN" else "NURTURING", color = Color.White.copy(alpha = 0.9f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Text("${((topPet.progressInStage) * 100).toInt()}%", color = Color.White.copy(alpha = 0.9f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { topPet.progressInStage },
                            modifier = Modifier.fillMaxWidth(0.55f).height(6.dp).clip(CircleShape),
                            color = Color.White,
                            trackColor = darkAccent,
                        )
                    } else {
                        // Empty State
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Awaiting\nSpark", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Write your first\njournal to awaken\na companion.", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, lineHeight = 18.sp)
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            // Image circle â€” always uses the neutral accentBackground
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 140.dp)
                    .size(280.dp)
                    .shadow(
                        elevation = 24.dp,
                        shape = CircleShape,
                        spotColor = Color.Black.copy(alpha = 0.5f),
                        ambientColor = Color.Black.copy(alpha = 0.3f)
                    )
                    .clip(CircleShape)
                    .background(heroBgColor),
                contentAlignment = Alignment.Center
            ) {
                if (topPet != null) {
                        com.gxdevs.athera.ui.pets.PetStageVisual(pet = topPet, moodColor = heroCardColor, isCenter = true)
                } else {
                    Icon(Icons.Rounded.Egg, null, tint = primaryAccent, modifier = Modifier.size(64.dp))
                }
            }
        }
    }
}

/**
 * Shows the 2nd and 3rd ranked pets side-by-side.
 * Caller guarantees list has 1 or 2 items (never 0, never 3+).
 */
@Composable
private fun IncubatingSection(pets: List<com.gxdevs.athera.ui.pets.PetUiState>, onViewAllClick: () -> Unit) {
    if (pets.isEmpty()) return
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("GROWING", color = textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text("VIEW ALL >", color = primaryAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onViewAllClick() })
        }

        // Side-by-side: show 2nd and 3rd ranked pets
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 2nd ranked â€” always present when IncubatingSection is shown
            IncubatingCard(
                pet = pets[0],
                modifier = Modifier.weight(1f)
            )
            // 3rd ranked â€” only if it exists
            if (pets.size >= 2) {
                IncubatingCard(
                    pet = pets[1],
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun IncubatingCard(
    pet: com.gxdevs.athera.ui.pets.PetUiState,
    modifier: Modifier = Modifier
) {
    val moodColor = com.gxdevs.athera.MoodConstants.colorOf[pet.moodId] ?: primaryAccent
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(cardBackground)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pet image / visual
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(com.gxdevs.athera.MoodConstants.bgColorOf[pet.moodId] ?: accentBackground),
                contentAlignment = Alignment.Center
            ) {
                com.gxdevs.athera.ui.pets.PetStageVisual(pet = pet, moodColor = moodColor, isCenter = false)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pet.name,
                    color = textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = pet.stageName,
                    color = textSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            // Level badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(mainContainerBackground)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("LV ${pet.journalCount}", color = textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
        LinearProgressIndicator(
            progress = { pet.progressInStage },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = moodColor,
            trackColor = borderColor
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
            Text(
                title, 
                color = textPrimary, 
                fontSize = 16.sp, 
                fontWeight = FontWeight.Bold, 
                maxLines = 1, 
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(time, color = textTertiary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            val sharedScope = com.gxdevs.athera.LocalSharedTransitionScope.current
            val navAnimScope = com.gxdevs.athera.LocalNavAnimatedVisibilityScope.current
            
            @OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
            val morphModifier = if (sharedScope != null && navAnimScope != null && entryId != null) {
                with(sharedScope) {
                    Modifier.sharedBounds(
                        sharedContentState = rememberSharedContentState(key = "journal_card_$entryId"),
                        animatedVisibilityScope = navAnimScope,
                        boundsTransform = { _, _ -> androidx.compose.animation.core.tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing) }
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
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = if (blurContent) Modifier.blur(8.dp) else Modifier
                    )
                    if (onClick != null || showLongPressHint) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            if (onClick != null) {
                                Text(
                                    text = "Tap to read â†’",
                                    color = primaryAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (showLongPressHint) {
                                Text(
                                    text = "Long press to count towards growth",
                                    color = textSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
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
        val sharedScope = com.gxdevs.athera.LocalSharedTransitionScope.current
        val navAnimScope = com.gxdevs.athera.LocalNavAnimatedVisibilityScope.current
        
        val morphModifier = if (sharedScope != null && navAnimScope != null) {
            with(sharedScope) {
                Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState(key = "bottom_bar_morph"),
                    animatedVisibilityScope = navAnimScope,
                    boundsTransform = { _, _ -> androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 400f) }
                )
            }
        } else {
            Modifier
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 45.dp)
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
                        spotColor = Color.Black.copy(alpha = 0.3f),
                        ambientColor = Color.Black.copy(alpha = 0.1f)
                    )
                    .clip(RoundedCornerShape(35.dp))
                    .background(bottomNavBackground)
                    .border(1.dp, borderColor.copy(alpha = 0.1f), RoundedCornerShape(35.dp))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                    BottomNavItem(icon = Icons.Rounded.Autorenew, isSelected = currentPage == 0, onClick = { onNavigatePage(0) })
                    BottomNavItem(icon = Icons.AutoMirrored.Rounded.List, isSelected = currentPage == 1, onClick = { onNavigatePage(1) })
                }
                
                Spacer(modifier = Modifier.width(60.dp)) // Space for overlapping center button
                
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                    BottomNavItem(icon = Icons.Rounded.LocalFireDepartment, isSelected = currentPage == 3, onClick = { onNavigatePage(3) })
                    BottomNavItem(icon = Icons.Rounded.Settings, isSelected = currentPage == 4, onClick = { onNavigatePage(4) })
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
                androidx.compose.foundation.Canvas(
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
                        .clickable(onClick = {
                            if (isHomeTab) onWriteJournal() else onNavigatePage(2)
                        }),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = isHomeTab,
                        label = "center_icon"
                    ) { isHome ->
                        if (isHome) {
                            Icon(
                                imageVector = Icons.Rounded.Create, // Write journal icon
                                contentDescription = "Write Journal",
                                tint = textPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Home, // Home icon
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
private fun BottomNavItem(icon: androidx.compose.ui.graphics.vector.ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    val animatedBgColor by animateColorAsState(
        targetValue = if (isSelected) accentBackground.copy(alpha = 0.15f) else Color.Transparent,
        animationSpec = tween(300),
        label = "nav_bg"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1.0f,
        animationSpec = tween(300),
        label = "nav_scale"
    )
    val iconColor by animateColorAsState(
        targetValue = if (isSelected) primaryAccent else textSecondary,
        animationSpec = tween(300),
        label = "nav_color"
    )
    Box(
        modifier = Modifier
            .height(40.dp)
            .width(56.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(animatedBgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
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
fun BetaWelcomeDialog(onComplete: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }

    val titles = listOf(
        "Welcome to Beta v1",
        "The Core Concept",
        "The Reliquary & Echo",
        "Privacy First"
    )
    val bodies = listOf(
        "This is a test beta version 1. For now, please test the currently available features in depth. Graphics are not finalized yet, so emojis are used instead of real artwork for companions.",
        "Your journey shapes your companions. Write a journal, and the dominant mood will unlock an egg or add growth points to it.\n\nNote: Only the latest journal of the day counts towards growth. You cannot write multiple journals for each emotion to unlock all eggs at once!",
        "â€¢ Reliquary: Seal a journal entry to be automatically unsealed after a set number of days. A message to your future self.\n\nâ€¢ The Echo: Occasionally resurfaces entries from exactly a year ago to reflect on your past.",
        "Your thoughts are private. In Settings, you can enable App Lock, setup a Decoy PIN (which displays a blank app state), prevent screenshots, and blur journal contents on your home screen."
    )
    val icons = listOf(
        Icons.Rounded.Science,
        Icons.Rounded.AutoAwesome,
        Icons.Rounded.HistoryEdu,
        Icons.Rounded.Shield
    )

    AlertDialog(
        onDismissRequest = { /* Require clicking buttons */ },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icons[step], contentDescription = null, tint = primaryAccent, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(titles[step], color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        },
        text = {
            Text(bodies[step], color = textSecondary, fontSize = 14.sp, lineHeight = 20.sp)
        },
        confirmButton = {
            Button(
                onClick = {
                    if (step < 3) step++ else onComplete()
                },
                colors = ButtonDefaults.buttonColors(containerColor = primaryAccent)
            ) {
                Text(if (step < 3) "Next" else "Let's Go", color = Color.White)
            }
        },
        dismissButton = {
            if (step > 0) {
                TextButton(onClick = { step-- }) {
                    Text("Back", color = textSecondary)
                }
            }
        },
        containerColor = cardBackground
    )
}
@Composable
fun InsightsTabPlaceholder() {
    Box(modifier = Modifier.fillMaxSize().background(mainContainerBackground), contentAlignment = Alignment.Center) {
        Text("Insights", color = textSecondary)
    }
}


@Composable
fun SettingsTabPlaceholder() {
    com.gxdevs.athera.ui.settings.SettingsScreen()
}

// ===================== VIEWMODEL CONNECTOR =====================
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel = viewModel(),
    petViewModel: com.gxdevs.athera.ui.pets.PetViewModel = viewModel(),
    onNavigateToText: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToJournals: () -> Unit = {},
    onNavigateToStats: () -> Unit = {},
    onEntryClick: (Long) -> Unit = {}
) {
    val userName by homeViewModel.userName.collectAsState()
    val entries by homeViewModel.filteredEntries.collectAsState()
    val searchQuery by homeViewModel.searchQuery.collectAsState()
    val petsState by petViewModel.petsState.collectAsState()

    HomeContent(
        userName = userName,
        entries = entries,
        searchQuery = searchQuery,
        onSearchQueryChange = { homeViewModel.updateSearchQuery(it) },
        onNavigateToText = onNavigateToText,
        onSaveUserName = { homeViewModel.saveUserName(it) },
        onEntryClick = onEntryClick
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
            onEntryClick = {}
        )
    }
}



