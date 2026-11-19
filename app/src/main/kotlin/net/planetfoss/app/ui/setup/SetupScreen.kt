// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.pluralStringResource
import net.planetfoss.app.ui.components.hasNotifyPermission
import net.planetfoss.app.ui.theme.MetaStyle
import net.planetfoss.core.api.TopicRef
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.planetfoss.app.AppLanguage
import net.planetfoss.app.R
import net.planetfoss.app.data.prefs.BrowserMode
import net.planetfoss.app.data.prefs.Settings
import net.planetfoss.app.data.prefs.ThemeMode
import net.planetfoss.app.ui.components.Choice
import net.planetfoss.app.ui.components.ChoiceItem
import net.planetfoss.app.ui.components.langLabel
import net.planetfoss.app.ui.components.openNotificationSettings
import net.planetfoss.app.ui.components.rememberNotifyRequest
import net.planetfoss.app.ui.theme.ContentMaxWidth
import net.planetfoss.app.ui.theme.Radius
import net.planetfoss.app.ui.theme.Space
import net.planetfoss.core.api.ContentLang

/** Pages: 0 welcome, 1..[Steps] the setup and tour pages, [Ready] the end. */
private const val Steps = 7

/** The page on which topics are followed (mockup v3, 2026-11-19). */
private const val TopicsPage = 3
private const val Ready = Steps + 1

/**
 * The setup assistant (mockup v2, 2026-11-07). The welcome and the closing page are the night sky of the website with the
 * wordmark; the seven pages in between have a night sky head with the step, the title and a symbol, and their choices in cards:
 * language and design, how articles open, topics to follow (they fill Radar), notifications, anonymous counting, then a tour of the four tabs and of the handles
 * people look for. Everything is optional and preset to the safe value; each choice is saved at once, so leaving on any page
 * keeps it. First start: opens by itself; later: Settings → Setup assistant.
 */
@Composable
fun SetupScreen(onDone: () -> Unit, vm: SetupViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val adsShown by vm.adsShown.collectAsStateWithLifecycle()
    val topics by vm.topics.collectAsStateWithLifecycle()
    val followed by vm.followed.collectAsStateWithLifecycle()
    // survives the recreation Android does after a language or design change
    var page by rememberSaveable { mutableIntStateOf(0) }
    val leave = { vm.finish(); onDone() }
    BackHandler { if (page > 0) page-- else leave() }
    val night = page == 0 || page == Ready
    SystemBarIcons(navigationLight = night)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AnimatedContent(page, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "setup page") { p ->
                when (p) {
                    0 -> Welcome(onStart = { page = 1 }, onSkip = leave)
                    Ready -> Closing(followed.size, onDone = leave, onBack = { page-- })
                    else -> Column(Modifier.fillMaxSize()) {
                        SetupBand(p, Steps, stringResource(titleOf(p)), painterOf(p))
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                            Column(
                                Modifier.widthIn(max = ContentMaxWidth).fillMaxSize().verticalScroll(rememberScrollState())
                                    .padding(start = Space.l, end = Space.l, top = Space.l, bottom = Space.l),
                            ) {
                                when (p) {
                                    1 -> LanguageAndDesign(settings, vm)
                                    2 -> OpenArticles(settings.browserMode, vm::setBrowserMode)
                                    TopicsPage -> FollowTopics(topics, followed, vm::toggleTopic, vm::loadTopics)
                                    4 -> Notifications(settings, followed.size, vm)
                                    5 -> CountForSources(settings.countReach, adsShown, vm::setCountReach)
                                    6 -> TourTabs()
                                    else -> TourHandles()
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!night) {
            if (page == TopicsPage) {
                Text(
                    if (followed.isEmpty()) stringResource(R.string.setup_topics_none) else pluralStringResource(R.plurals.setup_topics_chosen, followed.size, followed.size),
                    style = MetaStyle, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Space.xl, end = Space.xl, top = Space.s),
                )
            }
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Space.m, vertical = Space.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = { page-- }) { Text(stringResource(R.string.back)) }
                StepDots(page, Steps)
                Button(onClick = { page++ }, modifier = Modifier.height(48.dp)) {
                    Text(stringResource(R.string.setup_next))
                    Spacer(Modifier.size(Space.xs))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun titleOf(page: Int) = when (page) {
    1 -> R.string.setup_lang_title
    2 -> R.string.setup_open_title
    TopicsPage -> R.string.setup_topics_title
    4 -> R.string.setup_notify_title
    5 -> R.string.setup_count_title
    6 -> R.string.setup_tabs_title
    else -> R.string.setup_handles_title
}

@Composable
private fun painterOf(page: Int) = when (page) {
    1 -> painterResource(R.drawable.ic_language)
    2 -> painterResource(R.drawable.ic_open_in_new)
    TopicsPage -> painterResource(R.drawable.ic_radar)
    4 -> rememberVectorPainter(Icons.Filled.Notifications)
    5 -> painterResource(R.drawable.ic_chart)
    6 -> painterResource(R.drawable.ic_explore)
    else -> painterResource(R.drawable.ic_lightbulb)
}

/** The head is always night, so the status bar icons are light on every page; the bar below follows the page. */
@Composable
internal fun SystemBarIcons(navigationLight: Boolean) {
    val view = LocalView.current
    val dark = isSystemInDarkTheme()
    DisposableEffect(view, dark, navigationLight) {
        val window = (view.context as? android.app.Activity)?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        bars?.isAppearanceLightStatusBars = false
        bars?.isAppearanceLightNavigationBars = !dark && !navigationLight
        onDispose {
            bars?.isAppearanceLightStatusBars = !dark
            bars?.isAppearanceLightNavigationBars = !dark
        }
    }
}

@Composable
private fun NightButtons(primary: String, onPrimary: () -> Unit, secondary: String, onSecondary: () -> Unit) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = Space.l), horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = onPrimary, modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NebulaLight, contentColor = OnNebula),
        ) { Text(primary, style = MaterialTheme.typography.titleMedium) }
        TextButton(onClick = onSecondary, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFD4CAFF))) { Text(secondary) }
    }
}

