package com.gxdevs.lore.utils

import com.gxdevs.lore.data.journal.JournalEntry
import com.gxdevs.lore.data.mood.MoodConstants
import java.util.Calendar

/**
 * Tailored Wellbeing & Emotional Self-Care Engine for Nurtale.
 *
 * Generates personalized, non-professional wellness micro-interventions,
 * cognitive reframing prompts, and custom notification text tailored to the
 * user's emotional trends, journaling frequency, and time of day patterns.
 *
 * Disclaimer: Advice generated is for emotional self-reflection and personal wellness
 * only; not medical or professional therapy.
 *
 * 100% On-device · 100% Private.
 */
object TailoredWellbeingEngine {

    data class WellbeingAdvice(
        val moodCategory: String,          // e.g. "Heavy", "Tangled", "Dark", "Bright"
        val headline: String,              // Catchy, supportive title
        val reframingPrompt: String,       // Cognitive reframing thought prompt
        val physicalMicroAction: String,   // Simple 1-minute physical action (e.g. hydration, posture)
        val petWisdom: String,             // Speech from companion pet
        val categoryLabel: String          // e.g. "Emotional Recovery", "Stress Release", "Gratitude Anchor"
    )

    data class CustomNotificationVariant(
        val title: String,
        val text: String,
        val petCompanionName: String
    )

    /**
     * Generates a tailored wellbeing advice card based on recent entry history
     * and current detected mood.
     */
    fun generateTailoredAdvice(
        recentEntries: List<JournalEntry>,
        currentMood: String? = null
    ): WellbeingAdvice {
        val activeMood = currentMood ?: recentEntries.lastOrNull()?.detectedMood ?: MoodConstants.CALM

        // Count mood occurrences in recent entries to detect streaks
        val moodCounts = recentEntries.takeLast(7).mapNotNull { it.detectedMood }
        val heavyCount   = moodCounts.count { it.equals(MoodConstants.HEAVY, ignoreCase = true) }
        val tangledCount = moodCounts.count { it.equals(MoodConstants.TANGLED, ignoreCase = true) }
        val darkCount    = moodCounts.count { it.equals(MoodConstants.DARK, ignoreCase = true) }

        return when {
            // Persistent heavy trend (3+ heavy entries in last 7)
            heavyCount >= 3 -> WellbeingAdvice(
                moodCategory = MoodConstants.HEAVY,
                headline = "Lifting the Heavy Days",
                reframingPrompt = "Ask yourself: 'What is one small burden I can give myself permission to put down today, even for an hour?'",
                physicalMicroAction = "Place both hands over your heart, close your eyes, and take 3 deep, slow belly breaths.",
                petWisdom = "Moss says: 'Heavy feelings mean you care deeply. Be extra gentle with yourself tonight.'",
                categoryLabel = "Emotional Gentle Care"
            )

            // Persistent tangled trend (3+ tangled entries in last 7)
            tangledCount >= 3 -> WellbeingAdvice(
                moodCategory = MoodConstants.TANGLED,
                headline = "Untangling Mind Storms",
                reframingPrompt = "Write down one thought that is worrying you. Ask: 'Will this matter in 6 months? What can I control right now?'",
                physicalMicroAction = "Unclench your jaw, drop your shoulders away from your ears, and stretch your neck side to side.",
                petWisdom = "Knot says: 'When thoughts tangle, slow down. You don't have to solve everything all at once.'",
                categoryLabel = "Mental Clarity"
            )

            // Dark / Anger spike
            darkCount >= 2 || activeMood.equals(MoodConstants.DARK, ignoreCase = true) -> WellbeingAdvice(
                moodCategory = MoodConstants.DARK,
                headline = "Channeling the Fire",
                reframingPrompt = "Anger often protects a softer hurt underneath. What boundary or value felt violated today?",
                physicalMicroAction = "Walk briskly for 2 minutes or splash cool water on your face to signal safety to your nervous system.",
                petWisdom = "Nox says: 'Your anger has validity. Let it burn out safely onto paper rather than keeping it inside.'",
                categoryLabel = "Boundary & Release"
            )

            // Single heavy entry
            activeMood.equals(MoodConstants.HEAVY, ignoreCase = true) -> WellbeingAdvice(
                moodCategory = MoodConstants.HEAVY,
                headline = "Honoring Your Low Energy",
                reframingPrompt = "Instead of fighting sadness, treat it like rainy weather: 'I will rest until the sun returns.'",
                physicalMicroAction = "Sip a warm glass of water or tea slowly, focusing entirely on the warmth.",
                petWisdom = "Sage says: 'Low energy isn't failure; it's a signal to recharge.'",
                categoryLabel = "Rest & Recovery"
            )

            // Single tangled entry
            activeMood.equals(MoodConstants.TANGLED, ignoreCase = true) -> WellbeingAdvice(
                moodCategory = MoodConstants.TANGLED,
                headline = "Finding Grounding in Chaos",
                reframingPrompt = "Name 5 things you can see, 4 you can touch, 3 you can hear, 2 you can smell, and 1 deep breath.",
                physicalMicroAction = "Press your feet flat on the floor and feel the solid ground beneath you.",
                petWisdom = "Knot says: 'Focus on the next single step, not the whole staircase.'",
                categoryLabel = "5-4-3-2-1 Grounding"
            )

            // Bright mood
            activeMood.equals(MoodConstants.BRIGHT, ignoreCase = true) -> WellbeingAdvice(
                moodCategory = MoodConstants.BRIGHT,
                headline = "Amplifying Good Energy",
                reframingPrompt = "What contributed to this joy? How can you cultivate more of this in your routine?",
                physicalMicroAction = "Smile genuinely for 10 seconds to lock in this positive neuro-chemical state.",
                petWisdom = "Sol says: 'Your joy lights up our journey! Share a small piece of this warmth with someone today.'",
                categoryLabel = "Gratitude Anchor"
            )

            // Calm / Default
            else -> WellbeingAdvice(
                moodCategory = MoodConstants.CALM,
                headline = "Sustaining Peaceful Moments",
                reframingPrompt = "What is one quiet blessing from today that you're glad happened?",
                physicalMicroAction = "Inhale for 4 seconds, hold for 4 seconds, exhale for 6 seconds.",
                petWisdom = "Echo says: 'Peace is a quiet sanctuary. Enjoy this stillness.'",
                categoryLabel = "Mindful Balance"
            )
        }
    }

