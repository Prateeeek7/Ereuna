package com.researchradar.core.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Shape tokens. Soft but not bubbly: controls 12dp, cards 18dp, pills fully
 * rounded. Sheets round only their top edge.
 */
object RadarShape {
    /** Small tags and badges */
    val chip = RoundedCornerShape(8.dp)

    /** Fully rounded pills: filter chips, segmented controls, tab indicator */
    val pill = RoundedCornerShape(percent = 50)

    /** Buttons, inputs, list items inside cards */
    val control = RoundedCornerShape(12.dp)

    /** Content cards */
    val card = RoundedCornerShape(18.dp)

    /** Large hero surfaces (radar panel, search card) */
    val hero = RoundedCornerShape(26.dp)

    /** Bottom sheets */
    val sheet = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)

    /** Floating menus, popovers */
    val menu = RoundedCornerShape(14.dp)
}

/**
 * Spacing tokens. Everything snaps to the 4dp grid.
 */
object RadarSpacing {
    /** Base grid unit */
    val grid = 4.dp

    /** Screen-edge horizontal padding */
    val gutter = 20.dp

    /** Vertical padding inside list rows */
    val rowPadding = 16.dp

    /** Hairline divider thickness */
    val hairline = 1.dp

    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp

    /** Space to leave under scrollable content on screens with the floating nav bar */
    val bottomBarClearance = 104.dp
}
