package dev.matejgroombridge.voquab.ui.screens

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import android.content.Intent
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.matejgroombridge.voquab.BuildConfig
import dev.matejgroombridge.voquab.R
import dev.matejgroombridge.voquab.data.settings.WeekStart
import dev.matejgroombridge.voquab.ui.SettingsViewModel
import dev.matejgroombridge.voquab.ui.components.hasNotificationPermission
import dev.matejgroombridge.voquab.ui.theme.ThemeMode
import dev.matejgroombridge.voquab.ui.util.rememberHaptics
import java.time.LocalTime

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
    val pending by viewModel.pendingWords.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val payload = viewModel.exportBackup()
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(payload.toByteArray()) }
            }.isSuccess
            snackbar.showSnackbar(if (ok) "Backup saved" else "Couldn't save the backup")
        }
    }
    var pendingRestore by remember { mutableStateOf<String?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val raw = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (raw == null) scope.launch { snackbar.showSnackbar("Couldn't read that file") } else pendingRestore = raw
    }
    pendingRestore?.let { raw ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore from backup?") },
            text = { Text("This replaces your current progress, weekly-word history and added words with the backup's.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    scope.launch {
                        val count = viewModel.restoreBackup(raw)
                        snackbar.showSnackbar(if (count != null) "Restored progress for $count words" else "That isn't a Voquab backup")
                    }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } },
        )
    }

    val notifPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    } else null
    val askForNotifications = {
        if (notifPermission != null && !hasNotificationPermission(context)) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
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
            // Mode ----------------------------------------------------------
            SectionCaption("Mode")
            SettingsCard(contentPadding = 0.dp) {
                Column {
                    CompactSwitchRow(
                        label = "Passive mode",
                        checked = settings.passiveMode,
                        onCheckedChange = {
                            haptics.light()
                            viewModel.setPassiveMode(it)
                            if (it) askForNotifications()
                        },
                    )
                    if (settings.passiveMode) {
                        Divider()
                        Hint(
                            "No sessions in the app: your daily cards arrive as notifications, " +
                                "answered with one tap. Quizzes stay on while this is on.",
                        )
                    }
                }
            }

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

            // Notifications -------------------------------------------------
            SectionCaption("Notifications")
            SettingsCard(contentPadding = 0.dp) {
                Column {
                    CompactSwitchRow(
                        label = "Weekly word",
                        checked = settings.weekly.enabled,
                        onCheckedChange = { wantsOn ->
                            haptics.light()
                            viewModel.setWeeklyEnabled(wantsOn)
                            if (wantsOn) askForNotifications()
                        },
                    )
                    if (settings.weekly.enabled) {
                        Divider()
                        TimeRow(
                            label = "New word arrives at",
                            time = settings.weekly.announceTime,
                            onPick = {
                                haptics.light()
                                viewModel.setWeeklyAnnounceTime(it)
                            },
                        )
                        Divider()
                        CompactSwitchRow(
                            label = "Remind me if I haven't used it",
                            checked = settings.weekly.reminders,
                            onCheckedChange = {
                                haptics.light()
                                viewModel.setWeeklyReminders(it)
                            },
                        )
                    }
                    Divider()
                    val quizzesOn = settings.quizzes.enabled || settings.passiveMode
                    CompactSwitchRow(
                        label = "Daily quizzes",
                        checked = quizzesOn,
                        enabled = !settings.passiveMode,
                        onCheckedChange = { wantsOn ->
                            haptics.light()
                            viewModel.setQuizEnabled(wantsOn)
                            if (wantsOn) askForNotifications()
                        },
                    )
                    if (quizzesOn) {
                        Divider()
                        StepperRow(
                            label = "Quizzes per day",
                            value = settings.quizzes.timesPerDay,
                            min = 1, max = 6,
                            onChange = {
                                haptics.light()
                                viewModel.setQuizTimes(it)
                            },
                        )
                        Divider()
                        TimeRow(
                            label = if (settings.quizzes.timesPerDay == 1) "Quiz time" else "First quiz",
                            time = settings.quizzes.firstTime,
                            onPick = {
                                haptics.light()
                                viewModel.setQuizFirstTime(it)
                            },
                        )
                        if (settings.quizzes.timesPerDay > 1) {
                            Divider()
                            TimeRow(
                                label = "Last quiz",
                                time = settings.quizzes.lastTime,
                                onPick = {
                                    haptics.light()
                                    viewModel.setQuizLastTime(it)
                                },
                            )
                        }
                    }
                }
            }

            // Learning ------------------------------------------------------
            SectionCaption("Learning")
            SettingsCard(contentPadding = 0.dp) {
                Column {
                    StepperRow(
                        label = "Cards per day",
                        value = settings.learning.cardsPerDay,
                        min = 1, max = 10,
                        onChange = {
                            haptics.light()
                            viewModel.setCardsPerDay(it)
                        },
                    )
                    Divider()
                    StepperRow(
                        label = "New words per day",
                        value = settings.learning.newPerDay,
                        min = 0, max = 3,
                        onChange = {
                            haptics.light()
                            viewModel.setNewPerDay(it)
                        },
                    )
                    Divider()
                    Hint("Changes apply from tomorrow's cards.")
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
                    Divider()
                    NavRow(
                        label = "Back up progress",
                        onClick = {
                            haptics.light()
                            exportLauncher.launch("voquab-backup.json")
                        },
                    )
                    Divider()
                    NavRow(
                        label = "Restore from backup",
                        onClick = {
                            haptics.light()
                            importLauncher.launch(arrayOf("application/json", "text/plain"))
                        },
                    )
                    if (pending.isNotEmpty()) {
                        Divider()
                        NavRow(
                            label = "Words waiting (${pending.size})",
                            onClick = {
                                haptics.light()
                                sharePending(context, pending)
                                viewModel.clearPending()
                            },
                        )
                    }
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

/** Shares captured-but-undefined words as plain text, ready to paste into tools/words.txt. */
private fun sharePending(context: android.content.Context, words: List<String>) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Voquab words to add")
        putExtra(Intent.EXTRA_TEXT, words.joinToString(", "))
    }
    context.startActivity(Intent.createChooser(send, "Send words to add"))
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun StepperRow(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SETTINGS_ROW_MIN_HEIGHT)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { if (value > min) onChange(value - 1) }, enabled = value > min) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        IconButton(onClick = { if (value < max) onChange(value + 1) }, enabled = value < max) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun NavRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SETTINGS_ROW_MIN_HEIGHT)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
    enabled: Boolean = true,
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
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.5f),
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
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

/** Opens the system time picker; the trailing pill shows the current time. */
@Composable
private fun TimeRow(
    label: String,
    time: String,
    onPick: (String) -> Unit,
) {
    val context = LocalContext.current
    val parsed = runCatching { LocalTime.parse(time) }.getOrDefault(LocalTime.of(8, 0))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SETTINGS_ROW_MIN_HEIGHT)
            .clickable {
                TimePickerDialog(context, { _, h, m ->
                    onPick(LocalTime.of(h, m).toString())
                }, parsed.hour, parsed.minute, true).show()
            }
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                text = parsed.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
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
