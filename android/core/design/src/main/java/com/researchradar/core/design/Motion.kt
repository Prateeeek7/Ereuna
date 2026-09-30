package com.researchradar.core.design

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.delay

/**
 * Motion tokens. Movement explains cause and effect (what appeared, what
 * changed, what you pressed); it never decorates. Springs for anything the
 * finger touches, eased tweens for things that arrive on their own.
 */
object RadarMotion {
    /** Emphasised deceleration for elements entering the screen */
    val EnterEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    const val SHORT = 160
    const val MEDIUM = 280
    const val LONG = 460

    /** Delay between items in a staggered list entrance */
    const val STAGGER = 45

    fun <T> press() = spring<T>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
    fun <T> settle() = spring<T>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
    fun <T> snappy() = spring<T>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)
}

/**
 * Shrinks slightly while pressed and springs back on release. Pass the same
 * [interactionSource] used by the element's clickable.
 */
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.965f,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = RadarMotion.press(),
        label = "pressScale",
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Fades and lifts an element into place the first time it is composed,
 * delayed by its position so lists cascade in. [index] beyond ~12 is
 * clamped so long lists don't wait.
 */
fun Modifier.staggeredEnter(index: Int, lift: Float = 28f): Modifier = composed {
    // Lazy lists dispose items that scroll away; saved state survives that, so an
    // item that already entered does not replay (and sit blank) when it returns.
    var played by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (played) return@LaunchedEffect
        played = true
        delay((index.coerceAtMost(12) * RadarMotion.STAGGER).toLong())
        progress.animateTo(1f, tween(RadarMotion.LONG, easing = RadarMotion.EnterEasing))
    }
    this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * lift
    }
}

/**
 * A number that counts up to its value and rolls digits vertically when it
 * changes later (e.g. live counters while a map is being built).
 */
@Composable
fun AnimatedCounter(
    value: Int,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    prefix: String = "",
    suffix: String = "",
    countUpFromZero: Boolean = true,
) {
    // First appearance: count up from zero. Later changes: roll the digits.
    val countUp = remember { Animatable(if (countUpFromZero) 0f else 1f) }
    LaunchedEffect(Unit) {
        countUp.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    val counting = countUp.value < 1f
    Row(modifier = modifier) {
        if (prefix.isNotEmpty()) Text(prefix, style = style, color = color)
        if (counting) {
            Text((value * countUp.value).toInt().toString(), style = style, color = color)
        } else {
            value.toString().forEachIndexed { i, digit ->
                AnimatedContent(
                    targetState = digit,
                    transitionSpec = {
                        (slideInVertically { it / 2 } + fadeIn(tween(140))) togetherWith
                            (slideOutVertically { -it / 2 } + fadeOut(tween(140)))
                    },
                    label = "digit$i",
                ) { d -> Text(d.toString(), style = style, color = color) }
            }
        }
        if (suffix.isNotEmpty()) Text(suffix, style = style, color = color)
    }
}
