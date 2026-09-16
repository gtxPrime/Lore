package com.gxdevs.lore.ui.lock

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gxdevs.lore.ui.components.HapticFeedbackHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val bg = Color(0xFF0E1108)
private val surface = Color(0xFF181E10)
private val accent = Color(0xFF606F49)
private val textCol = Color(0xFFF4F1EA)
private val subText = Color(0xFF828779)
private val errorCol = Color(0xFFC06352)
private val keyBg = Color(0xFF1E2717)
private val keyBorder = Color(0xFF2E3820)

private const val KEY_DELETE = "DEL"

@Composable
fun PinLockScreen(
    onUnlockNormal: () -> Unit,
    onUnlockDecoy: () -> Unit,
    realPin: String?,
    decoyPinValue: String?
) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    val shakeOffset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun handleDigit(d: String) {
        if (input.length >= 4) return
        HapticFeedbackHelper.lightClick(context)
        isError = false
        val newInput = input + d
        input = newInput
        if (newInput.length == 4) {
            scope.launch {
                delay(120)
                when (newInput) {
                    realPin -> {
                        HapticFeedbackHelper.selectionClick(context)
                        onUnlockNormal()
                    }
                    decoyPinValue -> {
                        HapticFeedbackHelper.selectionClick(context)
                        onUnlockDecoy()
                    }
                    else -> {
                        isError = true
                        HapticFeedbackHelper.heavyImpact(context)
                        shakeOffset.animateTo(
                            targetValue = 0f,
                            animationSpec = keyframes {
                                durationMillis = 400
                                0f at 0
                                (-20f) at 50
                                20f at 100
                                (-15f) at 150
                                15f at 200
                                (-10f) at 250
                                10f at 300
                                (-5f) at 350
                                0f at 400
                            }
                        )
                        delay(250)
                        input = ""
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Lock icon
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(surface)
                    .border(1.dp, accent.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Lock, null, tint = accent, modifier = Modifier.size(32.dp))
            }

            Spacer(Modifier.height(20.dp))

            Text("Enter PIN", color = textCol, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Unlock your sanctuary", color = subText, fontSize = 13.sp)

            Spacer(Modifier.height(32.dp))

            // PIN dot indicators with fluid keyframe shake
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.offset {
                    IntOffset(
                        x = shakeOffset.value.dp.roundToPx(),
                        y = 0
                    )
                }
            ) {
                repeat(4) { idx ->
                    val filled = idx < input.length
                    val dotColor by animateColorAsState(
                        if (isError) errorCol else if (filled) accent else surface,
                        animationSpec = tween(150), label = "dot$idx"
                    )
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                            .border(1.5.dp, if (filled) accent else keyBorder, CircleShape)
                    )
                }
            }

            if (isError) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Incorrect PIN",
                    color = errorCol,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.height(40.dp))

            // Number pad
            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("", "0", KEY_DELETE)
            )
            keys.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { key ->
                        when (key) {
                            "" -> Spacer(Modifier.size(76.dp))
                            KEY_DELETE -> PinKey(label = key, isSpecial = true, onClick = {
                                if (input.isNotEmpty()) {
                                    HapticFeedbackHelper.lightClick(context)
                                    input = input.dropLast(1)
                                    isError = false
                                }
                            })

                            else -> PinKey(label = key, onClick = { handleDigit(key) })
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun PinKey(label: String, isSpecial: Boolean = false, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "scale"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isPressed) accent.copy(alpha = 0.35f) else keyBg,
        animationSpec = tween(80), label = "bg"
    )

    Box(
        modifier = Modifier
            .size(76.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(bgColor)
            .border(1.dp, if (isPressed) accent else keyBorder, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (label == KEY_DELETE) {
            Icon(
                Icons.AutoMirrored.Rounded.Backspace,
                contentDescription = "Delete",
                tint = subText,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Text(
                label,
                color = if (isSpecial) subText else textCol,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun PinLockScreenPreview() {
    com.gxdevs.lore.ui.theme.MyApplicationTheme {
        PinLockScreen(
            onUnlockNormal = {},
            onUnlockDecoy = {},
            realPin = "1234",
            decoyPinValue = "0000"
        )
    }
}

