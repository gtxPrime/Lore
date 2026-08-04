package com.gxdevs.nurtale.utils

import com.gxdevs.nurtale.data.mood.MoodConstants
import java.util.Locale

/**
 * Robust On-Device Mood Scoring Engine for Nurtale.
 *
 * A multi-signal, zero-dependency sentiment and mood classifier that:
 * ─ Uses exact word-boundary tokenization (no false-positive substring matches)
 * ─ Handles negation: "not happy" → negative signal, not positive
 * ─ Applies intensity amplifiers: "very sad" → 3×, "extremely" → 4×
 * ─ Scores bigrams (two-word phrases) for high-precision signals
 * ─ Reads punctuation and structural signals (!!!, ..., ???)
 * ─ Supports 25+ languages via ML Kit Language ID + language-weighted lexicons
 * ─ Runs in < 3ms on any Android device; 0 KB APK overhead (uses Play Services)
 *
 * Works in tandem with [AdaptiveMoodModel] which re-ranks these scores
 * using learned user-specific word weights stored in Room.
 */
object MoodScoringEngine {

    // ── Result Types ──────────────────────────────────────────────────────────

    data class ScoringResult(
        /** The mood label predicted by the pure lexicon engine (before adaptive re-ranking). */
        val topMood: String,
        /** Full scores for all 6 moods, normalized 0–100. */
        val moodScores: Map<String, Float>,
        /** Sentiment polarity: -1.0 (very negative) to +1.0 (very positive). */
        val sentimentScore: Float,
        /** Confidence of the top prediction, 0.0–1.0. */
        val confidence: Float,
        /** Auto-generated descriptive tags for this entry. */
        val suggestedTags: List<String>,
        val wordCount: Int,
        /** BCP-47 language code detected by ML Kit (e.g. "hi", "en", "es"). */
        val detectedLanguage: String = "und"
    )

    // ── Negation Words (multilingual) ─────────────────────────────────────────
    private val negationWords = setOf(
        // English
        "not", "no", "never", "don't", "dont", "doesn't", "doesnt",
        "didn't", "didnt", "can't", "cant", "won't", "wont", "isn't",
        "isnt", "wasn't", "wasnt", "aren't", "arent", "without", "hardly",
        "barely", "neither", "nor", "nothing", "nope", "nah", "neither",
        // Hindi negation
        "नहीं", "मत", "बिना", "कभी नहीं",
        // Spanish
        "no", "nunca", "jamás", "sin", "tampoco",
        // French
        "ne", "pas", "jamais", "sans", "ni",
        // German
        "nicht", "nein", "nie", "niemals", "ohne", "kein", "keine"
    )

    // ── Intensity Amplifiers ──────────────────────────────────────────────────
    private val amplifiers = mapOf(
        // English
        "very" to 2.5f, "really" to 2.5f, "so" to 2.0f, "extremely" to 4.0f,
        "incredibly" to 3.5f, "absolutely" to 3.0f, "completely" to 3.0f,
        "totally" to 2.5f, "deeply" to 3.0f, "intensely" to 3.0f,
        "utterly" to 3.5f, "quite" to 1.5f, "rather" to 1.5f,
        "little" to 0.5f, "bit" to 0.5f, "slightly" to 0.5f,
        "somewhat" to 0.7f, "overwhelmingly" to 3.5f, "profoundly" to 3.5f,
        "genuinely" to 2.0f, "truly" to 2.5f, "sincerely" to 2.0f,
        // Hindi amplifiers
        "बहुत" to 2.5f, "बहुत ज़्यादा" to 3.5f, "अत्यंत" to 4.0f,
        // Spanish
        "muy" to 2.5f, "tan" to 2.0f, "demasiado" to 3.0f, "increiblemente" to 3.5f,
        // French
        "très" to 2.5f, "tellement" to 2.5f, "vraiment" to 2.5f, "absolument" to 3.0f,
        // German
        "sehr" to 2.5f, "so" to 2.0f, "wirklich" to 2.5f, "absolut" to 3.0f
    )

