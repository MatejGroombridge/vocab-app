package dev.matejgroombridge.voquab.domain

import dev.matejgroombridge.voquab.data.settings.WeekStart
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Pure helpers for "what does the user's week look like?" math, parameterised
 * by their chosen [WeekStart]. Pure Kotlin so the weekly-word logic that
 * depends on it can be unit-tested on the JVM.
 */
object WeekMath {

    /**
     * Inclusive epoch-day range of the week containing [day], honouring the
     * configured week-start day. Returned as `Pair(start, end)`.
     */
    fun weekRange(day: Long, weekStart: WeekStart): Pair<Long, Long> {
        val date = LocalDate.ofEpochDay(day)
        val daysSinceStart = daysSinceWeekStart(date.dayOfWeek, weekStart.dayOfWeek)
        val start = day - daysSinceStart
        return start to (start + 6)
    }

    /**
     * How many days ago the most recent occurrence of [weekStart] was, given
     * that today is [today]. E.g. with `weekStart = Monday` and today =
     * Wednesday, returns 2.
     */
    private fun daysSinceWeekStart(today: DayOfWeek, weekStart: DayOfWeek): Int {
        val diff = today.value - weekStart.value
        return ((diff % 7) + 7) % 7
    }
}
