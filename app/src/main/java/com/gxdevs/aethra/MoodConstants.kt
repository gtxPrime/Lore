package com.gxdevs.aethra

import androidx.compose.ui.graphics.Color

/**
 * Single source of truth for all 6 mood types in Aethra.
 * Import and use these everywhere â€” never define mood colors inline.
 */
object MoodConstants {

    // ── Mood IDs (stable string keys) ────────────────────────────────────────
    const val BRIGHT  = "Bright"
    const val CALM    = "Calm"
    const val HEAVY   = "Heavy"
    const val TANGLED = "Tangled"
    const val DARK    = "Dark"
    const val BLANK   = "Blank"

    val ALL_MOODS = listOf(BRIGHT, CALM, HEAVY, TANGLED, DARK, BLANK)

    // ── Brand colors per mood ─────────────────────────────────────────────────
    val colorOf: Map<String, Color> = mapOf(
        BRIGHT  to Color(0xFFF3C042),   // warm golden yellow
        CALM    to Color(0xFF606F49),   // sage green
        HEAVY   to Color(0xFF4A5638),   // dark forest green
        TANGLED to Color(0xFFB86C5A),   // terracotta / rust
        DARK    to Color(0xFF2E332A),   // near-black earthy
        BLANK   to Color(0xFF9EACC1)    // muted steel blue
    )

    /** Light pastel / tinted backgrounds for each mood */
    val bgColorOf: Map<String, Color> = mapOf(
        BRIGHT  to Color(0xFFFDF3D4),
        CALM    to Color(0xFFD9DFCD),
        HEAVY   to Color(0xFFCDD4C2),
        TANGLED to Color(0xFFF5DDD7),
        DARK    to Color(0xFFD0D2CE),
        BLANK   to Color(0xFFDCE2EC)
    )

    /** Short flavour text shown in the pet/archive screen */
    val descriptionOf: Map<String, String> = mapOf(
        BRIGHT  to "A spark of joy that illuminates hidden corners of your mind.",
        CALM    to "A serene presence that smooths the ripples of anxious thoughts.",
        HEAVY   to "Formed from unspoken burdens, slowly turning grief into resilience.",
        TANGLED to "A knot of confusion that teaches patience as feelings unravel.",
        DARK    to "A shadow companion providing comfort in the quiet of the night.",
        BLANK   to "An empty slate, representing the potential for new beginnings."
    )

    // ── Pet growth stages ─────────────────────────────────────────────────────
    // journalsNeeded = cumulative journals with this mood required to REACH this stage
    data class PetStage(
        val stageIndex: Int,       // 0 = locked/egg, 1 = cracked egg, â€¦, 5 = full-grown
        val name: String,          // human-readable stage name
        val journalsNeeded: Int    // total count needed to unlock THIS stage
    )

    val stages: List<PetStage> = listOf(
        PetStage(0, "Egg",          1),   // writing 1 journal unlocks the egg
        PetStage(1, "Cracked Egg",  3),
        PetStage(2, "Hatchling",    7),
        PetStage(3, "Fledgling",    15),
        PetStage(4, "Juvenile",     30),
        PetStage(5, "Mythic Spirit",55)
    )

    val totalJournalsForFullGrown: Int = stages.last().journalsNeeded

    /** Resolve the current stage index for a given journal count. Returns -1 if not yet unlocked. */
    fun stageFor(journalCount: Int): Int {
        if (journalCount < 1) return -1   // locked
        var stage = 0
        for (s in stages) {
            if (journalCount >= s.journalsNeeded) stage = s.stageIndex
        }
        return stage
    }

    /** Progress (0f–1f) within the CURRENT stage toward the next one. */
    fun progressInStage(journalCount: Int): Float {
        if (journalCount < 1) return 0f
        val currentStageIdx = stageFor(journalCount)
        val currentStage = stages.getOrNull(currentStageIdx) ?: return 1f
        val nextStage = stages.getOrNull(currentStageIdx + 1) ?: return 1f
        val within = journalCount - currentStage.journalsNeeded
        val needed = nextStage.journalsNeeded - currentStage.journalsNeeded
        return (within.toFloat() / needed).coerceIn(0f, 1f)
    }

    // ── Emotion → mood category mapping ──────────────────────────────────────
    /** Map a raw emotion label to one of the 6 mood buckets. */
    fun emotionToMood(emotionLabel: String): String {
        val name = emotionLabel.lowercase()
        return when {
            name.contains("joy") || name.contains("energetic") ||
            name.contains("grateful") || name.contains("bright") ||
            name.contains("happy") || name.contains("excited") -> BRIGHT

            name.contains("calm") || name.contains("content") ||
            name.contains("peace") || name.contains("serene") ||
            name.contains("relax") -> CALM

            name.contains("sad") || name.contains("heavy") ||
            name.contains("tired") || name.contains("grief") ||
            name.contains("down") || name.contains("depress") -> HEAVY

            name.contains("stress") || name.contains("anxious") ||
            name.contains("tangled") || name.contains("overwhelm") ||
            name.contains("worry") || name.contains("confus") -> TANGLED

            name.contains("anger") || name.contains("dark") ||
            name.contains("fear") || name.contains("rage") ||
            name.contains("dread") -> DARK

            else -> BLANK
        }
    }

    /** Convenience: return the brand Color for a mood ID, falling back to BLANK color. */
    fun moodColor(moodId: String): Color = colorOf[moodId] ?: colorOf[BLANK]!!
}

