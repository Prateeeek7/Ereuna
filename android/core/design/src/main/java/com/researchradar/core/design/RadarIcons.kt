package com.researchradar.core.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Ereuna icon set: thin 1.6px strokes on a 24px grid with round caps,
 * drawn to sit next to Newsreader and Plex rather than stock Material glyphs.
 * Tint with Icon(tint = …).
 */
object RadarIcons {

    private fun icon(name: String, filled: Boolean = false, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = if (filled) SolidColor(Color.Black) else null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }.build()

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcToRelative(r, r, 0f, true, true, 2 * r, 0f)
        arcToRelative(r, r, 0f, true, true, -2 * r, 0f)
        close()
    }

    val Search: ImageVector by lazy {
        icon("Search") {
            circle(10.5f, 10.5f, 6.25f)
            moveTo(15.2f, 15.2f); lineTo(20f, 20f)
        }
    }

    val ArrowRight: ImageVector by lazy {
        icon("ArrowRight") {
            moveTo(4.5f, 12f); lineTo(19f, 12f)
            moveTo(13f, 6f); lineTo(19f, 12f); lineTo(13f, 18f)
        }
    }

    val ArrowLeft: ImageVector by lazy {
        icon("ArrowLeft") {
            moveTo(19.5f, 12f); lineTo(5f, 12f)
            moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f)
        }
    }

    val ArrowUpRight: ImageVector by lazy {
        icon("ArrowUpRight") {
            moveTo(7f, 17f); lineTo(17f, 7f)
            moveTo(9f, 7f); lineTo(17f, 7f); lineTo(17f, 15f)
        }
    }

    val Bookmark: ImageVector by lazy {
        icon("Bookmark") {
            moveTo(6.5f, 4f); lineTo(17.5f, 4f); lineTo(17.5f, 20f); lineTo(12f, 15.8f); lineTo(6.5f, 20f); close()
        }
    }

    val BookmarkFilled: ImageVector by lazy {
        icon("BookmarkFilled", filled = true) {
            moveTo(6.5f, 4f); lineTo(17.5f, 4f); lineTo(17.5f, 20f); lineTo(12f, 15.8f); lineTo(6.5f, 20f); close()
        }
    }

    /** Library: three spines and one leaning volume */
    val Library: ImageVector by lazy {
        icon("Library") {
            moveTo(5f, 4.5f); lineTo(5f, 19.5f)
            moveTo(9f, 4.5f); lineTo(9f, 19.5f)
            moveTo(13f, 6.5f); lineTo(13f, 19.5f)
            moveTo(15.6f, 6.2f); lineTo(19.6f, 18.8f)
            moveTo(3.5f, 19.5f); lineTo(20.5f, 19.5f)
        }
    }

    /** Sliders: settings and filters */
    val Sliders: ImageVector by lazy {
        icon("Sliders") {
            moveTo(4f, 7.5f); lineTo(20f, 7.5f)
            moveTo(4f, 16.5f); lineTo(20f, 16.5f)
            circle(9f, 7.5f, 2.2f)
            circle(15f, 16.5f, 2.2f)
        }
    }

    val Export: ImageVector by lazy {
        icon("Export") {
            moveTo(5f, 12.5f); lineTo(5f, 19f); lineTo(19f, 19f); lineTo(19f, 12.5f)
            moveTo(12f, 4f); lineTo(12f, 14.5f)
            moveTo(8f, 8f); lineTo(12f, 4f); lineTo(16f, 8f)
        }
    }

    val Close: ImageVector by lazy {
        icon("Close") {
            moveTo(6.5f, 6.5f); lineTo(17.5f, 17.5f)
            moveTo(17.5f, 6.5f); lineTo(6.5f, 17.5f)
        }
    }

    val Check: ImageVector by lazy {
        icon("Check") {
            moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7.5f)
        }
    }

    val ChevronDown: ImageVector by lazy {
        icon("ChevronDown") {
            moveTo(6f, 9.5f); lineTo(12f, 15.5f); lineTo(18f, 9.5f)
        }
    }

    /** Citation network: three nodes and their links */
    val Graph: ImageVector by lazy {
        icon("Graph") {
            circle(6f, 7f, 2.3f)
            circle(18f, 6f, 2.3f)
            circle(12f, 18f, 2.3f)
            moveTo(8.2f, 7.4f); lineTo(15.7f, 6.3f)
            moveTo(7.3f, 9f); lineTo(10.8f, 16f)
            moveTo(16.9f, 8f); lineTo(13.2f, 16f)
        }
    }

    val Document: ImageVector by lazy {
        icon("Document") {
            moveTo(6f, 3.5f); lineTo(14f, 3.5f); lineTo(19f, 8.5f); lineTo(19f, 20.5f); lineTo(6f, 20.5f); close()
            moveTo(14f, 3.5f); lineTo(14f, 8.5f); lineTo(19f, 8.5f)
            moveTo(9f, 13f); lineTo(16f, 13f)
            moveTo(9f, 16.5f); lineTo(14f, 16.5f)
        }
    }

    /** Radar: rings and a sweep line — the app mark */
    val Radar: ImageVector by lazy {
        icon("Radar") {
            circle(12f, 12f, 8.5f)
            circle(12f, 12f, 4.5f)
            moveTo(12f, 12f); lineTo(18f, 6f)
        }
    }

    val Clock: ImageVector by lazy {
        icon("Clock") {
            circle(12f, 12f, 8.5f)
            moveTo(12f, 7.5f); lineTo(12f, 12f); lineTo(15f, 14f)
        }
    }

    /** Quote mark for evidence */
    val Quote: ImageVector by lazy {
        icon("Quote") {
            moveTo(9.5f, 7f); curveTo(6.5f, 8f, 5f, 10.5f, 5f, 13.5f); lineTo(5f, 17f); lineTo(9f, 17f); lineTo(9f, 13f); lineTo(6.2f, 13f)
            moveTo(18.5f, 7f); curveTo(15.5f, 8f, 14f, 10.5f, 14f, 13.5f); lineTo(14f, 17f); lineTo(18f, 17f); lineTo(18f, 13f); lineTo(15.2f, 13f)
        }
    }

    /** Gap: a broken line */
    val Gap: ImageVector by lazy {
        icon("Gap") {
            moveTo(3.5f, 12f); lineTo(9f, 12f)
            moveTo(15f, 12f); lineTo(20.5f, 12f)
            moveTo(9f, 8.5f); lineTo(9f, 15.5f)
            moveTo(15f, 8.5f); lineTo(15f, 15.5f)
        }
    }

    val Retry: ImageVector by lazy {
        icon("Retry") {
            moveTo(19f, 12f)
            arcToRelative(7f, 7f, 0f, true, true, -2.05f, -4.95f)
            moveTo(19f, 4.5f); lineTo(19f, 9f); lineTo(14.5f, 9f)
        }
    }

    val Eye: ImageVector by lazy {
        icon("Eye") {
            moveTo(2.5f, 12f); curveTo(5f, 7f, 8.5f, 5f, 12f, 5f); curveTo(15.5f, 5f, 19f, 7f, 21.5f, 12f)
            curveTo(19f, 17f, 15.5f, 19f, 12f, 19f); curveTo(8.5f, 19f, 5f, 17f, 2.5f, 12f); close()
            circle(12f, 12f, 3f)
        }
    }

    val EyeOff: ImageVector by lazy {
        icon("EyeOff") {
            moveTo(2.5f, 12f); curveTo(5f, 7f, 8.5f, 5f, 12f, 5f); curveTo(15.5f, 5f, 19f, 7f, 21.5f, 12f)
            curveTo(19f, 17f, 15.5f, 19f, 12f, 19f); curveTo(8.5f, 19f, 5f, 17f, 2.5f, 12f); close()
            moveTo(4f, 4f); lineTo(20f, 20f)
        }
    }

    val Mail: ImageVector by lazy {
        icon("Mail") {
            moveTo(3.5f, 6f); lineTo(20.5f, 6f); lineTo(20.5f, 18f); lineTo(3.5f, 18f); close()
            moveTo(3.5f, 6.5f); lineTo(12f, 13f); lineTo(20.5f, 6.5f)
        }
    }

    val Lock: ImageVector by lazy {
        icon("Lock") {
            moveTo(5.5f, 11f); lineTo(18.5f, 11f); lineTo(18.5f, 20f); lineTo(5.5f, 20f); close()
            moveTo(8.5f, 11f); lineTo(8.5f, 8f); curveTo(8.5f, 5.8f, 10f, 4.5f, 12f, 4.5f); curveTo(14f, 4.5f, 15.5f, 5.8f, 15.5f, 8f); lineTo(15.5f, 11f)
        }
    }

    val Person: ImageVector by lazy {
        icon("Person") {
            circle(12f, 8.5f, 3.8f)
            moveTo(4.5f, 20f); curveTo(5.5f, 15.8f, 8.5f, 14f, 12f, 14f); curveTo(15.5f, 14f, 18.5f, 15.8f, 19.5f, 20f)
        }
    }

    val SignOut: ImageVector by lazy {
        icon("SignOut") {
            moveTo(14f, 4.5f); lineTo(5.5f, 4.5f); lineTo(5.5f, 19.5f); lineTo(14f, 19.5f)
            moveTo(10f, 12f); lineTo(20f, 12f)
            moveTo(16.5f, 8.5f); lineTo(20f, 12f); lineTo(16.5f, 15.5f)
        }
    }

    val Upload: ImageVector by lazy {
        icon("Upload") {
            moveTo(4.5f, 14.5f); lineTo(4.5f, 19.5f); lineTo(19.5f, 19.5f); lineTo(19.5f, 14.5f)
            moveTo(12f, 15.5f); lineTo(12f, 4.5f)
            moveTo(7.5f, 9f); lineTo(12f, 4.5f); lineTo(16.5f, 9f)
        }
    }

    val Plus: ImageVector by lazy {
        icon("Plus") {
            moveTo(12f, 5f); lineTo(12f, 19f)
            moveTo(5f, 12f); lineTo(19f, 12f)
        }
    }

    val Trash: ImageVector by lazy {
        icon("Trash") {
            moveTo(4.5f, 6.5f); lineTo(19.5f, 6.5f)
            moveTo(9.5f, 6.5f); lineTo(9.5f, 4.5f); lineTo(14.5f, 4.5f); lineTo(14.5f, 6.5f)
            moveTo(6.5f, 6.5f); lineTo(7.5f, 19.5f); lineTo(16.5f, 19.5f); lineTo(17.5f, 6.5f)
            moveTo(10.5f, 10f); lineTo(10.5f, 16f)
            moveTo(13.5f, 10f); lineTo(13.5f, 16f)
        }
    }
}
