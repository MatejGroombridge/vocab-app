package dev.matejgroombridge.voquab.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.matejgroombridge.voquab.data.model.WeekEntry
import dev.matejgroombridge.voquab.data.model.Word
import dev.matejgroombridge.voquab.tts.rememberSpeaker
import dev.matejgroombridge.voquab.ui.PastWeek
import dev.matejgroombridge.voquab.ui.WeekUiState
import dev.matejgroombridge.voquab.ui.WeekViewModel
import dev.matejgroombridge.voquab.ui.components.ConfettiOverlay
import dev.matejgroombridge.voquab.ui.components.EmptyState
import dev.matejgroombridge.voquab.ui.components.NotificationPermissionCard
import dev.matejgroombridge.voquab.ui.components.PageHeader
import dev.matejgroombridge.voquab.ui.components.StatTile
import dev.matejgroombridge.voquab.ui.components.WeeklyPalette
import dev.matejgroombridge.voquab.ui.components.WeeklyWordCard
import dev.matejgroombridge.voquab.ui.util.rememberHaptics
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The This Week tab (PLAN.md §3.2): the weekly word with ready-made lines
 * to use it, a one-tap "I used it", the streak, and every past word.
 */
@Composable
fun WeekScreen(
    viewModel: WeekViewModel,
    notificationsEnabled: Boolean,
    onOpenSettings: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val speaker = rememberSpeaker()

    // The week can roll over while the app sits in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    // Notes are opt-in from the card or a past week, never a prompt after
    // logging: that would bury the confetti and add a step to a one-tap job.
    var noteFor by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmPause by rememberSaveable { mutableStateOf(false) }
    var fireConfetti by remember { mutableStateOf(false) }
    LaunchedEffect(fireConfetti) {
        // Reset after the burst starts so the next log can trigger another.
        if (fireConfetti) {
            delay(500)
            fireConfetti = false
        }
    }

    val logUse: (Long) -> Unit = { weekStart ->
        haptics.completion()
        viewModel.logUse(weekStart) { fireConfetti = true }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding()),
        ) {
            PageHeader(
                title = "This Week",
                onOpenSettings = onOpenSettings,
                actions = {
                    IconButton(onClick = {
                        haptics.light()
                        if (state.paused) viewModel.setPaused(false) else confirmPause = true
                    }) {
                        Icon(
                            imageVector = if (state.paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                            contentDescription = if (state.paused) "Resume weekly word" else "Pause weekly word",
                        )
                    }
                },
            )
            if (state.loaded) {
                WeekContent(
                    state = state,
                    notificationsEnabled = notificationsEnabled,
                    bottomPadding = contentPadding.calculateBottomPadding(),
                    onSpeak = { word -> speaker.speak(word.term) },
                    onLogUse = logUse,
                    onSwap = {
                        haptics.light()
                        viewModel.swap()
                    },
                    onEditNote = { noteFor = it },
                    onUndo = {
                        haptics.light()
                        viewModel.undoUse(it)
                    },
                    onResume = {
                        haptics.light()
                        viewModel.setPaused(false)
                    },
                )
            }
        }
        ConfettiOverlay(trigger = fireConfetti)
    }

    noteFor?.let { weekStart ->
        val existing = (listOfNotNull(state.current, state.grace) + state.history.map { it.week })
            .firstOrNull { it.start == weekStart }?.note.orEmpty()
        NoteDialog(
            initial = existing,
            onSave = {
                viewModel.saveNote(weekStart, it)
                noteFor = null
            },
            onDismiss = { noteFor = null },
        )
    }

    if (confirmPause) {
        AlertDialog(
            onDismissRequest = { confirmPause = false },
            title = { Text("Take a break?") },
            text = {
                Text(
                    "While paused there's no weekly word and no reminders. Your streak is frozen: " +
                        "it won't break, and it won't grow. Resume whenever you're ready.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setPaused(true)
                    confirmPause = false
                }) { Text("Pause") }
            },
            dismissButton = { TextButton(onClick = { confirmPause = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun WeekContent(
    state: WeekUiState,
    notificationsEnabled: Boolean,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onSpeak: (Word) -> Unit,
    onLogUse: (Long) -> Unit,
    onSwap: () -> Unit,
    onEditNote: (Long) -> Unit,
    onUndo: (Long) -> Unit,
    onResume: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = bottomPadding + 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (notificationsEnabled) NotificationPermissionCard()

        val grace = state.grace
        val graceWord = state.graceWord
        if (grace != null && graceWord != null) {
            GraceCard(word = graceWord, onLogUse = { onLogUse(grace.start) })
        }

        val current = state.current
        val word = state.currentWord
        when {
            // A week already used stays on screen when pausing — the break
            // only applies from the next week on.
            current != null && word != null && (current.used || !state.paused) -> {
                if (state.paused) PausedFromNextWeek(onResume = onResume)
                WeeklyWordCard(
                week = current,
                word = word,
                today = state.today,
                canLog = state.canLogCurrent,
                canSwap = state.canSwap,
                onSpeak = { onSpeak(word) },
                onLogUse = { onLogUse(current.start) },
                onSwap = onSwap,
                onEditNote = { onEditNote(current.start) },
                onUndo = { onUndo(current.start) },
                )
            }
            state.paused -> PausedCard(onResume = onResume)
            else -> EmptyState(
                text = "No weekly word right now.",
                modifier = Modifier.heightIn(max = 200.dp),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val accent = WeeklyPalette.accent
            StatTile("Week streak", state.stats.current.toString(), Icons.Outlined.LocalFireDepartment, accent, Modifier.weight(1f))
            StatTile("Best", state.stats.best.toString(), Icons.Outlined.EmojiEvents, accent, Modifier.weight(1f))
            StatTile("Words used", state.stats.totalUsed.toString(), Icons.Outlined.TaskAlt, accent, Modifier.weight(1f))
        }

        if (state.history.isNotEmpty()) {
            Text(
                text = "PAST WEEKS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 6.dp, top = 6.dp),
            )
            HistoryCard(history = state.history, today = state.today, graceStart = state.grace?.start, onEditNote = onEditNote)
        }
    }
}

@Composable
private fun GraceCard(word: Word, onLogUse: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = buildAnnotatedString {
                    append("Did you use ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(word.term) }
                    append(" last week?")
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "You can still log it until noon today, so your streak survives a forgotten tap.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(onClick = onLogUse) { Text("I used it") }
        }
    }
}

@Composable
private fun PausedCard(onResume: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "On a break",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "No weekly word and no reminders until you're back. Your streak is safe.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Button(onClick = onResume) { Text("Resume") }
        }
    }
}

@Composable
private fun PausedFromNextWeek(onResume: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Text(
                text = "On a break from next week",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onResume) { Text("Resume") }
        }
    }
}

