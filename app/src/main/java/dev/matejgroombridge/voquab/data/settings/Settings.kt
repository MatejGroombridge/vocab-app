package dev.matejgroombridge.voquab.data.settings

import dev.matejgroombridge.voquab.ui.theme.ThemeMode

/**
 * All user-configurable settings, exposed as a single immutable snapshot.
 * Adding a new setting? Add a property here, a `Preferences.Key` + a
 * mapping in [SettingsRepository], and a row in `SettingsScreen`.
 */
data class Settings(
    /**
     * Passive mode: no in-app session — the day's cards arrive only as
     * notification quizzes (and the widget). For weeks when even opening
     * the app feels like effort. Forces quiz notifications on.
     */
    val passiveMode: Boolean = false,
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
    val weekly: WeeklyNotificationSettings = WeeklyNotificationSettings(),
    val learning: LearningSettings = LearningSettings(),
    val quizzes: QuizNotificationSettings = QuizNotificationSettings(),
)

/**
 * @param cardsPerDay  Hard cap on the day's set (PLAN.md §3.1), 1–10.
 * @param newPerDay    New words introduced per day, 0–3.
 */
data class LearningSettings(
    val cardsPerDay: Int = 5,
    val newPerDay: Int = 1,
)

/**
 * Notification quizzes: the day's cards as one-tap questions in the shade.
 *
 * @param timesPerDay  Quiz "sessions" per day, spread evenly between [firstTime]
 *                     and [lastTime]. Each starts with the next unanswered card
 *                     and offers "Next card" until the set is done.
 */
data class QuizNotificationSettings(
    val enabled: Boolean = true,
    val timesPerDay: Int = 3,
    val firstTime: String = "09:00",
    val lastTime: String = "20:00",
)

/**
 * Notifications for the weekly word. Local only — scheduled with
 * AlarmManager by `WeeklyAlarms`.
 *
 * @param enabled       Master switch for every weekly-word notification.
 * @param announceTime  "HH:MM" on the first day of the week when the new word is announced.
 *                      Also when last week's grace-period reminder goes out.
 * @param reminders     Mid-week nudge and last-day call, only sent while the word is unused.
 */
data class WeeklyNotificationSettings(
    val enabled: Boolean = true,
    val announceTime: String = "08:00",
    val reminders: Boolean = true,
)