@Composable
private fun Welcome(onStart: () -> Unit, onSkip: () -> Unit) {
    Column(Modifier.fillMaxSize().nightSky().statusBarsPadding()) {
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Image(painterResource(R.drawable.wordmark_on_night), stringResource(R.string.app_name), Modifier.fillMaxWidth().widthIn(max = 300.dp))
            Spacer(Modifier.height(36.dp))
            Text(
                stringResource(R.string.setup_welcome_title), style = MaterialTheme.typography.headlineSmall, color = MoonText, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(Space.m))
            Text(stringResource(R.string.setup_welcome_text), style = MaterialTheme.typography.bodyMedium, color = MoonMuted, textAlign = TextAlign.Center)
        }
        NightButtons(stringResource(R.string.setup_start), onStart, stringResource(R.string.setup_skip), onSkip)
    }
}

@Composable
private fun Closing(followed: Int, onDone: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().nightSky().statusBarsPadding()) {
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            // the supernova of the logo: a star with a soft glow
            Box(Modifier.size(120.dp).background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(StarYellow.copy(alpha = .4f), Color.Transparent))), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_supernova), null, Modifier.size(72.dp), tint = StarYellow)
            }
            Spacer(Modifier.height(Space.xl))
            Text(
                stringResource(R.string.setup_ready_title), style = MaterialTheme.typography.headlineSmall, color = MoonText, textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(Space.m))
            Text(if (followed > 0) pluralStringResource(R.plurals.setup_ready_follows, followed, followed) else stringResource(R.string.setup_ready_text), style = MaterialTheme.typography.bodyMedium, color = MoonMuted, textAlign = TextAlign.Center)
        }
        NightButtons(stringResource(R.string.setup_ready_cta), onDone, stringResource(R.string.back), onBack)
    }
}

@Composable
private fun LanguageAndDesign(settings: Settings, vm: SetupViewModel) {
    val context = LocalContext.current
    Lead(stringResource(R.string.setup_lang_lead))
    SetupCard {
        ChoiceItem(
            stringResource(R.string.settings_app_language),
            listOf(
                Choice(AppLanguage.Choice.SYSTEM, stringResource(R.string.app_lang_system)),
                Choice(AppLanguage.Choice.DE, stringResource(R.string.app_lang_de)),
                Choice(AppLanguage.Choice.EN, stringResource(R.string.app_lang_en)),
            ),
            remember { AppLanguage.current(context) },
            onSelect = { AppLanguage.set(context, it) },
            transparent = true,
        )
        CardDivider()
        ChoiceItem(stringResource(R.string.content_lang_title), ContentLang.entries.map { Choice(it, langLabel(it)) }, settings.contentLang, vm::setLang, transparent = true)
        CardDivider()
        ChoiceItem(
            stringResource(R.string.theme_title),
            listOf(
                Choice(ThemeMode.SYSTEM, stringResource(R.string.theme_system)),
                Choice(ThemeMode.LIGHT, stringResource(R.string.theme_light)),
                Choice(ThemeMode.DARK, stringResource(R.string.theme_dark)),
            ),
            settings.theme,
            onSelect = { vm.setTheme(context, it) },
            transparent = true,
        )
    }
}

