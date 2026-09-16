package com.gxdevs.lore.ui.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import com.gxdevs.lore.R
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ==================== DESIGN SYSTEM COLORS ====================
private val DeepSurface = Color(0xFF181E10)
private val DeepBorder = Color(0xFF2E3820)
private val AccentActive = Color(0xFF606F49)
private val IconActive = Color(0xFFF4F1EA)
private val IconInactive = Color(0xFF828779)

// ==================== NAV SCREEN ENUM ====================
enum class NavScreen {
    HOME,
    JOURNALS,
    STATS,
    SETTINGS
}

// ==================== SHARED BOTTOM NAV BAR ====================

@Composable
fun SharedBottomNavBar(
        modifier: Modifier = Modifier,
        currentScreen: NavScreen,
        onHomeClick: () -> Unit,
        onJournalsClick: () -> Unit,
        onStatsClick: () -> Unit,
        onSettingsClick: () -> Unit
) {
    Row(
            modifier =
                    modifier.fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 24.dp)
                            .shadow(
                                    elevation = 16.dp,
                                    spotColor = Color.Black.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(50)
                            )
                            .clip(RoundedCornerShape(50))
                            .background(DeepSurface.copy(alpha = 0.95f))
                            .border(1.dp, DeepBorder, RoundedCornerShape(50))
                            .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedNavItem(
                iconResId = R.drawable.house,
                contentDescription = "Home",
                selected = currentScreen == NavScreen.HOME,
                onClick = onHomeClick
        )
        AnimatedNavItem(
                iconResId = R.drawable.scroll_text,
                contentDescription = "Journals",
                selected = currentScreen == NavScreen.JOURNALS,
                onClick = onJournalsClick
        )
        AnimatedNavItem(
                iconResId = R.drawable.chart_line,
                contentDescription = "Stats",
                selected = currentScreen == NavScreen.STATS,
                onClick = onStatsClick
        )
        AnimatedNavItem(
                iconResId = R.drawable.settings,
                contentDescription = "Settings",
                selected = currentScreen == NavScreen.SETTINGS,
                onClick = onSettingsClick
        )
    }
}

@Composable
private fun AnimatedNavItem(
        iconResId: Int,
        contentDescription: String,
        selected: Boolean,
        onClick: () -> Unit
) {
    // Snappy icon size animation
    val iconSize by
            animateDpAsState(
                    targetValue = if (selected) 24.dp else 22.dp,
                    animationSpec =
                            spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = 3000f
                            ),
                    label = "iconSize"
            )

    Box(
            modifier =
                    Modifier.height(40.dp)
                            .width(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) AccentActive else Color.Transparent)
                            .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onClick
                            ),
            contentAlignment = Alignment.Center
    ) {
        Icon(
                painter = painterResource(id = iconResId),
                contentDescription = contentDescription,
                tint = if (selected) IconActive else IconInactive,
                modifier = Modifier.size(iconSize)
        )
    }
}


