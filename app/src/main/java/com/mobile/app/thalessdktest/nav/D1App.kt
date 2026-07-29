package com.mobile.app.thalessdktest.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mobile.app.thalessdktest.di.D1Locator
import com.mobile.app.thalessdktest.ui.cards.CardsScreen
import com.mobile.app.thalessdktest.ui.controls.ControlsScreen
import com.mobile.app.thalessdktest.ui.pin.PinScreen
import com.mobile.app.thalessdktest.ui.services.ServicesScreen
import com.mobile.app.thalessdktest.ui.session.SessionScreen
import com.mobile.app.thalessdktest.ui.wallet.WalletScreen

private enum class Section(val route: String, val label: String) {
    Session("session", "Session"),
    Cards("cards", "Cards"),
    Wallet("wallet", "Wallet"),
    Controls("controls", "Controls"),
    Pin("pin", "PIN"),
    Services("services", "Services"),
}

@Composable
fun D1App() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val context = androidx.compose.ui.platform.LocalContext.current
    val client = D1Locator.client(context).getOrNull()

    var cardId by rememberSaveable { mutableStateOf(client?.config?.cardId.orEmpty()) }
    var last4 by rememberSaveable { mutableStateOf("") }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Section.entries.forEach { section ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any {
                            it.route == section.route
                        } == true,
                        onClick = {
                            navController.navigate(section.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Text(section.label.take(2)) },
                        label = { Text(section.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Section.Session.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Section.Session.route) { SessionScreen() }
            composable(Section.Cards.route) {
                CardsScreen(
                    client = client,
                    cardId = cardId,
                    onCardIdChange = { cardId = it },
                )
            }
            composable(Section.Wallet.route) {
                WalletScreen(
                    cardId = cardId,
                    last4 = last4,
                    onLast4Change = { last4 = it },
                )
            }
            composable(Section.Controls.route) { ControlsScreen(cardId = cardId) }
            composable(Section.Pin.route) { PinScreen(client = client, cardId = cardId) }
            composable(Section.Services.route) {
                ServicesScreen(client = client, cardId = cardId)
            }
        }
    }
}
