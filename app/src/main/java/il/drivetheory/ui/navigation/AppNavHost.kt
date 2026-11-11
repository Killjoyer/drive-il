package il.drivetheory.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import il.drivetheory.R
import il.drivetheory.ui.exam.ExamHomeScreen
import il.drivetheory.ui.exam.ExamResultScreen
import il.drivetheory.ui.exam.ExamSessionScreen
import il.drivetheory.ui.practice.PracticeHomeScreen
import il.drivetheory.ui.practice.PracticeSessionScreen
import il.drivetheory.ui.settings.AboutScreen
import il.drivetheory.ui.settings.CreditsScreen
import il.drivetheory.ui.settings.SettingsScreen
import il.drivetheory.ui.signs.SignsScreen
import kotlin.reflect.KClass

private enum class TopLevel(val route: Any, val routeClass: KClass<*>, val icon: ImageVector, val label: Int) {
    PRACTICE(PracticeHomeRoute, PracticeHomeRoute::class, Icons.Filled.Edit, R.string.tab_practice),
    EXAM(ExamHomeRoute, ExamHomeRoute::class, Icons.Filled.CheckCircle, R.string.tab_exam),
    SIGNS(SignsRoute, SignsRoute::class, Icons.Filled.Warning, R.string.tab_signs),
    SETTINGS(SettingsRoute, SettingsRoute::class, Icons.Filled.Settings, R.string.tab_settings),
}

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val showBottomBar = TopLevel.entries.any { tab -> destination?.hasRoute(tab.routeClass) == true }

    Scaffold(
        // Each screen draws its own top bar and handles the status bar inset itself.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevel.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = destination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = PracticeHomeRoute,
            modifier = Modifier
                .padding(bottom = innerPadding.calculateBottomPadding())
                .consumeWindowInsets(innerPadding),
        ) {
            composable<PracticeHomeRoute> {
                PracticeHomeScreen(onStart = { mode -> navController.navigate(PracticeSessionRoute(mode.encode())) })
            }
            composable<PracticeSessionRoute> {
                PracticeSessionScreen(onBack = navController::popBackStack)
            }
            composable<ExamHomeRoute> {
                ExamHomeScreen(
                    onStartExam = { navController.navigate(ExamSessionRoute) },
                    onOpenResult = { id -> navController.navigate(ExamResultRoute(id)) },
                )
            }
            composable<ExamSessionRoute> {
                ExamSessionScreen(
                    onFinished = { id ->
                        navController.navigate(ExamResultRoute(id)) {
                            popUpTo<ExamSessionRoute> { inclusive = true }
                        }
                    },
                    onAbandon = navController::popBackStack,
                )
            }
            composable<ExamResultRoute> {
                ExamResultScreen(
                    onBack = navController::popBackStack,
                    onNewExam = {
                        navController.navigate(ExamSessionRoute) {
                            popUpTo<ExamResultRoute> { inclusive = true }
                        }
                    },
                )
            }
            composable<SignsRoute> { SignsScreen(onOpenCredits = { navController.navigate(CreditsRoute) }) }
            composable<SettingsRoute> { SettingsScreen(onOpenAbout = { navController.navigate(AboutRoute) }) }
            composable<AboutRoute> {
                AboutScreen(onBack = navController::popBackStack, onOpenCredits = { navController.navigate(CreditsRoute) })
            }
            composable<CreditsRoute> { CreditsScreen(onBack = navController::popBackStack) }
        }
    }
}
