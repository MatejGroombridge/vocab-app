package dev.matejgroombridge.voquab.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.matejgroombridge.voquab.data.model.WordEntry
import dev.matejgroombridge.voquab.data.model.WordStatus
import dev.matejgroombridge.voquab.tts.rememberSpeaker
import dev.matejgroombridge.voquab.ui.LibraryUiState
import dev.matejgroombridge.voquab.ui.LibraryViewModel
import dev.matejgroombridge.voquab.ui.components.ChoiceChip
import dev.matejgroombridge.voquab.ui.components.EmptyState
import dev.matejgroombridge.voquab.ui.components.PageHeader
import dev.matejgroombridge.voquab.ui.components.WordCard
import dev.matejgroombridge.voquab.ui.components.WordDetailDialog
import dev.matejgroombridge.voquab.ui.util.rememberHaptics

/**
 * Filters shown above the grid. "All" means every word still in play —
 * shelved words only appear under their own filter so they don't clutter
 * the main list.
 */
private enum class LibraryFilter(val label: String, val status: WordStatus?) {
    All("All", null),
    New("New", WordStatus.New),
    Learning("Learning", WordStatus.Learning),
    Known("Known", WordStatus.Known),
    Shelved("Shelved", WordStatus.Shelved),
    ;

    fun matches(entry: WordEntry): Boolean =
        if (status == null) entry.status != WordStatus.Shelved else entry.status == status
}

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenSettings: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val speaker = rememberSpeaker()

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(LibraryFilter.All) }
    // Holds only the id; the dialog re-reads the live entry each frame so
    // status changes and logged uses show immediately.
    var openWordId by rememberSaveable { mutableStateOf<String?>(null) }

    val visible = remember(state.entries, query, filter) {
        val q = query.trim().lowercase()
        state.entries.filter { entry ->
            filter.matches(entry) && (q.isEmpty() || entry.matchesQuery(q))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding()),
    ) {
        PageHeader(title = "Library", onOpenSettings = onOpenSettings)

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search words or meanings") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LibraryFilter.entries.forEach { f ->
                ChoiceChip(
                    label = f.label,
                    selected = filter == f,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptics.light()
                        filter = f
                    },
                )
            }
        }

        when {
            !state.loaded -> Unit
            visible.isEmpty() -> EmptyState(
                text = if (query.isNotBlank()) "No words match “${query.trim()}”."
                else emptyMessage(filter),
                modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 12.dp,
                    bottom = contentPadding.calculateBottomPadding() + 24.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = summary(state, filter, visible.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                items(items = visible, key = { it.word.id }) { entry ->
                    WordCard(entry = entry, onOpen = { openWordId = entry.word.id })
                }
            }
        }
    }

    val open = openWordId?.let { id -> state.entries.firstOrNull { it.word.id == id } }
    if (open != null) {
        WordDetailDialog(
            entry = open,
            todayEpochDay = state.todayEpochDay,
            onDismiss = { openWordId = null },
            onSpeak = { speaker.speak(open.word.term) },
            onToggleUsedToday = { viewModel.toggleUsedToday(open.word.id) },
            onSetStatus = { viewModel.setStatus(open.word.id, it) },
        )
    }
}

private fun WordEntry.matchesQuery(q: String): Boolean =
    word.term.lowercase().contains(q) ||
        word.gloss.lowercase().contains(q) ||
        word.forms.any { it.lowercase().contains(q) }

private fun summary(state: LibraryUiState, filter: LibraryFilter, shown: Int): String {
    if (filter != LibraryFilter.All) return "$shown ${if (shown == 1) "word" else "words"}"
    val learning = state.count(WordStatus.Learning)
    val known = state.count(WordStatus.Known)
    return "$shown words · $learning learning · $known known"
}

private fun emptyMessage(filter: LibraryFilter): String = when (filter) {
    LibraryFilter.All -> "No words yet."
    LibraryFilter.New -> "Every word has been started."
    LibraryFilter.Learning -> "Nothing in progress yet.\nWords move here once your daily cards begin."
    LibraryFilter.Known -> "No known words yet.\nUse one in conversation, or mark it known."
    LibraryFilter.Shelved -> "Nothing shelved."
}
