package dev.matejgroombridge.voquab.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One dictionary entry, as shipped in `assets/words.json`. This is content
 * only and never changes at runtime — everything the user does with a word
 * lives in [WordProgress], keyed by [id]. See `tools/README.md` for how each
 * field is written.
 */
@Serializable
data class Word(
    /** Stable key for progress. Never changes once shipped. */
    val id: String,
    val term: String,
    val pos: String,
    /** Two-or-three word meaning, short enough for a notification button. */
    val gloss: String,
    val definition: String,
    /**
     * One or two example sentences with the target word wrapped in
     * `*asterisks*`. Two only when they show a different sense, form or
     * situation; pronunciation comes from text-to-speech, not a respelling.
     */
    val examples: List<String> = emptyList(),
    /** One-line origin story / memory hook. */
    val hook: String? = null,
    val forms: List<String> = emptyList(),
    val synonyms: List<String> = emptyList(),
    /** 1 literary/technical … 3 easy to drop into conversation. */
    val conversational: Int = 1,
    /** Introduction order: 3 first, 1 last. */
    val usefulness: Int = 1,
    /** One or two ready-to-say lines for the weekly word, as different as the examples. */
    val openers: List<String> = emptyList(),
    /** The form originally looked up on the Kindle, when it differs from [term]. */
    val lookedUp: String? = null,
    /** Set when the word ships on the Shelved list, with the reason why. */
    @SerialName("shelved") val shelvedReason: ShelvedReason? = null,
)

@Serializable
enum class ShelvedReason(val label: String) {
    @SerialName("common") Common("Everyday word"),
    @SerialName("name") Name("Name"),
    @SerialName("offensive") Offensive("Offensive or dated"),
    @SerialName("archaic") Archaic("Archaic"),
    @SerialName("foreign") Foreign("Not English"),
    @SerialName("fragment") Fragment("Prefix"),
}
