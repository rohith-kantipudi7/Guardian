package com.guardian.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.guardian.app.ui.screens.DashboardScreen
import com.guardian.app.ui.screens.DeviceScreen
import com.guardian.app.ui.screens.EmergencyScreen
import com.guardian.app.ui.screens.HistoryScreen
import com.guardian.app.ui.screens.SettingsScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("dashboard", "Home", Icons.Rounded.Home),
    Tab("device", "Device", Icons.Rounded.Router),
    Tab("history", "History", Icons.Rounded.History),
    Tab("settings", "Settings", Icons.Rounded.Settings),
)

private fun NavHostController.openTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun GuardianNavHost(vm: GuardianViewModel, onPair: () -> Unit) {
    val nav = rememberNavController()
    val alert by vm.alert.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    LaunchedEffect(alert != null) {
        if (alert != null && route != "emergency") nav.navigate("emergency") { launchSingleTop = true }
    }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (route != "emergency") {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { nav.openTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            NavHost(nav, startDestination = "dashboard") {
                composable("dashboard") { DashboardScreen(vm, onPair, onOpenHistory = { nav.openTab("history") }) }
                composable("device") { DeviceScreen(vm, onPair) }
                composable("history") { HistoryScreen(vm) }
                composable("settings") { SettingsScreen(vm, onPair) }
                composable("emergency") {
                    val a = alert
                    if (a == null) {
                        LaunchedEffect(Unit) { nav.popBackStack() }
                    } else {
                        EmergencyScreen(
                            alert = a,
                            onImSafe = { vm.imSafe() },
                            onDismiss = { vm.dismissAlert() },
                        )
                    }
                }
            }
        }
    }
}
