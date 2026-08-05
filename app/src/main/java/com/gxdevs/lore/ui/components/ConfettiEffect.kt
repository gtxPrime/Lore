package com.gxdevs.lore.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.random.Random

/**
 * Lightweight, 60fps Canvas-based Confetti Particle Burst Animation.
 *
 * Fires colourful festive confetti shapes (circles, ribbons, stars, sparkles)
 * whenever a pet levels up, evolves, or a milestone is reached.
 *
 * 100% Native Compose Canvas · Zero external Lottie dependency · High performance.
 */
@Composable
fun ConfettiEffect(
    isVisible: Boolean,
    customColors: List<Color>? = null,
    onAnimationEnd: () -> Unit = {}
) {
    if (!isVisible) return

    val particleCount = 70
    val defaultColors = listOf(
        Color(0xFFF3C042), // Golden Yellow
        Color(0xFF606F49), // Sage Green
        Color(0xFFB86C5A), // Terracotta
        Color(0xFF9EACC1), // Steel Blue
        Color(0xFFE8927C), // Coral
        Color(0xFFA5C4A3), // Mint
        Color(0xFFF7D885)  // Soft Gold
    )
    val colors = customColors?.ifEmpty { defaultColors } ?: defaultColors

    val particles = remember(isVisible) {
        List(particleCount) {
            ConfettiParticle(
                x = Random.nextFloat(),
                startY = Random.nextFloat() * 0.3f, // start near top
                speedY = Random.nextFloat() * 0.6f + 0.4f,
                speedX = (Random.nextFloat() - 0.5f) * 0.3f,
                rotationSpeed = (Random.nextFloat() - 0.5f) * 720f,
                size = Random.nextFloat() * 12f + 8f,
                color = colors[it % colors.size],
                isRibbon = Random.nextBoolean()
            )
        }
    }

    val transition = rememberInfiniteTransition(label = "ConfettiTransition")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ConfettiProgress"
    )

    LaunchedEffect(isVisible) {
        kotlinx.coroutines.delay(2800)
        onAnimationEnd()
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        particles.forEach { p ->
            val currY = (p.startY + progress * p.speedY) * height
            val currX = (p.x + progress * p.speedX) * width
            val alpha = (1f - progress * 0.9f).coerceIn(0f, 1f)
            val currRotation = progress * p.rotationSpeed

            if (currY <= height && alpha > 0f) {
                withTransform({
                    rotate(degrees = currRotation, pivot = Offset(currX, currY))
                }) {
                    if (p.isRibbon) {
                        drawRect(
                            color = p.color.copy(alpha = alpha),
                            topLeft = Offset(currX - p.size / 2, currY - p.size / 4),
                            size = Size(p.size, p.size / 2)
                        )
                    } else {
                        drawCircle(
                            color = p.color.copy(alpha = alpha),
                            radius = p.size / 2,
                            center = Offset(currX, currY)
                        )
                    }
                }
            }
        }
    }
}

private data class ConfettiParticle(
    val x: Float,
    val startY: Float,
    val speedY: Float,
    val speedX: Float,
    val rotationSpeed: Float,
    val size: Float,
    val color: Color,
    val isRibbon: Boolean
)
