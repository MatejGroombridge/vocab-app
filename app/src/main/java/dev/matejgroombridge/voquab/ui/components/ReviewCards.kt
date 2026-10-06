package dev.matejgroombridge.voquab.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.matejgroombridge.voquab.data.model.CardKind
import dev.matejgroombridge.voquab.data.model.Word
import dev.matejgroombridge.voquab.domain.Question
import dev.matejgroombridge.voquab.ui.theme.Palette
import dev.matejgroombridge.voquab.ui.theme.PaletteEntry
import dev.matejgroombridge.voquab.ui.theme.containerColor
import dev.matejgroombridge.voquab.ui.theme.contentColor

/** Card colour by kind: new words in Sky, learning in Butter, checks on known words in Mint. */
fun CardKind.palette(): PaletteEntry = Palette.entry(
    when (this) {
        CardKind.Meet -> "sky"
        CardKind.Check -> "mint"
        else -> "butter"
    },
)

private val RightColor = Color(0xFF2E7D4F)
private val WrongColor = Color(0xFFB3261E)

/** Shared frame: rounded pastel card with a small caps label and optional 🔊. */
@Composable
private fun CardFrame(
    kind: CardKind,
    label: String,
    content: @Composable (Color) -> Unit,
) {
    val palette = kind.palette()
    val onColor = palette.contentColor()
    Surface(shape = RoundedCornerShape(24.dp), color = palette.containerColor(), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = onColor.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(8.dp))
            content(onColor)
        }
    }
}

@Composable
private fun TermWithSpeaker(word: Word, color: Color, onSpeak: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = word.term,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.weight(1f, fill = false),
        )
        IconButton(onClick = onSpeak) {
            Icon(
                Icons.AutoMirrored.Outlined.VolumeUp,
                contentDescription = "Pronounce ${word.term}",
                tint = color.copy(alpha = 0.75f),
            )
        }
    }
}

/** First exposure: just read it. No quiz, three honest choices. */
@Composable
fun MeetCard(
    word: Word,
    onSpeak: () -> Unit,
    onGotIt: () -> Unit,
    onAlreadyKnew: () -> Unit,
    onNotWorthIt: () -> Unit,
) = CardFrame(CardKind.Meet, "NEW WORD") { color ->
    TermWithSpeaker(word, color, onSpeak)
    Text(word.pos, style = MaterialTheme.typography.bodyMedium, color = color.copy(alpha = 0.7f))
    Spacer(Modifier.height(10.dp))
    Text(word.definition, style = MaterialTheme.typography.bodyLarge, color = color)
    word.examples.firstOrNull()?.let {
        Spacer(Modifier.height(10.dp))
        Text(markedText(it), style = MaterialTheme.typography.bodyMedium, color = color)
    }
    word.hook?.let {
        Spacer(Modifier.height(10.dp))
        Text(it, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = color.copy(alpha = 0.75f))
    }
    Spacer(Modifier.height(18.dp))
    PrimaryButton("Got it", onGotIt)
    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = onAlreadyKnew) { Text("Already knew it", color = color.copy(alpha = 0.75f)) }
        TextButton(onClick = onNotWorthIt) { Text("Not worth learning", color = color.copy(alpha = 0.75f)) }
    }
}

/**
 * Multiple-choice card (pick the meaning, pick the word, fill the gap, or a
 * check on a known word). Once [selected] is set, the right option turns
 * green and a wrong pick red; the caller decides when to move on.
 */
@Composable
fun QuestionCard(
    question: Question,
    selected: Int?,
    onSpeak: () -> Unit,
    onSelect: (Int) -> Unit,
) = CardFrame(question.kind, labelFor(question.kind)) { color ->
    when (question.kind) {
        CardKind.PickMeaning, CardKind.Check -> {
            TermWithSpeaker(question.word, color, onSpeak)
            question.support?.let {
                Text(markedText(it), style = MaterialTheme.typography.bodyMedium, color = color.copy(alpha = 0.85f))
            }
        }
        CardKind.Cloze -> Text(question.prompt, style = MaterialTheme.typography.titleLarge, color = color)
        else -> {
            Text(question.prompt, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = color)
            question.support?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = color.copy(alpha = 0.8f))
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        question.options.forEachIndexed { index, option ->
            OptionButton(
                text = option,
                state = when {
                    selected == null -> OptionState.Idle
                    index == question.correctIndex -> OptionState.Right
                    index == selected -> OptionState.Wrong
                    else -> OptionState.Dimmed
                },
                color = color,
                onClick = { if (selected == null) onSelect(index) },
            )
        }
    }
}

