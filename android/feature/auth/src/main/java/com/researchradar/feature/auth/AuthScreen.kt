package com.researchradar.feature.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.researchradar.core.design.RadarIcons
import com.researchradar.core.design.RadarMotion
import com.researchradar.core.design.RadarShape
import com.researchradar.core.design.RadarSpacing
import com.researchradar.core.design.RadarTheme
import com.researchradar.core.design.components.RadarButton
import com.researchradar.core.design.components.RadarButtonStyle
import com.researchradar.core.design.components.RadarIconButton
import com.researchradar.core.design.components.RadarSweep
import com.researchradar.core.design.components.RadarTextField
import com.researchradar.core.design.staggeredEnter

@Composable
fun AuthScreen(
    uiState: AuthUiState,
    onModeChange: (AuthMode) -> Unit,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography
    val focus = LocalFocusManager.current
    val signUp = uiState.mode == AuthMode.SignUp

    Column(
        modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = RadarSpacing.gutter),
    ) {
        Row(Modifier.padding(vertical = RadarSpacing.md), verticalAlignment = Alignment.CenterVertically) {
            RadarIconButton(RadarIcons.ArrowLeft, "Back", onBack)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(40.dp).clip(RadarShape.pill).background(colors.night), contentAlignment = Alignment.Center) {
                RadarSweep(Modifier.size(32.dp), periodMillis = 2600)
            }
        }

        Spacer(Modifier.height(RadarSpacing.lg))
        AnimatedContent(
            targetState = signUp,
            transitionSpec = { (fadeIn() + slideInVertically { it / 3 }) togetherWith (fadeOut() + slideOutVertically { -it / 3 }) },
            label = "authTitle",
        ) { isSignUp ->
            Column {
                Text(
                    if (isSignUp) "Create your account" else "Welcome back",
                    style = type.hero,
                    color = colors.ink,
                )
                Spacer(Modifier.height(RadarSpacing.sm))
                Text(
                    if (isSignUp) "Save maps to your library and pick up where you left off on any device."
                    else "Sign in to open your research maps and library.",
                    style = type.body,
                    color = colors.ink2,
                )
            }
        }

        Spacer(Modifier.height(RadarSpacing.xl))
        ModeSwitch(signUp = signUp, onModeChange = onModeChange, modifier = Modifier.staggeredEnter(1))
        Spacer(Modifier.height(RadarSpacing.xl))

        AnimatedVisibility(
            visible = signUp,
            enter = expandVertically(RadarMotion.snappy()) + fadeIn(),
            exit = shrinkVertically(RadarMotion.snappy()) + fadeOut(),
        ) {
            Column {
                RadarTextField(
                    value = uiState.name,
                    onValueChange = onNameChange,
                    label = "Name",
                    placeholder = "Your full name",
                    leadingIcon = RadarIcons.Person,
                    error = uiState.nameError,
                    enabled = !uiState.isSubmitting,
                    shakeKey = if (uiState.nameError != null) uiState.shakeKey else 0,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                )
                Spacer(Modifier.height(RadarSpacing.lg))
            }
        }

        RadarTextField(
            value = uiState.email,
            onValueChange = onEmailChange,
            label = "Email",
            placeholder = "you@university.edu",
            leadingIcon = RadarIcons.Mail,
            error = uiState.emailError,
            enabled = !uiState.isSubmitting,
            shakeKey = if (uiState.emailError != null) uiState.shakeKey else 0,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
        )
        Spacer(Modifier.height(RadarSpacing.lg))
        RadarTextField(
            value = uiState.password,
            onValueChange = onPasswordChange,
            label = "Password",
            placeholder = if (signUp) "At least 8 characters" else "Your password",
            leadingIcon = RadarIcons.Lock,
            isPassword = true,
            error = uiState.passwordError,
            enabled = !uiState.isSubmitting,
            shakeKey = if (uiState.passwordError != null) uiState.shakeKey else 0,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                focus.clearFocus()
                onSubmit()
            }),
        )

        AnimatedVisibility(
            visible = signUp,
            enter = expandVertically(RadarMotion.snappy()) + fadeIn(),
            exit = shrinkVertically(RadarMotion.snappy()) + fadeOut(),
        ) {
            val rules = uiState.passwordRules
            Column(Modifier.padding(top = RadarSpacing.md)) {
                Rule("At least 8 characters", rules.longEnough)
                Rule("One uppercase letter", rules.hasUppercase)
                Rule("One number", rules.hasNumber)
            }
        }

        AnimatedVisibility(
            visible = uiState.formError != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Row(
                Modifier
                    .padding(top = RadarSpacing.lg)
                    .fillMaxWidth()
                    .clip(RadarShape.control)
                    .background(colors.accentWash)
                    .padding(RadarSpacing.md),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(RadarIcons.Close, contentDescription = null, tint = colors.accent, modifier = Modifier.padding(top = 2.dp).size(16.dp))
                Spacer(Modifier.width(RadarSpacing.sm))
                Text(uiState.formError.orEmpty(), style = type.bodySmall, color = colors.ink)
            }
        }

        Spacer(Modifier.height(RadarSpacing.xl))
        RadarButton(
            text = if (signUp) "Create account" else "Sign in",
            onClick = {
                focus.clearFocus()
                onSubmit()
            },
            loading = uiState.isSubmitting,
            style = RadarButtonStyle.Accent,
            trailingIcon = RadarIcons.ArrowRight,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(RadarSpacing.lg))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
            Text(if (signUp) "Already have an account? " else "New to Ereuna? ", style = type.bodySmall, color = colors.ink2)
            Text(
                if (signUp) "Sign in" else "Create one",
                style = type.bodySmall.copy(fontWeight = type.title.fontWeight),
                color = colors.accent,
                modifier = Modifier.clickable { onModeChange(if (signUp) AuthMode.SignIn else AuthMode.SignUp) },
            )
        }

        Spacer(Modifier.height(RadarSpacing.xxl))
        Text(
            "Your email is used only to sign you in. Search topics are sent to the Ereuna server to build maps.",
            style = type.bodySmall,
            color = colors.ink3,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(RadarSpacing.xl))
    }
}

