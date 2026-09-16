package com.gxdevs.lore.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gxdevs.lore.data.mood.MoodConstants
import com.gxdevs.lore.ui.pets.getOrLoadPetPalette

private val cardBackground    = Color(0xFFFAF8F5)
private val textPrimary       = Color(0xFF2C3224)
private val textSecondary     = Color(0xFF7A8370)
private val primaryAccent     = Color(0xFF606F49)
private val borderColor       = Color(0xFFE5DFC9)

/**
 * Dynamic Pet Stage Evolution & Level Up Popup Dialog.
 *
 * Shows an animated glassmorphic dialog with a spring physics scale-in,
 * dynamic confetti burst, real companion image, stage name badge, and companion
 * quote whenever a pet unlocks or evolves.
 */
@Composable
fun PetLevelUpDialog(
    petName: String,
    stageName: String,
    stageIndex: Int,
    moodId: String,
    localImagePath: String? = null,
    dialogueQuote: String? = null,
    onDismiss: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1.0f else 0.85f,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = 450f
        ),
        label = "DialogScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1.0f else 0.0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "DialogAlpha"
    )

    // Extract dynamic dominant & background colors from real pet artwork
    val loadedImage = remember(localImagePath, moodId) {
        getOrLoadPetPalette(localImagePath, moodId)
    }

    val moodColor = loadedImage.dominantColor
    val moodBg    = loadedImage.lightBgColor

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Dynamic Confetti Particle Layer matching companion colors
            ConfettiEffect(
                isVisible = isVisible,
                customColors = listOf(
                    moodColor,
                    moodBg,
                    Color(0xFFF3C042), // Gold spark
                    moodColor.copy(alpha = 0.7f),
                    Color.White
                )
            )

            // Main Dialog Box with hardware accelerated scale + alpha
            Surface(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .shadow(
                        elevation = 20.dp,
                        shape = RoundedCornerShape(32.dp),
                        spotColor = moodColor.copy(alpha = 0.45f),
                        ambientColor = moodColor.copy(alpha = 0.15f)
                    )
                    .clip(RoundedCornerShape(32.dp))
                    .border(
                        width = 2.dp,
                        brush = Brush.verticalGradient(
                            listOf(moodColor.copy(alpha = 0.8f), moodColor.copy(alpha = 0.25f), borderColor)
                        ),
                        shape = RoundedCornerShape(32.dp)
                    ),
                color = cardBackground
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Close Button
                    Box(modifier = Modifier.fillMaxWidth()) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(borderColor.copy(alpha = 0.4f))
                        ) {
                            Icon(Icons.Rounded.Close, null, tint = textSecondary, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Large Glowing Radial Avatar Halo with Real Unlocked Image
                    Box(
                        modifier = Modifier.size(190.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Outer soft gradient aura (Hardware accelerated radial gradient - zero GPU blur lag)
                        Box(
                            modifier = Modifier
                                .size(186.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(moodColor.copy(alpha = 0.35f), moodBg.copy(alpha = 0.50f), Color.Transparent)
                                    )
                                )
                        )

                        Box(
                            modifier = Modifier
                                .size(165.dp)
                                .shadow(12.dp, CircleShape, spotColor = moodColor.copy(0.40f))
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(moodBg, moodBg.copy(alpha = 0.85f))
                                    )
                                )
                                .border(
                                    width = 3.dp,
                                    brush = Brush.linearGradient(
                                        listOf(moodColor, moodColor.copy(alpha = 0.4f))
                                    ),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (loadedImage.imageBitmap != null) {
                                Image(
                                    bitmap = loadedImage.imageBitmap,
                                    contentDescription = petName,
                                    modifier = Modifier
                                        .size(142.dp)
                                        .padding(2.dp),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Text(
                                    text = if (stageIndex <= 0) "🥚" else "✨",
                                    fontSize = 58.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Stage Badge
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = moodColor.copy(alpha = 0.14f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, moodColor.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, null, tint = moodColor, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (stageIndex < 0) "NEW COMPANION" else "STAGE ${stageIndex + 1}: $stageName",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = moodColor,
                                letterSpacing = 1.2.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Dynamic Title
                    val titleText = if (stageIndex == 0) "$petName Unlocked!" else "$petName Evolved!"
                    Text(
                        text = titleText,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        fontFamily = FontFamily.Serif,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val quote = dialogueQuote ?: MoodConstants.descriptionOf[moodId]
                    if (quote != null) {
                        Text(
                            text = "\"$quote\"",
                            fontSize = 13.sp,
                            color = textSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Dynamic Action Button
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .shadow(8.dp, RoundedCornerShape(20.dp), spotColor = moodColor.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = moodColor)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Awesome!",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
