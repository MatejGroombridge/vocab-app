package dev.matejgroombridge.voquab.data.settings

import dev.matejgroombridge.voquab.ui.theme.ThemeMode

/**
 * All user-configurable settings, exposed as a single immutable snapshot.
 * Adding a new setting? Add a property here, a `Preferences.Key` + a
 * mapping in [SettingsRepository], and a row in `SettingsScreen`.
 */
data class Settings(
    val themeMode: ThemeMode = ThemeMode.System,
    /** When [ThemeMode] resolves to dark, render with pure black backgrounds. */
    val amoled: Boolean = false,
    /**
     * Decides where each "week" begins — the weekly word rolls over on
     * this day and the streak is counted in these weeks.
     */
    val weekStart: WeekStart = WeekStart.Default,
    /**
     * When `true` the user can swipe horizontally between This Week / Today
     * / Library. When `false` the pager only responds to bottom-bar taps.
     */
    val swipeToNavigate: Boolean = true,
)
