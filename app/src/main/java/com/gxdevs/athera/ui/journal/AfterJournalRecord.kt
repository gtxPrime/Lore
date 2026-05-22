package com.gxdevs.athera.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.vector.ImageVector

private val appBackground = Color(0xFFEBE8E0)
private val mainContainerBackground = Color(0xFFF4F1EA)
private val borderColor = Color(0xFFE0DCD1)
private val textPrimary = Color(0xFF2E332A)
private val textSecondary = Color(0xFF828779)
private val primaryAccent = Color(0xFF606F49)


@Composable
fun AfterJournalRecordScreen(
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    viewModel: AfterJournalViewModel = viewModel(),
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

            Spacer(modifier = Modifier.height(48.dp))

            // Grid of buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val handleSave = { mood: String ->
                    viewModel.toggleEmotion(Emotion(mood.lowercase(), mood))
                    viewModel.saveEntry(emptyMap())
                    onSave()
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    MoodButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.WbSunny,
                        label = "BRIGHT",
                        onClick = { handleSave("BRIGHT") }
                    )
                    MoodButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.Spa,
                        label = "CALM",
                        onClick = { handleSave("CALM") }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    MoodButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.FitnessCenter,
                        label = "HEAVY",
                        onClick = { handleSave("HEAVY") }
                    )
                    MoodButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.DarkMode,
                        label = "DARK",
                        onClick = { handleSave("DARK") }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    MoodButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.RadioButtonUnchecked,
                        label = "BLANK",
                        onClick = { handleSave("BLANK") }
                    )
                    MoodButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.Air,
                        label = "TANGLED",
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
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .aspectRatio(1f) // Makes it a square
            .clip(RoundedCornerShape(32.dp))
            .border(1.dp, borderColor, RoundedCornerShape(32.dp))
            .background(appBackground)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
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