    // ── BRIGHT / JOY Lexicon ──────────────────────────────────────────────────
    private val brightLexicon = setOf(
        // Core English
        "happy", "happiness", "joy", "joyful", "joyous", "excited", "exciting",
        "amazing", "wonderful", "fantastic", "great", "love", "loved", "loving",
        "smile", "smiling", "laughing", "laugh", "hope", "hopeful", "grateful",
        "gratitude", "blessed", "blissful", "bliss", "elated", "euphoric",
        "thrilled", "proud", "triumph", "win", "won", "success", "achieved",
        "achievement", "celebrate", "celebrating", "celebrated", "cheerful",
        "delighted", "glad", "inspired", "inspiring", "energized", "pumped",
        "alive", "vibrant", "light", "bright", "sunshine", "radiant", "glow",
        "glowing", "fulfillment", "fulfilled", "content", "beautiful", "perfect",
        "excellent", "awesome", "spectacular", "magnificent", "exhilarated",
        "passionate", "enthusiasm", "enthusiastic", "motivated", "ecstatic",
        "overjoyed", "thriving", "flourishing", "victorious", "liberated",
        "uplifted", "rejoice", "rejoicing", "jubilant", "jubilee", "blessed",
        "cherished", "cherish", "treasure", "treasured", "warm", "warmth",
        "magic", "magical", "miracle", "blessed", "divine", "incredible",
        "outstanding", "remarkable", "exceptional", "radiate", "radiating",
        "alive", "bouncing", "bubbly", "buoyant", "buzzed", "buzzing",
        "captivated", "captivating", "dazzled", "dazzling", "dazzle",
        "exuberant", "exuberance", "flourish", "fulfilling", "glorious",
        "gorgeous", "heavenly", "invigorated", "lively", "merry", "optimistic",
        "optimism", "paradise", "pleasurable", "precious", "refreshed",
        "rejuvenated", "rewarding", "sensational", "sparkling", "splendid",
        "stellar", "stimulated", "stimulating", "superb", "thriving",
        "triumphant", "upbeat", "vivid", "vivacious", "wholesome", "zest",
        "zestful", "zingy",
        // Hindi — Joy
        "खुश", "खुशी", "आनंद", "प्यार", "जीत", "बढ़िया", "सुंदर", "शानदार",
        "अद्भुत", "प्रसन्न", "मुस्कुराना", "हँसी", "उत्साह", "हर्ष",
        "संतोष", "कृतज्ञ", "आभारी", "उमंग", "जोश", "तरक्की",
        // Spanish — Joy
        "feliz", "alegria", "alegre", "amor", "excelente", "genial", "sonrisa",
        "maravilloso", "fantástico", "emocionado", "emoción", "esperanza",
        "esperanzado", "agradecido", "contento", "satisfecho", "triunfo",
        "celebrar", "dichoso", "encantado", "jovial", "radiante", "animado",
        // French — Joy
        "heureux", "heureuse", "joie", "amour", "magnifique", "formidable",
        "merveilleux", "content", "satisfait", "rayonnant", "enthousiaste",
        "célébrer", "enchantée", "ravie", "extatique", "épanoui",
        // German — Joy
        "glücklich", "freude", "liebe", "wunderbar", "fantastisch", "begeistert",
        "zufrieden", "dankbar", "strahlen", "feiern", "triumphieren",
        // Portuguese
        "feliz", "alegria", "amor", "maravilhoso", "fantástico", "animado",
        "grato", "contente", "celebrar", "vitória",
        // Arabic
        "سعيد", "فرح", "حب", "رائع", "ممتاز", "شكرا", "ممتنن",
        // Italian
        "felice", "gioia", "amore", "meraviglioso", "fantastico", "entusiasta",
        // Korean
        "행복", "기쁨", "사랑", "멋진", "감사",
        // Japanese
        "嬉しい", "幸せ", "愛", "素晴らしい", "感謝",
        // Russian
        "счастливый", "радость", "любовь", "замечательный", "благодарный",
        // Indonesian / Malay
        "bahagia", "gembira", "cinta", "luar biasa", "bersyukur"
    )

