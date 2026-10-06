package dev.matejgroombridge.voquab.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.matejgroombridge.voquab.data.model.CardKind
import dev.matejgroombridge.voquab.domain.MeetOutcome
import dev.matejgroombridge.voquab.domain.Question
import dev.matejgroombridge.voquab.tts.rememberSpeaker
import dev.matejgroombridge.voquab.ui.TodayUiState
import dev.matejgroombridge.voquab.ui.TodayViewModel
import dev.matejgroombridge.voquab.ui.components.AnswerFeedback
import dev.matejgroombridge.voquab.ui.components.MeetCard
import dev.matejgroombridge.voquab.ui.components.PageHeader
import dev.matejgroombridge.voquab.ui.components.QuestionCard
import dev.matejgroombridge.voquab.ui.components.RecallCard
import dev.matejgroombridge.voquab.ui.components.WeeklyPalette
import dev.matejgroombridge.voquab.ui.theme.containerColor
import dev.matejgroombridge.voquab.ui.theme.contentColor
import dev.matejgroombridge.voquab.ui.util.rememberHaptics
import kotlinx.coroutines.delay

/** A multiple-choice answer held on screen after the card itself has been ticked off. */
private data class Feedback(val question: Question, val selected: Int, val graduated: Boolean = false) {
    val correct: Boolean get() = selected == question.correctIndex
}

/**
 * The Today tab (PLAN.md §3.1): a small, fixed set of cards — usually five,
 * about a minute. Progress dots, never a due count. When the set is done
 * it says so and gets out of the way.
 */
@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    onOpenSettings: () -> Unit,
    onOpenWeek: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val speaker = rememberSpeaker()

    // The day can roll over (4am) while the app sits in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    var feedback by remember { mutableStateOf<Feedback?>(null) }
    var revealedFor by rememberSaveable { mutableStateOf<String?>(null) }
    var celebrate by remember { mutableStateOf<String?>(null) }

    // A right answer moves on by itself after a beat (longer if it just
    // graduated, so the sparkle is seen); a wrong one waits for Continue.
    LaunchedEffect(feedback) {
        val f = feedback ?: return@LaunchedEffect
        if (f.correct) {
            delay(if (f.graduated) 1_800 else 900)
            feedback = null
        }
    }
    LaunchedEffect(celebrate) {
        if (celebrate != null) {
            delay(2_000)
            celebrate = null
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        PageHeader(title = "Today", onOpenSettings = onOpenSettings)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp),
        ) {
            if (!state.loaded) return@Column
            if (state.cards.isNotEmpty()) {
                ProgressDots(total = state.cards.size, answered = state.answered)
                Spacer(Modifier.height(14.dp))
            }
            celebrate?.let { Celebration(it) }

            val f = feedback
            val current = state.current
            when {
                state.passive -> PassiveNotice(state)
                f != null -> {
                    QuestionCard(f.question, f.selected, onSpeak = { speaker.speak(f.question.word.term) }, onSelect = {})
                    AnswerFeedback(f.question, f.correct, f.graduated, onContinue = { feedback = null })
                }
                current != null -> {
                    val word = current.word
                    val question = current.question
                    when {
                        current.card.kind == CardKind.Meet -> MeetCard(
                            word = word,
                            onSpeak = { speaker.speak(word.term) },
                            onGotIt = {
                                haptics.completion()
                                viewModel.meet(word.id, MeetOutcome.GotIt)
                            },
                            onAlreadyKnew = {
                                haptics.light()
                                viewModel.meet(word.id, MeetOutcome.AlreadyKnew)
                            },
                            onNotWorthIt = {
                                haptics.light()
                                viewModel.meet(word.id, MeetOutcome.NotWorthLearning)
                            },
                        )
                        current.card.kind == CardKind.Recall -> RecallCard(
                            word = word,
                            revealed = revealedFor == word.id,
                            onSpeak = { speaker.speak(word.term) },
                            onReveal = {
                                haptics.light()
                                revealedFor = word.id
                            },
                            onGrade = { had ->
                                haptics.completion()
                                revealedFor = null
                                viewModel.answer(word.id, had) { if (it.graduated) celebrate = word.term }
                            },
                        )
                        question != null -> QuestionCard(
                            question = question,
                            selected = null,
                            onSpeak = { speaker.speak(word.term) },
                            onSelect = { index ->
                                haptics.completion()
                                feedback = Feedback(question, index)
                                viewModel.answer(word.id, index == question.correctIndex) { result ->
                                    if (result.graduated) feedback = feedback?.copy(graduated = true)
                                }
                            },
                        )
                    }
                }
                state.done -> DoneState(state, onOneMore = viewModel::oneMore, onOpenWeek = onOpenWeek)
                else -> NothingDue(onOneMore = viewModel::oneMore)
            }
        }
    }
}

@Composable
private fun ProgressDots(total: Int, answered: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { i ->
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        if (i < answered) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape,
                    ),
            )
        }
        Spacer(Modifier.size(4.dp))
        Text(
            text = "$answered of $total",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Celebration(term: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) {
        Text(
            text = "✨ Learned! $term is now Known",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun DoneState(state: TodayUiState, onOneMore: () -> Unit, onOpenWeek: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 32.dp)) {
        Text("That's today ✓", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "${state.cards.size} ${if (state.cards.size == 1) "card" else "cards"} · " +
                "${state.correct} right. See you tomorrow.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WeeklyReminder(state, onOpenWeek)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onOneMore) { Text("One more") }
    }
}

@Composable
private fun NothingDue(onOneMore: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 48.dp)) {
        Text(
            text = "Nothing due today.\nEnjoy the day.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onOneMore) { Text("One more anyway") }
    }
}

@Composable
private fun PassiveNotice(state: TodayUiState) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Passive mode", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (state.done) "All ${state.cards.size} of today's cards are done."
                else "Today's cards arrive as notifications. Answer them from the shade " +
                    "with one tap. ${state.answered} of ${state.cards.size} done so far.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A gentle nudge towards the weekly word once the cards are done. */
@Composable
private fun WeeklyReminder(state: TodayUiState, onOpenWeek: () -> Unit) {
    val word = state.weeklyWord ?: return
    val week = state.weeklyWeek ?: return
    Spacer(Modifier.height(24.dp))
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = WeeklyPalette.containerColor(),
        onClick = onOpenWeek,
        modifier = Modifier.fillMaxWidth(),
    ) {
        val content = WeeklyPalette.contentColor()
        Column(modifier = Modifier.padding(16.dp)) {
            Text("THIS WEEK'S WORD", style = MaterialTheme.typography.labelMedium, color = content.copy(alpha = 0.7f))
            Text(word.term, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = content)
            Text(
                text = if (week.used) "Used ✓" else "Not used yet. Try: “${word.openers.firstOrNull() ?: word.gloss}”",
                style = MaterialTheme.typography.bodyMedium,
                color = content.copy(alpha = 0.8f),
            )
        }
    }
}
