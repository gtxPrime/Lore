package com.gxdevs.lore.ui.components

import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gxdevs.lore.utils.MediaEncryptionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File

private val cardBackground    = Color(0xFFEAE7DF)
private val textPrimary       = Color(0xFF2E332A)
private val textSecondary     = Color(0xFF828779)
private val primaryAccent     = Color(0xFF606F49)
private val accentBackground  = Color(0xFFD9DFCD)
private val darkAccent        = Color(0xFF4A5638)
private val bottomNavBackground = Color(0xFF2E332A)

/**
 * Sanctuary Audio Waveform Player.
 * Plays voice reflections recorded with journal entries.
 * Supports scrubbing, play/pause, waveform animation, and speed toggling.
 */
@Composable
fun AudioWaveformPlayer(
    audioPath: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(0) }
    var speedMultiplier by remember { mutableFloatStateOf(1.0f) }
    var isPrepared by remember { mutableStateOf(false) }
    var decryptedTempFile by remember { mutableStateOf<File?>(null) }

    // Waveform heights
    val waveformBars = remember {
        listOf(0.35f, 0.65f, 0.45f, 0.85f, 0.55f, 0.95f, 0.70f, 0.40f, 0.80f, 0.60f, 0.90f, 0.50f, 0.75f, 0.35f, 0.85f, 0.60f)
    }

    // Resolve file (decrypt if encrypted)
    LaunchedEffect(audioPath) {
        val file = if (MediaEncryptionManager.isEncrypted(audioPath)) {
            val dec = MediaEncryptionManager.getOrDecryptTempFile(context, audioPath, "m4a")
            decryptedTempFile = dec
            dec
        } else {
            File(audioPath).takeIf { it.exists() }
        }

        if (file != null && file.exists()) {
            try {
                val player = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    setOnPreparedListener { mp ->
                        durationMs = mp.duration
                        isPrepared = true
                    }
                    setOnCompletionListener {
                        isPlaying = false
                        currentPositionMs = durationMs
                    }
                    prepareAsync()
                }
                mediaPlayer = player
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Progress update loop
    LaunchedEffect(isPlaying) {
        while (isPlaying && isActive) {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    currentPositionMs = player.currentPosition
                }
            }
            delay(100L)
        }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.runCatching {
                if (isPlaying) stop()
                release()
            }
            mediaPlayer = null
            decryptedTempFile?.delete()
        }
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: return
        if (!isPrepared) return

        if (isPlaying) {
            player.pause()
            isPlaying = false
        } else {
            if (currentPositionMs >= durationMs && durationMs > 0) {
                player.seekTo(0)
                currentPositionMs = 0
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    player.playbackParams = PlaybackParams().apply { speed = speedMultiplier }
                } catch (_: Exception) {}
            }
            player.start()
            isPlaying = true
        }
    }

    fun toggleSpeed() {
        val newSpeed = when (speedMultiplier) {
            1.0f -> 1.5f
            1.5f -> 2.0f
            else -> 1.0f
        }
        speedMultiplier = newSpeed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && isPlaying) {
            mediaPlayer?.runCatching {
                playbackParams = PlaybackParams().apply { speed = newSpeed }
            }
        }
    }

    val progressFraction = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    // Animated Play Button Scale
    var playPressed by remember { mutableStateOf(false) }
    val playButtonScale by animateFloatAsState(
        targetValue = if (playPressed) 0.92f else 1.0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "playScale"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(cardBackground)
            .border(1.dp, Color(0xFFE0DCD1), RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = primaryAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "VOICE REFLECTION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = primaryAccent
                )
            }

            // Speed toggle button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentBackground.copy(alpha = 0.6f))
                    .clickable { toggleSpeed() }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${speedMultiplier}x",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = darkAccent
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play/Pause Button
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .scale(playButtonScale)
                    .clip(CircleShape)
                    .background(primaryAccent)
                    .clickable {
                        HapticFeedbackHelper.performImpact(context, 1)
                        togglePlayPause()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            // Waveform visualizer & scrub area
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    waveformBars.forEachIndexed { index, baseHeight ->
                        val barFraction = (index + 1).toFloat() / waveformBars.size.toFloat()
                        val isPassed = barFraction <= progressFraction
                        val barColor by animateColorAsState(
                            targetValue = if (isPassed) primaryAccent else primaryAccent.copy(alpha = 0.25f),
                            label = "barColor"
                        )
                        val animHeight = if (isPlaying) {
                            val pulse = if (index % 2 == 0) 0.85f else 1.15f
                            (baseHeight * pulse).coerceIn(0.2f, 1.0f)
                        } else baseHeight

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(animHeight)
                                .clip(RoundedCornerShape(2.dp))
                                .background(barColor)
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Time counters
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val curSec = currentPositionMs / 1000
                    val totSec = durationMs / 1000
                    Text(
                        text = "%02d:%02d".format(curSec / 60, curSec % 60),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = textPrimary
                    )
                    Text(
                        text = "%02d:%02d".format(totSec / 60, totSec % 60),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = textSecondary
                    )
                }
            }
        }
    }
}
