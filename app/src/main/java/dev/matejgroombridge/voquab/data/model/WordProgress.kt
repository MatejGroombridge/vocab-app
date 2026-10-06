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
)

/** A word joined with its progress — what every screen actually renders. */
data class WordEntry(
    val word: Word,
    val progress: WordProgress?,
) {
    val status: WordStatus
        get() = progress?.status
            ?: if (word.shelvedReason != null) WordStatus.Shelved else WordStatus.New

    val timesUsed: Int get() = progress?.usedOnDays?.size ?: 0

    fun usedOn(epochDay: Long): Boolean = progress?.usedOnDays?.contains(epochDay) == true
}
