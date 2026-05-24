package com.gxdevs.aethra.ui.pets

import androidx.compose.animation.core.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.gxdevs.aethra.MoodConstants
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

// --- App colour palette ------------------------------------------------------
// ——— App colour palette ——————————————————————————————————————————————————————
private val appBackground           = Color(0xFFEBE8E0)
private val mainContainerBackground = Color(0xFFF4F1EA)
private val borderColor             = Color(0xFFE0DCD1)
private val cardBackground          = Color(0xFFEAE7DF)
private val textPrimary             = Color(0xFF2E332A)
private val textSecondary           = Color(0xFF828779)
private val primaryAccent           = Color(0xFF606F49)
private val accentBackground        = Color(0xFFD9DFCD)
private val tabSelectedColor        = primaryAccent

// ——— Emoji per emotion per stage (fallback / DEMO_MODE) —————————————————————
private val petEmojis: Map<String, List<String>> = mapOf(
    "bright"  to listOf("🥚", "💛", "🐣", "🐥", "⭐", "☀️"),
    "calm"    to listOf("🥚", "💚", "🐣", "🌿", "🍃", "🌳"),
    "heavy"   to listOf("🥚", "💚", "🐣", "🐛", "🦫", "🦉"),
    "tangled" to listOf("🥚", "🧡", "🐣", "🐛", "🦋", "🌀"),
    "dark"    to listOf("🥚", "🖤", "🐣", "🦇", "🌑", "🌌"),
    "blank"   to listOf("🥚", "🤍", "🐣", "🌫️", "💨", "⚡")
)

private fun emojiFor(emotion: String, stageIndex: Int): String {
    val list = petEmojis[emotion.lowercase()] ?: listOf("🥚", "🐣", "🐤", "🐦", "🦅", "🌟")
    return list.getOrElse(stageIndex.coerceAtLeast(0)) { list.last() }
}

