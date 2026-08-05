package com.gxdevs.lore.utils

import com.gxdevs.lore.data.journal.JournalEntry
import com.gxdevs.lore.data.mood.MoodConstants
import java.util.Calendar
import java.util.Locale

/**
 * On-Device Pet Commentary & Contextual Insight Engine.
 *
 * Analyzes journal history, time of day, writing patterns, location hints,
 * and sentiment trends to generate:
 * 1. [PetComment]: In-character speech from the companion pet matching its mood element.
 * 2. [ContextInsight]: Statistical patterns (e.g. "You write most warmly on Friday evenings").
 * 3. [ActionableSuggestion]: Gentle self-care advice tailored to current emotional state.
 *
 * 100% On-device · 100% Private · 0 KB network cost.
 */
object PetInsightEngine {

    data class PetComment(
        val petMood: String,         // e.g. "Bright", "Calm", "Heavy", "Tangled", "Dark", "Blank"
        val commentText: String,     // In-character dialogue
        val emotionTag: String,       // e.g. "Encouraging", "Reflective", "Soothing"
        val petNameHint: String       // e.g. "Sol", "Sage", "Ember", "Knot", "Nyx", "Echo"
    )

    data class ContextInsight(
        val title: String,
        val description: String,
        val metricValue: String,
        val iconType: String          // e.g. "STREAK", "TIME_OF_DAY", "DOMINANT_MOOD", "SENTIMENT_TREND"
    )

    data class ActionableSuggestion(
        val title: String,
        val actionText: String,
        val category: String          // e.g. "Mindfulness", "Rest", "Expression", "Gratitude"
    )

    /**
     * Generates custom pet commentary based on the newly recorded or selected mood
     * and entry metadata (time of day, length, sentiment).
     */
    fun generatePetReaction(
        selectedMood: String,
        content: String?,
        timeSpentSec: Long = 0L,
        sentimentScore: Float = 0f,
        timestamp: Long = System.currentTimeMillis()
    ): PetComment {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val wordCount = content?.trim()?.split(Regex("\\s+"))?.size ?: 0

        val timeLabel = when (hour) {
            in 5..11  -> "morning"
            in 12..16 -> "afternoon"
            in 17..21 -> "evening"
            else      -> "late night"
        }

        val normMood = MoodConstants.ALL_MOODS.firstOrNull {
            it.equals(selectedMood, ignoreCase = true)
        } ?: MoodConstants.CALM

        val comment = when (normMood) {
            MoodConstants.BRIGHT -> when {
                hour in 22..23 || hour in 0..4 ->
                    "Your golden spark shines bright even at $timeLabel! Nighttime joy hits different."
                wordCount > 150 ->
                    "So much joy poured onto the page ($wordCount words)! I feel stronger already."
                else ->
                    "A vibrant burst of energy! Every moment of joy nourishes my spirit."
            }

            MoodConstants.CALM -> when {
                timeLabel == "morning" ->
                    "Starting your morning with a quiet mind set such a gentle rhythm for the day."
                wordCount < 30 ->
                    "Short, sweet, and centered. A quiet pause in a busy world."
                else ->
                    "Ah... peace washes over us. Thank you for breathing through these thoughts with me."
            }

            MoodConstants.HEAVY -> when {
                timeLabel == "late night" ->
                    "It's late, and the heart feels heavy. I'm sitting quietly right beside you."
                sentimentScore < -0.5f ->
                    "You released some deep pain today. Carrying it on paper means you carry less of it alone."
                else ->
                    "Heavy feelings are just clouds passing through. We'll hold space until the sky clears."
            }

            MoodConstants.TANGLED -> when {
                timeSpentSec > 300 ->
                    "You spent over ${timeSpentSec / 60} minutes untangling these thoughts. Give yourself credit for sitting with the mess."
                wordCount > 200 ->
                    "A whirlwind of thoughts! Putting them into words is the first step to unknotting them."
                else ->
                    "Things feel jumbled right now, but every thread unravels one gentle pull at a time."
            }

            MoodConstants.DARK -> when {
                sentimentScore < -0.6f ->
                    "Raw, unfiltered truth. It takes courage to look into the dark and write what hurts."
                else ->
                    "The storm raged, but you stood through it. Your fire burns resilient in the dark."
            }

            else -> when { // BLANK
                wordCount < 15 ->
                    "A blank space is an open door. No pressure to fill it with anything more than you feel."
                else ->
                    "Uncharted territory. A peaceful pause where anything can begin."
            }
        }

        val (petName, tag) = when (normMood) {
            MoodConstants.BRIGHT  -> "Sol" to "Radiant"
            MoodConstants.CALM    -> "Sage" to "Serene"
            MoodConstants.HEAVY   -> "Moss" to "Empathetic"
            MoodConstants.TANGLED -> "Knot" to "Understanding"
            MoodConstants.DARK    -> "Nox" to "Resilient"
            else                  -> "Echo" to "Observant"
        }

        return PetComment(
            petMood     = normMood,
            commentText = comment,
            emotionTag  = tag,
            petNameHint = petName
        )
    }