    /**
     * Generates a custom notification text tailored to the user's emotional trend
     * and habitual journaling time.
     */
    fun generateTailoredNotification(recentEntries: List<JournalEntry>): CustomNotificationVariant {
        val lastEntry = recentEntries.lastOrNull()
        val lastMood  = lastEntry?.detectedMood ?: MoodConstants.CALM
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        return when {
            lastMood.equals(MoodConstants.HEAVY, ignoreCase = true) -> CustomNotificationVariant(
                title = "Moss is listening 🍃",
                text = "Yesterday felt heavy. Take a moment to write a few words and check in with yourself.",
                petCompanionName = "Moss"
            )

            lastMood.equals(MoodConstants.TANGLED, ignoreCase = true) -> CustomNotificationVariant(
                title = "Knot suggests a pause 🌀",
                text = "Unpack your thoughts before sleep. A 2-minute brain dump can clear your mind.",
                petCompanionName = "Knot"
            )

            lastMood.equals(MoodConstants.BRIGHT, ignoreCase = true) -> CustomNotificationVariant(
                title = "Sol is beaming ✨",
                text = "You recorded great energy recently! What made today special?",
                petCompanionName = "Sol"
            )

            hour in 21..23 || hour in 0..4 -> CustomNotificationVariant(
                title = "Late Night Check-in 🌙",
                text = "Writing before sleep helps quiet an overactive mind. Record your night thoughts.",
                petCompanionName = "Nox"
            )

            else -> CustomNotificationVariant(
                title = "Your Nurtale Sanctuary 🌿",
                text = "Sage invites you: 'Take a soft pause and capture a memory from today.'",
                petCompanionName = "Sage"
            )
        }
    }
}
