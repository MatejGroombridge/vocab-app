package dev.matejgroombridge.voquab.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.matejgroombridge.voquab.data.model.WordEntry
import dev.matejgroombridge.voquab.data.model.WordStatus
import dev.matejgroombridge.voquab.ui.theme.containerColor
import dev.matejgroombridge.voquab.ui.theme.contentColor
import dev.matejgroombridge.voquab.ui.theme.paletteEntry
import dev.matejgroombridge.voquab.ui.util.rememberHaptics

/**
 * One word tile in the Library grid — the word and its short meaning on a
 * background coloured by learning status. Tap or long-press opens the
 * detail dialog; there's no in-place action, because unlike a habit there's
 * nothing to "tick" from the Library.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WordCard(
    entry: WordEntry,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val color = entry.paletteEntry()
    // Shelved words are pulled toward the page background so they read as
    // set aside rather than as another active state.
    val container = if (entry.status == WordStatus.Shelved) {
        blend(color.containerColor(), MaterialTheme.colorScheme.background, 0.5f)
    } else color.containerColor()
    val content = color.contentColor()

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = container,
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                role = Role.Button,
                onClick = {
                    haptics.light()
                    onOpen()
                },
                onLongClick = {
                    haptics.longPress()
                    onOpen()
                },
            ),
    ) {
        Column(
            modifier = Modifier
                .heightIn(min = 76.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        ) {
            Text(
                text = entry.word.term,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.word.gloss,
                style = MaterialTheme.typography.bodySmall,
                color = content.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Linear blend between [a] and [b] in straight RGB (good enough for pastels). */
internal fun blend(a: Color, b: Color, t: Float): Color {
    val u = t.coerceIn(0f, 1f)
    return Color(
        red = a.red * (1 - u) + b.red * u,
        green = a.green * (1 - u) + b.green * u,
        blue = a.blue * (1 - u) + b.blue * u,
        alpha = a.alpha * (1 - u) + b.alpha * u,
    )
}
