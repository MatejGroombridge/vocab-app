package dev.matejgroombridge.voquab.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.matejgroombridge.voquab.ui.components.EmptyState
import dev.matejgroombridge.voquab.ui.components.PageHeader

// Stand-ins for pages that later phases of PLAN.md replace with the real
// thing. Each keeps the shared header so navigation and Settings already
// behave like the finished app.

@Composable
fun TodayScreen(
    onOpenSettings: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) = Placeholder(
    title = "Today",
    message = "A few cards a day will show up here.",
    onOpenSettings = onOpenSettings,
    contentPadding = contentPadding,
)

@Composable
private fun Placeholder(
    title: String,
    message: String,
    onOpenSettings: () -> Unit,
    contentPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding()),
    ) {
        PageHeader(title = title, onOpenSettings = onOpenSettings)
        EmptyState(
            text = message,
            modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
        )
    }
}
