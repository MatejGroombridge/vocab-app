package dev.matejgroombridge.voquab.data.model

import kotlinx.serialization.Serializable

/**
 * Where a word sits in the learning lifecycle (see PLAN.md §2). Persisted by
 * name — append new values, never reorder or rename.
 */
@Serializable
enum class WordStatus { New, Learning, Known, Shelved }

/**
 * Everything the user has done with one word. Words with no stored progress
 * are [WordStatus.New] (or [WordStatus.Shelved] if the word ships shelved),
 * so this only needs persisting once something actually happens.
 *
 * Every field has a default so older blobs keep decoding as new fields are
 * added (agent.md §10.7.17).
 */
@Serializable
data class WordProgress(
    val status: WordStatus = WordStatus.New,
    /** Epoch days on which the user used the word in a real conversation. */
    val usedOnDays: Set<Long> = emptySet(),
    /**
     * Learning stage, 0–5 (PLAN.md §2): 0 = meet (read the word), 1–3 =
     * multiple-choice cards of rising difficulty, 4–5 = recall. Only
     * meaningful while [status] is Learning.
     */
    val stage: Int = 0,
    /** Epoch day the next card for this word is due; null = not scheduled. */
    val dueDay: Long? = null,
    /** Consecutive misses — two in a row sends the word back to stage 0. */
    val lapses: Int = 0,
    val reviews: Int = 0,
    val introducedOn: Long? = null,
)

/** A word joined with its progress — what every screen actually renders. */
data class WordEntry(
    val word: Word,
    val progress: WordProgress?,
) {
    val status: WordStatus
        get() = progress?.status
            ?: if (word.shelvedReason != null) WordStatus.Shelved else WordStatus.New

    val stage: Int get() = progress?.stage ?: 0

    val timesUsed: Int get() = progress?.usedOnDays?.size ?: 0

    fun usedOn(epochDay: Long): Boolean = progress?.usedOnDays?.contains(epochDay) == true
}
