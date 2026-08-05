package com.gxdevs.lore.ui.pets

import android.graphics.BitmapFactory
import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.palette.graphics.Palette
import com.gxdevs.lore.data.mood.MoodConstants
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.absoluteValue

// ─── Design Tokens ────────────────────────────────────────────────────────────

private val mainContainerBackground = Color(0xFFFAF8F5)
private val appBackground           = Color(0xFFF3EEE6)
private val cardBackground          = Color(0xFFFFFFFF)
private val primaryAccent           = Color(0xFF606F49)
private val accentBackground        = Color(0xFFD9DFCD)
private val textPrimary             = Color(0xFF2C3224)
private val textSecondary           = Color(0xFF7A8370)
private val borderColor             = Color(0xFFE5DFC9)
private val tabSelectedColor        = Color(0xFF4A5638)

// ─── Palette & Bitmap Cache ──────────────────────────────────────────────────

data class LoadedPetImage(
    val imageBitmap: ImageBitmap?,
    val dominantColor: Color,
    val lightBgColor: Color
)

private val petImageMemoryCache = mutableMapOf<String, LoadedPetImage>()

fun getOrLoadPetPalette(filePath: String?, fallbackMoodId: String): LoadedPetImage {
    val fallbackColor = MoodConstants.colorOf[fallbackMoodId] ?: primaryAccent
    val fallbackBg    = MoodConstants.bgColorOf[fallbackMoodId] ?: accentBackground

    if (filePath.isNullOrBlank() || !File(filePath).exists()) {
        return LoadedPetImage(null, fallbackColor, fallbackBg)
    }

    // Special handling for DARK mood (Luna / Shadow companion):
    // Prevents small accent flowers/grass in Luna's stage artwork from randomly turning her card neon green, crimson, or royal blue
    if (fallbackMoodId.equals("dark", ignoreCase = true)) {
        val lunaColor = Color(0xFF3B3E56) // Mystical midnight twilight slate
        val lunaBg    = Color(0xFFDCE0EA)
        return petImageMemoryCache.getOrPut(filePath) {
            try {
                val bmp = BitmapFactory.decodeFile(filePath)
                LoadedPetImage(bmp?.asImageBitmap(), lunaColor, lunaBg)
            } catch (_: Exception) {
                LoadedPetImage(null, lunaColor, lunaBg)
            }
        }
    }

    return petImageMemoryCache.getOrPut(filePath) {
        try {
            val bmp = BitmapFactory.decodeFile(filePath)
            if (bmp != null) {
                val palette = Palette.from(bmp).generate()
                // Prefer vibrant swatch for rich character colors, falling back to dominant/muted
                val swatch = palette.vibrantSwatch 
                    ?: palette.dominantSwatch 
                    ?: palette.lightVibrantSwatch 
                    ?: palette.mutedSwatch

                if (swatch == null) {
                    return@getOrPut LoadedPetImage(bmp.asImageBitmap(), fallbackColor, fallbackBg)
                }

                val hsv = FloatArray(3)
                android.graphics.Color.colorToHSV(swatch.rgb, hsv)
                val saturation = hsv[1]
                val lightness  = hsv[2]

                val finalColor: Color
                val finalBgColor: Color

                // EXCEPTION FOR WHITE / DESATURATED PNGs:
                // If the extracted color is white/gray (saturation < 0.12f or lightness > 0.85f with low saturation),
                // use the emotion theme fallback so white pets get clean theme colors without random blue/green artifacts!
                if (saturation < 0.12f || (saturation < 0.20f && lightness > 0.85f)) {
                    finalColor = fallbackColor
                    finalBgColor = fallbackBg
                } else {
                    // Tune HSV for a vibrant, rich card color with balanced brightness (never muddy or dark)
                    val cardHsv = hsv.clone()
                    cardHsv[1] = cardHsv[1].coerceIn(0.35f, 0.75f) // Vibrant saturation
                    cardHsv[2] = cardHsv[2].coerceAtLeast(0.68f)    // Bright, vibrant lightness!

                    finalColor = Color(android.graphics.Color.HSVToColor(cardHsv))

                    val bgHsv = hsv.clone()
                    bgHsv[1] = (bgHsv[1] * 0.20f).coerceIn(0.10f, 0.28f)
                    bgHsv[2] = 0.96f // Soft luminous pastel background
                    finalBgColor = Color(android.graphics.Color.HSVToColor(bgHsv))
                }

                LoadedPetImage(bmp.asImageBitmap(), finalColor, finalBgColor)
            } else {
                LoadedPetImage(null, fallbackColor, fallbackBg)
            }
        } catch (_: Exception) {
            LoadedPetImage(null, fallbackColor, fallbackBg)
        }
    }
}

