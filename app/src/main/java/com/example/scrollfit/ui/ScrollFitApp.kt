package com.example.scrollfit.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.scrollfit.R
import com.example.scrollfit.BuildConfig
import com.example.scrollfit.spike.SpikeEntry
import com.example.scrollfit.monitoring.MonitoringSetup
import com.example.scrollfit.core.exercise.Exercise
import com.example.scrollfit.core.exercise.ProductDefaults

private enum class Destination(val route: String, @param:StringRes val label: Int) {
    DASHBOARD("dashboard", R.string.dashboard),
    EXERCISES("exercises", R.string.exercises),
    HISTORY("history", R.string.history),
    SETTINGS("settings", R.string.settings),
}

@Composable
fun ScrollFitApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
            Scaffold(
                bottomBar = {
                    if (route != null && route != "welcome") {
                        NavigationBar {
                            Destination.entries.forEach { destination ->
                                NavigationBarItem(
                                    selected = route == destination.route,
                                    onClick = {
                                        navController.navigate(destination.route) {
                                            popUpTo(Destination.DASHBOARD.route) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Text(stringResource(destination.label)) },
                                    alwaysShowLabel = false,
                                )
                            }
                        }
                    }
                },
            ) { insets ->
                NavHost(navController, startDestination = "welcome", modifier = Modifier.padding(insets)) {
                    composable("welcome") {
                        Page(R.string.welcome) {
                            Text(stringResource(R.string.welcome_body))
                            Text(stringResource(R.string.bootstrap_notice))
                            Button(onClick = { navController.navigate("monitoring") }) {
                                Text(stringResource(R.string.monitor_title))
                            }
                            Button(onClick = {
                                navController.navigate(Destination.DASHBOARD.route) {
                                    popUpTo("welcome") { inclusive = true }
                                }
                            }) { Text(stringResource(R.string.enter_dashboard)) }
                        }
                    }
                    composable(Destination.DASHBOARD.route) {
                        Page(R.string.dashboard) {
                            Text(stringResource(R.string.bootstrap_notice))
                            Button(onClick = { navController.navigate("monitoring") }) {
                                Text(stringResource(R.string.monitor_title))
                            }
                            if (BuildConfig.DEBUG) {
                                Button(onClick = { navController.navigate("spike") }) {
                                    Text(stringResource(R.string.open_spike))
                                }
                            }
                            Button(onClick = { navController.navigate(Destination.EXERCISES.route) }) {
                                Text(stringResource(R.string.view_exercises))
                            }
                        }
                    }
                    composable("spike") { SpikeEntry() }
                    composable("monitoring") { MonitoringSetup() }
                    composable(Destination.EXERCISES.route) {
                        Page(R.string.exercises) {
                            Text(stringResource(R.string.exercise_catalog_notice))
                            Exercise.entries.forEach { exercise ->
                                val rule = ProductDefaults.rewardRules.getValue(exercise)
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(stringResource(exercise.nameResource()), style = MaterialTheme.typography.titleMedium)
                                        Text(stringResource(R.string.reward_proposal, rule.repetitions, rule.rewardSeconds / 60))
                                        Text(stringResource(exercise.guidanceResource()))
                                    }
                                }
                            }
                        }
                    }
                    composable(Destination.HISTORY.route) {
                        Page(R.string.history) { Text(stringResource(R.string.history_empty)) }
                    }
                    composable(Destination.SETTINGS.route) {
                        Page(R.string.settings) {
                            Button(onClick = { navController.navigate("monitoring") }) {
                                Text(stringResource(R.string.monitor_title))
                            }
                            Text(stringResource(R.string.defaults_notice))
                            Text(stringResource(R.string.default_allowance, ProductDefaults.BASE_ALLOWANCE_SECONDS / 60))
                            Text(stringResource(R.string.default_ceiling, ProductDefaults.COMBINED_CEILING_SECONDS / 60))
                            Text(stringResource(R.string.default_award_cap, ProductDefaults.DAILY_AWARD_CAP_SECONDS / 60))
                            Text(stringResource(R.string.default_session, ProductDefaults.SESSION_SECONDS / 60))
                            Text(stringResource(R.string.other_defaults))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Page(@StringRes title: Int, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PaddingValues(20.dp)),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineMedium)
        content()
    }
}

@StringRes
private fun Exercise.nameResource(): Int = when (this) {
    Exercise.PUSH_UP -> R.string.push_up
    Exercise.SQUAT -> R.string.squat
    Exercise.JUMPING_JACK -> R.string.jumping_jack
    Exercise.ALTERNATING_KNEE_RAISE -> R.string.knee_raise
}

@StringRes
private fun Exercise.guidanceResource(): Int = when (this) {
    Exercise.PUSH_UP -> R.string.push_up_guidance
    Exercise.SQUAT -> R.string.squat_guidance
    Exercise.JUMPING_JACK, Exercise.ALTERNATING_KNEE_RAISE -> R.string.front_guidance
}
