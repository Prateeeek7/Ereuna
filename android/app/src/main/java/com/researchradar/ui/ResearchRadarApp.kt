package com.researchradar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.researchradar.core.design.RadarIcons
import com.researchradar.core.design.RadarMotion
import com.researchradar.core.design.RadarShape
import com.researchradar.core.design.RadarTheme
import com.researchradar.navigation.RadarDestinations
import com.researchradar.navigation.ResearchRadarNavHost

enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    SEARCH(RadarDestinations.SEARCH, "Search", RadarIcons.Radar),
    LIBRARY(RadarDestinations.LIBRARY, "Library", RadarIcons.Library),
    SETTINGS(RadarDestinations.SETTINGS, "Settings", RadarIcons.Sliders),
}

@Composable
fun ResearchRadarApp(
    navController: NavHostController = rememberNavController(),
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBar = currentRoute == null || TopLevelDestination.entries.any { it.route == currentRoute }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RadarTheme.colors.background),
    ) {
        ResearchRadarNavHost(
            navController = navController,
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            visible = showBar,
            enter = slideInVertically(RadarMotion.snappy()) { it } + fadeIn(),
            exit = slideOutVertically(RadarMotion.snappy()) { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            // Content scrolling under the bar fades into the background instead of
            // showing through around it and behind the gesture handle.
            val bg = RadarTheme.colors.background
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to bg.copy(alpha = 0f),
                            0.35f to bg.copy(alpha = 0.82f),
                            1f to bg,
                        ),
                    )
                    .padding(top = 28.dp),
            ) {
            RadarBottomBar(
                currentRoute = currentRoute,
                onNavigateToDestination = { destination ->
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
            }
        }
    }
}

@Composable
private fun RadarBottomBar(
    currentRoute: String?,
    onNavigateToDestination: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RadarTheme.colors
    val haptics = LocalHapticFeedback.current
    val selectedIndex = TopLevelDestination.entries.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)

    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 12.dp)
            .fillMaxWidth()
            .height(64.dp)
            .shadow(18.dp, RadarShape.pill, ambientColor = colors.ink.copy(alpha = 0.25f), spotColor = colors.ink.copy(alpha = 0.25f))
            .clip(RadarShape.pill)
            .background(colors.night)
            .border(1.dp, colors.nightRule, RadarShape.pill)
            .padding(6.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val itemWidth = maxWidth / TopLevelDestination.entries.size
            val indicatorOffset by animateDpAsState(
                targetValue = itemWidth * selectedIndex,
                animationSpec = RadarMotion.settle(),
                label = "navIndicator",
            )
            // Sliding highlight behind the selected item
            Box(
                Modifier
                    .offset(x = indicatorOffset)
                    .width(itemWidth)
                    .fillMaxHeight()
                    .clip(RadarShape.pill)
                    .background(colors.accent),
            )
            Row(Modifier.fillMaxSize()) {
                TopLevelDestination.entries.forEachIndexed { index, destination ->
                    val isSelected = index == selectedIndex
                    val interaction = remember { MutableInteractionSource() }
                    val fg by animateColorAsState(if (isSelected) colors.onAccent else colors.onNight.copy(alpha = 0.62f), label = "navFg")
                    val iconScale by animateFloatAsState(if (isSelected) 1.12f else 1f, RadarMotion.settle(), label = "navIcon")
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RadarShape.pill)
                            .clickable(interactionSource = interaction, indication = null, role = Role.Tab) {
                                if (!isSelected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNavigateToDestination(destination)
                            }
                            .semantics {
                                selected = isSelected
                                contentDescription = destination.label
                            },
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            destination.icon,
                            contentDescription = null,
                            tint = fg,
                            modifier = Modifier
                                .size(20.dp)
                                .graphicsLayer { scaleX = iconScale; scaleY = iconScale },
                        )
                        AnimatedVisibility(visible = isSelected) {
                            Row {
                                Spacer(Modifier.width(8.dp))
                                Text(destination.label, style = RadarTheme.typography.title, color = fg, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