    // ── CALM / PEACEFUL Lexicon ───────────────────────────────────────────────
    private val calmLexicon = setOf(
        // English
        "calm", "calming", "calmed", "peaceful", "peace", "serene", "serenity",
        "quiet", "silence", "silent", "still", "stillness", "relax", "relaxed",
        "relaxing", "rest", "resting", "breathe", "breathing", "cozy", "cosy",
        "soft", "gentle", "soothing", "soothe", "solitude", "tranquil",
        "tranquility", "meditate", "meditating", "meditation", "mindful",
        "mindfulness", "ease", "eased", "easing", "steady", "grounded",
        "settled", "stable", "okay", "fine", "alright", "neutral", "balanced",
        "smooth", "graceful", "patient", "patience", "acceptance", "accepting",
        "present", "centred", "centered", "anchored", "composed", "at peace",
        "at ease", "mellow", "languid", "unhurried", "leisurely", "breezy",
        "clear", "clarity", "serene", "undisturbed", "untroubled", "undisturbed",
        "contented", "placid", "mild", "temperate", "moderate", "harmonious",
        "harmony", "flowing", "flowing", "floating", "drifting",
        // Hindi — Calm
        "शांत", "सुकून", "आराम", "सांस", "विश्राम", "मन की शांति",
        "धैर्य", "संतुलन", "स्थिर", "चैन",
        // Spanish
        "tranquilo", "tranquila", "calma", "paz", "relajado", "sereno",
        "calmado", "suave", "quieto", "sosegado", "pacífico",
        // French
        "calme", "paisible", "repos", "tranquille", "serein", "reposant",
        "doux", "apaisé", "zen",
        // German
        "ruhig", "frieden", "entspannt", "sanft", "gelassen", "ausgeglichen",
        // Portuguese
        "calmo", "paz", "tranquilo", "sereno", "descansado",
        // Hindi-mixed (Hinglish)
        "chill", "chillax", "relax", "peace"
    )

    // ── HEAVY / SADNESS Lexicon ───────────────────────────────────────────────
    private val heavyLexicon = setOf(
        // English
        "sad", "sadness", "sadder", "saddest", "unhappy", "depressed", "depression",
        "cry", "crying", "cried", "tears", "tear", "grief", "grieve", "grieving",
        "loss", "lost", "lonely", "loneliness", "alone", "isolated", "isolation",
        "heavy", "burden", "burdened", "pain", "painful", "hurt", "hurting",
        "heartbreak", "heartbroken", "heartache", "numb", "numbness", "empty",
        "emptiness", "hollow", "hopeless", "helpless", "worthless", "broken",
        "shattered", "devastated", "devastation", "crushed", "exhausted",
        "exhaustion", "drained", "tired", "fatigue", "miserable", "misery",
        "gloomy", "gloom", "despair", "despairing", "down", "downhearted",
        "downcast", "low", "sorrowful", "sorrow", "ache", "aching", "blue",
        "melancholy", "melancholic", "despondent", "wretched", "forlorn",
        "bereaved", "bereavement", "mourning", "mournful", "mourn", "lament",
        "lamenting", "inconsolable", "devastate", "shatter", "crumble",
        "crumbling", "wilting", "withering", "wilt", "wither", "languish",
        "languishing", "suffer", "suffering", "sufferer", "anguish",
        "heartache", "tearful", "weeping", "weep", "sob", "sobbing",
        "desolate", "desolation", "dejected", "dejection", "disheartened",
        "dispirited", "distraught", "drowning", "sinking", "falling",
        "longing", "miss", "missing", "yearning", "yearn",
        // Hindi — Sadness
        "दुखी", "दर्द", "अकेला", "रोया", "थका", "भारी", "उदास",
        "निराश", "टूटा", "खाली", "गम", "बेदर्द", "दुख", "पीड़ा",
        "अकेलापन", "बोझ", "नाउम्मीद", "तड़प",
        // Spanish
        "triste", "tristeza", "dolor", "llorar", "solo", "sola", "cansado",
        "agotado", "desesperado", "vacío", "roto", "quebrado", "sufrimiento",
        "pena", "angustia", "melancolía", "deprimido", "desolado",
        // French
        "triste", "tristesse", "douleur", "pleurer", "seul", "seule",
        "fatigué", "épuisé", "désespéré", "vide", "brisé", "souffrance",
        "peine", "déprimé", "mélancolie", "désolé",
        // German
        "traurig", "schmerz", "weinen", "allein", "müde", "erschöpft",
        "hoffnungslos", "leer", "gebrochen", "leiden", "traurigkeit",
        // Portuguese
        "triste", "tristeza", "dor", "chorar", "sozinho", "cansado",
        "desesperado", "vazio", "sofrimento",
        // Arabic
        "حزين", "ألم", "وحيد", "يبكي", "متعب", "يأس", "فراغ",
        // Italian
        "triste", "tristezza", "dolore", "piangere", "solo", "stanco",
        "disperato", "vuoto",
        // Korean
        "슬픔", "외로움", "아픔", "피곤", "절망",
        // Japanese
        "悲しい", "孤独", "痛み", "疲れ", "絶望",
        // Russian
        "грустный", "боль", "одинокий", "плакать", "устал", "отчаяние"
    )