@Composable
private fun OpenArticles(selected: BrowserMode, onSelect: (BrowserMode) -> Unit) {
    Lead(stringResource(R.string.setup_open_lead))
    Column(Modifier.selectableGroup()) {
        OptionCard(selected == BrowserMode.CUSTOM_TAB, stringResource(R.string.mode_custom_tab), stringResource(R.string.setup_mode_custom_tab_text), stringResource(R.string.setup_recommended)) { onSelect(BrowserMode.CUSTOM_TAB) }
        OptionCard(selected == BrowserMode.INTERNAL, stringResource(R.string.mode_internal), stringResource(R.string.mode_internal_text)) { onSelect(BrowserMode.INTERNAL) }
        OptionCard(selected == BrowserMode.CHOOSER, stringResource(R.string.mode_chooser), stringResource(R.string.mode_chooser_text)) { onSelect(BrowserMode.CHOOSER) }
    }
}

/**
 * Topics to follow, as chips: what rises right now (Latest's "Rising") and the evergreen topics with the most posts.
 * A tap follows, another one unfollows; nothing asks for a permission here. The page can be passed without a choice.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FollowTopics(topics: SetupTopics, followed: Set<String>, onToggle: (TopicRef) -> Unit, onLoad: () -> Unit) {
    LaunchedEffect(Unit) { onLoad() }
    Lead(stringResource(R.string.setup_topics_lead))
    when {
        !topics.loaded -> Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        topics.failed -> {
            Text(stringResource(R.string.setup_topics_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onLoad) { Text(stringResource(R.string.retry)) }
        }
        else -> {
            @Composable
            fun Group(title: String, list: List<TopicRef>) {
                if (list.isEmpty()) return
                Text(title.uppercase(), style = MetaStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = Space.xs).semantics { heading() })
                FlowRow(Modifier.fillMaxWidth().padding(bottom = Space.m), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    list.forEach { t ->
                        val on = t.key in followed
                        FilterChip(
                            selected = on,
                            onClick = { onToggle(t) },
                            label = { Text(if (t.key in topics.comets && !on) "☄ " + t.label else t.label, maxLines = 1) },
                            leadingIcon = if (on) ({ Icon(Icons.Filled.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }) else null,
                        )
                    }
                }
            }
            Group(stringResource(R.string.setup_topics_rising), topics.rising)
            Group(stringResource(R.string.setup_topics_evergreen), topics.evergreen)
        }
    }
    Text(stringResource(R.string.setup_topics_more), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Notifications(settings: Settings, followed: Int, vm: SetupViewModel) {
    val context = LocalContext.current
    // the same Android permission as in Settings: granted = switch on, denied = a hint instead of a silent "off"
    var denied by rememberSaveable { mutableStateOf(false) }
    var wanted by rememberSaveable { mutableStateOf("") }
    val request = rememberNotifyRequest { granted ->
        denied = !granted
        if (granted) {
            when (wanted) {
                "logbook" -> vm.setLogbookNotify(true)
                "follow" -> vm.setFollowNotify(true)
                else -> vm.setNotify(true)
            }
        }
    }
    Lead(stringResource(R.string.setup_notify_lead))
    SetupCard {
        SwitchRow(stringResource(R.string.logbook_notify_switch), stringResource(R.string.logbook_notify_explain), settings.logbookNotify) { on ->
            if (on) { wanted = "logbook"; request() } else vm.setLogbookNotify(false)
        }
        CardDivider()
        SwitchRow(stringResource(R.string.notify_switch), stringResource(R.string.notify_explain), settings.novaNotify) { on ->
            if (on) { wanted = "nova"; request() } else vm.setNotify(false)
        }
        // only with topics chosen on the page before: their notifications are a choice of their own, off like the others
        if (followed > 0) {
            CardDivider()
            SwitchRow(stringResource(R.string.follow_notify_switch), pluralStringResource(R.plurals.setup_follow_notify_text, followed, followed), settings.followNotify && hasNotifyPermission(context)) { on ->
                if (on) { wanted = "follow"; request() } else vm.setFollowNotify(false)
            }
        }
    }
    if (denied && !settings.logbookNotify && !settings.novaNotify) {
        ListItem(
            colors = rowColors(),
            headlineContent = { Text(stringResource(R.string.notify_denied), color = MaterialTheme.colorScheme.error) },
            supportingContent = { TextButton(onClick = { openNotificationSettings(context) }) { Text(stringResource(R.string.open_system_settings)) } },
        )
    }
}

@Composable
private fun CountForSources(on: Boolean, adsShown: Boolean, onChange: (Boolean) -> Unit) {
    Lead(stringResource(R.string.setup_count_lead))
    SetupCard {
        SwitchRow(
            stringResource(R.string.count_reach_switch),
            stringResource(R.string.setup_count_switch_text),
            on, onChange,
        )
    }
    Spacer(Modifier.height(Space.m))
    Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
        Fact(Modifier.weight(1f), true, R.string.setup_count_yes_title, if (adsShown) R.string.setup_count_yes_text_ads else R.string.setup_count_yes_text)
        Fact(Modifier.weight(1f), false, R.string.setup_count_no_title, R.string.setup_count_no_text)
    }
}

/** A short fact box: what is counted (tinted) and what is never part of it (outlined). */
@Composable
private fun Fact(modifier: Modifier, yes: Boolean, @androidx.annotation.StringRes title: Int, @androidx.annotation.StringRes text: Int) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier.clip(Radius.card)
            .background(if (yes) scheme.secondaryContainer else scheme.surfaceContainerLowest)
            .then(if (yes) Modifier else Modifier.border(1.dp, scheme.outlineVariant, Radius.card))
            .padding(Space.m),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            Icon(if (yes) Icons.Filled.Check else Icons.Filled.Close, null, Modifier.size(16.dp), tint = if (yes) scheme.onSecondaryContainer else scheme.onSurfaceVariant)
            Text(stringResource(title), style = MaterialTheme.typography.labelLarge, color = if (yes) scheme.onSecondaryContainer else scheme.onSurface)
        }
        Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = if (yes) scheme.onSecondaryContainer else scheme.onSurfaceVariant)
    }
}

