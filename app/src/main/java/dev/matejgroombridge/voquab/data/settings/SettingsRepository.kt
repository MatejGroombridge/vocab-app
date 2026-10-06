package dev.matejgroombridge.voquab.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
            themeMode = prefs[KEY_THEME_MODE]?.let(::parseThemeMode) ?: ThemeMode.System,
            amoled = prefs[KEY_AMOLED] ?: false,
            weekStart = prefs[KEY_WEEK_START]?.let(::parseWeekStart) ?: WeekStart.Default,
            swipeToNavigate = prefs[KEY_SWIPE_TO_NAVIGATE] ?: true,
            weekly = WeeklyNotificationSettings(
                enabled = prefs[KEY_WEEKLY_ENABLED] ?: true,
                announceTime = prefs[KEY_WEEKLY_ANNOUNCE_TIME] ?: "08:00",
                reminders = prefs[KEY_WEEKLY_REMINDERS] ?: true,
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
    }
}
