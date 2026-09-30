package com.researchradar.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.researchradar.core.design.RadarIcons
import com.researchradar.core.design.RadarMotion
import com.researchradar.core.design.RadarShape
import com.researchradar.core.design.RadarSpacing
import com.researchradar.core.design.RadarTheme
import com.researchradar.core.design.components.RadarBlip
import com.researchradar.core.design.components.RadarButton
import com.researchradar.core.design.components.RadarButtonStyle
import com.researchradar.core.design.components.RadarSweep
import com.researchradar.core.design.staggeredEnter
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun LandingScreen(
    onCreateAccount: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RadarTheme.colors
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val heroCtaPx = with(density) { 560.dp.toPx() }
    val showStickyCta by remember { derivedStateOf { scroll.value > heroCtaPx } }
    // The hero is roughly one screen tall; past it the page is light paper.
    val heroPx = with(density) { 900.dp.toPx() }
    val pastHero by remember { derivedStateOf { scroll.value > heroPx } }
    StatusBarIcons(light = !pastHero || colors.paper.isDark())

    Box(modifier.fillMaxSize().background(colors.paper)) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Hero(scrollOffset = { scroll.value.toFloat() }, onCreateAccount = onCreateAccount, onSignIn = onSignIn)
            About(onCreateAccount = onCreateAccount)
        }

        // Backdrop behind the status bar so scrolled content never collides with it.
        val scrim by androidx.compose.animation.animateColorAsState(if (pastHero) colors.paper else colors.night, label = "statusScrim")
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(scrim),
        )

        // Once the hero buttons scroll away, keep the main action within reach.
        AnimatedVisibility(
            visible = showStickyCta,
            enter = slideInVertically(RadarMotion.snappy()) { it } + fadeIn(),
            exit = slideOutVertically(RadarMotion.snappy()) { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                Modifier
                    .navigationBarsPadding()
                    .padding(RadarSpacing.gutter)
                    .fillMaxWidth()
                    .clip(RadarShape.pill)
                    .background(colors.night)
                    .padding(start = RadarSpacing.lg, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Free to start", style = RadarTheme.typography.title, color = colors.onNight, modifier = Modifier.weight(1f))
                RadarButton("Create account", onCreateAccount, style = RadarButtonStyle.Accent, height = 44.dp, trailingIcon = RadarIcons.ArrowRight)
            }
        }
    }
}

