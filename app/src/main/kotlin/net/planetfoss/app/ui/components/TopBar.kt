// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import net.planetfoss.app.ui.theme.LocalPlanetColors
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import net.planetfoss.app.R
import net.planetfoss.core.api.ContentLang

@Composable
fun langLabel(lang: ContentLang): String = stringResource(
    when (lang) {
        ContentLang.ALL -> R.string.lang_all
        ContentLang.DE -> R.string.lang_de
        ContentLang.EN -> R.string.lang_en
    },
)

/** Where the stars of the night header sit (x, y as fractions, radius in dp) – like the website's header. */
private val NIGHT_STARS = listOf(
    Triple(0.08f, 0.35f, 1f), Triple(0.23f, 0.75f, 0.8f), Triple(0.41f, 0.25f, 1.2f),
    Triple(0.63f, 0.65f, 0.8f), Triple(0.78f, 0.3f, 1f), Triple(0.91f, 0.7f, 1.2f),
)

/** True for pages in the list pane while a detail pane stands beside it (wide screens, docs/11-tablet-mode.md). */
val LocalBesidePane = androidx.compose.runtime.staticCompositionLocalOf { false }

/** Light status bar icons while the night header is on screen; the theme's choice again afterwards. */
@Composable
private fun LightStatusIcons() {
    val view = LocalView.current
    val dark = isSystemInDarkTheme()
    DisposableEffect(view, dark) {
        val window = (view.context as? android.app.Activity)?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        bars?.isAppearanceLightStatusBars = false
        onDispose { bars?.isAppearanceLightStatusBars = !dark }
    }
}

/**
 * Top bar of every tab: title, content language (as on the website: Alle | DE | EN), settings.
 * [brand] (the Latest tab, the app's home): the PlanetFOSS wordmark instead of the title, as in
 * the website's header; TalkBack still hears the tab's name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabTopBar(
    title: String,
    lang: ContentLang,
    onLang: (ContentLang) -> Unit,
    onSettings: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    onSearch: (() -> Unit)? = null,
    brand: Boolean = false,
    /** A former tab page now pushed from Discover (sky, rubrics): back arrow instead of nothing. */
    onBack: (() -> Unit)? = null,
) {
    val planet = LocalPlanetColors.current
    // brand: the website's header – night sky with a few stars, light wordmark, in light and dark mode alike
    val night = Modifier.drawBehind {
        drawRect(planet.night)
        NIGHT_STARS.forEach { (x, y, r) -> drawCircle(Color.White.copy(alpha = 0.5f), radius = r * density, center = Offset(size.width * x, size.height * y)) }
    }
    // beside the detail pane the night header covers only the list; the status bar has one icon colour for the whole
    // window, so it follows the larger pane on the right
    if (brand && !LocalBesidePane.current) LightStatusIcons()
    TopAppBar(
        modifier = if (brand) night else Modifier,
        colors = if (brand) {
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
                actionIconContentColor = Color(0xFFC9CEE3),
                titleContentColor = Color(0xFFF5F6FB),
            )
        } else {
            TopAppBarDefaults.topAppBarColors()
        },
        title = {
            if (brand) {
                Image(
                    painterResource(R.drawable.wordmark_on_night),
                    contentDescription = stringResource(R.string.wordmark_title, title),
                    modifier = Modifier.height(30.dp).semantics { heading() },
                )
            } else {
                Text(title)
            }
        },
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) {
                Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
            }
        },
        actions = {
            if (onSearch != null) IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, stringResource(R.string.search)) }
            var open by rememberSaveable { mutableStateOf(false) }
            val chooser = stringResource(R.string.lang_choose)
            val current = langLabel(lang)
            Box {
                TextButton(
                    onClick = { open = true },
                    colors = if (brand) ButtonDefaults.textButtonColors(contentColor = Color(0xFFB3A4FF)) else ButtonDefaults.textButtonColors(),
                    modifier = Modifier.semantics { contentDescription = "$chooser: $current" },
                ) {
                    Text(if (lang == ContentLang.ALL) stringResource(R.string.lang_chip_all) else lang.param.uppercase())
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    ContentLang.entries.forEach { l ->
                        DropdownMenuItem(
                            text = { Text(langLabel(l)) },
                            leadingIcon = { RadioButton(selected = l == lang, onClick = null) },
                            onClick = { open = false; onLang(l) },
                        )
                    }
                }
            }
            IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, stringResource(R.string.settings)) }
        },
    )
}
