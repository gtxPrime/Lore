package com.gxdevs.lore.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import com.gxdevs.lore.R
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.rounded.AutoFixHigh
import java.util.Calendar
import java.util.Locale
import java.text.SimpleDateFormat
import androidx.compose.ui.platform.LocalLocale

private val appBackground = Color(0xFFEBE8E0)
private val mainContainerBackground = Color(0xFFF4F1EA)
private val borderColor = Color(0xFFE0DCD1)
private val cardBackground = Color(0xFFEAE7DF)
private val textPrimary = Color(0xFF2E332A)
private val textSecondary = Color(0xFF828779)
private val primaryAccent = Color(0xFF606F49)

@Composable
fun RelicUnlockSettingsDialog(
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val calendar = remember { Calendar.getInstance() }
    val todayCalendar = remember { Calendar.getInstance() }
    val todayYear = todayCalendar.get(Calendar.YEAR)
    val todayMonth = todayCalendar.get(Calendar.MONTH)
    val todayDay = todayCalendar.get(Calendar.DAY_OF_MONTH)

    // Selected date states
    var selYear by remember { mutableIntStateOf(calendar.get(Calendar.YEAR)) }
    var selMonth by remember { mutableIntStateOf(calendar.get(Calendar.MONTH)) }
    var selDay by remember { mutableIntStateOf(calendar.get(Calendar.DAY_OF_MONTH)) }

    // Viewing date states (for month navigation)
    var viewYear by remember { mutableIntStateOf(calendar.get(Calendar.YEAR)) }
    var viewMonth by remember { mutableIntStateOf(calendar.get(Calendar.MONTH)) }

    // Hour and minute states
    var hourVal by remember { mutableIntStateOf(calendar.get(Calendar.HOUR_OF_DAY)) }
    var minuteVal by remember { mutableIntStateOf(calendar.get(Calendar.MINUTE)) }

    var activeTab by remember { mutableStateOf("DATE") } // "DATE" or "TIME"

    val selectedTimeInMillis = remember(selYear, selMonth, selDay, hourVal, minuteVal) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, selYear)
            set(Calendar.MONTH, selMonth)
            set(Calendar.DAY_OF_MONTH, selDay)
            set(Calendar.HOUR_OF_DAY, hourVal)
            set(Calendar.MINUTE, minuteVal)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    
    val isValid = selectedTimeInMillis > System.currentTimeMillis()

    // Monthly calendar helper: list of days, padded with nulls for first day week offset
    val daysList = remember(viewYear, viewMonth) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, viewYear)
            set(Calendar.MONTH, viewMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val startDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1 = Sunday, 2 = Monday, etc.
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        
        val list = mutableListOf<Int?>()
        repeat(startDayOfWeek - 1) {
            list.add(null)
        }
        for (d in 1..maxDays) {
            list.add(d)
        }
        list
    }

    val monthNames = remember {
        listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    }
    val monthName = monthNames[viewMonth]

    val displayHour = remember(hourVal) {
        val h = hourVal % 12
        if (h == 0) 12 else h
    }
    val isPm = remember(hourVal) { hourVal >= 12 }

    val formatSelectedStr = remember(selYear, selMonth, selDay, hourVal, minuteVal) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, selYear)
            set(Calendar.MONTH, selMonth)
            set(Calendar.DAY_OF_MONTH, selDay)
            set(Calendar.HOUR_OF_DAY, hourVal)
            set(Calendar.MINUTE, minuteVal)
        }
        SimpleDateFormat("MMM dd, yyyy  |  hh:mm a", Locale.getDefault()).format(cal.time)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Seal Relic",
                    color = textPrimary,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Text(
                    text = "Pick when this relic becomes readable again.",
                    color = textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Tab capsule selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(cardBackground)
                        .border(1.dp, borderColor, RoundedCornerShape(50))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val dateActive = activeTab == "DATE"
                    val timeActive = activeTab == "TIME"
                    
                    Button(
                        onClick = { activeTab = "DATE" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (dateActive) primaryAccent else Color.Transparent,
                            contentColor = if (dateActive) Color.White else textSecondary
                        ),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(Icons.Rounded.CalendarMonth, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Date", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    Button(
                        onClick = { activeTab = "TIME" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (timeActive) primaryAccent else Color.Transparent,
                            contentColor = if (timeActive) Color.White else textSecondary
                        ),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(Icons.Rounded.AccessTime, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Time", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Date Tab View
                if (activeTab == "DATE") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Month/Year navigation row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isPrevDisabled = viewYear == todayYear && viewMonth == todayMonth
                            IconButton(
                                onClick = {
                                    if (viewMonth == 0) {
                                        viewMonth = 11
                                        viewYear -= 1
                                    } else {
                                        viewMonth -= 1
                                    }
                                },
                                enabled = !isPrevDisabled
                            ) {
                                Icon(
                                    Icons.Rounded.ChevronLeft,
                                    null,
                                    tint = if (isPrevDisabled) textSecondary.copy(alpha = 0.3f) else textPrimary
                                )
                            }
                            
                            Text(
                                text = "$monthName $viewYear",
                                color = textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            
                            IconButton(
                                onClick = {
                                    if (viewMonth == 11) {
                                        viewMonth = 0
                                        viewYear += 1
                                    } else {
                                        viewMonth += 1
                                    }
                                }
                            ) {
                                Icon(Icons.Rounded.ChevronRight, null, tint = textPrimary)
                            }
                        }
                        
                        Spacer(Modifier.height(8.dp))
                        
                        // Weekday labels
                        Row(modifier = Modifier.fillMaxWidth()) {
                            val weekdays = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")
                            weekdays.forEach { day ->
                                Text(
                                    text = day,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    color = textSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        Spacer(Modifier.height(4.dp))
                        
                        // Days grid
                        val rows = daysList.chunked(7)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            rows.forEach { rowDays ->
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    rowDays.forEach { day ->
                                        if (day == null) {
                                            Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                                        } else {
                                            val isSelected = selYear == viewYear && selMonth == viewMonth && selDay == day
                                            val isPast = viewYear < todayYear || 
                                                         (viewYear == todayYear && viewMonth < todayMonth) || 
                                                         (viewYear == todayYear && viewMonth == todayMonth && day < todayDay)
                                            val isToday = viewYear == todayYear && viewMonth == todayMonth && day == todayDay
                                            
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .aspectRatio(1f)
                                                    .padding(2.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) primaryAccent else Color.Transparent)
                                                    .border(
                                                        width = if (isToday && !isSelected) 1.dp else 0.dp,
                                                        color = if (isToday && !isSelected) primaryAccent else Color.Transparent,
                                                        shape = CircleShape
                                                    )
                                                    .clickable(enabled = !isPast) {
                                                        selYear = viewYear
                                                        selMonth = viewMonth
                                                        selDay = day
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = day.toString(),
                                                    color = when {
                                                        isSelected -> Color.White
                                                        isPast -> textSecondary.copy(alpha = 0.3f)
                                                        isToday -> primaryAccent
                                                        else -> textPrimary
                                                    },
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    }
                                    if (rowDays.size < 7) {
                                        repeat(7 - rowDays.size) {
                                            Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Time Tab View
                if (activeTab == "TIME") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        ) {
                            // Hour selector
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("HOUR", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textSecondary, letterSpacing = 1.sp)
                                Spacer(Modifier.height(6.dp))
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(cardBackground, RoundedCornerShape(16.dp))
                                        .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                                        .padding(vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    IconButton(
                                        onClick = {
                                            val nextHour = (hourVal + 1) % 24
                                            hourVal = nextHour
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Rounded.KeyboardArrowUp, null, tint = textPrimary)
                                    }
                                    Text(
                                        text = String.format(LocalLocale.current.platformLocale,"%02d", displayHour),
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary
                                    )
                                    IconButton(
                                        onClick = {
                                            val prevHour = if (hourVal == 0) 23 else hourVal - 1
                                            hourVal = prevHour
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Rounded.KeyboardArrowDown, null, tint = textPrimary)
                                    }
                                }
                            }
                            
                            // Colon
                            Text(":", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = textPrimary, modifier = Modifier.padding(top = 16.dp))
                            
                            // Minute selector
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("MINUTE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textSecondary, letterSpacing = 1.sp)
                                Spacer(Modifier.height(6.dp))
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(cardBackground, RoundedCornerShape(16.dp))
                                        .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                                        .padding(vertical = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    IconButton(
                                        onClick = { minuteVal = (minuteVal + 1) % 60 },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Rounded.KeyboardArrowUp, null, tint = textPrimary)
                                    }
                                    Text(
                                        text = String.format(LocalLocale.current.platformLocale,"%02d", minuteVal),
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary
                                    )
                                    IconButton(
                                        onClick = { minuteVal = if (minuteVal == 0) 59 else minuteVal - 1 },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Rounded.KeyboardArrowDown, null, tint = textPrimary)
                                    }
                                }
                            }
                            
                            // AM/PM selector
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("AM/PM", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textSecondary, letterSpacing = 1.sp)
                                Spacer(Modifier.height(6.dp))
                                Column(
                                    modifier = Modifier.height(108.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Button(
                                        onClick = { if (isPm) hourVal -= 12 },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (!isPm) primaryAccent else cardBackground,
                                            contentColor = if (!isPm) Color.White else textPrimary
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("AM", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = { if (!isPm) hourVal += 12 },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isPm) primaryAccent else cardBackground,
                                            contentColor = if (isPm) Color.White else textPrimary
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("PM", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(Modifier.height(4.dp))
                
                // Selection Summary Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBackground.copy(alpha = 0.5f))
                        .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "UNSEALS AT",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = textSecondary,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = formatSelectedStr,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isValid) textPrimary else textSecondary
                    )
                }

                if (!isValid) {
                    Text(
                        text = "Please select a future date & time",
                        color = Color(0xFFC84B31),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(selectedTimeInMillis)
                },
                colors = ButtonDefaults.buttonColors(containerColor = primaryAccent),
                shape = RoundedCornerShape(20.dp),
                enabled = isValid
            ) {
                Text("Confirm", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = textSecondary)
            }
        },
        containerColor = mainContainerBackground,
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
fun AfterJournalRecordScreen(
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    viewModel: AfterJournalViewModel = viewModel(),
    isRelic: Boolean = false,
    encryptMedia: Boolean = false
) {
    Scaffold(
        containerColor = mainContainerBackground,
        contentWindowInsets = WindowInsets(0)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(), bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.clickable(onClick = onDiscard),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Back", tint = textSecondary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "BACK",
                        color = textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Title
            Text(
                text = "Capture your\nmood.",
                color = textPrimary,
                fontFamily = FontFamily.Serif,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 48.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Subtitle
            val subtitleText = buildAnnotatedString {
                append("How did this entry feel? This determines which companion grows.")
            }

            Text(
                text = subtitleText,
                color = textSecondary,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
                lineHeight = 24.sp
            )

            val selectedEmotions by viewModel.selectedEmotions.collectAsState()
            val predictedLabel = selectedEmotions.firstOrNull()?.label?.uppercase(Locale.getDefault())

            if (predictedLabel != null) {
                val petReaction = remember(predictedLabel) {
                    com.gxdevs.lore.utils.PetInsightEngine.generatePetReaction(
                        selectedMood = predictedLabel,
                        content = null
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(primaryAccent.copy(alpha = 0.12f))
                            .border(1.dp, primaryAccent.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.AutoFixHigh, null, tint = primaryAccent, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("AI Suggested: $predictedLabel", color = primaryAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Companion Pet Reaction Bubble
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = cardBackground,
                        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🐾", fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "${petReaction.petNameHint} (${petReaction.emotionTag})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryAccent
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "\"${petReaction.commentText}\"",
                                    fontSize = 13.sp,
                                    color = textPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                Spacer(modifier = Modifier.height(32.dp))
            }

            var showDateTimePicker by remember { mutableStateOf(false) }
            var selectedMoodForRelic by remember { mutableStateOf<String?>(null) }

            if (showDateTimePicker) {
                RelicUnlockSettingsDialog(
                    onConfirm = { unlockTime ->
                        selectedMoodForRelic?.let { mood ->
                            viewModel.toggleEmotion(Emotion(mood.lowercase(), mood))
                            viewModel.saveEntry(isRelic = true, unlockDate = unlockTime, encryptMedia = encryptMedia)
                            onSave()
                        }
                        showDateTimePicker = false
                    },
                    onDismiss = {
                        showDateTimePicker = false
                    }
                )
            }

            // Grid of buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val handleSave = { mood: String ->
                    if (isRelic) {
                        selectedMoodForRelic = mood
                        showDateTimePicker = true
                    } else {
                        viewModel.toggleEmotion(Emotion(mood.lowercase(), mood))
                        viewModel.saveEntry(encryptMedia = encryptMedia)
                        onSave()
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    MoodButton(
                        modifier = Modifier.weight(1f),
                        painter = painterResource(id = R.drawable.sun),
                        label = "BRIGHT",
                        isPredicted = predictedLabel == "BRIGHT",
                        onClick = { handleSave("BRIGHT") }
                    )
                    MoodButton(
                        modifier = Modifier.weight(1f),
                        painter = painterResource(id = R.drawable.calm),
                        label = "CALM",
                        isPredicted = predictedLabel == "CALM",
                        onClick = { handleSave("CALM") }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    MoodButton(
                        modifier = Modifier.weight(1f),
                        painter = painterResource(id = R.drawable.heavy),
                        label = "HEAVY",
                        isPredicted = predictedLabel == "HEAVY",
                        onClick = { handleSave("HEAVY") }
                    )
                    MoodButton(
                        modifier = Modifier.weight(1f),
                        painter = painterResource(id = R.drawable.moon),
                        label = "DARK",
                        isPredicted = predictedLabel == "DARK",
                        onClick = { handleSave("DARK") }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    MoodButton(
                        modifier = Modifier.weight(1f),
                        painter = rememberVectorPainter(image = Icons.Rounded.RadioButtonUnchecked),
                        label = "BLANK",
                        isPredicted = predictedLabel == "BLANK",
                        onClick = { handleSave("BLANK") }
                    )
                    MoodButton(
                        modifier = Modifier.weight(1f),
                        painter = painterResource(id = R.drawable.tangled),
                        label = "TANGLED",
                        isPredicted = predictedLabel == "TANGLED",
                        onClick = { handleSave("TANGLED") }
                    )
                }
            }
        }
    }
}

@Composable
fun MoodButton(
    modifier: Modifier = Modifier,
    painter: Painter,
    label: String,
    isPredicted: Boolean = false,
    onClick: () -> Unit
) {
    val buttonBg = if (isPredicted) primaryAccent.copy(alpha = 0.15f) else appBackground
    val buttonBorder = if (isPredicted) primaryAccent else borderColor

    Column(
        modifier = modifier
            .aspectRatio(1f) // Makes it a square
            .clip(RoundedCornerShape(32.dp))
            .border(if (isPredicted) 2.dp else 1.dp, buttonBorder, RoundedCornerShape(32.dp))
            .background(buttonBg)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painter,
            contentDescription = label,
            tint = textSecondary,
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = label,
            color = textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

