package com.researchradar.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import com.researchradar.core.design.RadarMotion
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.researchradar.feature.compare.CompareScreen
import com.researchradar.feature.graph.GraphScreen
import com.researchradar.feature.library.LibraryScreen
import com.researchradar.feature.map.MapScreen
import com.researchradar.feature.paper.PaperScreen
import com.researchradar.feature.search.SearchRoute
import com.researchradar.feature.settings.SettingsScreen

object RadarDestinations {
    const val SEARCH = "search"
    const val JOB = "job/{jobId}?topic={topic}"
    const val MAP = "map/{mapId}"
    const val PAPER = "paper/{paperId}"
    const val COMPARE = "compare/{mapId}"
    const val GRAPH = "graph/{mapId}"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"

    fun jobRoute(jobId: String, topic: String = "") = "job/$jobId?topic=$topic"
    fun mapRoute(mapId: String) = "map/$mapId"
    fun paperRoute(paperId: String) = "paper/$paperId"
    fun compareRoute(mapId: String) = "compare/$mapId"
    fun graphRoute(mapId: String) = "graph/$mapId"
}

@Composable
fun ResearchRadarNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = RadarDestinations.SEARCH,
) {
    val topLevel = setOf(RadarDestinations.SEARCH, RadarDestinations.LIBRARY, RadarDestinations.SETTINGS)
    fun isTopLevel(route: String?) = route in topLevel

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        // Detail screens slide in from the right over a slightly receding parent;
        // switching between top-level tabs is a quick cross-fade.
        enterTransition = {
            if (isTopLevel(targetState.destination.route) && isTopLevel(initialState.destination.route)) {
                fadeIn(tween(220))
            } else {
                slideInHorizontally(RadarMotion.snappy()) { it / 3 } + fadeIn(tween(260))
            }
        },
        exitTransition = {
            if (isTopLevel(targetState.destination.route) && isTopLevel(initialState.destination.route)) {
                fadeOut(tween(160))
            } else {
                slideOutHorizontally(RadarMotion.snappy()) { -it / 8 } + fadeOut(tween(200))
            }
        },
        popEnterTransition = {
            slideInHorizontally(RadarMotion.snappy()) { -it / 8 } + fadeIn(tween(260))
        },
        popExitTransition = {
            slideOutHorizontally(RadarMotion.snappy()) { it / 3 } + fadeOut(tween(200))
        },
    ) {
        composable(RadarDestinations.SEARCH) {
            SearchRoute(
                onNavigateToMap = { mapId ->
                    navController.navigate(RadarDestinations.mapRoute(mapId))
                },
                onNavigateToJob = { jobId, topic ->
                    navController.navigate(RadarDestinations.jobRoute(jobId, android.net.Uri.encode(topic)))
                },
            )
        }

        composable(
            route = RadarDestinations.JOB,
            arguments = listOf(
                navArgument("jobId") { type = NavType.StringType },
                navArgument("topic") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { backStackEntry ->
            val jobId = backStackEntry.arguments?.getString("jobId") ?: ""
            com.researchradar.feature.search.RunningJobRoute(
                jobId = jobId,
                onNavigateToMap = { mapId ->
                    navController.navigate(RadarDestinations.mapRoute(mapId)) {
                        popUpTo(RadarDestinations.SEARCH) { inclusive = false }
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }

        composable(
            route = RadarDestinations.MAP,
            arguments = listOf(navArgument("mapId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val mapId = backStackEntry.arguments?.getString("mapId") ?: ""
            MapScreen(
                mapId = mapId,
                onNavigateToPaper = { paperId ->
                    navController.navigate(RadarDestinations.paperRoute(paperId))
                },
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }

        composable(
            route = RadarDestinations.PAPER,
            arguments = listOf(navArgument("paperId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val paperId = backStackEntry.arguments?.getString("paperId") ?: ""
            PaperScreen(
                paperId = paperId,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = RadarDestinations.COMPARE,
            arguments = listOf(navArgument("mapId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val mapId = backStackEntry.arguments?.getString("mapId") ?: ""
            CompareScreen(
                mapId = mapId,
                onNavigateToPaper = { paperId ->
                    navController.navigate(RadarDestinations.paperRoute(paperId))
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = RadarDestinations.GRAPH,
            arguments = listOf(navArgument("mapId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val mapId = backStackEntry.arguments?.getString("mapId") ?: ""
            GraphScreen(mapId = mapId)
        }

        composable(RadarDestinations.LIBRARY) {
            LibraryScreen(
                onNavigateToMap = { mapId ->
                    navController.navigate(RadarDestinations.mapRoute(mapId))
                },
                onNavigateToPaper = { paperId ->
                    navController.navigate(RadarDestinations.paperRoute(paperId))
                },
                onBack = {
                    navController.navigate(RadarDestinations.SEARCH) {
                        popUpTo(RadarDestinations.SEARCH) { inclusive = false }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(RadarDestinations.SETTINGS) {
            SettingsScreen()
        }
    }
}