// ——— Screen ———————————————————————————————————————————————————————————————————

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PetsScreen(
    viewModel: PetViewModel = viewModel()
) {
    val petsState by viewModel.petsState.collectAsState()
    val pets      = petsState.pets

    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = 0,
        pageCount   = { pets.size.coerceAtLeast(1) }   // DB-driven, not hardcoded
    )
    var selectedFilter by remember { mutableStateOf("ALL") }
    val tabs           = listOf("ALL", "GROWING", "MYTHIC")
    val coroutineScope = rememberCoroutineScope()
    val scrollState    = rememberScrollState()

    val filteredIndices = remember(selectedFilter, pets) {
        pets.indices.filter { idx ->
            when (selectedFilter) {
                "ALL"     -> true
                "GROWING" -> pets[idx].stageIndex in 0 until (pets[idx].totalStages - 1)
                "MYTHIC"  -> pets[idx].isFullyGrown
                else      -> true
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(mainContainerBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                .verticalScroll(scrollState)
                .padding(bottom = 120.dp)
        ) {
            // --- Top bar ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text       = "Archive.",
                    fontSize   = 32.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    color      = textPrimary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    tabs.forEach { tab ->
                        val isSelected = selectedFilter == tab
                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) tabSelectedColor else textSecondary,
                            animationSpec = tween(300),
                            label = "filter_color"
                        )
                        Text(
                            text       = tab,
                            fontSize   = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color      = textColor,
                            modifier   = Modifier.clickable { selectedFilter = tab }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (petsState.isLoading) {
                Box(
                    modifier           = Modifier.fillMaxWidth().height(240.dp),
                    contentAlignment   = Alignment.Center
                ) { CircularProgressIndicator(color = primaryAccent) }
            } else if (pets.isEmpty()) {
                Box(
                    modifier           = Modifier.fillMaxWidth().height(240.dp),
                    contentAlignment   = Alignment.Center
                ) {
                    Text(
                        text      = "No companions yet.\nStart journaling to hatch your first pet.",
                        color     = textSecondary,
                        fontSize  = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                }
            } else {
                // --- Hero Pager ---
                HorizontalPager(
                    state          = pagerState,
                    contentPadding = PaddingValues(horizontal = 100.dp),
                    modifier       = Modifier.fillMaxWidth().height(260.dp)
                ) { page ->
                    val pet         = pets.getOrNull(page) ?: return@HorizontalPager
                    val pageOffset  = ((pagerState.currentPage - page) +
                        pagerState.currentPageOffsetFraction).absoluteValue
                    val scale       = 1f - (0.3f * pageOffset.coerceIn(0f, 1f))
                    val alpha       = 1f - (0.5f * pageOffset.coerceIn(0f, 1f))
                    val isCenter    = pageOffset < 0.5f
                    val moodColor   = MoodConstants.colorOf[pet.moodId] ?: primaryAccent
                    val moodBgColor = MoodConstants.bgColorOf[pet.moodId] ?: accentBackground

                    Box(
                        modifier         = Modifier
                            .fillMaxSize()
                            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(190.dp)
                                .shadow(
                                    elevation     = if (isCenter) 28.dp else 8.dp,
                                    shape         = CircleShape,
                                    spotColor     = moodColor.copy(alpha = 0.3f),
                                    ambientColor  = moodColor.copy(alpha = 0.1f)
                                )
                                .clip(CircleShape)
                                .background(
                                    if (isCenter)
                                        Brush.radialGradient(listOf(moodBgColor, moodBgColor.copy(alpha = 0.6f)))
                                    else
                                        Brush.radialGradient(listOf(appBackground, appBackground))
                                )
                                .border(
                                    width  = if (isCenter) 2.dp else 0.dp,
                                    brush  = Brush.linearGradient(listOf(moodColor.copy(0.5f), moodColor.copy(0.1f))),
                                    shape  = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            PetStageVisual(
                                pet        = pet,
                                moodColor  = if (isCenter) moodColor else textSecondary.copy(alpha = 0.4f),
                                isCenter   = isCenter
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // --- Pet detail ---
                val currentPet      = pets.getOrElse(pagerState.currentPage) { pets.first() }
                val currentMoodColor = MoodConstants.colorOf[currentPet.moodId] ?: primaryAccent

                Column(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalAlignment   = Alignment.CenterHorizontally
                ) {
                    // Pet name from DB (not moodId)
                    Text(
                        text       = currentPet.name,
                        fontSize   = 32.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        color      = textPrimary
                    )
                    // Emotion label (mood category)
                    Text(
                        text      = currentPet.emotion.uppercase(),
                        fontSize  = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color     = currentMoodColor.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(4.dp))

                    // Stage badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(currentMoodColor.copy(alpha = 0.12f))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text          = currentPet.stageName.uppercase(),
                            fontSize      = 10.sp,
                            fontWeight    = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color         = currentMoodColor
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Description from DB catalog (not MoodConstants)
                    Text(
                        text       = currentPet.description,
                        fontSize   = 15.sp,
                        fontFamily = FontFamily.Serif,
                        fontStyle  = FontStyle.Italic,
                        color      = textSecondary,
                        textAlign  = TextAlign.Center,
                        modifier   = Modifier.padding(horizontal = 32.dp),
                        lineHeight = 24.sp
                    )

                    Spacer(Modifier.height(20.dp))

                    // Progress
                    when {
                        currentPet.stageIndex < 0 -> {
                            Text(
                                text      = "Evolve the previous lineage pet to unlock",
                                fontSize  = 12.sp,
                                color     = textSecondary,
                                fontStyle = FontStyle.Italic
                            )
                        }
                        !currentPet.isFullyGrown -> {
                            Column(
                                modifier            = Modifier.padding(horizontal = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                LinearProgressIndicator(
                                    progress   = { currentPet.progressInStage },
                                    modifier   = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                    color      = currentMoodColor,
                                    trackColor = borderColor
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text     = if (currentPet.journalsToNext > 0)
                                        "${currentPet.journalsToNext} more journaling day${if (currentPet.journalsToNext != 1) "s" else ""} until next stage"
                                    else "Stage complete!",
                                    fontSize = 11.sp,
                                    color    = textSecondary
                                )
                            }
                        }
                        else -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.AutoAwesome, null, tint = currentMoodColor, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Fully evolved · Mythic", fontSize = 12.sp, color = currentMoodColor, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                // --- Pet grid ---
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    if (filteredIndices.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                            Text("No companions match this filter.", color = textSecondary, fontSize = 14.sp, fontStyle = FontStyle.Italic)
                        }
                    } else {
                        for (i in filteredIndices.indices step 3) {
                            Row(
                                modifier             = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                for (j in 0 until 3) {
                                    val idx = i + j
                                    if (idx < filteredIndices.size) {
                                        val realIndex  = filteredIndices[idx]
                                        val pet        = pets[realIndex]
                                        val isSelected = realIndex == pagerState.currentPage
                                        val moodColor  = MoodConstants.colorOf[pet.moodId] ?: primaryAccent
                                        val isLocked   = pet.stageIndex < 0

                                        val bgColor by animateColorAsState(
                                            targetValue = if (isSelected) cardBackground else if (isLocked) appBackground.copy(alpha = 0.7f) else appBackground,
                                            animationSpec = tween(300),
                                            label = "pet_bg"
                                        )
                                        val bw by androidx.compose.animation.core.animateDpAsState(
                                            targetValue = if (isSelected) 1.5.dp else 0.dp,
                                            animationSpec = tween(300),
                                            label = "pet_border"
                                        )
                                        val bc by animateColorAsState(
                                            targetValue = if (isSelected) moodColor.copy(0.4f) else Color.Transparent,
                                            animationSpec = tween(300),
                                            label = "pet_border_color"
                                        )

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(0.85f)
                                                .clip(RoundedCornerShape(24.dp))
                                                .background(bgColor)
                                                .border(
                                                    width = bw,
                                                    color = bc,
                                                    shape = RoundedCornerShape(24.dp)
                                                )
                                                .clickable {
                                                    coroutineScope.launch { pagerState.animateScrollToPage(realIndex) }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier            = Modifier.padding(8.dp)
                                            ) {
                                                val iconBgColor by animateColorAsState(
                                                    targetValue = if (isLocked) borderColor.copy(alpha = 0.5f) else if (isSelected) MoodConstants.bgColorOf[pet.moodId] ?: accentBackground else cardBackground,
                                                    animationSpec = tween(300),
                                                    label = "icon_bg"
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .size(52.dp)
                                                        .clip(CircleShape)
                                                        .background(iconBgColor),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    PetGridIcon(pet = pet, tint = if (isLocked) textSecondary.copy(0.4f) else if (isSelected) moodColor else textSecondary)
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                                // Pet name from DB
                                                Text(
                                                    text       = pet.name,
                                                    fontSize   = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Serif,
                                                    color      = if (isLocked) textSecondary.copy(0.4f) else if (isSelected) textPrimary else textSecondary
                                                )
                                                Text(
                                                    text          = if (isLocked) "LOCKED" else pet.stageName.uppercase(),
                                                    fontSize      = 8.sp,
                                                    fontWeight    = FontWeight.Bold,
                                                    letterSpacing = 0.5.sp,
                                                    color         = if (isLocked) textSecondary.copy(0.3f) else if (isSelected) moodColor else textSecondary.copy(0.6f)
                                                )
                                            }
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}

// --- Pet visual — emoji/shape (DEMO_MODE or no image) / image (cached) -------

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun PetStageVisual(pet: PetUiState, moodColor: Color, isCenter: Boolean) {
    val size = if (isCenter) 80.dp else 64.dp

    // Locked
    if (pet.stageIndex < 0) {
        Icon(Icons.Rounded.Lock, contentDescription = "Locked",
            modifier = Modifier.size(48.dp), tint = textSecondary.copy(alpha = 0.3f))
        return
    }

    // If DEMO_MODE OR no local image cached → show emoji / shape visual
    if (PetViewModel.DEMO_MODE || pet.localImagePath == null) {
        PetEmojiVisual(pet = pet, moodColor = moodColor, size = size)
        return
    }

    // Real image (cached locally)
    com.bumptech.glide.integration.compose.GlideImage(
        model       = java.io.File(pet.localImagePath),
        contentDescription = pet.name,
        modifier    = Modifier.size(size)
    )
}

@Composable
fun PetEmojiVisual(pet: PetUiState, moodColor: Color, size: androidx.compose.ui.unit.Dp) {
    val emoji = emojiFor(pet.emotion, pet.stageIndex)

    when (pet.stageIndex) {
        0 -> {
            // Egg — pulsing
            val inf = rememberInfiniteTransition(label = "egg_pulse")
            val scale by inf.animateFloat(0.95f, 1.05f,
                infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "scale")
            Text(emoji, fontSize = (size.value * 0.55f).sp,
                modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale })
        }
        1 -> {
            // Cracked — slight wobble
            val inf = rememberInfiniteTransition(label = "crack_wobble")
            val rot by inf.animateFloat(-3f, 3f,
                infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "rot")
            Text(emoji, fontSize = (size.value * 0.55f).sp,
                modifier = Modifier.graphicsLayer { rotationZ = rot })
        }
        in 2..3 -> {
            Text(emoji, fontSize = (size.value * 0.55f).sp)
        }
        4 -> {
            // Juvenile — float
            val inf = rememberInfiniteTransition(label = "float")
            val offsetY by inf.animateFloat(0f, -8f,
                infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "y")
            Text(emoji, fontSize = (size.value * 0.55f).sp,
                modifier = Modifier.graphicsLayer { translationY = offsetY })
        }
        else -> {
            // Mythic — float + glow
            val inf = rememberInfiniteTransition(label = "mythic")
            val offsetY by inf.animateFloat(0f, -10f,
                infiniteRepeatable(tween(2000), RepeatMode.Reverse), label = "y")
            val glowAlpha by inf.animateFloat(0.3f, 0.9f,
                infiniteRepeatable(tween(2000), RepeatMode.Reverse), label = "glow")
            Box(contentAlignment = Alignment.Center,
                modifier = Modifier.size(size).graphicsLayer { translationY = offsetY }) {
                Box(Modifier.size(size).clip(CircleShape)
                    .background(Brush.radialGradient(listOf(moodColor.copy(glowAlpha), Color.Transparent))))
                Text(emoji, fontSize = (size.value * 0.55f).sp)
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun PetGridIcon(pet: PetUiState, tint: Color) {
    if (pet.stageIndex < 0) {
        Icon(Icons.Rounded.Lock, null, tint = tint, modifier = Modifier.size(20.dp))
        return
    }
    // In DEMO_MODE or no image → emoji; else small icon
    if (PetViewModel.DEMO_MODE || pet.localImagePath == null) {
        Text(emojiFor(pet.emotion, pet.stageIndex), fontSize = 20.sp)
    } else {
        GlideImage(
            model              = java.io.File(pet.localImagePath),
            contentDescription = pet.name,
            modifier           = Modifier.size(22.dp).clip(CircleShape)
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun PetsScreenPreview() {
    com.gxdevs.aethra.ui.theme.MyApplicationTheme { PetsScreen() }
}

