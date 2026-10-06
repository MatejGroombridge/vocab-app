package dev.matejgroombridge.voquab.data.model

import kotlinx.serialization.Serializable

/**
 * The kind of card a word gets, decided when the day's set is built (so a
 * card never changes shape mid-day). Persisted by name — append only.
 */
@Serializable
enum class CardKind {
    /** First exposure: read the word, its meaning and origin. No quiz. */
    Meet,
    /** Word in its sentence → pick the meaning (stage 1). */
    PickMeaning,
    /** Short meaning → pick the word (stage 2). */
    PickWord,
    /** Sentence with a gap → pick the word (stage 3). */
    Cloze,
    /** Definition → think of the word → reveal and self-grade (stages 4–5). */
    Recall,
    /** Occasional multiple-choice check on a Known word. */
    Check,
}

@Serializable
data class DailyCard(
    val wordId: String,
    val kind: CardKind,
    /** Null until answered; then whether it counted as a hit. Meet cards record true. */
    val correct: Boolean? = null,
    /** Added with "One more" after the day's set was done. */
    val bonus: Boolean = false,
) {
    val answered: Boolean get() = correct != null
}

/**
 * Today's cards. Built once per day (4am rollover) and then only ticked
 * off, so the app, notifications and widgets all agree on what's left.
 */
@Serializable
data class DailyState(
    val day: Long = Long.MIN_VALUE,
    val cards: List<DailyCard> = emptyList(),
) {
    val next: DailyCard? get() = cards.firstOrNull { !it.answered }
    val answeredCount: Int get() = cards.count { it.answered }
    val done: Boolean get() = cards.isNotEmpty() && cards.all { it.answered }
}
