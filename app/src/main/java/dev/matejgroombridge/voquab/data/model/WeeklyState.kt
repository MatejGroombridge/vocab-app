package dev.matejgroombridge.voquab.data.model

import kotlinx.serialization.Serializable

/**
 * The whole weekly-word history. Weeks are stored back to back with no
 * gaps — when the app hasn't run for a while, the missing weeks are filled
 * in as missed (or paused) entries so the streak maths never has to reason
 * about holes. See [dev.matejgroombridge.voquab.domain.WeeklyEngine].
 */
@Serializable
data class WeeklyState(
    val weeks: List<WeekEntry> = emptyList(),
    /** Holiday mode: new weeks get no word, no reminders, and don't break the streak. */
    val paused: Boolean = false,
)

/**
 * One week of the weekly word. [start]..[end] are inclusive epoch days.
 * Weeks are usually 7 days, but the first one and any week straddling a
 * change of the week-start setting can be shorter or longer.
 */
@Serializable
data class WeekEntry(
    val start: Long,
    val end: Long,
    /** Null for weeks with no word: back-filled missed weeks and paused weeks. */
    val wordId: String? = null,
    /** The day a real-life use was logged (may be just after [end], within the grace period). */
    val usedOn: Long? = null,
    /** Optional "how did you use it?" note. */
    val note: String? = null,
    /** The word swapped away this week, if the free re-roll was used. */
    val swappedOut: String? = null,
    val paused: Boolean = false,
    /** Whether the week-start notification has gone out, so it's only sent once. */
    val announced: Boolean = false,
) {
    val used: Boolean get() = usedOn != null

    fun covers(day: Long): Boolean = day in start..end
}
