package com.gxdevs.lore.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

private val primaryAccent = Color(0xFF606F49)
private val cardBackground = Color(0xFFEAE7DF)
private val borderColor = Color(0xFFE0DCD1)
private val textPrimary = Color(0xFF2E332A)
private val textSecondary = Color(0xFF828779)

/**
 * Calendar Heatmap Activity Matrix component for Nurtale Insights.
 * Renders a 30-day contribution activity grid showing journaling consistency.
 */
@Composable
fun CalendarActivityHeatmap(
    modifier: Modifier = Modifier,
    activeDays: Set<Int> = emptySet() // Day-of-year integers that have journal entries
) {
    val calendar = Calendar.getInstance()
    val currentDayOfYear = calendar.get(Calendar.DAY_OF_YEAR)

    // Build grid of last 28 days (4 weeks x 7 days)
    val past28Days = (27 downTo 0).map { offset ->
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -offset)
        val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
        val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // 1 = Sun, 7 = Sat
        Triple(dayOfYear, dayOfMonth, dayOfWeek)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardBackground)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "JOURNALING ACTIVITY",
                color = textSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
            Text(
                text = "${activeDays.size} active days",
                color = primaryAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 7-day grid columns (4 weeks)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            past28Days.chunked(7).forEach { week ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    week.forEach { (dayOfYear, dayOfMonth, _) ->
                        val isActive = activeDays.contains(dayOfYear) || dayOfYear == currentDayOfYear
                        val isToday = dayOfYear == currentDayOfYear

                        val cellBg = when {
                            isToday && isActive -> primaryAccent
                            isActive -> primaryAccent.copy(alpha = 0.7f)
                            else -> Color(0xFFDCD8CD)
                        }

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(cellBg)
                                .then(
                                    if (isToday) Modifier.border(1.5.dp, primaryAccent, RoundedCornerShape(8.dp))
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dayOfMonth.toString(),
                                color = if (isActive || isToday) Color.White else textSecondary.copy(alpha = 0.7f),
                                fontSize = 10.sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}