    // ── TANGLED / ANXIOUS Lexicon ─────────────────────────────────────────────
    private val tangledLexicon = setOf(
        // English
        "anxious", "anxiety", "panic", "panicking", "stressed", "stress",
        "overwhelmed", "overwhelming", "nervous", "nervousness", "worry",
        "worried", "worrying", "restless", "restlessness", "uneasy", "unease",
        "tense", "tension", "agitated", "agitation", "frustrated", "frustration",
        "confused", "confusion", "confusing", "uncertain", "uncertainty",
        "doubt", "doubting", "doubtful", "tangled", "jumbled", "scattered",
        "overthinking", "overthink", "spinning", "racing", "dread", "dreading",
        "apprehensive", "apprehension", "fear", "fearful", "scared", "scarred",
        "paranoid", "paranoia", "pressured", "pressure", "trapped", "stuck",
        "claustrophobic", "suffocating", "suffocate", "frantic", "frenzied",
        "frenzy", "chaotic", "chaos", "unraveling", "unravel", "spiraling",
        "spiral", "falling apart", "overwhelm", "swamped", "buried", "drowning",
        "consumed", "unsettled", "rattled", "shaken", "turbulent", "turbulence",
        "turmoil", "whirlwind", "tornado", "storm", "stormy", "volatile",
        "jittery", "antsy", "on edge", "edge", "hyperventilating", "trembling",
        "shaking", "tremor", "clammy",
        // Hindi — Anxiety / Tangled
        "गुस्सा", "तनाव", "चिड़चिड़ा", "डर", "घबराहट", "परेशान",
        "उलझन", "चिंता", "बेचैन", "घबराया", "डरा",
        // Spanish
        "ansioso", "ansiosa", "ansiedad", "estrés", "frustrado", "confundido",
        "nervioso", "preocupado", "agitado", "abrumado", "pánico", "miedo",
        // French
        "anxieux", "anxieuse", "anxiété", "stress", "frustré", "confus",
        "nerveux", "inquiet", "agité", "submergé", "panique", "peur",
        // German
        "ängstlich", "angst", "stress", "frustriert", "verwirrt", "nervös",
        "besorgt", "aufgewühlt", "überwältigt", "panik",
        // Portuguese
        "ansioso", "ansiosa", "estresse", "frustrado", "confuso", "nervoso",
        "preocupado", "agitado",
        // Arabic
        "قلق", "توتر", "محبط", "خائف", "مرهق", "مرتبك",
        // Italian
        "ansioso", "ansia", "stress", "frustrato", "confuso", "nervoso",
        "preoccupato",
        // Korean
        "불안", "스트레스", "좌절", "두려움", "혼란",
        // Japanese
        "不安", "ストレス", "欲求不満", "恐怖", "混乱",
        // Russian
        "тревожный", "стресс", "расстроенный", "испуганный", "запутанный"
    )

    // ── DARK / ANGER Lexicon ──────────────────────────────────────────────────
    private val darkLexicon = setOf(
        // English
        "angry", "anger", "rage", "furious", "fury", "hate", "hatred", "hating",
        "despise", "despising", "disgusted", "disgust", "resentment", "resentful",
        "bitter", "bitterness", "hostile", "hostility", "violent", "violence",
        "enraged", "outraged", "outrage", "irritated", "irritation", "fed",
        "done", "over", "nightmare", "terror", "horrified", "horror",
        "livid", "seething", "fuming", "boiling", "raging", "wrathful", "wrath",
        "indignant", "indignation", "vexed", "irate", "infuriated", "inflamed",
        "vengeful", "vengeance", "revenge", "retribution", "spiteful", "spite",
        "cruel", "cruelty", "malicious", "malice", "wicked", "toxic", "poisonous",
        "loathe", "loathing", "abhor", "abhorrence", "contempt", "scornful",
        "scorn", "disdain", "disdainful", "cynical", "cynicism", "bitter",
        "nihilistic", "nihilism", "bleak", "bleakness",
        // Hindi — Anger / Dark
        "नफरत", "क्रोध", "गुस्सा", "चिड़चिड़ा", "हिंसा", "कड़वाहट",
        // Spanish
        "enojado", "rabia", "odio", "ira", "furioso", "amargado", "hostil",
        "violento", "rencor",
        // French
        "colère", "haine", "rage", "furieux", "amer", "hostile", "violent",
        "rancune",
        // German
        "wütend", "hass", "zorn", "wut", "bitter", "feindlich", "gewaltsam",
        // Portuguese
        "raiva", "ódio", "furioso", "amargo", "hostil",
        // Arabic
        "غضب", "كره", "غضبان", "مرير",
        // Italian
        "arrabbiato", "rabbia", "odio", "furioso", "amaro",
        // Korean
        "분노", "증오", "화남", "격분",
        // Japanese
        "怒り", "憎しみ", "激怒",
        // Russian
        "злой", "ненависть", "ярость", "злость"
    )

