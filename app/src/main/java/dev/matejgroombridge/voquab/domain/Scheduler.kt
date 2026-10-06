package dev.matejgroombridge.voquab.domain

import dev.matejgroombridge.voquab.data.model.WordProgress
import dev.matejgroombridge.voquab.data.model.WordStatus
import kotlin.math.roundToInt
import kotlin.random.Random

/** What the user chose on a word's first ("meet") card. */
enum class MeetOutcome { GotIt, AlreadyKnew, NotWorthLearning }

/**
 * The spaced-repetition ladder (PLAN.md §2). A fixed Leitner-style ladder
 * rather than FSRS/SM-2: predictable, easy to test, and plenty for
 * recognising vocabulary.
 *
 * Intervals are counted from the day the card was actually answered, so a
 * week away just shifts everything along — no penalty, no pile-up.
 */
object Scheduler {

    /** Days until the next card after a correct answer at each stage (0 = meet). */
    val INTERVALS: List<Int> = listOf(1, 3, 7, 16, 35)

    /** A stage-5 hit graduates the word; Known words get an occasional check. */
    const val LAST_STAGE = 5
    const val KNOWN_INTERVAL = 120

    /** A Known word that's missed drops back to this stage (fill-in-the-blank). */
    const val RELEARN_STAGE = 3

    /** Words introduced together drift apart by up to ±15%. */
    private const val FUZZ = 0.15

    fun meet(progress: WordProgress, today: Long, outcome: MeetOutcome): WordProgress {
        val base = progress.copy(
            introducedOn = progress.introducedOn ?: today,
            reviews = progress.reviews + 1,
            lapses = 0,
        )
        return when (outcome) {
            MeetOutcome.GotIt -> base.copy(status = WordStatus.Learning, stage = 1, dueDay = today + INTERVALS[0])
            MeetOutcome.AlreadyKnew -> base.copy(status = WordStatus.Known, dueDay = today + KNOWN_INTERVAL)
            MeetOutcome.NotWorthLearning -> base.copy(status = WordStatus.Shelved, dueDay = null)
        }
    }

    /**
     * Applies a graded answer. A hit moves one stage up (or keeps a Known
     * word known); a miss drops one stage and comes back **tomorrow** — never
     * again today, which is how cramming loops start. Two misses in a row
     * send the word back to the meet card so the full explanation is seen
     * again.
     */
    fun review(progress: WordProgress, today: Long, correct: Boolean, seed: Long = 0): WordProgress {
        val reviewed = progress.copy(reviews = progress.reviews + 1)
        val known = progress.status == WordStatus.Known
        if (correct) {
            return when {
                known || progress.stage >= LAST_STAGE -> reviewed.copy(
                    status = WordStatus.Known,
                    lapses = 0,
                    dueDay = today + fuzz(KNOWN_INTERVAL, seed),
                )
                else -> reviewed.copy(
                    status = WordStatus.Learning,
                    stage = progress.stage + 1,
                    lapses = 0,
                    dueDay = today + fuzz(INTERVALS[progress.stage.coerceIn(0, INTERVALS.lastIndex)], seed),
                )
            }
        }
        if (known) {
            return reviewed.copy(status = WordStatus.Learning, stage = RELEARN_STAGE, lapses = 1, dueDay = today + 1)
        }
        val lapses = progress.lapses + 1
        return if (lapses >= 2) {
            reviewed.copy(status = WordStatus.Learning, stage = 0, lapses = 0, dueDay = today + 1)
        } else {
            reviewed.copy(
                status = WordStatus.Learning,
                stage = (progress.stage - 1).coerceAtLeast(1),
                lapses = lapses,
                dueDay = today + 1,
            )
        }
    }

    /** [days] ±15%, never less than one day. Deterministic for a given [seed]. */
    fun fuzz(days: Int, seed: Long): Int {
        if (days <= 1) return days
        val factor = 1 + (Random(seed).nextDouble() * 2 - 1) * FUZZ
        return (days * factor).roundToInt().coerceAtLeast(1)
    }
}
