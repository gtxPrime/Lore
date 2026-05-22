package com.gxdevs.athera.ui.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

// ==================== DESIGN SYSTEM COLORS ====================
private val DeepSurface = Color(0xFF121212)
private val DeepBorder = Color(0xFF2A2A2A)
private val AccentMint = Color(0xFFBFF0D4)
private val AccentDarkGreen = Color(0xFF003923)
private val TextGrey = Color(0xFF888888)

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
                icon = Icons.Default.Home,
                contentDescription = "Home",
                selected = currentScreen == NavScreen.HOME,
                onClick = onHomeClick
        )
        AnimatedNavItem(
                icon = Icons.Default.AutoStories,
                contentDescription = "Journals",
                selected = currentScreen == NavScreen.JOURNALS,
                onClick = onJournalsClick
        )
        AnimatedNavItem(
                icon = Icons.Default.BarChart,
                contentDescription = "Stats",
                selected = currentScreen == NavScreen.STATS,
                onClick = onStatsClick
        )
        AnimatedNavItem(
                icon = Icons.Default.Settings,
                contentDescription = "Settings",
                selected = currentScreen == NavScreen.SETTINGS,
                onClick = onSettingsClick
        )
    }
}

@Composable
private fun AnimatedNavItem(
        icon: ImageVector,
        contentDescription: String,
        selected: Boolean,
        onClick: () -> Unit
) {
    // Smooth liquid animations
    val scale by
            animateFloatAsState(
                    targetValue = if (selected) 1.1f else 1f,
                    animationSpec =
                            spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                            ),
                    label = "scale"
            )

    val width by
            animateDpAsState(
                    targetValue = if (selected) 54.dp else 44.dp,
                    animationSpec =
                            spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                            ),
                    label = "width"
            )

    val iconSize by
            animateDpAsState(
                    targetValue = if (selected) 26.dp else 22.dp,
                    animationSpec =
                            spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                            ),
                    label = "iconSize"
            )

    Box(
            modifier =
                    Modifier.height(40.dp)
                            .width(width)
                            .scale(scale)
                            .clip(RoundedCornerShape(22.dp))
                            .background(if (selected) AccentMint else Color.Transparent)
                            .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onClick
                            ),
            contentAlignment = Alignment.Center
    ) {
        Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (selected) AccentDarkGreen else TextGrey,
                modifier = Modifier.size(iconSize)
        )
    }
}


