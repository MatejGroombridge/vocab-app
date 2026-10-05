package dev.matejgroombridge.voquab.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.matejgroombridge.voquab.BuildConfig
import dev.matejgroombridge.voquab.R
import dev.matejgroombridge.voquab.data.settings.WeekStart
import dev.matejgroombridge.voquab.ui.SettingsViewModel
import dev.matejgroombridge.voquab.ui.theme.ThemeMode
import dev.matejgroombridge.voquab.ui.util.rememberHaptics

/**
 * Settings is intentionally kept calm and uncluttered: a couple of grouped
 * tiles on a soft container background, with each section having its own
 * card and labelled with a small caption above. Adding a setting? Drop a new
 * row inside the relevant [SettingsCard], or add a new card entirely.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Appearance ----------------------------------------------------
            SectionCaption("Appearance")
            SettingsCard(contentPadding = 0.dp) {
                Column {
                    ThemePickerRow(
                        selected = settings.themeMode,
                        onChange = {
                            haptics.light()
                            viewModel.setThemeMode(it)
                        },
                    )
                    Divider()
                    CompactSwitchRow(
                        label = "AMOLED dark mode",
                        checked = settings.amoled,
                        onCheckedChange = {
                            haptics.light()
                            viewModel.setAmoled(it)
                        },
                    )
                }
            }

            // General -------------------------------------------------------
            SectionCaption("General")
            SettingsCard(contentPadding = 0.dp) {
                Column {
                    CompactSwitchRow(
                        label = "Swipe to navigate",
                        checked = settings.swipeToNavigate,
                        onCheckedChange = {
                            haptics.light()
                            viewModel.setSwipeToNavigate(it)
                        },
                    )
                    Divider()
                    WeekStartNavRow(
                        selected = settings.weekStart,
                        onChange = {
                            haptics.light()
                            viewModel.setWeekStart(it)
                        },
                    )
                }
            }

            // About ---------------------------------------------------------
            SectionCaption("About")
            SettingsCard(contentPadding = 0.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

// --- Building blocks -------------------------------------------------------

/**
 * Shared minimum height for every row in a SettingsCard. Picking a single
 * value (rather than letting padding drive the height) means a switch row
 * (whose Switch is ~32dp), a chevron nav row (whose text is ~24dp), and
 * the week-start row (text + small accent value) all line up exactly.
 */
private val SETTINGS_ROW_MIN_HEIGHT = 56.dp

@Composable
private fun SectionCaption(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun SettingsCard(
    contentPadding: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}

@Composable
private fun ThemePickerRow(
    selected: ThemeMode,
    onChange: (ThemeMode) -> Unit,
) {
    // Wrapped in identical horizontal/vertical padding to the rest of
    // the rows in zero-padded SettingsCards. Title text sits where a
    // row label would, the chip row underneath fills the rest.
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(
            text = "Theme",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeButton(
                label = "System", icon = Icons.Outlined.SettingsBrightness,
                selected = selected == ThemeMode.System, modifier = Modifier.weight(1f),
                onClick = { onChange(ThemeMode.System) },
            )
            ThemeButton(
                label = "Light", icon = Icons.Outlined.LightMode,
                selected = selected == ThemeMode.Light, modifier = Modifier.weight(1f),
                onClick = { onChange(ThemeMode.Light) },
            )
            ThemeButton(
                label = "Dark", icon = Icons.Outlined.DarkMode,
                selected = selected == ThemeMode.Dark, modifier = Modifier.weight(1f),
                onClick = { onChange(ThemeMode.Dark) },
            )
        }
    }
}

@Composable
private fun ThemeButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val border = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surface
    val onContainer = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurface

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = container,
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = border,
        ),
        onClick = onClick,
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 12.dp),
        ) {
            Icon(icon, null, tint = onContainer, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = onContainer,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/**
 * Compact switch row with no subtitle — used for self-explanatory toggles
 * like AMOLED dark mode, where the label alone is enough.
 */
@Composable
private fun CompactSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    // All rows in a unified card are pinned to the same minimum height
    // so toggles, nav rows, and the week-start row line up exactly.
    // Vertical padding is small (4dp) because the Switch + bodyLarge
    // already fill ~48dp; heightIn handles the rest.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SETTINGS_ROW_MIN_HEIGHT)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * NavRow-shaped two-state toggle for week start. Tap anywhere to flip
 * Monday ↔ Sunday; the trailing text shows the current selection so the
 * user doesn't need to drill into a separate sub-screen.
 */
@Composable
private fun WeekStartNavRow(selected: WeekStart, onChange: (WeekStart) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SETTINGS_ROW_MIN_HEIGHT)
            .clickable {
                onChange(if (selected == WeekStart.Monday) WeekStart.Sunday else WeekStart.Monday)
            }
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            text = "Week starts on",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (selected == WeekStart.Monday) "Mon" else "Sun",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(horizontal = 64.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    )
}
