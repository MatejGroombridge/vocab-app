package dev.matejgroombridge.voquab.data.repository

import android.content.Context
import dev.matejgroombridge.voquab.data.model.Word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Read-only access to the dictionary bundled in `assets/words.json`. The
 * asset can't change while the app is running, so it's parsed once per
 * process and shared by every caller (UI, notifications, widgets).
 */
class WordRepository(private val context: Context) {

    suspend fun words(): List<Word> = cache ?: lock.withLock {
        cache ?: load().also { cache = it }
    }

    private suspend fun load(): List<Word> = withContext(Dispatchers.IO) {
        val raw = context.applicationContext.assets.open(ASSET).bufferedReader().use { it.readText() }
        json.decodeFromString(ListSerializer(Word.serializer()), raw)
    }

    private companion object {
        const val ASSET = "words.json"
        val json = Json { ignoreUnknownKeys = true }
        val lock = Mutex()
        @Volatile var cache: List<Word>? = null
    }
}