/** Two-option segmented control with a sliding thumb. */
@Composable
private fun ModeSwitch(signUp: Boolean, onModeChange: (AuthMode) -> Unit, modifier: Modifier = Modifier) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RadarShape.pill)
            .background(colors.raised)
            .border(1.dp, colors.rule, RadarShape.pill)
            .padding(4.dp),
    ) {
        val half = maxWidth / 2
        val offset by animateDpAsState(if (signUp) half else 0.dp, RadarMotion.settle(), label = "modeThumb")
        Box(
            Modifier
                .offset(x = offset)
                .width(half)
                .fillMaxHeight()
                .clip(RadarShape.pill)
                .background(colors.night),
        )
        Row(Modifier.fillMaxSize()) {
            listOf(AuthMode.SignIn to "Sign in", AuthMode.SignUp to "Create account").forEach { (mode, label) ->
                val selected = (mode == AuthMode.SignUp) == signUp
                val fg by animateColorAsState(if (selected) colors.onNight else colors.ink2, label = "modeFg")
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RadarShape.pill)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                        ) { onModeChange(mode) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = type.title.copy(fontSize = type.bodySmall.fontSize), color = fg)
                }
            }
        }
    }
}

@Composable
private fun Rule(text: String, met: Boolean) {
    val colors = RadarTheme.colors
    val fill by animateColorAsState(if (met) colors.positive else colors.rule, label = "ruleFill")
    val fg by animateColorAsState(if (met) colors.ink else colors.ink2, label = "ruleText")
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(18.dp).clip(RadarShape.pill).background(fill), contentAlignment = Alignment.Center) {
            PopIn(met) { Icon(RadarIcons.Check, contentDescription = null, tint = colors.paper, modifier = Modifier.size(12.dp)) }
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = RadarTheme.typography.bodySmall, color = fg)
    }
}

@Composable
private fun PopIn(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible = visible, enter = fadeIn() + androidx.compose.animation.scaleIn(RadarMotion.settle()), exit = fadeOut()) { content() }
}
