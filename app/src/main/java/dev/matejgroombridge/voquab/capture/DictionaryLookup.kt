package dev.matejgroombridge.voquab.capture

import dev.matejgroombridge.voquab.data.model.Word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Looks a captured word up and turns the first sense into a [Word]: the
 * free Dictionary API (https://dictionaryapi.dev) first, then Wiktionary's
 * REST API as a fallback (the former is free but goes down now and then).
 * Good enough to start learning with; it won't have an origin hook or
 * conversation openers like the hand-written entries.
 */
object DictionaryLookup {

    private const val ENDPOINT = "https://api.dictionaryapi.dev/api/v2/entries/en/"
    private const val WIKTIONARY = "https://en.wiktionary.org/api/rest_v1/page/definition/"
    // Wikimedia asks API clients to identify themselves.
    private const val USER_AGENT = "Voquab/1 (personal vocabulary app)"
    private const val GLOSS_MAX = 18
    private const val TIMEOUT_MS = 8_000

    private val json = Json { ignoreUnknownKeys = true }

    /** Null when offline, not found anywhere, or the responses are unusable. */
    suspend fun lookup(term: String): Word? = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(term.trim().lowercase(), "UTF-8").replace("+", "%20")
        fetch(ENDPOINT + encoded)?.let { parse(term, it) }
            ?: fetch(WIKTIONARY + encoded)?.let { parseWiktionary(term, it) }
    }

    private fun fetch(url: String): String? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) null
            else conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    /** Wiktionary's `page/definition` response: definitions and examples are HTML snippets. */
    fun parseWiktionary(term: String, body: String): Word? = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        val sense = root["en"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        val definitionEntry = sense["definitions"]?.jsonArray
            ?.map { it.jsonObject }
            ?.firstOrNull { !stripHtml(it.string("definition").orEmpty()).isBlank() }
            ?: return null
        val definition = stripHtml(definitionEntry.string("definition")!!)
        val example = definitionEntry["examples"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
            ?.replace(Regex("</?b>"), "*")
            ?.let(::stripHtml)
            ?.let { if (it.contains('*')) it else markTerm(it, term) }
        Word(
            id = customId(term),
            term = term.trim(),
            pos = sense.string("partOfSpeech")?.lowercase() ?: "word",
            gloss = gloss(emptyList(), definition),
            definition = definition,
            examples = listOfNotNull(example),
            conversational = 1,
            usefulness = 3,
        )
    }.getOrNull()

    private fun stripHtml(html: String): String = html
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
        .replace(Regex("\\s+"), " ")
        .trim()

    fun parse(term: String, body: String): Word? {
        val entry = (json.parseToJsonElement(body) as? JsonArray)?.firstOrNull()?.jsonObject ?: return null
        val meaning = entry["meanings"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        val pos = meaning.string("partOfSpeech") ?: "word"
        val sense = meaning["definitions"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        val definition = sense.string("definition") ?: return null
        val synonyms = (sense.strings("synonyms") + meaning.strings("synonyms")).distinct().take(4)
        val example = sense.string("example")?.let { markTerm(it, term) }
        return Word(
            id = customId(term),
            term = term.trim(),
            pos = pos,
            gloss = gloss(synonyms, definition),
            definition = definition,
            examples = listOfNotNull(example),
            synonyms = synonyms,
            // Unknown conversational value: keep it out of the weekly-word
            // pool, but introduce it soon — the user just went to the effort.
            conversational = 1,
            usefulness = 3,
        )
    }

    fun customId(term: String): String =
        "custom-" + term.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    /**
     * A short meaning for buttons: a short synonym, else the definition's
     * first phrase ("Gloomy, mournful or dismal…" → "gloomy"), else the
     * definition trimmed at a word boundary.
     */
    private fun gloss(synonyms: List<String>, definition: String): String {
        synonyms.firstOrNull { it.length <= GLOSS_MAX }?.let { return it }
        definition.split(',', ';').first().trim().trimEnd('.')
            .takeIf { it.length in 3..GLOSS_MAX && it.length < definition.length - 1 }
            ?.let { return it.replaceFirstChar(Char::lowercase) }
        val words = definition.trimEnd('.').split(" ")
        val out = StringBuilder()
        for (w in words) {
            if (out.length + w.length + 1 > GLOSS_MAX - 1) break
            if (out.isNotEmpty()) out.append(' ')
            out.append(w)
        }
        return if (out.length < definition.length) "$out…" else out.toString()
    }

    /** Wraps the term (or a form starting with it) in *asterisks*, as the bundled examples are. */
    private fun markTerm(sentence: String, term: String): String {
        val match = Regex("\\b${Regex.escape(term.trim())}\\w*", RegexOption.IGNORE_CASE).find(sentence)
            ?: return "*${term.trim()}*: $sentence"
        return sentence.replaceRange(match.range, "*${match.value}*")
    }

    private fun JsonObject.string(key: String): String? = runCatching { this[key]?.jsonPrimitive?.content }.getOrNull()
    private fun JsonObject.strings(key: String): List<String> =
        runCatching { this[key]?.jsonArray?.map { it.jsonPrimitive.content } }.getOrNull().orEmpty()
}