/** Recall: see the meaning, think of the word, reveal, then say honestly whether you had it. */
@Composable
fun RecallCard(
    word: Word,
    revealed: Boolean,
    onSpeak: () -> Unit,
    onReveal: () -> Unit,
    onGrade: (Boolean) -> Unit,
) = CardFrame(CardKind.Recall, "WHAT'S THE WORD?") { color ->
    Text(word.definition, style = MaterialTheme.typography.titleLarge, color = color)
    Spacer(Modifier.height(4.dp))
    Text("Think of it, then check.", style = MaterialTheme.typography.bodySmall, color = color.copy(alpha = 0.7f))
    Spacer(Modifier.height(16.dp))
    if (!revealed) {
        PrimaryButton("Show the word", onReveal)
    } else {
        TermWithSpeaker(word, color, onSpeak)
        word.examples.lastOrNull()?.let {
            Text(markedText(it), style = MaterialTheme.typography.bodyMedium, color = color.copy(alpha = 0.85f))
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { onGrade(false) },
                colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.12f), contentColor = color),
                modifier = Modifier.weight(1f).height(52.dp),
            ) { Text("Didn't") }
            Button(
                onClick = { onGrade(true) },
                colors = ButtonDefaults.buttonColors(containerColor = Palette.entry("mint").accent, contentColor = Color(0xFF143222)),
                modifier = Modifier.weight(1f).height(52.dp),
            ) { Text("Had it") }
        }
    }
}

/** Shown under a multiple-choice card once answered. */
@Composable
fun AnswerFeedback(question: Question, correct: Boolean, graduated: Boolean, onContinue: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Text(
            text = if (correct) "✓ Right" else "Not quite: ${question.word.term} means “${question.word.gloss}”",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (correct) RightColor else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (!correct) {
            Text(
                text = "It'll come back tomorrow.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Delight moment #2 (agent.md §10.7.16): a word graduating to Known.
        AnimatedVisibility(
            visible = graduated,
            enter = fadeIn() + scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Palette.entry("mint").accent,
                modifier = Modifier.padding(top = 10.dp),
            ) {
                Text(
                    text = "✨ Learned! ${question.word.term} is now Known",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF143222),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        if (!correct) {
            Spacer(Modifier.height(10.dp))
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Continue") }
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

private enum class OptionState { Idle, Right, Wrong, Dimmed }

@Composable
private fun OptionButton(text: String, state: OptionState, color: Color, onClick: () -> Unit) {
    val border = when (state) {
        OptionState.Right -> BorderStroke(2.dp, RightColor)
        OptionState.Wrong -> BorderStroke(2.dp, WrongColor)
        else -> BorderStroke(1.dp, color.copy(alpha = 0.25f))
    }
    val background = when (state) {
        OptionState.Right -> RightColor.copy(alpha = 0.15f)
        OptionState.Wrong -> WrongColor.copy(alpha = 0.12f)
        else -> color.copy(alpha = 0.06f)
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = background,
        border = border,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = color.copy(alpha = if (state == OptionState.Dimmed) 0.5f else 1f),
                modifier = Modifier.weight(1f),
            )
            when (state) {
                OptionState.Right -> Text("✓", color = RightColor, fontWeight = FontWeight.Bold)
                OptionState.Wrong -> Text("✗", color = WrongColor, fontWeight = FontWeight.Bold)
                else -> Spacer(Modifier.size(0.dp))
            }
        }
    }
}

private fun labelFor(kind: CardKind): String = when (kind) {
    CardKind.PickMeaning -> "WHAT DOES IT MEAN?"
    CardKind.PickWord -> "WHICH WORD?"
    CardKind.Cloze -> "FILL THE GAP"
    CardKind.Check -> "QUICK CHECK"
    CardKind.Recall -> "WHAT'S THE WORD?"
    CardKind.Meet -> "NEW WORD"
}