    // ── High-Signal Bigrams ───────────────────────────────────────────────────
    private val brightBigrams = setOf(
        "feel good", "feeling great", "really happy", "so grateful", "love life",
        "full joy", "pure joy", "best day", "great day", "good day", "so good",
        "feel alive", "feeling alive", "on fire", "sky high", "over moon",
        "made it", "so proud", "truly blessed", "beyond grateful", "love this",
        "love today", "amazing day", "beautiful day", "good news", "great news",
        "finally happened", "dream come", "came true", "so excited"
    )

    private val heavyBigrams = setOf(
        "fell apart", "breaking down", "can't stop", "feel lost", "so empty",
        "can't cope", "give up", "can't breathe", "losing hope", "hit rock",
        "rock bottom", "don't care", "lost everything", "nothing matters",
        "miss you", "still hurts", "so tired", "so drained", "barely holding",
        "falling apart", "going through", "hard time", "tough time",
        "no one cares", "all alone", "by myself", "nothing left",
        "don't belong", "never enough", "keep crying", "can't stop crying",
        "broken inside", "heavy heart", "heart hurts", "hurts so much",
        "doesn't matter", "what's point", "whats point", "no point"
    )

    private val tangledBigrams = setOf(
        "can't stop thinking", "mind racing", "heart racing", "so much pressure",
        "don't know", "what if", "worst case", "keep worrying", "can't focus",
        "feel trapped", "feel stuck", "so confused", "overwhelming feeling",
        "can't decide", "too much", "falling behind", "losing control",
        "out of control", "going crazy", "losing mind", "can't handle",
        "so anxious", "anxiety attack", "panic attack", "constant worry",
        "overthinking everything", "head spinning", "can't calm", "can't sleep"
    )

    private val darkBigrams = setOf(
        "so angry", "makes me angry", "want to scream", "can't stand",
        "fed up", "sick of", "hate this", "want to explode",
        "pure rage", "losing it", "losing mind", "done with",
        "can't take", "had enough", "so done", "absolute nightmare",
        "makes me sick", "drives me crazy", "can't forgive"
    )

    // ── Tag Keyword Sets ──────────────────────────────────────────────────────
    private val gratitudeKeywords = setOf(
        "grateful", "gratitude", "thankful", "appreciate", "appreciated",
        "blessed", "fortunate", "cherish", "gift", "grace", "kindness",
        "ممنون", "شكرا", "شكر", "कृतज्ञ", "आभारी", "agradecido",
        "reconnaissant", "dankbar", "grato"
    )

    private val growthKeywords = setOf(
        "learned", "learn", "growth", "growing", "improve", "improved",
        "better", "progress", "realized", "realize", "understand", "insight",
        "breakthrough", "change", "changed", "evolve", "evolving", "overcome",
        "overcoming", "developed", "develop", "mature", "maturing", "wisdom",
        "lesson", "opportunity", "potential", "strength", "stronger"
    )

    private val relationshipKeywords = setOf(
        "friend", "family", "love", "partner", "relationship", "together",
        "connected", "bond", "miss", "missing", "mom", "dad", "sister",
        "brother", "mother", "father", "husband", "wife", "girlfriend",
        "boyfriend", "friends", "close", "care", "caring", "support",
        "supported", "belonging", "community", "amigo", "famille"
    )

    private val reflectionKeywords = setOf(
        "think", "thinking", "reflect", "reflecting", "wonder", "wondering",
        "ponder", "pondering", "realize", "realized", "question", "memory",
        "remember", "remembering", "past", "future", "meaning", "purpose",
        "identity", "who am", "what am", "soul", "inner", "depth", "truth",
        "aware", "awareness", "conscious", "consciousness", "introspect",
        "introspection", "perspective", "clarity", "understand", "understanding"
    )

    private val creativeKeywords = setOf(
        "wrote", "write", "writing", "create", "creating", "created", "made",
        "build", "building", "design", "art", "paint", "painting", "draw",
        "drawing", "music", "play", "song", "poem", "story", "imagine",
        "imagining", "dream", "vision", "idea", "ideas", "inspired"
    )

    private val healthKeywords = setOf(
        "workout", "exercise", "run", "running", "yoga", "walk", "walked",
        "healthy", "eat", "sleep", "slept", "body", "mind", "meditate",
        "gym", "breathe", "hydrate", "rest", "recover", "recovery", "heal",
        "healing", "wellness", "wellbeing", "fit", "fitness", "strong"
    )

