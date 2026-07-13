package com.retrovault.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.retrovault.core.ui.theme.PulsarBackgroundBrush
import com.retrovault.core.ui.theme.PulsarPrimary
import com.retrovault.core.ui.theme.PulsarStroke
import com.retrovault.core.ui.theme.PulsarTextFaint
import com.retrovault.feature.store.BootScreen
import com.retrovault.feature.store.ControlsScreen
import com.retrovault.feature.store.GameDetailScreen
import com.retrovault.feature.player.EmulatorActivity
import com.retrovault.feature.store.HomeScreen
import com.retrovault.feature.store.OnboardingScreen
import com.retrovault.feature.store.SavesScreen
import com.retrovault.feature.store.SettingsScreen

@Composable
fun RetroVaultRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showTabs = currentRoute in setOf(
        Destination.Library.route,
        Destination.Saves.route,
        Destination.Controls.route,
        Destination.Settings.route
    )

    androidx.compose.foundation.layout.Box(
        Modifier
            .fillMaxSize()
            .background(PulsarBackgroundBrush)
    ) {
        NavHost(
            navController = navController,
            startDestination = Destination.Boot.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Destination.Boot.route) {
                BootScreen(onFinished = {
                    val next = if (com.retrovault.core.ui.AppPrefs.onboardingSeen)
                        Destination.Library.route else Destination.Onboarding.route
                    navController.navigate(next) {
                        popUpTo(Destination.Boot.route) { inclusive = true }
                    }
                })
            }
            composable(Destination.Onboarding.route) {
                OnboardingScreen(onFinish = {
                    com.retrovault.core.ui.AppPrefs.setOnboardingSeen(true)
                    navController.navigate(Destination.Library.route) {
                        popUpTo(Destination.Onboarding.route) { inclusive = true }
                    }
                })
            }
            composable(Destination.Library.route) {
                val context = LocalContext.current
                HomeScreen(
                    onGameClick = { id -> navController.navigate(Destination.Detail.create(id)) },
                    onPlayLocal = { path, title, system, serial ->
                        context.startActivity(
                            EmulatorActivity.intent(context, serial, title, system, path, false)
                        )
                    },
                )
            }
            composable(Destination.Saves.route) { SavesScreen() }
            composable(Destination.Controls.route) { ControlsScreen() }
            composable(
                route = Destination.Settings.route,
                arguments = listOf(navArgument("gameKey") { nullable = true; defaultValue = null })
            ) { entry ->
                SettingsScreen(gameKey = entry.arguments?.getString("gameKey"))
            }
            composable(
                route = Destination.Detail.route,
                arguments = listOf(navArgument("gameId") { type = NavType.StringType })
            ) { entry ->
                val gameId = entry.arguments?.getString("gameId").orEmpty()
                val context = LocalContext.current
                GameDetailScreen(
                    gameId = gameId,
                    onBack = { navController.popBackStack() },
                    onPlay = { id, title, system, gamePath, resume ->
                        context.startActivity(
                            EmulatorActivity.intent(context, id, title, system, gamePath, resume)
                        )
                    },
                    onOpenSaves = { navController.navigate(Destination.Saves.route) },
                    onOpenControls = { navController.navigate(Destination.Controls.route) },
                    onOpenSettings = { gameKey ->
                        navController.navigate(Destination.Settings.create(gameKey))
                    },
                )
            }
        }

        if (showTabs) {
            PulsarBottomBar(
                currentRoute = currentRoute,
                onSelect = { dest ->
                    navController.navigate(dest.route) {
                        popUpTo(Destination.Library.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun PulsarBottomBar(
    currentRoute: String?,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Row(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 14.dp)
            .padding(bottom = 16.dp)
            .fillMaxWidth()
            .height(66.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xC70B0F18))
            .border(1.dp, PulsarStroke, RoundedCornerShape(24.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        topLevelDestinations.forEach { item ->
            val selected = currentRoute == item.destination.route
            val tint = if (selected) PulsarPrimary else PulsarTextFaint
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelect(item.destination) }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Icon(item.icon, contentDescription = item.label, tint = tint, modifier = Modifier.size(25.dp))
                Text(item.label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = tint)
            }
        }
    }
}