@Composable
private fun Hero(scrollOffset: () -> Float, onCreateAccount: () -> Unit, onSignIn: () -> Unit) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography

    // Decorative blips that keep appearing, as papers do while a map is built.
    val blips = remember { mutableStateListOf<RadarBlip>() }
    LaunchedEffect(Unit) {
        var n = 0
        val rnd = Random(7)
        while (true) {
            delay(if (blips.size < 10) 520 else 1400)
            if (blips.size >= 14) blips.removeAt(0)
            blips.add(RadarBlip("b${n++}", rnd.nextFloat(), 0.2f + rnd.nextFloat() * 0.72f, 0.5f + rnd.nextFloat() * 0.5f))
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RadarShape.hero.copy(topStart = androidx.compose.foundation.shape.CornerSize(0.dp), topEnd = androidx.compose.foundation.shape.CornerSize(0.dp)))
            .background(colors.night)
            .statusBarsPadding()
            .padding(horizontal = RadarSpacing.gutter)
            .padding(bottom = RadarSpacing.xxl),
    ) {
        Row(Modifier.padding(vertical = RadarSpacing.md).staggeredEnter(0, 10f), verticalAlignment = Alignment.CenterVertically) {
            Icon(RadarIcons.Radar, contentDescription = null, tint = colors.accent, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
            com.researchradar.core.design.components.Wordmark(color = colors.onNight, showTagline = true)
        }

        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.15f)
                .graphicsLayer {
                    val s = scrollOffset()
                    translationY = s * 0.35f
                    alpha = (1f - s / 900f).coerceIn(0f, 1f)
                    val scale = 1f - (s / 3000f).coerceIn(0f, 0.15f)
                    scaleX = scale
                    scaleY = scale
                },
            contentAlignment = Alignment.Center,
        ) {
            RadarSweep(Modifier.fillMaxSize().padding(RadarSpacing.md), blips = blips, periodMillis = 3600)
        }

        Text("FOR RESEARCHERS AND ENGINEERS", style = type.label, color = colors.accent, modifier = Modifier.staggeredEnter(1))
        Spacer(Modifier.height(RadarSpacing.sm))
        Text("Every claim,\ntraced to its source.", style = type.hero, color = colors.onNight, modifier = Modifier.staggeredEnter(2))
        Spacer(Modifier.height(RadarSpacing.md))
        Text(
            "Give it a research topic, or your own PDFs. Ereuna reads the literature and builds a map of papers, findings, gaps and citations, with the original passage behind every item.",
            style = type.body,
            color = colors.onNight.copy(alpha = 0.72f),
            modifier = Modifier.staggeredEnter(3),
        )
        Spacer(Modifier.height(RadarSpacing.xl))
        RadarButton(
            text = "Create free account",
            onClick = onCreateAccount,
            style = RadarButtonStyle.Accent,
            trailingIcon = RadarIcons.ArrowRight,
            modifier = Modifier.fillMaxWidth().staggeredEnter(4),
        )
        Spacer(Modifier.height(RadarSpacing.sm))
        Box(
            Modifier
                .fillMaxWidth()
                .staggeredEnter(5)
                .clip(RadarShape.pill)
                .border(1.dp, colors.nightRule, RadarShape.pill),
        ) {
            RadarButton(
                text = "I already have an account",
                onClick = onSignIn,
                style = RadarButtonStyle.Ghost,
                contentColor = colors.onNight,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun About(onCreateAccount: () -> Unit) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography

    Column(Modifier.padding(horizontal = RadarSpacing.gutter).padding(top = RadarSpacing.xxxl)) {
        Text("WHAT YOU GET", style = type.label, color = colors.accent)
        Spacer(Modifier.height(RadarSpacing.sm))
        Text("A research map, not a chat reply.", style = type.display, color = colors.ink)
        Spacer(Modifier.height(RadarSpacing.lg))

        Feature(RadarIcons.Search, "Three databases at once", "OpenAlex, Semantic Scholar and arXiv are searched together, and papers are ranked by how closely they match your topic.")
        Feature(RadarIcons.Quote, "Findings you can check", "Every finding, metric and limitation comes with the exact passage it was taken from. Anything that can't be found in the paper is dropped.")
        Feature(RadarIcons.Gap, "Gaps from the authors", "Research gaps are built from limitations the papers state themselves, with a count of how many papers report each one.")
        Feature(RadarIcons.Graph, "Who cites whom", "A citation graph drawn from the papers' real reference lists, plus the classic works they all build on.")

        Spacer(Modifier.height(RadarSpacing.xxxl))
        Text("HOW IT WORKS", style = type.label, color = colors.accent)
        Spacer(Modifier.height(RadarSpacing.sm))
        Text("Four steps, a few minutes.", style = type.display, color = colors.ink)
        Spacer(Modifier.height(RadarSpacing.lg))
        listOf(
            "Describe a topic" to "In plain words, as specific as you like.",
            "Papers are found and ranked" to "Up to 50 of the most relevant papers are selected.",
            "Full text is read" to "Open-access PDFs are read; others are analysed from their abstracts.",
            "Your map is ready" to "Browse, compare, save to your library, or export to BibTeX, Markdown or CSV.",
        ).forEachIndexed { i, (title, body) -> Step(i + 1, title, body, isLast = i == 3) }

        Spacer(Modifier.height(RadarSpacing.xxl))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RadarShape.card)
                .background(colors.surface)
                .border(1.dp, colors.rule, RadarShape.card)
                .padding(RadarSpacing.lg),
        ) {
            Text("What it won't do", style = type.heading, color = colors.ink)
            Spacer(Modifier.height(RadarSpacing.sm))
            listOf(
                "Chat with you or give opinions.",
                "Invent papers, numbers or quotes.",
                "Read paywalled papers. Those are analysed from their abstracts and marked as such.",
                "Present experiment ideas as findings. They are labelled as model-generated suggestions.",
            ).forEach { line ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                    Icon(RadarIcons.Close, contentDescription = null, tint = colors.accent, modifier = Modifier.padding(top = 3.dp).size(14.dp))
                    Spacer(Modifier.width(RadarSpacing.sm))
                    Text(line, style = type.bodySmall, color = colors.ink)
                }
            }
        }

        Spacer(Modifier.height(RadarSpacing.xxl))
        Text("BUILT ON OPEN DATA", style = type.label, color = colors.ink2)
        Spacer(Modifier.height(RadarSpacing.sm))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("OpenAlex", "Semantic Scholar", "arXiv", "Crossref", "Unpaywall", "Europe PMC").forEach { source ->
                Text(
                    source,
                    style = type.bodySmall,
                    color = colors.ink,
                    modifier = Modifier.clip(RadarShape.pill).border(1.dp, colors.rule, RadarShape.pill).padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        Spacer(Modifier.height(RadarSpacing.xxxl))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RadarShape.hero)
                .background(colors.accent)
                .padding(RadarSpacing.xl),
        ) {
            Text("Start your first map", style = type.display, color = colors.onAccent)
            Spacer(Modifier.height(RadarSpacing.xs))
            Text("Create an account to save maps to your library.", style = type.body, color = colors.onAccent.copy(alpha = 0.85f))
            Spacer(Modifier.height(RadarSpacing.lg))
            RadarButton("Create free account", onCreateAccount, style = RadarButtonStyle.Primary, trailingIcon = RadarIcons.ArrowRight)
        }
        Spacer(Modifier.height(140.dp))
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RadarShape.card)
            .background(colors.surface)
            .border(1.dp, colors.rule, RadarShape.card)
            .padding(RadarSpacing.lg),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.size(40.dp).clip(RadarShape.control).background(colors.accentWash),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(RadarSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = type.title, color = colors.ink)
            Spacer(Modifier.height(4.dp))
            Text(body, style = type.bodySmall, color = colors.ink2)
        }
    }
}

