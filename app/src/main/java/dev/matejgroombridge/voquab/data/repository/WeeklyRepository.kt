package dev.matejgroombridge.voquab.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.matejgroombridge.voquab.data.model.WeeklyState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.weeklyDataStore: DataStore<Preferences> by preferencesDataStore(name = "weekly")

/**
 * Persists the weekly-word history as one JSON blob. Deliberately dumb:
 * every rule about weeks and streaks lives in
 * [dev.matejgroombridge.voquab.domain.WeeklyEngine], applied through
 * [update] so each change is a single atomic read-modify-write — the app,
 * notification buttons and alarms can all touch this without racing.
 */
class WeeklyRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val state: Flow<WeeklyState> = context.weeklyDataStore.data.map { load(it[KEY_WEEKLY_JSON]) }

    suspend fun snapshot(): WeeklyState = state.first()

    /** Atomically replaces the state with `block(current)`; returns the new state. */
    suspend fun update(block: (WeeklyState) -> WeeklyState): WeeklyState {
        var result = WeeklyState()
        context.weeklyDataStore.edit { prefs ->
            result = block(load(prefs[KEY_WEEKLY_JSON]))
            prefs[KEY_WEEKLY_JSON] = json.encodeToString(WeeklyState.serializer(), result)
        }
        return result
    }

    private fun load(raw: String?): WeeklyState {
        if (raw.isNullOrBlank()) return WeeklyState()
        return runCatching { json.decodeFromString(WeeklyState.serializer(), raw) }.getOrDefault(WeeklyState())
    }

    private companion object {
        val KEY_WEEKLY_JSON = stringPreferencesKey("weekly_json")
    }
}
