package dev.matejgroombridge.voquab.domain

import java.time.LocalDateTime

/**
 * The app's notion of "today". The day rolls over at 4am rather than
 * midnight, so a late-night review or a use logged at 1am still counts for
 * the day the user is actually living in (PLAN.md §2). Every date-based
 * feature should ask this, never `LocalDate.now()` directly.
 */
object Day {

    private const val ROLLOVER_HOUR = 4L

    fun today(now: LocalDateTime = LocalDateTime.now()): Long =
        now.minusHours(ROLLOVER_HOUR).toLocalDate().toEpochDay()
}
