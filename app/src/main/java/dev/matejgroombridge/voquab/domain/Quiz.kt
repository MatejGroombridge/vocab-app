package dev.matejgroombridge.voquab.domain

import dev.matejgroombridge.voquab.data.model.CardKind
import dev.matejgroombridge.voquab.data.model.Word
import kotlin.random.Random

/**
 * A ready-to-render multiple-choice question: the same object drives the
 * in-app card and the notification's answer buttons, so both show the same
 * options in the same order.
 */
data class Question(
    val word: Word,
    val kind: CardKind,
    /** Main line, e.g. the word, a short meaning, or a sentence with a gap. */
    val prompt: String,
    /** Optional supporting line, e.g. the example sentence (with `*marks*`). */
    val support: String?,
    val options: List<String>,
    val correctIndex: Int,
)

/**
 * Turns a card into a question with two wrong answers drawn from the
 * user's own deck — same part of speech where possible, so they're
 * plausible, and every quiz doubles as exposure to other words.
 *
 * Seeded by word and day so the options don't reshuffle while the user is
 * looking at them, and match between the app and a notification. Every
 * candidate gets its own stable rank (rather than shuffling the whole
 * list), so adding or shelving an unrelated word mid-day can't change a
 * card that's already been shown.
 */
object Quiz {

    const val OPTION_COUNT = 3
    const val BLANK = "_____"

    fun question(word: Word, kind: CardKind, pool: List<Word>, day: Long): Question? {
        val seed = seed(word.id, day)
        return when (kind) {
            CardKind.PickMeaning, CardKind.Check -> {
                val wrong = distractors(word, pool, seed) { it.gloss }
                build(word, kind, prompt = word.term, support = word.examples.firstOrNull(), word.gloss, wrong, seed)
            }
            CardKind.PickWord, CardKind.Recall -> {
                val wrong = distractors(word, pool, seed) { it.term }
                build(word, kind, prompt = word.gloss, support = word.definition, word.term, wrong, seed)
            }
            CardKind.Cloze -> {
                val sentence = word.examples.firstOrNull() ?: return question(word, CardKind.PickWord, pool, day)
                val wrong = distractors(word, pool, seed) { it.term }
                build(word, kind, prompt = blank(sentence), support = null, word.term, wrong, seed)
            }
            CardKind.Meet -> null
        }
    }

    /** "She gave an *adroit* answer" → "She gave an _____ answer". */
    fun blank(sentence: String): String = sentence.replace(Regex("\\*[^*]+\\*"), BLANK)

    private fun build(
        word: Word,
        kind: CardKind,
        prompt: String,
        support: String?,
        right: String,
        wrong: List<String>,
        seed: Long,
    ): Question {
        val options = (wrong + right).sortedBy { rank(seed, it) }
        return Question(word, kind, prompt, support, options, options.indexOf(right))
    }

    /**
     * Two options from other active words, preferring the same part of
     * speech, never repeating the right answer's text.
     */
    private fun distractors(word: Word, pool: List<Word>, seed: Long, label: (Word) -> String): List<String> {
        val right = label(word).lowercase()
        val others = pool.filter { it.id != word.id && it.shelvedReason == null && label(it).lowercase() != right }
        val samePos = others.filter { it.pos == word.pos }.sortedBy { rank(seed, it.id) }
        val rest = others.filter { it.pos != word.pos }.sortedBy { rank(seed, it.id) }
        return (samePos + rest).map(label).distinctBy { it.lowercase() }.take(OPTION_COUNT - 1)
    }

    private fun seed(wordId: String, day: Long): Long = wordId.hashCode().toLong() * 31 + day

    /** A stable pseudo-random rank for [key] under [seed]. */
    private fun rank(seed: Long, key: String): Int = Random(seed * 1_000_003 + key.hashCode()).nextInt()
}