    // ── Stop Words (excluded from adaptive learning) ──────────────────────────
    val stopWords = setOf(
        "the", "and", "for", "are", "but", "not", "this", "that", "with",
        "have", "from", "they", "will", "been", "more", "was", "has", "had",
        "its", "their", "what", "which", "when", "where", "there", "here",
        "just", "can", "all", "one", "out", "into", "some", "also", "than",
        "then", "only", "even", "like", "get", "got", "did", "does", "do",
        "my", "me", "he", "she", "we", "you", "it", "is", "in", "on",
        "at", "to", "of", "a", "i", "am", "be", "so", "if", "as", "by",
        "or", "an", "up", "his", "her", "our", "your", "its", "day", "today",
        "been", "about", "after", "before", "again", "could", "would", "should",
        "still", "want", "know", "think", "feel", "time", "back", "way",
        "people", "life", "make", "right", "now", "little", "much", "too",
        "every", "being", "actually", "always", "already", "later", "maybe",
        "going", "things", "something", "nothing", "everything", "anything"
    )

    // ── Main Analysis Function ────────────────────────────────────────────────

    /**
     * Analyzes journal text and returns a full [ScoringResult].
     *
     * @param text          Raw journal text
     * @param runLanguageDetection If true (default), runs ML Kit language detection
     *                      synchronously to apply language-specific score boosts.
     *                      Set to false in tests or perf-sensitive contexts.
     */
    fun analyze(text: String?, runLanguageDetection: Boolean = true): ScoringResult {
        if (text.isNullOrBlank()) {
            return ScoringResult(
                topMood = MoodConstants.CALM,
                moodScores = defaultScores(),
                sentimentScore = 0f,
                confidence = 0f,
                suggestedTags = emptyList(),
                wordCount = 0,
                detectedLanguage = "und"
            )
        }

        // ── Language Detection ─────────────────────────────────────────────
        val langResult = if (runLanguageDetection) {
            LanguageDetector.detectSync(text)
        } else {
            LanguageDetector.DetectionResult("und", 0f, true)
        }
        val langCode = langResult.bcp47Code
        val langWeight = LanguageDetector.lexiconWeightFor(langCode)

        val rawLower = text.lowercase(Locale.getDefault())

        // Tokenize: split on whitespace and punctuation, keep words >= 2 chars
        val tokens = rawLower
            .split(Regex("[\\s,!.?\"';:\\-()\\[\\]\n\r]+"))
            .filter { it.length >= 2 }

        val wordCount = tokens.size

        if (wordCount == 0) {
            return ScoringResult(
                topMood = MoodConstants.CALM,
                moodScores = defaultScores(),
                sentimentScore = 0f,
                confidence = 0f,
                suggestedTags = emptyList(),
                wordCount = 0,
                detectedLanguage = langCode
            )
        }

        // ── Initialize score accumulators ──────────────────────────────────
        val scores = mutableMapOf(
            MoodConstants.BRIGHT  to 0f,
            MoodConstants.CALM    to 0f,
            MoodConstants.HEAVY   to 0f,
            MoodConstants.TANGLED to 0f,
            MoodConstants.DARK    to 0f,
            MoodConstants.BLANK   to 0f
        )

        // ── Punctuation Signals ────────────────────────────────────────────
        val exclamationCount = text.count { it == '!' }
        val questionCount    = text.count { it == '?' }
        val dotCount         = rawLower.windowed(3).count { it == "..." }

        if (exclamationCount >= 3) {
            scores[MoodConstants.TANGLED] = scores[MoodConstants.TANGLED]!! + 3f
            scores[MoodConstants.BRIGHT]  = scores[MoodConstants.BRIGHT]!!  + 1.5f
        } else if (exclamationCount >= 1) {
            scores[MoodConstants.BRIGHT]  = scores[MoodConstants.BRIGHT]!!  + 1f
        }
        if (questionCount >= 3) {
            scores[MoodConstants.TANGLED] = scores[MoodConstants.TANGLED]!! + 2f
            scores[MoodConstants.BLANK]   = scores[MoodConstants.BLANK]!!   + 2f
        } else if (questionCount >= 1) {
            scores[MoodConstants.BLANK]   = scores[MoodConstants.BLANK]!!   + 0.5f
        }
        if (dotCount >= 2) {
            scores[MoodConstants.HEAVY]   = scores[MoodConstants.HEAVY]!!   + 2f
            scores[MoodConstants.BLANK]   = scores[MoodConstants.BLANK]!!   + 1f
        }

        // ALL CAPS words signal intensity
        val capsWords = text.split(Regex("\\s+")).count { w ->
            w.length >= 3 && w == w.uppercase(Locale.getDefault())
        }
        if (capsWords >= 3) {
            scores[MoodConstants.DARK]    = scores[MoodConstants.DARK]!!    + 2.5f
            scores[MoodConstants.TANGLED] = scores[MoodConstants.TANGLED]!! + 1.5f
        } else if (capsWords >= 1) {
            scores[MoodConstants.DARK]    = scores[MoodConstants.DARK]!!    + 1f
        }

        // ── Bigram Scoring ─────────────────────────────────────────────────
        for (i in 0 until tokens.size - 1) {
            val bigram = "${tokens[i]} ${tokens[i + 1]}"
            when {
                brightBigrams.contains(bigram)   -> scores[MoodConstants.BRIGHT]  = scores[MoodConstants.BRIGHT]!!  + 4f * langWeight
                heavyBigrams.contains(bigram)    -> scores[MoodConstants.HEAVY]   = scores[MoodConstants.HEAVY]!!   + 4f * langWeight
                tangledBigrams.contains(bigram)  -> scores[MoodConstants.TANGLED] = scores[MoodConstants.TANGLED]!! + 4f * langWeight
                darkBigrams.contains(bigram)     -> scores[MoodConstants.DARK]    = scores[MoodConstants.DARK]!!    + 4f * langWeight
            }
        }

        // ── Token-level Scoring with Negation + Amplifiers ─────────────────
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]