@Composable
private fun Step(number: Int, title: String, body: String, isLast: Boolean) {
    val colors = RadarTheme.colors
    val type = RadarTheme.typography
    Row {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(32.dp).clip(RadarShape.pill).background(colors.night),
                contentAlignment = Alignment.Center,
            ) { Text(number.toString().padStart(2, '0'), style = type.mono, color = colors.onNight) }
            if (!isLast) Box(Modifier.width(1.5.dp).height(46.dp).background(colors.rule))
        }
        Spacer(Modifier.width(RadarSpacing.md))
        Column(Modifier.padding(top = 5.dp, bottom = RadarSpacing.md)) {
            Text(title, style = type.title, color = colors.ink)
            Spacer(Modifier.height(2.dp))
            Text(body, style = type.bodySmall, color = colors.ink2)
        }
    }
}

private fun androidx.compose.ui.graphics.Color.isDark(): Boolean = (0.2126f * red + 0.7152f * green + 0.0722f * blue) < 0.5f

/** Sets status-bar icon colour for as long as the landing page is shown. */
@Composable
private fun StatusBarIcons(light: Boolean) {
    val view = androidx.compose.ui.platform.LocalView.current
    val restoreLight = !RadarTheme.colors.paper.isDark()
    androidx.compose.runtime.DisposableEffect(light) {
        val window = (view.context as? android.app.Activity)?.window
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = !light
        onDispose { controller?.isAppearanceLightStatusBars = restoreLight }
    }
}
