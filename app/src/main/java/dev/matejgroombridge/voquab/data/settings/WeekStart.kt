package dev.matejgroombridge.voquab.data.settings

import java.time.DayOfWeek

/**
 * Day the user wants their week to start on. Affects when the weekly word
 * rolls over and how weeks are counted for the weekly-word streak.
 */
enum class WeekStart(val dayOfWeek: DayOfWeek) {
    Monday(DayOfWeek.MONDAY),
    Sunday(DayOfWeek.SUNDAY),
    ;

    companion object {
        val Default: WeekStart = Monday
    }
}