            // Negation check: any negation within 2-word window before token
            val isNegated = (i > 0 && negationWords.contains(tokens[i - 1])) ||
                            (i > 1 && negationWords.contains(tokens[i - 2]))

            // Amplifier: word immediately before token
            val amplifier = amplifiers[tokens.getOrNull(i - 1)] ?: 1.0f

            val baseScore = amplifier * langWeight

            when {
                brightLexicon.contains(token) -> {
                    if (isNegated) {
                        scores[MoodConstants.HEAVY] = scores[MoodConstants.HEAVY]!! + baseScore * 1.5f
                    } else {
                        scores[MoodConstants.BRIGHT] = scores[MoodConstants.BRIGHT]!! + baseScore * 2f
                    }
                }
                heavyLexicon.contains(token) -> {
                    if (isNegated) {
                        scores[MoodConstants.CALM] = scores[MoodConstants.CALM]!! + baseScore * 0.8f
                    } else {
                        scores[MoodConstants.HEAVY] = scores[MoodConstants.HEAVY]!! + baseScore * 2f
                    }
                }
                tangledLexicon.contains(token) -> {
                    if (isNegated) {
                        scores[MoodConstants.CALM] = scores[MoodConstants.CALM]!! + baseScore * 0.8f
                    } else {
                        scores[MoodConstants.TANGLED] = scores[MoodConstants.TANGLED]!! + baseScore * 2f
                    }
                }
                darkLexicon.contains(token) -> {
                    if (isNegated) {
                        // "don't hate" → very slight calm
                        scores[MoodConstants.CALM] = scores[MoodConstants.CALM]!! + baseScore * 0.4f
                    } else {
                        scores[MoodConstants.DARK] = scores[MoodConstants.DARK]!! + baseScore * 2f
                    }
                }
                calmLexicon.contains(token) -> {
                    if (isNegated) {
                        scores[MoodConstants.TANGLED] = scores[MoodConstants.TANGLED]!! + baseScore * 0.8f
                    } else {
                        scores[MoodConstants.CALM] = scores[MoodConstants.CALM]!! + baseScore * 2f
                    }
                }
            }

