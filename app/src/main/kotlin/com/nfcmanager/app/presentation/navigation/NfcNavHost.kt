package com.nfcmanager.app.presentation.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.nfcmanager.app.presentation.actions.ActionsScreen
import com.nfcmanager.app.presentation.actions.CreateActionScreen
import com.nfcmanager.app.presentation.home.HomeScreen
import com.nfcmanager.app.presentation.settings.SettingsScreen
import com.nfcmanager.app.presentation.theme.LocalAppColors
import com.nfcmanager.app.presentation.write.WriteScreen

/**
 * Premium physics-based navigation transitions.
 * Optimized for responsiveness with increased stiffness to prevent "stuck" feelings during gestures.
 */
private val PremiumSpringOffset = spring<IntOffset>(
    dampingRatio = 0.8f,
    stiffness = 500f,    // Increased stiffness for immediate response
    visibilityThreshold = IntOffset(1, 1),
)

@Composable
fun NfcNavHost(
    navController: NavHostController,
    padding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = TopDestination.Home.route,
        modifier = modifier.fillMaxSize(),
        enterTransition = {
            val direction = getTransitionDirection(initialState.destination.route, targetState.destination.route, isPop = false)
            slideInHorizontally(PremiumSpringOffset) { width -> width * direction }
        },
        exitTransition = {
            val direction = getTransitionDirection(initialState.destination.route, targetState.destination.route, isPop = false)
            // Removed parallax (0.6x) to ensure smooth, synchronous motion
            slideOutHorizontally(PremiumSpringOffset) { width -> -(width * direction) }
        },
        popEnterTransition = {
            val direction = getTransitionDirection(initialState.destination.route, targetState.destination.route, isPop = true)
            slideInHorizontally(PremiumSpringOffset) { width -> width * direction }
        },
        popExitTransition = {
            val direction = getTransitionDirection(initialState.destination.route, targetState.destination.route, isPop = true)
            slideOutHorizontally(PremiumSpringOffset) { width -> -(width * direction) }
        },
    ) {
        composable(TopDestination.Home.route) {
            ScreenContainer(padding) { HomeScreen() }
        }
        composable(TopDestination.Message.route) {
            ScreenContainer(padding) { WriteScreen() }
        }
        composable(TopDestination.Actions.route) {
            ScreenContainer(padding) {
                ActionsScreen(onCreate = { navController.navigate(ROUTE_CREATE_ACTION) })
            }
        }
        composable(ROUTE_CREATE_ACTION) {
            ScreenContainer(padding) {
                CreateActionScreen(
                    onDone = { navController.popBackStack() },
                )
            }
        }
        composable(TopDestination.Settings.route) {
            ScreenContainer(padding) { SettingsScreen() }
        }
    }
}

@Composable
private fun ScreenContainer(
    padding: PaddingValues,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalAppColors.current.background)
            .padding(padding)
    ) {
        content()
    }
}

/**
 * Calculates direction based on TopDestination order.
 * Returns 1 for forward (slide from right), -1 for backward (slide from left).
 */
private fun getTransitionDirection(initialRoute: String?, targetRoute: String?, isPop: Boolean): Int {
    val initial = TopDestination.fromNavRoute(initialRoute) ?: return 1
    val target = TopDestination.fromNavRoute(targetRoute) ?: return 1
    
    return when {
        target.ordinal > initial.ordinal -> 1
        target.ordinal < initial.ordinal -> -1
        else -> if (isPop) -1 else 1
    }
}