@Composable
private fun HistoryCard(history: List<PastWeek>, today: Long, graceStart: Long?, onEditNote: (Long) -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            history.forEachIndexed { index, past ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                HistoryRow(
                    past = past,
                    today = today,
                    inGrace = past.week.start == graceStart,
                    onClick = if (past.week.used) ({ onEditNote(past.week.start) }) else null,
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(past: PastWeek, today: Long, inGrace: Boolean, onClick: (() -> Unit)?) {
    val week = past.week
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = past.word?.term ?: if (week.paused) "Paused" else "No word",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (past.word != null) FontWeight.Medium else FontWeight.Normal,
                    color = if (past.word != null) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = dateRange(week),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = if (inGrace) "Log by noon" else outcome(week, today),
                style = MaterialTheme.typography.labelLarge,
                color = if (week.used) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        week.note?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NoteDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How did you use it?") },
        text = {
            Column {
                Text(
                    text = "Optional. A line to look back on.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("e.g. told Sam his parking was adroit") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Skip") } },
    )
}

private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

private fun dateRange(week: WeekEntry): String {
    val start = LocalDate.ofEpochDay(week.start)
    val end = LocalDate.ofEpochDay(week.end)
    return if (start.month == end.month) "${start.dayOfMonth}–${end.format(DAY_MONTH)}"
    else "${start.format(DAY_MONTH)} – ${end.format(DAY_MONTH)}"
}

private fun outcome(week: WeekEntry, today: Long): String = when {
    week.used -> "Used ✓"
    week.paused -> "Paused"
    week.end >= today -> ""
    else -> "Missed"
}
