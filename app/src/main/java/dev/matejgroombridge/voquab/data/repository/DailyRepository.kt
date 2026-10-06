package dev.matejgroombridge.voquab.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.matejgroombridge.voquab.data.model.DailyState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dailyDataStore: DataStore<Preferences> by preferencesDataStore(name = "daily")

/** Today's card set, stored so the app, notifications and widgets share one view of it. */
class DailyRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val state: Flow<DailyState> = context.dailyDataStore.data.map { load(it[KEY_DAILY_JSON]) }

    /** Atomically replaces the state with `block(current)`; returns the new state. */
    suspend fun update(block: suspend (DailyState) -> DailyState): DailyState {
        var result = DailyState()
        context.dailyDataStore.edit { prefs ->
            result = block(load(prefs[KEY_DAILY_JSON]))
            prefs[KEY_DAILY_JSON] = json.encodeToString(DailyState.serializer(), result)
        }
        return result
    }

    private fun load(raw: String?): DailyState =
        if (raw.isNullOrBlank()) DailyState()
        else runCatching { json.decodeFromString(DailyState.serializer(), raw) }.getOrDefault(DailyState())

    private companion object {
        val KEY_DAILY_JSON = stringPreferencesKey("daily_json")
    }
}
