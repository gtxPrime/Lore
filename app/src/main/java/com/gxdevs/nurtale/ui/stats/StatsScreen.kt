package com.gxdevs.nurtale.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gxdevs.nurtale.data.mood.MoodConstants
import com.gxdevs.nurtale.ui.navigation.NavScreen
import com.gxdevs.nurtale.ui.navigation.SharedBottomNavBar
import com.gxdevs.nurtale.ui.pets.PetUiState
import com.gxdevs.nurtale.ui.pets.PetViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLocale

// ===================== BEIGE / EARTH / SAGE THEME =====================
private val appBackground = Color(0xFFEBE8E0)
private val mainContainerBackground = Color(0xFFF4F1EA)
private val borderColor = Color(0xFFE0DCD1)
private val cardBackground = Color(0xFFEAE7DF)
private val textPrimary = Color(0xFF2E332A)
private val textSecondary = Color(0xFF828779)
private val primaryAccent = Color(0xFF606F49)

@Composable
fun StatsScreen(
    onNavigateToHome: () -> Unit = {},
    onNavigateToJournals: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    viewModel: StatsViewModel = viewModel(),
    petViewModel: PetViewModel = viewModel()
) {
    val stats by viewModel.statsState.collectAsState()
    
    Scaffold(
        containerColor = mainContainerBackground,
        bottomBar = {
            SharedBottomNavBar(
                currentScreen = NavScreen.STATS,
                onHomeClick = onNavigateToHome,
                onJournalsClick = onNavigateToJournals,
                onStatsClick = { /* Already here */},
                onSettingsClick = onNavigateToSettings
            )
        }
    ) { paddingValues ->
        val petsState by petViewModel.petsState.collectAsState()
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            InsightsTab(stats, petsState.pets, onWriteJournal = onNavigateToJournals)
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun InsightsTab(stats: StatsState, pets: List<PetUiState> = emptyList(), onWriteJournal: () -> Unit = {}) {
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(top = 24.dp)) {
            Text("Your Nurtale.", color = textPrimary, fontSize = 36.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
            Spacer(modifier = Modifier.height(8.dp))
            Text("The history of your moods and presence.", color = textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(24.dp))
            
            // Tabs
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(borderColor.copy(alpha = 0.5f)).padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val tabs = listOf("JOURNEY", "MOODS", "COMPANIONS")
                tabs.forEachIndexed { index, title ->
                    val isSelected = pagerState.currentPage == index
                    val tabBgColor by animateColorAsState(
                        targetValue = if (isSelected) primaryAccent else Color.Transparent,
                        animationSpec = tween(300),
                        label = "tab_bg"
                    )
                    val tabTextColor by animateColorAsState(
                        targetValue = if (isSelected) mainContainerBackground else textSecondary,
                        animationSpec = tween(300),
                        label = "tab_text"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(tabBgColor)
                            .clickable { coroutineScope.launch { pagerState.animateScrollToPage(index) } }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(title, color = tabTextColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
                        val alpha = 1f - (0.5f * pageOffset.coerceIn(0f, 1f))
                        val scale = 1f - (0.1f * pageOffset.coerceIn(0f, 1f))
                        this.alpha = alpha
                        this.scaleX = scale
                        this.scaleY = scale
                    }
            ) {
                if (stats.daysShowedUp == 0 && page != 2) {
                    // Empty state for stats
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 32.dp)) {
                            Icon(Icons.Rounded.HistoryEdu, null, tint = textSecondary, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(16.dp))
                            Text("No entries yet", color = textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
                            Spacer(Modifier.height(8.dp))
                            Text("Start writing to see your insights here.", color = textSecondary, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Spacer(Modifier.height(24.dp))
                            Box(
                                modifier = Modifier
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                                    .background(primaryAccent)
                                    .clickable { onWriteJournal() }
                                    .padding(horizontal = 20.dp, vertical = 12.dp)
                            ) {
                                Text("Start Writing →", color = androidx.compose.ui.graphics.Color.White, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 180.dp)
                    ) {
                        item {
                            when (page) {
                                0 -> JourneyTabContent(stats)
                                1 -> MoodsTabContent(stats)
                                2 -> CompanionsTabContent(pets)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MoodsTabContent(stats: StatsState) {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        // Emotional Fingerprint
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cardBackground).padding(24.dp)
        ) {
            Text("Your Emotional Fingerprint", color = textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
            Spacer(modifier = Modifier.height(32.dp))
            
            val p = stats.emotionPercents
            val isEmpty = p.isEmpty()
            
            if (isEmpty) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.AutoMirrored.Rounded.ShowChart, null, tint = textSecondary, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No emotional data found", color = textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Record your mood to see insights", color = textSecondary, fontSize = 12.sp)
                    }
                }
            } else {
                // Donut Chart (Canvas) colors from MoodConstants
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.size(160.dp)) {
                        val strokeWidth = 40f
                        var startAngle = -90f
                        MoodConstants.ALL_MOODS.forEach { mood ->
                            val percent = p[mood] ?: 0f
                            if (percent > 0f) {
                                val color = MoodConstants.colorOf[mood] ?: Color.Gray
                                val sweepAngle = percent * 360f
                                drawArc(
                                    color = color, startAngle = startAngle, sweepAngle = sweepAngle,
                                    useCenter = false,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                                )
                                startAngle += sweepAngle
                            }
                        }
                    }
                    
                    // Center Icon
                    Icon(Icons.AutoMirrored.Rounded.ShowChart, null, tint = textSecondary, modifier = Modifier.size(32.dp))
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Legend colors from MoodConstants
                val legends = MoodConstants.ALL_MOODS.map { mood ->
                    Pair(mood, MoodConstants.colorOf[mood] ?: Color.Gray) to (stats.emotionPercents[mood] ?: 0f)
                }
                
                val columns = 2
                val rows = (legends.size + columns - 1) / columns
                
                for (r in 0 until rows) {
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        for (c in 0 until columns) {
                            val index = r + c * rows
                            if (index < legends.size) {
                                val item = legends[index]
                                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(item.first.second))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(item.first.first, color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Text("${(item.second * 100).toInt()}%", color = textSecondary, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(16.dp))
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = borderColor.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("All-time dominant", color = textSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                    if (stats.allTimeDominant != null) {
                        Text(stats.allTimeDominant, color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(6.dp))
                        val dColor = MoodConstants.colorOf[stats.allTimeDominant] ?: Color(0xFFF3C042)
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dColor))
                    } else {
                        Text("Not enough data", color = textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Lately feeling", color = textSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                    if (stats.latelyFeeling != null) {
                        Text(stats.latelyFeeling, color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(6.dp))
                        val dColor = MoodConstants.colorOf[stats.latelyFeeling] ?: Color(0xFF4A5638)
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dColor))
                    } else {
                        Text("Not enough data", color = textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("After Dark you usually feel", color = textSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                    if (stats.afterDarkFeeling != null) {
                        Text(stats.afterDarkFeeling, color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(6.dp))
                        val dColor = MoodConstants.colorOf[stats.afterDarkFeeling] ?: Color(0xFF606F49)
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dColor))
                    } else {
                        Text("Not enough data", color = textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
fun JourneyTabContent(stats: StatsState) {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        // Presence
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cardBackground).padding(24.dp)
        ) {
            Text("Your Presence", color = textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(appBackground).padding(16.dp)) {
                    Column {
                        Icon(Icons.Rounded.DateRange, null, tint = textSecondary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("${stats.daysShowedUp}", color = textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
                        Text("DAYS SHOWED\nUP", color = textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, lineHeight = 14.sp)
                    }
                }
                Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(appBackground).padding(16.dp)) {
                    Column {
                        Icon(Icons.Rounded.EditNote, null, tint = textSecondary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(String.format(LocalLocale.current.platformLocale,"%,d", stats.wordsWoven), color = textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
                        Text("WORDS WOVEN", color = textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(appBackground).padding(16.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${stats.currentStreak}", color = primaryAccent, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
                        Text("CURRENT\nSTREAK", color = textSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(appBackground).padding(16.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${stats.longestStreak}", color = textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
                        Text("LONGEST\nSTREAK", color = textSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(appBackground).padding(16.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val hours = stats.timeInsideSec / 3600
                        val mins = (stats.timeInsideSec % 3600) / 60
                        val timeStr = if (hours > 0) "${hours}h" else "${mins}m"
                        Text(timeStr, color = primaryAccent, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
                        Text("TIME\nINSIDE", color = textSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            CalendarActivityHeatmap()
            Spacer(modifier = Modifier.height(24.dp))
            Text("THIS WEEK", color = textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val days = listOf("M", "T", "W", "T", "F", "S", "S")
                days.forEachIndexed { index, d ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val dayMood = stats.thisWeekMoods.getOrNull(index)
                        val color = dayMood?.let { MoodConstants.colorOf[it] } ?: borderColor
                        Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(color))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(d, color = textSecondary, fontSize = 10.sp)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Capture Style
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cardBackground).padding(24.dp)
        ) {
            Text("How You Capture", color = textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
            Spacer(modifier = Modifier.height(32.dp))
            
            // Clock
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.size(160.dp).clip(CircleShape).border(1.dp, borderColor, CircleShape)) {
                    // Blob Shade based on mostCommonHourRaw
                    val angle = (stats.mostCommonHourRaw % 12) * 30f
                    val radians = Math.toRadians(angle.toDouble() - 90.0)
                    val radius = 50f
                    val offsetX = (cos(radians) * radius).toFloat()
                    val offsetY = (sin(radians) * radius).toFloat()
                    
                    Box(modifier = Modifier.align(Alignment.Center).offset(offsetX.dp, offsetY.dp).size(60.dp).background(
                        brush = androidx.compose.ui.graphics.Brush.radialGradient(
                            colors = listOf(Color(0xFFF3C042).copy(alpha = 0.5f), Color.Transparent)
                        )
                    ))
                    
                    // Clock hands/marks
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.align(Alignment.TopCenter).padding(8.dp).size(4.dp).clip(CircleShape).background(textSecondary))
                        Box(modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp).size(4.dp).clip(CircleShape).background(textSecondary))
                        Box(modifier = Modifier.align(Alignment.CenterStart).padding(8.dp).size(4.dp).clip(CircleShape).background(textSecondary))
                        Box(modifier = Modifier.align(Alignment.CenterEnd).padding(8.dp).size(4.dp).clip(CircleShape).background(textSecondary))
                        Icon(Icons.Rounded.Schedule, null, tint = textSecondary.copy(alpha = 0.5f), modifier = Modifier.align(Alignment.Center).size(24.dp))
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(stats.mostCommonHourStr, color = textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
                Text("Your most common\nwriting hour.", color = textSecondary, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            Text("CAPTURE STYLE", color = textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(16.dp))
            
            val total = (stats.textCount + stats.voiceCount + stats.photoCount + stats.videoCount).coerceAtLeast(1)
            val styles = listOf(
                Pair("Text", Color(0xFF606F49)) to stats.textCount,
                Pair("Voice", Color(0xFF4A5638)) to stats.voiceCount,
                Pair("Photo", Color(0xFFF3C042)) to stats.photoCount,
                Pair("Video", Color(0xFFB86C5A)) to stats.videoCount
            )
            
            styles.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Text(item.first.first, color = textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(56.dp))
                    LinearProgressIndicator(
                        progress = { item.second.toFloat() / total },
                        modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape),
                        color = item.first.second,
                        trackColor = borderColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val pct = (item.second.toFloat() / total * 100).toInt()
                    Text("$pct%", color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(appBackground).padding(16.dp)) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Image, null, tint = textSecondary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Photos saved", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                        Text("${stats.photoCount} memories", color = textSecondary, fontSize = 14.sp, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Mic, null, tint = textSecondary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Voice notes", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                        Text("${stats.voiceCount} memories", color = textSecondary, fontSize = 14.sp, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.VideoFile, null, tint = textSecondary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Video moments", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                        Text("${stats.videoCount} memories", color = textSecondary, fontSize = 14.sp, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun CompanionsTabContent(pets: List<PetUiState> = emptyList()) {
    // Only show pets/eggs the user has actually unlocked (stageIndex >= 0)
    val activePets = pets.filter { it.stageIndex >= 0 }
    // Higher-tier companions still evolving (same mood, next level)
    val lockedTierPets = pets.filter { it.stageIndex < 0 }

    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cardBackground).padding(24.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text("Your Companions", color = textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
                val evolving = activePets.count { !it.isFullyGrown }
                Text(if (evolving > 0) "$evolving EVOLVING" else if (activePets.isNotEmpty()) "${activePets.size} ACTIVE" else "",
                    color = textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            Spacer(modifier = Modifier.height(24.dp))

            if (activePets.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Egg, null, tint = textSecondary, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("No companions yet", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        Text("Write your first journal entry to hatch an egg!", color = textSecondary, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            } else {
                val chunked = activePets.chunked(2)
                chunked.forEachIndexed { idx, row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { pet ->
                            val color = MoodConstants.colorOf[pet.moodId] ?: primaryAccent
                            val icon = when(pet.stageIndex) {
                                0, 1 -> Icons.Rounded.Egg
                                2    -> Icons.Rounded.CrueltyFree
                                3    -> Icons.Rounded.Pets
                                4    -> Icons.Rounded.Flare
                                else -> Icons.Rounded.AutoAwesome
                            }
                            CompanionCard(
                                modifier = Modifier.weight(1f),
                                name = pet.moodId,
                                type = pet.stageName.uppercase(),
                                journalCount = pet.journalCount,
                                progress = pet.progressInStage,
                                color = color,
                                icon = icon
                            )
                        }
                        if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                    if (idx < chunked.size - 1) Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }

        // Only show next-tier locked section if there are higher-level companions waiting
        if (lockedTierPets.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cardBackground).padding(20.dp)
            ) {
                Text("Next Tier", color = textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
                Spacer(Modifier.height(8.dp))
                Text("${lockedTierPets.size} higher forms waiting. Keep journaling to unlock them.",
                    color = textSecondary, fontSize = 13.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    lockedTierPets.forEach { pet ->
                        val color = MoodConstants.colorOf[pet.moodId] ?: primaryAccent
                        Box(
                            modifier = Modifier.size(36.dp).clip(CircleShape)
                                .background(color.copy(alpha = 0.15f))
                                .border(1.dp, color.copy(0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Lock, null, tint = color.copy(0.4f), modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CompanionCard(modifier: Modifier = Modifier, name: String, type: String, journalCount: Int, progress: Float, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(modifier = modifier.clip(RoundedCornerShape(16.dp)).background(appBackground).padding(16.dp)) {
        Column {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape)
                    .background(color.copy(alpha = 0.12f))
                    .border(1.dp, color.copy(0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(name, color = textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(type, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.weight(1f).height(4.dp).clip(CircleShape),
                    color = color,
                    trackColor = borderColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("$journalCount d", color = textSecondary, fontSize = 10.sp)
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFFF4F1EA)
@Composable
fun InsightsTabPreview() {
    val mockStats = StatsState(
        daysShowedUp = 42,
        wordsWoven = 15300,
        currentStreak = 5,
        longestStreak = 14,
        timeInsideSec = 3600 * 12 + 60 * 30, // 12h 30m
        mostCommonHourStr = "11:00 pm",
        mostCommonHourRaw = 23,
        textCount = 120,
        voiceCount = 15,
        photoCount = 30,
        videoCount = 5,
        emotionPercents = mapOf(
            "Bright" to 0.4f,
            "Calm" to 0.2f,
            "Heavy" to 0.15f,
            "Tangled" to 0.1f,
            "Dark" to 0.1f,
            "Blank" to 0.05f
        ),
        allTimeDominant = "Bright",
        latelyFeeling = "Heavy",
        afterDarkFeeling = "Calm",
        thisWeekMoods = listOf(null, "Bright", null, "Heavy", "Calm", null, "Bright")
    )

    com.gxdevs.nurtale.ui.theme.MyApplicationTheme {
        Scaffold(containerColor = mainContainerBackground) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                InsightsTab(mockStats)
            }
        }
    }
}

