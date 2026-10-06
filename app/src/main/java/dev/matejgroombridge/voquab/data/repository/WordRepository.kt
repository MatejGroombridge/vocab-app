package dev.matejgroombridge.voquab.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.matejgroombridge.voquab.data.model.Word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.customWordsDataStore: DataStore<Preferences> by preferencesDataStore(name = "custom_words")

/** Words the user added from other apps (PLAN.md Phase 5), plus ones still waiting for a definition. */
@Serializable
data class CustomWords(
    val words: List<Word> = emptyList(),
    /** Captured words no definition could be found for — shared to be written up by hand. */
    val pending: List<String> = emptyList(),
)

/**
 * Every word the app knows: the dictionary bundled in `assets/words.json`
 * (parsed once per process — it can't change at runtime) merged with words
 * the user added themselves.
 */
class WordRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val custom: Flow<CustomWords> = context.customWordsDataStore.data.map { decode(it[KEY_CUSTOM_JSON]) }

    /** Bundled + custom words, updating when a word is added. */
    val all: Flow<List<Word>> = custom.map { bundled() + it.words }

    suspend fun words(): List<Word> = all.first()

    suspend fun addCustom(word: Word) = updateCustom { c ->
        c.copy(
            words = c.words.filterNot { it.id == word.id } + word,
            pending = c.pending.filterNot { it.equals(word.term, ignoreCase = true) },
        )
    }

    suspend fun addPending(term: String) = updateCustom { c ->
        if (c.pending.any { it.equals(term, ignoreCase = true) }) c else c.copy(pending = c.pending + term)
    }

    suspend fun clearPending() = updateCustom { it.copy(pending = emptyList()) }

    suspend fun replaceCustom(custom: CustomWords) = updateCustom { custom }

    private suspend fun updateCustom(block: (CustomWords) -> CustomWords) {
        context.customWordsDataStore.edit { prefs ->
            prefs[KEY_CUSTOM_JSON] = json.encodeToString(CustomWords.serializer(), block(decode(prefs[KEY_CUSTOM_JSON])))
        }
    }

    private fun decode(raw: String?): CustomWords =
        if (raw.isNullOrBlank()) CustomWords()
        else runCatching { json.decodeFromString(CustomWords.serializer(), raw) }.getOrDefault(CustomWords())

    private suspend fun bundled(): List<Word> = cache ?: lock.withLock {
        cache ?: load().also { cache = it }
    }

    private suspend fun load(): List<Word> = withContext(Dispatchers.IO) {
        val raw = context.applicationContext.assets.open(ASSET).bufferedReader().use { it.readText() }
        assetJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Word.serializer()), raw)
    }

    private companion object {
        const val ASSET = "words.json"
        val KEY_CUSTOM_JSON = stringPreferencesKey("custom_json")
        val assetJson = Json { ignoreUnknownKeys = true }
        val lock = Mutex()
        @Volatile var cache: List<Word>? = null
    }
}
