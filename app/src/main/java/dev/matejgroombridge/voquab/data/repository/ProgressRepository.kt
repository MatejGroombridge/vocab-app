package dev.matejgroombridge.voquab.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.matejgroombridge.voquab.data.model.WordProgress
import dev.matejgroombridge.voquab.data.model.WordStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.progressDataStore: DataStore<Preferences> by preferencesDataStore(name = "progress")

/**
 * The user's learning progress, as one JSON map of word id → [WordProgress]
 * under a single DataStore key — the same "one JSON blob" approach as the
 * Habit Tracker. Even a 1,000-word library stays well under 100 KB.
 */
class ProgressRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val mapSerializer = MapSerializer(String.serializer(), WordProgress.serializer())

    val progress: Flow<Map<String, WordProgress>> = context.progressDataStore.data.map { prefs ->
        load(prefs[KEY_PROGRESS_JSON])
    }

    suspend fun setStatus(wordId: String, status: WordStatus) {
        update(wordId) { it.copy(status = status) }
    }

    /**
     * Logs (or, if already logged that day, un-logs) a real-life use of the
     * word on [epochDay]. Using a word in conversation is the end goal, so a
     * logged use also marks it Known. Un-logging leaves the status alone —
     * the user can change it back explicitly if they want to.
     */
    suspend fun toggleUse(wordId: String, epochDay: Long) {
        update(wordId) { p ->
            if (epochDay in p.usedOnDays) p.copy(usedOnDays = p.usedOnDays - epochDay)
            else p.copy(usedOnDays = p.usedOnDays + epochDay, status = WordStatus.Known)
        }
    }

    /**
     * Records (or removes) a use on exactly [epochDay] — the explicit form
     * of [toggleUse] for callers like the weekly word that track their own
     * logged state. Recording a use marks the word Known.
     */
    suspend fun setUsed(wordId: String, epochDay: Long, used: Boolean) {
        update(wordId) { p ->
            if (used) p.copy(usedOnDays = p.usedOnDays + epochDay, status = WordStatus.Known)
            else p.copy(usedOnDays = p.usedOnDays - epochDay)
        }
    }

    /**
     * Applies [block] to [wordId]'s progress. Words without a stored entry
     * start from [WordProgress]'s defaults; callers that care about a
     * shipped-shelved word's starting status set it explicitly.
     */
    private suspend fun update(wordId: String, block: (WordProgress) -> WordProgress) {
        context.progressDataStore.edit { prefs ->
            val current = load(prefs[KEY_PROGRESS_JSON])
            val updated = current + (wordId to block(current[wordId] ?: WordProgress()))
            prefs[KEY_PROGRESS_JSON] = json.encodeToString(mapSerializer, updated)
        }
    }

    private fun load(raw: String?): Map<String, WordProgress> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching { json.decodeFromString(mapSerializer, raw) }.getOrDefault(emptyMap())
    }

    private companion object {
        val KEY_PROGRESS_JSON = stringPreferencesKey("progress_json")
    }
}