// ─── Emoji per emotion per stage (fallback / DEMO_MODE) ──────────────────────
private val petEmojis: Map<String, List<String>> = mapOf(
    "bright"  to listOf("\uD83E\uDD5A", "\uD83D\uDC9B", "\uD83D\uDC23", "\uD83D\uDC25", "\u2B50", "\u2600\uFE0F"),
    "calm"    to listOf("\uD83E\uDD5A", "\uD83D\uDC9A", "\uD83D\uDC23", "\uD83C\uDF3F", "\uD83C\uDF43", "\uD83C\uDF33"),
    "heavy"   to listOf("\uD83E\uDD5A", "\uD83D\uDC99", "\uD83D\uDC23", "\uD83D\uDC1B", "\uD83E\uDDAB", "\uD83E\uDD89"),
    "tangled" to listOf("\uD83E\uDD5A", "\uD83E\uDDE1", "\uD83D\uDC23", "\uD83D\uDC1B", "\uD83E\uDD8B", "\uD83C\uDF00"),
    "dark"    to listOf("\uD83E\uDD5A", "\uD83D\uDDA4", "\uD83D\uDC23", "\uD83E\uDD87", "\uD83C\uDF11", "\uD83C\uDF0C"),
    "blank"   to listOf("\uD83E\uDD5A", "\uD83E\uDD0D", "\uD83D\uDC23", "\uD83C\uDF2B\uFE0F", "\uD83D\uDCA8", "\u26A1")
)

internal fun emojiFor(emotion: String, stageIndex: Int): String {
    val list = petEmojis[emotion.lowercase()] ?: listOf("\uD83E\uDD5A", "\uD83D\uDC23", "\uD83D\uDC24", "\uD83D\uDC26", "\uD83E\uDD85", "\uD83C\uDF1F")
    return list.getOrElse(stageIndex.coerceAtLeast(0)) { list.last() }
}