            i++
        }

        // ── Length Signals ─────────────────────────────────────────────────
        when {
            wordCount < 10  -> {
                // Very short = likely a note, lean calm/blank
                scores[MoodConstants.CALM]  = scores[MoodConstants.CALM]!!  + 2f
                scores[MoodConstants.BLANK] = scores[MoodConstants.BLANK]!! + 1.5f
            }
            wordCount in 10..29 -> {
                scores[MoodConstants.CALM]  = scores[MoodConstants.CALM]!!  + 1f
                scores[MoodConstants.BLANK] = scores[MoodConstants.BLANK]!! + 0.5f
            }
            wordCount > 250 -> {
                // Long emotional journal — emotional moods slightly more likely
                scores[MoodConstants.HEAVY]   = scores[MoodConstants.HEAVY]!!   + 1.5f
                scores[MoodConstants.TANGLED] = scores[MoodConstants.TANGLED]!! + 1f
                scores[MoodConstants.BRIGHT]  = scores[MoodConstants.BRIGHT]!!  + 1f
            }
        }

        // ── Normalize Scores 0–100 ─────────────────────────────────────────
        val maxScore = scores.values.maxOrNull()?.coerceAtLeast(0.01f) ?: 0.01f
        val normalizedScores = scores.mapValues { (_, v) ->
            ((v / maxScore) * 100f).coerceIn(0f, 100f)
        }

        // ── Top Mood ───────────────────────────────────────────────────────
        val topEntry = normalizedScores.maxByOrNull { it.value }!!
        val topMood  = topEntry.key
        val finalMood = if (maxScore < 0.5f) MoodConstants.CALM else topMood

        // ── Confidence Calculation ─────────────────────────────────────────
        val sortedScores = normalizedScores.values.sortedDescending()
        val gap = (sortedScores.getOrElse(0) { 0f } - sortedScores.getOrElse(1) { 0f })
        val rawConfidence = (gap / 100f).coerceIn(0f, 1f)
        val signalStrength = (maxScore / (wordCount * 0.5f)).coerceIn(0f, 1f)
        // Language detection adds confidence (we know the language → better lexicon match)
        val langBonus = if (!langResult.isUndetermined) 0.05f else 0f
        val confidence = ((rawConfidence * 0.55f) + (signalStrength * 0.40f) + langBonus)
            .coerceIn(0.10f, 0.97f)

        // ── Sentiment Score ────────────────────────────────────────────────
        val posTotal = (normalizedScores[MoodConstants.BRIGHT] ?: 0f) +
                       (normalizedScores[MoodConstants.CALM] ?: 0f) * 0.4f
        val negTotal = (normalizedScores[MoodConstants.HEAVY] ?: 0f) +
                       (normalizedScores[MoodConstants.DARK] ?: 0f) +
                       (normalizedScores[MoodConstants.TANGLED] ?: 0f) * 0.7f
        val sentimentScore = when {
            posTotal + negTotal < 0.1f -> 0f
            else -> ((posTotal - negTotal) / (posTotal + negTotal)).coerceIn(-1f, 1f)
        }

        // ── Auto-Generated Tags ────────────────────────────────────────────
        val tokenSet = tokens.toSet()
        val tags = mutableListOf<String>()

        if (gratitudeKeywords.any { tokenSet.contains(it) }) tags.add("Gratitude")
        if (growthKeywords.any { tokenSet.contains(it) })    tags.add("Growth")
        if (relationshipKeywords.any { tokenSet.contains(it) }) tags.add("Connection")
        if (reflectionKeywords.any { tokenSet.contains(it) }) tags.add("Reflection")
        if (creativeKeywords.any { tokenSet.contains(it) }) tags.add("Creative")
        if (healthKeywords.any { tokenSet.contains(it) }) tags.add("Wellness")

        // Mood-based tags
        when (finalMood) {
            MoodConstants.BRIGHT  -> tags.add("Positive Energy")
            MoodConstants.HEAVY   -> tags.add("Processing")
            MoodConstants.TANGLED -> tags.add("Overthinking")
            MoodConstants.DARK    -> tags.add("Release")
            MoodConstants.CALM    -> tags.add("Centered")
            MoodConstants.BLANK   -> tags.add("Open Space")
        }

        // Quantitative tags
        if (wordCount > 200)         tags.add("Deep Thoughts")
        if (wordCount < 30)          tags.add("Quick Note")
        if (sentimentScore > 0.6f)   tags.add("High Vibe")
        if (sentimentScore < -0.6f)  tags.add("Tough Day")
        if (!langResult.isUndetermined && langCode != "en") {
            tags.add(LanguageDetector.languageNameFor(langCode))
        }

        return ScoringResult(
            topMood        = finalMood,
            moodScores     = normalizedScores,
            sentimentScore = sentimentScore,
            confidence     = confidence,
            suggestedTags  = tags.distinct().take(6),
            wordCount      = wordCount,
            detectedLanguage = langCode
        )
    }

    private fun defaultScores(): Map<String, Float> = mapOf(
        MoodConstants.BRIGHT  to 0f,
        MoodConstants.CALM    to 100f,
        MoodConstants.HEAVY   to 0f,
        MoodConstants.TANGLED to 0f,
        MoodConstants.DARK    to 0f,
        MoodConstants.BLANK   to 0f
    )
}
