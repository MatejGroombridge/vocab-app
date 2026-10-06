package dev.matejgroombridge.voquab.domain

import dev.matejgroombridge.voquab.data.model.WordStatus
import kotlin.random.Random

/** The few facts about a word the weekly picker needs. */
data class PickCandidate(
    val id: String,
    val conversational: Int,
    val usefulness: Int,
    val status: WordStatus,
)

/**
 * Chooses the weekly word (PLAN.md §3.2):
 *  1. Only words easy enough to say out loud (conversational ≥ 2), never
 *     shelved, never a previous weekly word.
 *  2. Prefer words already being learned, then new ones, then known ones —
 *     the weekly word should reinforce what the daily cards are teaching.
 *  3. Within that, the most useful words first, picked at random among
 *     equals so the order doesn't feel alphabetical.
 *
 * [seed] makes the choice repeatable for a given week (and different for
 * a swap), which keeps tests deterministic.
 */
object WeeklyPicker {

    private const val MIN_CONVERSATIONAL = 2

    fun pick(candidates: List<PickCandidate>, exclude: Set<String>, seed: Long): String? {
        val open = candidates.filter { it.status != WordStatus.Shelved && it.id !in exclude }
        // Running out of conversational words takes years; fall back to any
        // remaining word rather than leaving a week empty.
        val pool = open.filter { it.conversational >= MIN_CONVERSATIONAL }.ifEmpty { open }
        if (pool.isEmpty()) return null

        val bestTier = pool.minOf { tier(it.status) }
        val tiered = pool.filter { tier(it.status) == bestTier }
        val topScore = tiered.maxOf { score(it) }
        val top = tiered.filter { score(it) == topScore }.sortedBy { it.id }
        return top[Random(seed).nextInt(top.size)].id
    }

    private fun tier(status: WordStatus): Int = when (status) {
        WordStatus.Learning -> 0
        WordStatus.New -> 1
        WordStatus.Known -> 2
        WordStatus.Shelved -> 3
    }

    private fun score(c: PickCandidate): Int = c.usefulness * 2 + c.conversational
}
