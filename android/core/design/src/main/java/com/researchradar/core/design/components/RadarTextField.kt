package com.researchradar.core.design.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.researchradar.core.design.RadarIcons
import com.researchradar.core.design.RadarShape
import com.researchradar.core.design.RadarTheme

/**
 * Labelled input with a leading icon. The border and icon take the accent
 * colour on focus, errors appear beneath with a short horizontal shake, and
 * password fields get a show/hide toggle.
 */
@Composable
fun RadarTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    placeholder: String = "",
    error: String? = null,
    isPassword: Boolean = false,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    shakeKey: Int = 0,
) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var revealed by remember { mutableStateOf(false) }

    val borderColor by animateColorAsState(
        when {
            error != null -> colors.accent
            focused -> colors.ink
            else -> colors.rule
        },
        label = "fieldBorder",
    )
    val iconColor by animateColorAsState(if (focused) colors.accent else colors.ink2, label = "fieldIcon")

    // Shake when the caller bumps shakeKey (e.g. a failed submit).
    val shake = remember { Animatable(0f) }
    LaunchedEffect(shakeKey) {
        if (shakeKey > 0) {
            shake.animateTo(
                0f,
                keyframes {
                    durationMillis = 360
                    -10f at 50
                    9f at 110
                    -6f at 170
                    4f at 230
                    -2f at 290
                },
            )
        }
    }

    Column(modifier.graphicsLayer { translationX = shake.value }) {
        Text(label, style = type.label, color = if (error != null) colors.accent else colors.ink2)
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .clip(RadarShape.control)
                .background(colors.surface)
                .border(if (focused) 1.5.dp else 1.dp, borderColor, RadarShape.control)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
            }
            Box(Modifier.weight(1f).padding(vertical = 14.dp)) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, style = type.body, color = colors.ink3)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = true,
                    textStyle = type.body.copy(color = colors.ink),
                    cursorBrush = SolidColor(colors.accent),
                    interactionSource = interaction,
                    visualTransformation = if (isPassword && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (isPassword) {
                RadarIconButton(
                    icon = if (revealed) RadarIcons.EyeOff else RadarIcons.Eye,
                    contentDescription = if (revealed) "Hide password" else "Show password",
                    onClick = { revealed = !revealed },
                    size = 36.dp,
                    border = null,
                    background = colors.surface,
                    tint = colors.ink2,
                )
            }
        }
        AnimatedVisibility(visible = error != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Text(
                text = error.orEmpty(),
                style = type.bodySmall,
                color = colors.accent,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
