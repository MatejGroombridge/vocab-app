package dev.matejgroombridge.voquab

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.matejgroombridge.voquab.ui.LibraryViewModel
import dev.matejgroombridge.voquab.ui.SettingsViewModel
import dev.matejgroombridge.voquab.ui.screens.LibraryScreen
import dev.matejgroombridge.voquab.ui.screens.SettingsScreen
import dev.matejgroombridge.voquab.ui.screens.TodayScreen
import dev.matejgroombridge.voquab.ui.screens.WeekScreen
import dev.matejgroombridge.voquab.ui.theme.AppTheme
import dev.matejgroombridge.voquab.ui.util.rememberHaptics
import kotlinx.coroutines.launch

private object Routes {
    /** Single host route for the swipeable This Week / Today / Library pager. */
    const val MAIN = "main"
    const val SETTINGS = "settings"
}

private data class BottomTab(
    val label: String,
    val icon: ImageVector,
)

// Order is intentional: pager index 0 → This Week, 1 → Today, 2 → Library.
// Today sits in the middle so the user can swipe to it from either side; it's
// also the page the app launches on (see [TODAY_PAGE_INDEX] / initialPage).
// Adjust both this list AND the `when (page)` switch in MainPager() to add a tab.
private const val TODAY_PAGE_INDEX = 1
private val BOTTOM_TABS = listOf(
    BottomTab("This Week", Icons.Outlined.ChatBubbleOutline),
    BottomTab("Today", Icons.Outlined.CheckCircle),
    BottomTab("Library", Icons.Outlined.AutoStories),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.factory(application),
            )
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

            AppTheme(
                themeMode = settings.themeMode,
                amoled = settings.amoled,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppShell(settingsViewModel = settingsViewModel)
                }
            }
        }
    }
}

@Composable
private fun AppShell(settingsViewModel: SettingsViewModel) {
    val navController = rememberNavController()
    val app = LocalContext.current.applicationContext as Application

    val libraryViewModel: LibraryViewModel = viewModel(
        factory = LibraryViewModel.factory(app),
    )

    NavHost(
        navController = navController,
        startDestination = Routes.MAIN,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable(Routes.MAIN) {
            MainPager(
                settingsViewModel = settingsViewModel,
                libraryViewModel = libraryViewModel,
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

/**
 * Hosts the three top-level screens (This Week, Today, Library) inside a
 * [HorizontalPager], so the user can swipe between them. The bottom
 * NavigationBar mirrors the pager's selected index — tapping a tab animates
 * the pager, swiping the pager updates the highlighted tab.
 */
@Composable
private fun MainPager(
    settingsViewModel: SettingsViewModel,
    libraryViewModel: LibraryViewModel,
    onOpenSettings: () -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = TODAY_PAGE_INDEX,
        pageCount = { BOTTOM_TABS.size },
    )
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

    // Light buzz whenever the pager actually settles on a new page (whether
    // initiated by a swipe or a tab tap). We snapshot the previous page so
    // the initial composition (page == initialPage) doesn't fire a buzz.
    var lastPage by remember { mutableStateOf(pagerState.currentPage) }
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != lastPage) {
            haptics.light()
            lastPage = pagerState.currentPage
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                BOTTOM_TABS.forEachIndexed { index, tab ->
                    val selected = pagerState.currentPage == index
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                // The page-change LaunchedEffect above will
                                // emit the haptic once the pager settles —
                                // no need to duplicate here.
                                scope.launch { pagerState.animateScrollToPage(index) }
                            } else {
                                // Tapping the already-selected tab still
                                // gives a small confirmation tick.
                                haptics.light()
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // Keep adjacent pages composed so swiping is instant; at most
            // 3 pages exist at once which is fine for our screens.
            beyondViewportPageCount = 1,
            userScrollEnabled = settings.swipeToNavigate,
        ) { page ->
            when (page) {
                0 -> WeekScreen(onOpenSettings = onOpenSettings, contentPadding = padding)
                TODAY_PAGE_INDEX -> TodayScreen(onOpenSettings = onOpenSettings, contentPadding = padding)
                2 -> LibraryScreen(
                    viewModel = libraryViewModel,
                    onOpenSettings = onOpenSettings,
                    contentPadding = padding,
                )
            }
        }
    }
}
