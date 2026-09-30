package com.researchradar.feature.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.researchradar.core.design.RadarMotion

/**
 * Signed-out experience: landing page (with About) → sign in / create account.
 * When sign-in succeeds the session changes and the app shows the main screens.
 */
@Composable
fun AuthFlow(viewModel: AuthViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showForm by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = showForm) { showForm = false }

    AnimatedContent(
        targetState = showForm,
        transitionSpec = {
            val dir = if (targetState) 1 else -1
            (slideInHorizontally(RadarMotion.snappy()) { it / 3 * dir } + fadeIn()) togetherWith
                (slideOutHorizontally(RadarMotion.snappy()) { -it / 3 * dir } + fadeOut())
        },
        label = "authFlow",
    ) { form ->
        if (form) {
            AuthScreen(
                uiState = uiState,
                onModeChange = viewModel::setMode,
                onNameChange = viewModel::onNameChange,
                onEmailChange = viewModel::onEmailChange,
                onPasswordChange = viewModel::onPasswordChange,
                onSubmit = viewModel::submit,
                onBack = { showForm = false },
            )
        } else {
            LandingScreen(
                onCreateAccount = {
                    viewModel.setMode(AuthMode.SignUp)
                    showForm = true
                },
                onSignIn = {
                    viewModel.setMode(AuthMode.SignIn)
                    showForm = true
                },
            )
        }
    }
}
