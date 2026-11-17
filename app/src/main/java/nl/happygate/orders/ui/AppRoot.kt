package nl.happygate.orders.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

private sealed class Tab(val route: String, val label: String, val icon: ImageVector) {
    data object New : Tab("new", "New", Icons.Filled.NotificationsActive)
    data object Active : Tab("active", "Active", Icons.Filled.Restaurant)
    data object History : Tab("history", "History", Icons.Filled.History)
    data object Settings : Tab("settings", "Settings", Icons.Filled.Settings)
}

private val tabs = listOf(Tab.New, Tab.Active, Tab.History, Tab.Settings)

@Composable
fun AppRoot(viewModel: OrdersViewModel, requestedOrderId: Int?, onRequestedOrderConsumed: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings = state.settings ?: return // still loading from disk

    if (!settings.isLoggedIn) {
        LoginScreen(viewModel)
        return
    }

    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }
    LaunchedEffect(requestedOrderId) {
        if (requestedOrderId != null) {
            nav.navigate("order/$requestedOrderId")
            onRequestedOrderConsumed()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            val backStack by nav.currentBackStackEntryAsState()
            val current = backStack?.destination
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = current?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            val count = when (tab) {
                                Tab.New -> state.pending.size
                                Tab.Active -> state.active.size
                                else -> 0
                            }
                            if (count > 0) {
                                BadgedBox(badge = { Badge { Text("$count") } }) { Icon(tab.icon, tab.label) }
                            } else {
                                Icon(tab.icon, tab.label)
                            }
                        },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Tab.New.route, modifier = Modifier.padding(padding)) {
            composable(Tab.New.route) { PendingOrdersScreen(viewModel, onOpen = { nav.navigate("order/${it.id}") }) }
            composable(Tab.Active.route) { ActiveOrdersScreen(viewModel, onOpen = { nav.navigate("order/${it.id}") }) }
            composable(Tab.History.route) { HistoryScreen(viewModel, onOpen = { nav.navigate("order/${it.id}") }) }
            composable(Tab.Settings.route) { SettingsScreen(viewModel) }
            composable("order/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.toIntOrNull() ?: return@composable
                OrderDetailScreen(viewModel, orderId = id, onBack = { nav.popBackStack() })
            }
        }
    }
}
