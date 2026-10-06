package dev.matejgroombridge.voquab.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.matejgroombridge.voquab.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Single source of truth for user preferences. Backed by a Preferences
 * DataStore — one [Preferences.Key] per setting, mapped into a [Settings]
 * snapshot for the UI to consume.
 */
class SettingsRepository(private val context: Context) {

    val settings: Flow<Settings> = context.settingsDataStore.data.map { prefs ->
        Settings(
            passiveMode = prefs[KEY_PASSIVE_MODE] ?: false,
            themeMode = prefs[KEY_THEME_MODE]?.let(::parseThemeMode) ?: ThemeMode.System,
            amoled = prefs[KEY_AMOLED] ?: false,
            weekStart = prefs[KEY_WEEK_START]?.let(::parseWeekStart) ?: WeekStart.Default,
            swipeToNavigate = prefs[KEY_SWIPE_TO_NAVIGATE] ?: true,
            weekly = WeeklyNotificationSettings(
                enabled = prefs[KEY_WEEKLY_ENABLED] ?: true,
                announceTime = prefs[KEY_WEEKLY_ANNOUNCE_TIME] ?: "08:00",
                reminders = prefs[KEY_WEEKLY_REMINDERS] ?: true,
            ),
            learning = LearningSettings(
                cardsPerDay = (prefs[KEY_CARDS_PER_DAY] ?: 5).coerceIn(1, 10),
                newPerDay = (prefs[KEY_NEW_PER_DAY] ?: 1).coerceIn(0, 3),
            ),
            quizzes = QuizNotificationSettings(
                enabled = prefs[KEY_QUIZ_ENABLED] ?: true,
                timesPerDay = (prefs[KEY_QUIZ_TIMES] ?: 3).coerceIn(1, 6),
                firstTime = prefs[KEY_QUIZ_FIRST] ?: "09:00",
                lastTime = prefs[KEY_QUIZ_LAST] ?: "20:00",
            ),
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setAmoled(amoled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_AMOLED] = amoled }
    }

    suspend fun setWeekStart(weekStart: WeekStart) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_WEEK_START] = weekStart.name }
    }

    suspend fun setSwipeToNavigate(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_SWIPE_TO_NAVIGATE] = enabled }
    }

    suspend fun setWeeklyEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_WEEKLY_ENABLED] = enabled }
    }

    suspend fun setWeeklyAnnounceTime(time: String) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_WEEKLY_ANNOUNCE_TIME] = time }
    }

    suspend fun setWeeklyReminders(enabled: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_WEEKLY_REMINDERS] = enabled }
    }

    suspend fun setPassiveMode(enabled: Boolean) = set(KEY_PASSIVE_MODE, enabled)
    suspend fun setCardsPerDay(n: Int) = set(KEY_CARDS_PER_DAY, n.coerceIn(1, 10))
    suspend fun setNewPerDay(n: Int) = set(KEY_NEW_PER_DAY, n.coerceIn(0, 3))
    suspend fun setQuizEnabled(enabled: Boolean) = set(KEY_QUIZ_ENABLED, enabled)
    suspend fun setQuizTimes(n: Int) = set(KEY_QUIZ_TIMES, n.coerceIn(1, 6))
    suspend fun setQuizFirstTime(time: String) = set(KEY_QUIZ_FIRST, time)
    suspend fun setQuizLastTime(time: String) = set(KEY_QUIZ_LAST, time)

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.settingsDataStore.edit { prefs -> prefs[key] = value }
    }

    private fun parseThemeMode(raw: String): ThemeMode = runCatching {
        ThemeMode.valueOf(raw)
    }.getOrDefault(ThemeMode.System)

    private fun parseWeekStart(raw: String): WeekStart = runCatching {
        WeekStart.valueOf(raw)
    }.getOrDefault(WeekStart.Default)

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_AMOLED = booleanPreferencesKey("amoled")
        val KEY_WEEK_START = stringPreferencesKey("week_start")
        val KEY_SWIPE_TO_NAVIGATE = booleanPreferencesKey("swipe_to_navigate")
        val KEY_WEEKLY_ENABLED = booleanPreferencesKey("weekly_enabled")
        val KEY_WEEKLY_ANNOUNCE_TIME = stringPreferencesKey("weekly_announce_time")
        val KEY_WEEKLY_REMINDERS = booleanPreferencesKey("weekly_reminders")
        val KEY_PASSIVE_MODE = booleanPreferencesKey("passive_mode")
        val KEY_CARDS_PER_DAY = intPreferencesKey("cards_per_day")
        val KEY_NEW_PER_DAY = intPreferencesKey("new_per_day")
        val KEY_QUIZ_ENABLED = booleanPreferencesKey("quiz_enabled")
        val KEY_QUIZ_TIMES = intPreferencesKey("quiz_times_per_day")
        val KEY_QUIZ_FIRST = stringPreferencesKey("quiz_first_time")
        val KEY_QUIZ_LAST = stringPreferencesKey("quiz_last_time")
    }
}