    /**
     * Analyzes all journal entries to produce rich contextual pattern insights for the stats tab.
     */
    fun computeContextualInsights(entries: List<JournalEntry>): List<ContextInsight> {
        if (entries.isEmpty()) return emptyList()

        val insights = mutableListOf<ContextInsight>()
        val cal = Calendar.getInstance()

        // 1. Dominant Time of Day
        val hourCounts = IntArray(24)
        val dayOfWeekMoods = Array(7) { mutableListOf<String>() } // Sun=0, Sat=6

        entries.forEach { entry ->
            cal.timeInMillis = entry.timestamp
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val dow  = cal.get(Calendar.DAY_OF_WEEK) - 1
            hourCounts[hour]++

            entry.detectedMood?.let { mood ->
                dayOfWeekMoods[dow].add(mood)
            }
        }

        val peakHour = hourCounts.indices.maxByOrNull { hourCounts[it] } ?: 12
        val peakTimeLabel = when (peakHour) {
            in 5..11  -> "Morning Person"
            in 12..16 -> "Afternoon Writer"
            in 17..21 -> "Evening Reflector"
            else      -> "Night Owl"
        }

        insights.add(
            ContextInsight(
                title = "Peak Journaling Window",
                description = "You write most frequently around ${format12Hour(peakHour)}. Your creativity peaks here.",
                metricValue = peakTimeLabel,
                iconType = "TIME_OF_DAY"
            )
        )

        // 2. Weekly Mood Pattern
        val days = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        val happiestDayIndex = dayOfWeekMoods.indices.maxByOrNull { idx ->
            dayOfWeekMoods[idx].count { it.equals(MoodConstants.BRIGHT, ignoreCase = true) }
        } ?: 0

        val brightestDayName = days[happiestDayIndex]
        insights.add(
            ContextInsight(
                title = "Warmest Day",
                description = "You record the most positive & energized entries on ${brightestDayName}s.",
                metricValue = brightestDayName,
                iconType = "DOMINANT_MOOD"
            )
        )

        // 3. Average Journaling Depth (Words per entry)
        val totalWords = entries.sumOf { e ->
            e.content?.trim()?.split(Regex("\\s+"))?.size ?: 0
        }
        val avgWords = totalWords / entries.size.coerceAtLeast(1)

        insights.add(
            ContextInsight(
                title = "Expression Depth",
                description = "On average, you weave $avgWords words per journal entry.",
                metricValue = "$avgWords words/entry",
                iconType = "DEPTH"
            )
        )

        return insights
    }

    /**
     * Generates personalized self-care micro-suggestions based on current feelings.
     */
    fun generateSuggestions(recentEntries: List<JournalEntry>): List<ActionableSuggestion> {
        val suggestions = mutableListOf<ActionableSuggestion>()
        if (recentEntries.isEmpty()) {
            suggestions.add(
                ActionableSuggestion(
                    title = "Begin Your Journaling Habit",
                    actionText = "Write 2 sentences about how your morning started.",
                    category = "Routine"
                )
            )
            return suggestions
        }

        val latestMood = recentEntries.lastOrNull()?.detectedMood ?: MoodConstants.CALM

        when (latestMood) {
            MoodConstants.HEAVY -> {
                suggestions.add(
                    ActionableSuggestion(
                        title = "Unload & Decompress",
                        actionText = "Drink a warm cup of water and take 5 slow, grounding breaths.",
                        category = "Rest"
                    )
                )
                suggestions.add(
                    ActionableSuggestion(
                        title = "Gentle Movement",
                        actionText = "Step outside for 5 minutes without your phone to look at trees or sky.",
                        category = "Mindfulness"
                    )
                )
            }
            MoodConstants.TANGLED -> {
                suggestions.add(
                    ActionableSuggestion(
                        title = "Brain Dump Exercise",
                        actionText = "Write down 3 things within your control and 3 things outside your control.",
                        category = "Clarity"
                    )
                )
            }
            MoodConstants.BRIGHT -> {
                suggestions.add(
                    ActionableSuggestion(
                        title = "Anchor the Joy",
                        actionText = "Share a brief compliment or message of love with someone close to you.",
                        category = "Connection"
                    )
                )
            }
            else -> { // CALM / DARK / BLANK
                suggestions.add(
                    ActionableSuggestion(
                        title = "Evening Reflection",
                        actionText = "Note one small detail from today that made you feel peaceful.",
                        category = "Gratitude"
                    )
                )
            }
        }

        return suggestions
    }

    private fun format12Hour(hour: Int): String {
        val amPm = if (hour >= 12) "PM" else "AM"
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        return "$h12:00 $amPm"
    }
}
