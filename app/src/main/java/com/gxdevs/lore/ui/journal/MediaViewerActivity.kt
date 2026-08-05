package com.gxdevs.lore.ui.journal

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.gxdevs.lore.MainActivity
import com.gxdevs.lore.data.SettingsRepository
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.firstOrNull
import com.gxdevs.lore.utils.MediaEncryptionManager
import kotlinx.coroutines.*
import java.io.File

class MediaViewerActivity : ComponentActivity() {

    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var tempMediaFile: File? = null

    override fun onPause() {
        super.onPause()
        if (!isFinishing) {
            MainActivity.pauseTimestamp = System.currentTimeMillis()
        }
    }

    override fun onResume() {
        super.onResume()
        val pt = MainActivity.pauseTimestamp
        if (pt != null) {
            lifecycleScope.launch {
                val settingsRepo = SettingsRepository(this@MediaViewerActivity)
                val settings = settingsRepo.securitySettings.firstOrNull()
                if (settings != null) {
                    val appLockEnabled = settings.appLockEnabled
                    val autoLockDelay = settings.autoLockDelay
                    val elapsedMs = System.currentTimeMillis() - pt
                    val thresholdMs = if (autoLockDelay == 0) 200L else autoLockDelay * 1000L
                    if (appLockEnabled && elapsedMs >= thresholdMs) {
                        finish()
                        return@launch
                    }
                }
                MainActivity.pauseTimestamp = null
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mediaUriStr = intent.getStringExtra("media_uri") ?: ""
        val isVideoExtra = intent.getBooleanExtra("is_video", false)
        val encryptionEnabled = intent.getBooleanExtra("encryption_enabled", false)

        if (mediaUriStr.isEmpty()) {
            Toast.makeText(this, "No media to display.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            var resolvedFile by remember { mutableStateOf<File?>(null) }
            var resolveError by remember { mutableStateOf(false) }
            var isVideo by remember { mutableStateOf(isVideoExtra) }

            LaunchedEffect(mediaUriStr) {
                withContext(Dispatchers.IO) {
                    try {
                        val uri = mediaUriStr.toUri()
                        val uriString = uri.toString()

                        // Check if it is currently encrypted (contains encrypted_media and ends with .enc)
                        val isCurrentlyEncrypted = MediaEncryptionManager.isEncrypted(uriString) ||
                                (uri.scheme == null && MediaEncryptionManager.isEncrypted(uri.path))

                        var isVideoLoc = isVideoExtra
                        if (isCurrentlyEncrypted) {
                            val encPath = uri.path ?: uriString
                            // Sniff if not already identified as video
                            if (!isVideoLoc) {
                                val detectedVideo = MediaEncryptionManager.isVideoEncrypted(this@MediaViewerActivity, encPath)
                                if (detectedVideo) {
                                    isVideoLoc = true
                                    withContext(Dispatchers.Main) {
                                        isVideo = true
                                    }
                                }
                            }
                            val extHint = if (isVideoLoc) "mp4" else "jpg"
                            val decrypted = MediaEncryptionManager.decryptToTemp(this@MediaViewerActivity, encPath, extHint)
                            if (decrypted != null) {
                                tempMediaFile = decrypted
                                resolvedFile = decrypted
                            } else {
                                resolveError = true
                            }
                        } else {
                            // Unencrypted media.
                            val uriS = uri.toString()
                            if (!isVideoLoc) {
                                val mimeType = try { contentResolver.getType(uri) } catch (_: Exception) { null }
                                val detectedVideo = mimeType?.startsWith("video/") == true ||
                                        uriS.endsWith(".mp4", ignoreCase = true) ||
                                        uriS.endsWith(".mkv", ignoreCase = true)
                                if (detectedVideo) {
                                    isVideoLoc = true
                                    withContext(Dispatchers.Main) { isVideo = true }
                                }
                            }

                            // For plain file paths (no scheme, or file://), we can use
                            // the file directly — no need to copy to a temp file.
                            val directFile: File? = when (uri.scheme) {
                                null -> File(mediaUriStr).takeIf { it.exists() }
                                "file" -> File(uri.path ?: mediaUriStr).takeIf { it.exists() }
                                else -> null
                            }

                            if (directFile != null) {
                                resolvedFile = directFile
                            } else {
                                // content:// or other — copy to temp so ExoPlayer / Glide can access it
                                val mimeType = try { contentResolver.getType(uri) } catch (_: Exception) { null }
                                val ext = when {
                                    isVideoLoc -> "mp4"
                                    mimeType?.startsWith("image/") == true -> mimeType.substringAfter("/")
                                    mimeType?.startsWith("video/") == true -> mimeType.substringAfter("/")
                                    uriS.endsWith(".mp4", ignoreCase = true) -> "mp4"
                                    else -> "jpg"
                                }
                                val sharedDir = File(cacheDir, "shared_media").apply { mkdirs() }
                                val temp = File(sharedDir, "media_${System.currentTimeMillis()}.$ext")
                                val inputStream = contentResolver.openInputStream(uri)
                                if (inputStream != null) {
                                    inputStream.use { input -> temp.outputStream().use { out -> input.copyTo(out) } }
                                    tempMediaFile = temp
                                    resolvedFile = temp
                                } else {
                                    resolveError = true
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        resolveError = true
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                when {
                    resolveError -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.ErrorOutline, "Error", tint = Color.Red, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Failed to load media.", color = Color.White, fontSize = 16.sp)
                            }
                        }
                    }
                    resolvedFile == null -> {
                        // Cohesive loader screen, matching Nurtale colors
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFF606F49), strokeWidth = 3.dp)
                        }
                    }
                    else -> {
                        val file = resolvedFile!!
                        if (isVideo) {
                            ExoVideoPlayer(file = file, modifier = Modifier.fillMaxSize())
                        } else {
                            ZoomableGlideImage(file = file, modifier = Modifier.fillMaxSize())
                        }

                        // Premium Header Overlay
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .align(Alignment.TopCenter),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Back Button
                            IconButton(
                                onClick = { finish() },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Rounded.Close, "Close", tint = Color.White)
                            }

                            // Share / Open Externally Button (ONLY if encryption is OFF in settings)
                            if (!encryptionEnabled) {
                                IconButton(
                                    onClick = { openExternally(this@MediaViewerActivity, file) },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.5f))
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Open Externally", tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun openExternally(context: Context, file: File) {
        try {
            val fileUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "com.gxdevs.lore.fileprovider",
                file
            )
            val mimeType = if (file.name.endsWith(".mp4", ignoreCase = true)) "video/mp4" else "image/jpeg"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "No default app found to open this media.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
        // Clean up temporary session files
        try {
            tempMediaFile?.delete()
        } catch (_: Exception) {}
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun ZoomableGlideImage(
    file: File,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    if (scale > 1f) {
                        offset += pan
                    } else {
                        offset = Offset.Zero
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { _ ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    }
                )
            }
    ) {
        GlideImage(
            model = file,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )
    }
}

@Composable
fun ExoVideoPlayer(
    file: File,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uri = file.toUri()

    // Initialize ExoPlayer
    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var duration by remember { mutableLongStateOf(0L) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var isControlVisible by remember { mutableStateOf(true) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    duration = exoPlayer.duration.coerceAtLeast(0L)
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    LaunchedEffect(isControlVisible, isPlaying) {
        if (isControlVisible && isPlaying) {
            delay(3000)
            isControlVisible = false
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            delay(200)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { isControlVisible = !isControlVisible },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                if (view.player != exoPlayer) {
                    view.player = exoPlayer
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = isControlVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
            ) {
                IconButton(
                    onClick = {
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                        } else {
                            exoPlayer.play()
                        }
                    },
                    modifier = Modifier
                        .size(64.dp)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(Color(0xFF606F49).copy(alpha = 0.85f))
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                        .padding(horizontal = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTime(currentPosition),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Slider(
                            value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                            onValueChange = { progress ->
                                val targetPos = (progress * duration).toLong()
                                exoPlayer.seekTo(targetPos)
                                currentPosition = targetPos
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF606F49),
                                activeTrackColor = Color(0xFF606F49),
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                        Text(
                            text = formatTime(duration),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSecs = ms / 1000
    val minutes = totalSecs / 60
    val seconds = totalSecs % 60
    return "%02d:%02d".format(minutes, seconds)
}
