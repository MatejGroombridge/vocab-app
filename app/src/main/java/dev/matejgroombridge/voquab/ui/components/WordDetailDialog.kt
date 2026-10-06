package dev.matejgroombridge.voquab.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.matejgroombridge.voquab.data.model.WordEntry
import dev.matejgroombridge.voquab.data.model.WordStatus
import dev.matejgroombridge.voquab.ui.theme.paletteEntry
import dev.matejgroombridge.voquab.ui.util.rememberHaptics

/**
 * Everything about one word, opened from a Library card. Reading comes
 * first — meaning, examples, origin — with the few actions kept to the
 * header icons (known / shelve) and one primary "I used this" button.
 * No close button: tap outside to dismiss (agent.md §10.7.20).
 *
 * The caller passes the *live* entry from state, so logging a use or
 * changing status updates the dialog in place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordDetailDialog(
    entry: WordEntry,
    todayEpochDay: Long,
    onDismiss: () -> Unit,
    onSpeak: () -> Unit,
    onToggleUsedToday: () -> Unit,
    onSetStatus: (WordStatus) -> Unit,
) {
    val word = entry.word
    val status = entry.status
    val accent = status.paletteEntry().accent
    val haptics = rememberHaptics()
    val usedToday = entry.usedOn(todayEpochDay)

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                // Header: word + pronunciation on the left, status actions
                // on the right — same shape as the habit overview header.
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = word.term,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = word.say,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            IconButton(onClick = onSpeak, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.VolumeUp,
                                    contentDescription = "Pronounce ${word.term}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                    HeaderActionIcon(
                        icon = Icons.Outlined.CheckCircle,
                        contentDescription = if (status == WordStatus.Known) "Mark as not known"
                        else "Mark as known",
                        active = status == WordStatus.Known,
                        accent = accent,
                        onClick = {
                            haptics.light()
                            onSetStatus(if (status == WordStatus.Known) WordStatus.New else WordStatus.Known)
                        },
                    )
                    HeaderActionIcon(
                        icon = if (status == WordStatus.Shelved) Icons.Outlined.Unarchive
                        else Icons.Outlined.Archive,
                        contentDescription = if (status == WordStatus.Shelved) "Unshelve" else "Shelve",
                        active = status == WordStatus.Shelved,
                        accent = accent,
                        onClick = {
                            haptics.light()
                            onSetStatus(if (status == WordStatus.Shelved) WordStatus.New else WordStatus.Shelved)
                        },
                    )
                }

                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip(text = word.pos, accent = MaterialTheme.colorScheme.outlineVariant)
                    Chip(
                        text = if (status == WordStatus.Shelved && word.shelvedReason != null)
                            "Shelved · ${word.shelvedReason.label}"
                        else status.name,
                        accent = accent,
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = word.definition,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                if (word.examples.isNotEmpty()) {
                    Section("Examples")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        word.examples.forEach { example ->
                            Row {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 3.dp)
                                        .width(3.dp)
                                        .height(16.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(accent),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = markedText(example),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }

                word.hook?.let { hook ->
                    Section("Origin")
                    Text(
                        text = hook,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (word.forms.isNotEmpty() || word.synonyms.isNotEmpty()) {
                    Section("Related")
                    if (word.forms.isNotEmpty()) {
                        Text(
                            text = word.forms.joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (word.synonyms.isNotEmpty()) {
                        Text(
                            text = "Similar: " + word.synonyms.joinToString(", "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                word.lookedUp?.let { original ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "You looked up “$original”",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(18.dp))
                FilledTonalButton(
                    onClick = {
                        haptics.completion()
                        onToggleUsedToday()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (usedToday) "Used today ✓  (tap to undo)" else "I used this today")
                }
                if (entry.timesUsed > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Used in conversation on ${entry.timesUsed} " +
                            if (entry.timesUsed == 1) "day" else "days",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(16.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Chip(text: String, accent: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.3f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Compact icon-only action for the dialog header. When [active] the icon
 * sits inside a tinted circular badge so the current state is visible at a
 * glance (same treatment as the Habit Tracker's skip / pause icons).
 */
@Composable
private fun HeaderActionIcon(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val tint = if (active) MaterialTheme.colorScheme.onSurface
    else MaterialTheme.colorScheme.onSurfaceVariant
    val bg = if (active) accent.copy(alpha = 0.45f) else Color.Transparent
    IconButton(onClick = onClick) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(bg, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = contentDescription, tint = tint)
        }
    }
}