@Composable
private fun TourTabs() {
    // the real navigation bar in miniature, Latest marked
    SetupCard(Modifier.padding(bottom = Space.m)) {
        Row(Modifier.fillMaxWidth().padding(vertical = Space.s), horizontalArrangement = Arrangement.SpaceAround) {
            MiniTab(R.drawable.ic_latest, R.string.tab_latest, true)
            MiniTab(R.drawable.ic_radar, R.string.tab_radar, false)
            MiniTab(R.drawable.ic_explore, R.string.tab_discover, false)
            MiniTab(R.drawable.ic_bookmark, R.string.tab_saved, false)
        }
    }
    SetupCard {
        TipRow(painterResource(R.drawable.ic_latest), stringResource(R.string.tab_latest), stringResource(R.string.setup_tab_latest))
        CardDivider()
        TipRow(painterResource(R.drawable.ic_radar), stringResource(R.string.tab_radar), stringResource(R.string.setup_tab_radar))
        CardDivider()
        TipRow(painterResource(R.drawable.ic_explore), stringResource(R.string.tab_discover), stringResource(R.string.setup_tab_discover))
        CardDivider()
        TipRow(painterResource(R.drawable.ic_bookmark), stringResource(R.string.tab_saved), stringResource(R.string.setup_tab_saved))
    }
}

@Composable
private fun MiniTab(@androidx.annotation.DrawableRes icon: Int, @androidx.annotation.StringRes label: Int, selected: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.widthIn(min = 64.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
        Box(
            Modifier.size(width = 52.dp, height = 30.dp).clip(RoundedCornerShape(15.dp)).background(if (selected) scheme.secondaryContainer else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) { Icon(painterResource(icon), null, Modifier.size(20.dp), tint = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant) }
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = if (selected) scheme.onSurface else scheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun TourHandles() {
    Lead(stringResource(R.string.setup_handles_lead))
    SetupCard {
        TipRow(rememberVectorPainter(Icons.Filled.Search), stringResource(R.string.setup_search_title), stringResource(R.string.setup_search_text))
        CardDivider()
        TipRow(rememberVectorPainter(Icons.Filled.Notifications), stringResource(R.string.setup_follow_title), stringResource(R.string.setup_follow_text))
        CardDivider()
        TipRow(painterResource(R.drawable.ic_bookmark_border), stringResource(R.string.setup_save_title), stringResource(R.string.setup_save_text))
        CardDivider()
        TipRow(painterResource(R.drawable.ic_supernova), stringResource(R.string.setup_nova_title), stringResource(R.string.setup_nova_text))
        CardDivider()
        TipRow(rememberVectorPainter(Icons.Filled.Settings), stringResource(R.string.settings), stringResource(R.string.setup_settings_text))
    }
}
