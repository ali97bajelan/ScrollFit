package com.example.scrollfit.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.navigation.compose.*
import com.example.scrollfit.R
import com.example.scrollfit.BuildConfig
import com.example.scrollfit.ScrollFitApplication
import com.example.scrollfit.spike.SpikeEntry
import com.example.scrollfit.monitoring.MonitoringSetup
import com.example.scrollfit.challenge.ChallengeScreen
import com.example.scrollfit.core.data.AppPreferences
import com.example.scrollfit.core.data.ChallengeMode
import com.example.scrollfit.core.exercise.Exercise
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

private enum class Destination(val route: String, @param:StringRes val label: Int, val icon: Int) {
    DASHBOARD("dashboard", R.string.dashboard, R.drawable.ic_nav_dashboard),
    EXERCISES("exercises", R.string.exercises, R.drawable.ic_nav_exercises),
    HISTORY("history", R.string.history, R.drawable.ic_nav_history),
    SETTINGS("settings", R.string.settings, R.drawable.ic_nav_settings),
}

@Composable
fun ScrollFitApp() {
    val context = LocalContext.current
    val persistence = (context.applicationContext as ScrollFitApplication).persistence
    var initial by remember { mutableStateOf<AppPreferences?>(null) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableStateOf(0) }
    LaunchedEffect(persistence, retry) {
        failed = false
        try { initial = persistence.preferences.values.first() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val layoutDirection = if (LocalConfiguration.current.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        ScrollFitTheme {
            if (initial == null) {
                ProductPage(R.string.app_name) {
                    if (failed) {
                        Text(stringResource(R.string.product_read_error))
                        Button(onClick = { retry++ }) { Text(stringResource(R.string.camera_retry)) }
                    } else CircularProgressIndicator()
                }
                return@ScrollFitTheme
            }
            Scaffold(bottomBar = {
                if (Destination.entries.any { it.route == route }) NavigationBar {
                    Destination.entries.forEach { destination ->
                        NavigationBarItem(selected = route == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(Destination.DASHBOARD.route) { saveState = true }
                                    launchSingleTop = true; restoreState = true
                                }
                            }, icon = {
                                Icon(painterResource(destination.icon), contentDescription = null,
                                    modifier = Modifier.clearAndSetSemantics { })
                            }, label = { Text(stringResource(destination.label)) })
                    }
                }
            }) { insets ->
                NavHost(navController, startDestination = if (initial!!.onboardingCompleted) "dashboard" else "welcome",
                    modifier = Modifier.padding(insets)) {
                    composable("welcome") {
                        WelcomeScreen(onSetup = { navController.navigate("monitoring") }, onEnter = {
                            navController.navigate("dashboard") { popUpTo("welcome") { inclusive = true }; launchSingleTop = true }
                        })
                    }
                    composable("dashboard") {
                        Column {
                            if (BuildConfig.DEBUG) TextButton(onClick = { navController.navigate("spike") }) { Text(stringResource(R.string.open_spike)) }
                            DashboardScreen(onSetup = { navController.navigate("monitoring") }, onEarn = { target ->
                                navController.navigate(if (target == null) "challenge/advance" else "challenge/advance/${android.net.Uri.encode(target)}")
                            }, onOnly = { navController.navigate("challenge/only") })
                        }
                    }
                    composable("monitoring") {
                        Column(Modifier.fillMaxSize()) {
                            TextButton(onClick = { navController.popBackStack() }) { Text(stringResource(R.string.product_back)) }
                            Box(Modifier.weight(1f)) { MonitoringSetup() }
                        }
                    }
                    composable("spike") {
                        Column(Modifier.fillMaxSize()) {
                            TextButton(onClick = { navController.popBackStack() }) { Text(stringResource(R.string.product_back)) }
                            SpikeEntry()
                        }
                    }
                    composable("challenge/advance") { ChallengeScreen(ChallengeMode.ADVANCE, onClose = { navController.popBackStack() }) }
                    composable("challenge/advance/{target}") { entry ->
                        ChallengeScreen(ChallengeMode.ADVANCE, target = entry.arguments?.getString("target"), onClose = { navController.popBackStack() })
                    }
                    composable("challenge/only") { ChallengeScreen(ChallengeMode.EXERCISE_ONLY, onClose = { navController.popBackStack() }) }
                    composable("challenge/only/{exercise}") { entry ->
                        ChallengeScreen(ChallengeMode.EXERCISE_ONLY, initialExercise = Exercise.entries.firstOrNull {
                            it.name == entry.arguments?.getString("exercise")
                        }, onClose = { navController.popBackStack() })
                    }
                    composable("exercises") {
                        ExerciseCatalog(onExercise = { navController.navigate("challenge/only/${it.name}") },
                            onEarn = { navController.navigate("challenge/advance") })
                    }
                    composable("history") { HistoryScreen() }
                    composable("settings") { SettingsScreen(onSetup = { navController.navigate("monitoring") }, onWelcome = { navController.navigate("welcome") }) }
                }
            }
        }
    }
}
