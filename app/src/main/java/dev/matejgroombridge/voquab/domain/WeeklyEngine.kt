package dev.matejgroombridge.voquab.domain

import dev.matejgroombridge.voquab.data.model.WeekEntry
import dev.matejgroombridge.voquab.data.model.WeeklyState
import dev.matejgroombridge.voquab.data.settings.WeekStart
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class StreakStats(
    /** Consecutive used weeks ending now (paused weeks are skipped, not counted). */
    val current: Int = 0,
    val best: Int = 0,
    val totalUsed: Int = 0,
)

/**
 * Pure rules for the weekly word: when weeks roll over, when a use can still
 * be logged, and how the streak is counted. No Android, no I/O — the
 * repository and notification code call these, and the tests pin them down.
 *
 * `today` is always the app's day ([Day.today], which rolls over at 4am);
 * `now` is the real clock, used only for the noon grace deadline.
 */
object WeeklyEngine {

    /** A week's word can still be logged until this time on the day after it ends. */
    private val GRACE_UNTIL: LocalTime = LocalTime.NOON

    /** A first week shorter than this many remaining days is stretched to the following week's end. */
    private const val MIN_FIRST_WEEK_DAYS = 4

    /**
     * Brings [state] up to date so its last week covers [today]. Weeks the app
     * slept through are filled in without a word (missed, or paused if the
     * user is on a break); only the current week gets a word, chosen by
     * [pick] from words not excluded. [pick] gets the ids already used as
     * weekly words (or swapped away) so they're never repeated.
     */
    fun rollOver(
        state: WeeklyState,
        today: Long,
        weekStart: WeekStart,
        pick: (exclude: Set<String>) -> String?,
    ): WeeklyState {
        val weeks = state.weeks.toMutableList()

        if (weeks.isEmpty()) {
            // Start counting today rather than at the top of the week, so a
            // mid-week install doesn't open with a "missed" stub. If that
            // leaves too little time to use the word, run on to the end of
            // next week instead.
            val end = WeekMath.weekRange(today, weekStart).second
                .let { if (it - today + 1 < MIN_FIRST_WEEK_DAYS) it + 7 else it }
            weeks += newWeek(today, end, isCurrent = true, state.paused, weeks, pick)
        }

        while (weeks.last().end < today) {
            val start = weeks.last().end + 1
            // Ranging from `start` (not from today) keeps weeks contiguous even
            // if the week-start setting changed — that week is just ragged.
            val end = WeekMath.weekRange(start, weekStart).second
            weeks += newWeek(start, end, isCurrent = today in start..end, state.paused, weeks, pick)
        }

        return state.copy(weeks = weeks)
    }

    private fun newWeek(
        start: Long,
        end: Long,
        isCurrent: Boolean,
        paused: Boolean,
        existing: List<WeekEntry>,
        pick: (Set<String>) -> String?,
    ): WeekEntry = WeekEntry(
        start = start,
        end = end,
        wordId = if (isCurrent && !paused) pick(usedWordIds(existing)) else null,
        paused = paused,
    )

    /** Every word that has been a weekly word or was swapped away — never offered again. */
    fun usedWordIds(weeks: List<WeekEntry>): Set<String> =
        weeks.flatMap { listOfNotNull(it.wordId, it.swappedOut) }.toSet()

    fun current(state: WeeklyState, today: Long): WeekEntry? =
        state.weeks.lastOrNull()?.takeIf { it.covers(today) }

    /** True until noon on the day after [week] ends. */
    fun inGrace(week: WeekEntry, now: LocalDateTime): Boolean =
        now < LocalDate.ofEpochDay(week.end + 1).atTime(GRACE_UNTIL)

    /** Whether a use of [week]'s word can be logged right now. */
    fun canLog(week: WeekEntry, today: Long, now: LocalDateTime): Boolean =
        week.wordId != null && !week.used && !week.paused &&
            (week.covers(today) || (today > week.end && inGrace(week, now)))

    /**
     * Last week's entry, if its word wasn't logged and the grace period is
     * still open — the "did you use it last week?" prompt.
     */
    fun graceWeek(state: WeeklyState, today: Long, now: LocalDateTime): WeekEntry? =
        state.weeks.lastOrNull { it.end < today }?.takeIf { canLog(it, today, now) }

    fun canSwap(week: WeekEntry): Boolean =
        week.wordId != null && !week.used && !week.paused && week.swappedOut == null

    /**
     * Counts streaks over the whole history. A week extends the run if used,
     * is skipped if paused or still open (the current week, or last week
     * during its grace period), and otherwise breaks the run.
     */
    fun stats(weeks: List<WeekEntry>, today: Long, now: LocalDateTime): StreakStats {
        var run = 0
        var best = 0
        for (week in weeks) {
            when {
                week.used -> {
                    run++
                    best = maxOf(best, run)
                }
                week.paused -> Unit
                week.covers(today) || (week.end < today && canLog(week, today, now)) -> Unit
                else -> run = 0
            }
        }
        return StreakStats(current = run, best = best, totalUsed = weeks.count { it.used })
    }
}