// ─── Screen ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PetsScreen(
    viewModel: PetViewModel = viewModel(),
    journalViewModel: com.gxdevs.lore.ui.JournalViewModel = viewModel(),
    onOpenJourney: (PetUiState) -> Unit = {}
) {
    val petsState by viewModel.petsState.collectAsState()
    val pets      = petsState.pets

    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = 0,
        pageCount   = { pets.size.coerceAtLeast(1) }
    )
    val coroutineScope = rememberCoroutineScope()
    val scrollState    = rememberScrollState()

    val filteredIndices = remember(pets) { pets.indices.toList() }

    var levelUpDialogPet by remember { mutableStateOf<PetUiState?>(null) }

    // --- Dynamic Active Pet Palette (updates as pager scrolls or pet changes) ---
    val currentPet = remember(pets, pagerState.currentPage) {
        pets.getOrNull(pagerState.currentPage.coerceIn(0, (pets.size - 1).coerceAtLeast(0)))
    }
    val currentLoadedImage = remember(currentPet?.localImagePath, currentPet?.moodId) {
        if (currentPet != null) getOrLoadPetPalette(currentPet.localImagePath, currentPet.moodId) else null
    }

    val activeMoodColor by animateColorAsState(
        targetValue = if (currentPet == null) primaryAccent
        else if (petsState.useEmojiVisuals) (MoodConstants.colorOf[currentPet.moodId] ?: primaryAccent)
        else (currentLoadedImage?.dominantColor ?: primaryAccent),
        animationSpec = tween(400),
        label = "active_mood_color"
    )
    val activeBgColor by animateColorAsState(
        targetValue = if (currentPet == null) mainContainerBackground
        else if (petsState.useEmojiVisuals) (MoodConstants.bgColorOf[currentPet.moodId] ?: accentBackground)
        else (currentLoadedImage?.lightBgColor ?: accentBackground),
        animationSpec = tween(400),
        label = "active_bg_color"
    )
    val animatedScreenBg by animateColorAsState(
        targetValue = if (currentPet == null) mainContainerBackground
        else Color(
            red = (mainContainerBackground.red * 0.65f + activeBgColor.red * 0.35f),
            green = (mainContainerBackground.green * 0.65f + activeBgColor.green * 0.35f),
            blue = (mainContainerBackground.blue * 0.65f + activeBgColor.blue * 0.35f),
            alpha = 1f
        ),
        animationSpec = tween(500),
        label = "animated_screen_bg"
    )

    // Floating idle animation driver
    val infiniteTransition = rememberInfiniteTransition(label = "pet_float")
    val petFloatY by infiniteTransition.animateFloat(
        initialValue = -4f, targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pet_float_y"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(animatedScreenBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                .verticalScroll(scrollState)
                .padding(bottom = 120.dp)
        ) {
            // --- Top Header: Title ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text       = "Archive.",
                        fontSize   = 32.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        color      = textPrimary
                    )
                }
            }

            // --- Interactive Demo Controller Card ---
            if (petsState.isDemoMode) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "⚡ DEMO CONTROLLER",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryAccent.copy(alpha = 0.7f),
                        letterSpacing = 1.sp
                    )

                    Spacer(Modifier.height(6.dp))

                    val stageLabels = listOf("Auto", "Egg (1)", "Stage 2", "Stage 3", "Stage 4", "Mythic (5)")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        stageLabels.forEachIndexed { index, label ->
                            val targetStage = if (index == 0) null else index - 1
                            val isSelected = petsState.demoStageOverride == targetStage
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (isSelected) primaryAccent else Color.Transparent)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) Color.Transparent else borderColor,
                                        shape = RoundedCornerShape(50)
                                    )
                                    .clickable { 
                                        viewModel.setDemoStageOverride(targetStage)
                                        pets.firstOrNull()?.let { samplePet ->
                                            val stageIdx = targetStage ?: samplePet.stageIndex
                                            levelUpDialogPet = samplePet.copy(
                                                stageIndex = stageIdx,
                                                stageName = MoodConstants.stages.getOrNull(stageIdx)?.name ?: samplePet.stageName
                                            )
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isSelected) Color.White else textSecondary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (petsState.isLoading) {
                Box(
                    modifier           = Modifier.fillMaxWidth().height(260.dp),
                    contentAlignment   = Alignment.Center
                ) { CircularProgressIndicator(color = primaryAccent) }
            } else if (pets.isEmpty()) {
                Box(
                    modifier           = Modifier.fillMaxWidth().height(260.dp),
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
                    contentPadding = PaddingValues(horizontal = 90.dp),
                    modifier       = Modifier.fillMaxWidth().height(280.dp)
                ) { page ->
                    val pet = pets.getOrNull(page) ?: return@HorizontalPager
                    val position = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                    val pageOffset = position.absoluteValue.coerceIn(0f, 1f)
                    val centerFactor = (1f - pageOffset).coerceIn(0f, 1f)
                    val isCenter = pageOffset < 0.5f

                    // Extract dynamic dominant color from pet image bitmap (or mood fallback)
                    val loadedImage = remember(pet.localImagePath, pet.moodId) {
                        getOrLoadPetPalette(pet.localImagePath, pet.moodId)
                    }

                    val moodColor by animateColorAsState(
                        targetValue = if (petsState.useEmojiVisuals) (MoodConstants.colorOf[pet.moodId] ?: primaryAccent) else loadedImage.dominantColor,
                        animationSpec = tween(350),
                        label = "mood_color"
                    )
                    val moodBgColor by animateColorAsState(
                        targetValue = if (petsState.useEmojiVisuals) (MoodConstants.bgColorOf[pet.moodId] ?: accentBackground) else loadedImage.lightBgColor,
                        animationSpec = tween(350),
                        label = "mood_bg_color"
                    )

                    // Smooth continuous background color calculation (zero dark box snapping)
                    val smoothCircleBgColor = Color(
                        red = appBackground.red * (1f - centerFactor) + moodBgColor.red * centerFactor,
                        green = appBackground.green * (1f - centerFactor) + moodBgColor.green * centerFactor,
                        blue = appBackground.blue * (1f - centerFactor) + moodBgColor.blue * centerFactor,
                        alpha = 1f
                    )

                    Box(
                        modifier         = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val scale       = 1f - (0.20f * pageOffset)
                                val alpha       = 1f - (0.40f * pageOffset)
                                scaleX = scale
                                scaleY = scale
                                this.alpha = alpha
                                rotationY = (position.coerceIn(-1f, 1f) * -10f)
                                translationX = position * -8f
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Background Circle (100% continuous elevation, background & border interpolation)
                        Box(
                            modifier = Modifier
                                .size(200.dp)
                                .shadow(
                                    elevation     = (6 + 10 * centerFactor).dp,
                                    shape         = CircleShape,
                                    spotColor     = androidx.compose.ui.graphics.lerp(Color.Black.copy(alpha = 0.16f), moodColor.copy(alpha = 0.28f), centerFactor),
                                    ambientColor  = androidx.compose.ui.graphics.lerp(Color.Black.copy(alpha = 0.06f), moodColor.copy(alpha = 0.08f), centerFactor)
                                )
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(smoothCircleBgColor, smoothCircleBgColor.copy(alpha = 0.75f))
                                    )
                                )
                                .border(
                                    width  = (2.5f * centerFactor).dp,
                                    brush  = Brush.linearGradient(
                                        listOf(moodColor.copy(0.6f * centerFactor), moodColor.copy(0.15f * centerFactor))
                                    ),
                                    shape  = CircleShape
                                )
                        )

                        // Pet Stage Drawing (Centered inside circle with floating animation when centered)
                        Box(
                            modifier = if (isCenter) Modifier.graphicsLayer { translationY = (petFloatY * centerFactor).dp.toPx() } else Modifier
                        ) {
                            PetStageVisual(
                                pet        = pet,
                                moodColor  = if (isCenter) moodColor else textSecondary.copy(alpha = 0.4f),
                                isCenter   = isCenter,
                                forceEmoji = petsState.useEmojiVisuals,
                                loadedImage = loadedImage
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // --- Pet detail ---
                val currentPetDetail = pets.getOrElse(pagerState.currentPage.coerceIn(0, (pets.size - 1).coerceAtLeast(0))) { pets.first() }

                androidx.compose.animation.AnimatedContent(
                    targetState = currentPetDetail,
                    transitionSpec = {
                        (fadeIn(tween(350, easing = FastOutSlowInEasing)) + scaleIn(tween(350, easing = FastOutSlowInEasing), initialScale = 0.95f))
                            .togetherWith(fadeOut(tween(200)))
                    },
                    label = "pet_details_anim"
                ) { targetPet ->
                    val targetLoadedImage = remember(targetPet.localImagePath, targetPet.moodId) {
                        getOrLoadPetPalette(targetPet.localImagePath, targetPet.moodId)
                    }

                    val targetMoodColor by animateColorAsState(
                        targetValue = if (petsState.useEmojiVisuals) (MoodConstants.colorOf[targetPet.moodId] ?: primaryAccent) else targetLoadedImage.dominantColor,
                        animationSpec = tween(350, easing = FastOutSlowInEasing),
                        label = "detail_mood_color"
                    )

                    Column(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalAlignment   = Alignment.CenterHorizontally
                    ) {
                        // Pet name from DB (Solara, Cappi, Pebble, River, Luna, Vael)
                        Text(
                            text       = targetPet.name,
                            fontSize   = 32.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif,
                            color      = textPrimary
                        )
                        // Emotion label (mood category)
                        Text(
                            text          = targetPet.emotion.uppercase(),
                            fontSize      = 10.sp,
                            fontWeight    = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            color         = targetMoodColor.copy(alpha = 0.7f)
                        )
                        Spacer(Modifier.height(6.dp))

                        // Dynamic Stage badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(targetMoodColor.copy(alpha = 0.12f))
                                .border(1.dp, targetMoodColor.copy(alpha = 0.35f), RoundedCornerShape(50))
                                .padding(horizontal = 14.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text       = if (targetPet.stageIndex < 0) "Locked" else "Stage ${targetPet.stageIndex + 1} • ${targetPet.stageName}",
                                fontSize   = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color      = targetMoodColor
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Description text
                        Text(
                            text       = targetPet.description,
                            fontSize   = 13.sp,
                            color      = textSecondary,
                            textAlign  = TextAlign.Center,
                            lineHeight = 20.sp,
                            modifier   = Modifier.padding(horizontal = 40.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // --- Dynamic Progress bar ---
                        if (targetPet.stageIndex >= 0) {
                            Column(
                                modifier            = Modifier.padding(horizontal = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier              = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text     = if (targetPet.isFullyGrown) "MAX LEVEL REACHED" else "PROGRESS TO STAGE ${targetPet.stageIndex + 2}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        color    = textSecondary
                                    )
                                    Text(
                                        text     = if (targetPet.isFullyGrown) "Full Grown" else "${targetPet.journalsToNext} journal${if (targetPet.journalsToNext == 1) "" else "s"} left",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color    = targetMoodColor
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .shadow(4.dp, CircleShape, spotColor = targetMoodColor.copy(0.3f))
                                        .clip(CircleShape)
                                        .background(targetMoodColor.copy(alpha = 0.15f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(fraction = if (targetPet.isFullyGrown) 1f else targetPet.progressInStage)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(targetMoodColor, targetMoodColor.copy(alpha = 0.75f))
                                                )
                                            )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // --- "✦ View Journey" Button ---
                        val journeyShimmer = rememberInfiniteTransition(label = "journey_btn_shimmer")
                        val journeyShimmerX by journeyShimmer.animateFloat(
                            initialValue = -200f,
                            targetValue  = 500f,
                            animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
                            label = "journey_shimmer_x"
                        )
                        val journeyBtnScale by journeyShimmer.animateFloat(
                            initialValue = 1f, targetValue = 1.025f,
                            animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
                            label = "journey_btn_scale"
                        )

                        Box(
                            modifier = Modifier
                                .graphicsLayer { scaleX = journeyBtnScale; scaleY = journeyBtnScale }
                                .clip(RoundedCornerShape(50))
                                .shadow(8.dp, RoundedCornerShape(50), spotColor = targetMoodColor.copy(0.35f))
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            targetMoodColor.copy(alpha = 0.90f),
                                            targetMoodColor.copy(alpha = 0.65f)
                                        )
                                    )
                                )
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null
                                ) {
                                    onOpenJourney(targetPet)
                                }
                                .padding(horizontal = 28.dp, vertical = 13.dp)
                        ) {
                            // Shimmer sweep on button
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color.White.copy(alpha = 0.25f),
                                                Color.Transparent
                                            ),
                                            start = androidx.compose.ui.geometry.Offset(journeyShimmerX, 0f),
                                            end   = androidx.compose.ui.geometry.Offset(journeyShimmerX + 120f, 60f)
                                        )
                                    )
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text       = "✦",
                                    fontSize   = 14.sp,
                                    color      = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text       = "View Journey",
                                    fontSize   = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color      = Color.White,
                                    letterSpacing = 0.3.sp
                                )
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
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                for (j in 0 until 3) {
                                    val idx = i + j
                                    if (idx < filteredIndices.size) {
                                        val realIndex  = filteredIndices[idx]
                                        val pet        = pets[realIndex]
                                        val isSelected = realIndex == pagerState.currentPage

                                        val gridLoadedImage = remember(pet.localImagePath, pet.moodId) {
                                            getOrLoadPetPalette(pet.localImagePath, pet.moodId)
                                        }

                                        val moodColor = if (petsState.useEmojiVisuals) (MoodConstants.colorOf[pet.moodId] ?: primaryAccent) else gridLoadedImage.dominantColor
                                        val isLocked  = pet.stageIndex < 0

                                        val bgColor by animateColorAsState(
                                            targetValue = if (isSelected) cardBackground else if (isLocked) appBackground.copy(alpha = 0.7f) else appBackground,
                                            animationSpec = tween(300),
                                            label = "pet_bg"
                                        )
                                        val bw by animateDpAsState(
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
                                                .clickable(
                                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                                    indication = null
                                                ) {
                                                    coroutineScope.launch { pagerState.animateScrollToPage(realIndex) }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier            = Modifier.padding(8.dp)
                                            ) {
                                                val iconBgColor by animateColorAsState(
                                                    targetValue = if (isLocked) borderColor.copy(alpha = 0.5f) else if (isSelected) gridLoadedImage.lightBgColor else cardBackground,
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
                                                    PetGridIcon(
                                                        pet = pet,
                                                        tint = if (isLocked) textSecondary.copy(0.4f) else if (isSelected) moodColor else textSecondary,
                                                        forceEmoji = petsState.useEmojiVisuals,
                                                        loadedImage = gridLoadedImage
                                                    )
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
                                                if (isLocked && pet.journalsToNext > 0) {
                                                    Text(
                                                        text      = "${pet.journalsToNext}j needed",
                                                        fontSize  = 7.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color     = textSecondary.copy(0.35f)
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(30.dp))
            }
        }

        val pendingPet by viewModel.pendingLevelUpPet.collectAsState()
        val activeDialogPet = levelUpDialogPet ?: pendingPet

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
                    if (levelUpDialogPet != null) levelUpDialogPet = null
                    viewModel.clearPendingLevelUpPet()
                }
            )
        }
    }
}

// --- Pet visual - emoji/shape (fallback) / image (cached with palette) ───────

@Composable
fun PetStageVisual(
    pet: PetUiState,
    moodColor: Color,
    isCenter: Boolean,
    forceEmoji: Boolean = false,
    loadedImage: LoadedPetImage? = null
) {
    val size = if (isCenter) 140.dp else 100.dp

    // Locked
    if (pet.stageIndex < 0) {
        Icon(Icons.Rounded.Lock, contentDescription = "Locked",
            modifier = Modifier.size(48.dp), tint = textSecondary.copy(alpha = 0.3f))
        return
    }

    // Render bitmap if available and emoji is not forced (unclipped with 2.dp inner margin)
    if (!forceEmoji && loadedImage?.imageBitmap != null) {
        Image(
            bitmap = loadedImage.imageBitmap,
            contentDescription = pet.name,
            modifier = Modifier
                .size(size)
                .padding(2.dp),
            contentScale = ContentScale.Fit
        )
        return
    }

    // Show spinner if downloading, else show emoji fallback
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        if (!forceEmoji && !pet.currentStageImageUrl.isNullOrEmpty() && loadedImage?.imageBitmap == null) {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                color = moodColor.copy(alpha = 0.6f),
                strokeWidth = 2.dp
            )
        } else {
            PetEmojiVisual(pet = pet, moodColor = moodColor, size = size)
        }
    }
}

@Composable
fun PetEmojiVisual(pet: PetUiState, moodColor: Color, size: androidx.compose.ui.unit.Dp) {
    val emoji = emojiFor(pet.emotion, pet.stageIndex)

    when (pet.stageIndex) {
        0 -> {
            val inf = rememberInfiniteTransition(label = "egg_pulse")
            val scale by inf.animateFloat(0.95f, 1.05f,
                infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "scale")
            Text(emoji, fontSize = (size.value * 0.55f).sp,
                modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale })
        }
        1 -> {
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
            val inf = rememberInfiniteTransition(label = "float")
            val offsetY by inf.animateFloat(0f, -8f,
                infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "y")
            Text(emoji, fontSize = (size.value * 0.55f).sp,
                modifier = Modifier.graphicsLayer { translationY = offsetY })
        }
        else -> {
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

@Composable
fun PetGridIcon(
    pet: PetUiState,
    tint: Color,
    forceEmoji: Boolean = false,
    loadedImage: LoadedPetImage? = null
) {
    if (pet.stageIndex < 0) {
        Icon(Icons.Rounded.Lock, null, tint = tint, modifier = Modifier.size(20.dp))
        return
    }

    if (!forceEmoji && loadedImage?.imageBitmap != null) {
        Image(
            bitmap = loadedImage.imageBitmap,
            contentDescription = pet.name,
            modifier = Modifier.size(32.dp).clip(CircleShape),
            contentScale = ContentScale.Fit
        )
    } else {
        Text(emojiFor(pet.emotion, pet.stageIndex), fontSize = 20.sp)
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun PetsScreenPreview() {
    com.gxdevs.lore.ui.theme.MyApplicationTheme { PetsScreen() }
}
