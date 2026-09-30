package com.researchradar

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.researchradar.core.data.preferences.ThemeMode
import com.researchradar.core.data.preferences.UserPreferences
import com.researchradar.core.data.preferences.UserPreferencesRepository
import com.researchradar.core.data.session.SessionRepository
import com.researchradar.core.data.session.SessionState
import com.researchradar.core.design.RadarTheme
import com.researchradar.core.design.ResearchRadarTheme
import com.researchradar.core.design.components.RadarSweep
import com.researchradar.feature.auth.AuthFlow
import com.researchradar.ui.ResearchRadarApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var preferences: UserPreferencesRepository
    @Inject lateinit var session: SessionRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs by preferences.preferences.collectAsState(initial = UserPreferences())
            val sessionState by session.state.collectAsState()
            val dark = when (prefs.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Status and navigation bar icons follow the app's theme, not just the system's.
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            ResearchRadarTheme(darkTheme = dark) {
                // Signed out: landing + sign in. Signed in: the app. A brief splash while loading.
                AnimatedContent(
                    targetState = when (sessionState) {
                        SessionState.Loading -> Gate.Loading
                        SessionState.SignedOut -> Gate.SignedOut
                        is SessionState.SignedIn -> Gate.SignedIn
                    },
                    transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(250)) },
                    label = "sessionGate",
                ) { gate ->
                    when (gate) {
                        Gate.Loading -> Splash()
                        Gate.SignedOut -> AuthFlow()
                        Gate.SignedIn -> ResearchRadarApp()
                    }
                }
            }
        }
    }
}

private enum class Gate { Loading, SignedOut, SignedIn }

@Composable
private fun Splash() {
    Box(
        Modifier.fillMaxSize().background(RadarTheme.colors.night),
        contentAlignment = Alignment.Center,
    ) {
        RadarSweep(Modifier.size(96.dp))
    }
}
