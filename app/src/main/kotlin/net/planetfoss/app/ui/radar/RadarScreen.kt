// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.radar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.planetfoss.app.R
import net.planetfoss.app.data.FeedRepository
import net.planetfoss.app.data.FollowRepository
import net.planetfoss.app.data.RadarCounter
import net.planetfoss.app.data.RadarRow
import net.planetfoss.app.data.prefs.SettingsRepository
import net.planetfoss.app.ui.components.CenterMessage
import net.planetfoss.app.ui.components.ErrorBanner
import net.planetfoss.app.ui.components.ItemCard
import net.planetfoss.app.ui.components.LocalItemActions
import net.planetfoss.app.ui.components.RefreshBox
import net.planetfoss.app.ui.components.ScrollToTopOnTabTap
import net.planetfoss.app.ui.components.SectionHead
import net.planetfoss.app.ui.components.TabTopBar
import net.planetfoss.app.ui.components.UiError
import net.planetfoss.app.ui.components.errorText
import net.planetfoss.app.ui.theme.ContentMaxWidth
import net.planetfoss.app.ui.theme.LocalPlanetColors
import net.planetfoss.app.ui.theme.MetaStyle
import net.planetfoss.app.ui.theme.Radius
import net.planetfoss.app.ui.theme.Space
import net.planetfoss.core.api.ContentLang
import net.planetfoss.core.api.Item
import net.planetfoss.core.api.Source
import net.planetfoss.core.api.TopicRef
import javax.inject.Inject

/** A suggestion for an empty Radar: something in the sky right now. */
data class RadarHint(val key: String, val label: String, val comet: Boolean)

data class RadarUi(
    val rows: List<RadarRow> = emptyList(),
    /** Posts of everything followed, newest first, without doubles (a post can be in a topic and its source). */
    val items: List<Item> = emptyList(),
    val hints: List<RadarHint> = emptyList(),
    /** False until the follows were read once (no flash of the empty state). */
    val loaded: Boolean = false,
    val refreshing: Boolean = false,
    val error: UiError? = null,
)

/**
 * Radar (STRATEGIE.md 4.2, variant A, 6.11.2026): what the user follows, at a glance. Page 1 of each followed topic or source
 * in the content language – the same requests the follow check makes – counted against the end of the last visit
 * ([SeenRepository]). Nothing about the follows leaves the phone beyond these public list requests.
 */
@HiltViewModel
class RadarViewModel @Inject constructor(
    private val follows: FollowRepository,
    private val feed: FeedRepository,
    private val settings: SettingsRepository,
    private val counter: RadarCounter,
) : ViewModel() {
    private val _state = MutableStateFlow(RadarUi())
    val state: StateFlow<RadarUi> = _state.asStateFlow()
    val lang: StateFlow<ContentLang> = settings.settings.map { it.contentLang }.stateIn(viewModelScope, SharingStarted.Eagerly, ContentLang.ALL)
    private var loadedAt = 0L
    private var lastKeys: Set<String>? = null

    init {
        // a follow added or removed elsewhere (bell on a topic page): load again
        viewModelScope.launch {
            follows.all.collect { list ->
                val keys = list.map { it.kind + ":" + it.ref }.toSet()
                if (keys != lastKeys) {
                    lastKeys = keys
                    load()
                }
            }
        }
    }

    /** Back on the tab: ask again after five minutes. */
    fun resumed() {
        if (System.currentTimeMillis() - loadedAt > 5 * 60_000L) load()
    }

    fun setLang(l: ContentLang) {
        viewModelScope.launch {
            settings.setContentLang(l)
            load()
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true, error = null) }
            val c = counter.count()
            if (c.rows.isEmpty() && c.error == null) {
                // nothing followed: suggestions from the sky right now
                val lang = settings.settings.first().contentLang
                val novas = runCatching { feed.refreshSupernovas(lang) }.getOrDefault(emptyList()).take(4).map { RadarHint(it.key, it.label, false) }
                val comets = runCatching { feed.observeSupernovas(lang).first().first.comets.take(2).map { RadarHint(it.key, it.label, true) } }.getOrDefault(emptyList())
                _state.value = RadarUi(hints = novas + comets, loaded = true)
            } else {
                val error = c.error?.let(UiError::from)
                _state.update { old ->
                    if (error != null && old.rows.isNotEmpty()) old.copy(refreshing = false, error = error)
                    else RadarUi(c.rows, c.items, loaded = true, error = error)
                }
            }
            if (c.error == null) loadedAt = System.currentTimeMillis()
        }
    }
}

@Composable
fun RadarScreen(onSettings: () -> Unit, onSearch: () -> Unit, vm: RadarViewModel = hiltViewModel()) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val lang by vm.lang.collectAsStateWithLifecycle()
    val actions = LocalItemActions.current
    val listState = rememberLazyListState()
    ScrollToTopOnTabTap(listState)
    LifecycleResumeEffect(Unit) { vm.resumed(); onPauseOrDispose { } }
    Scaffold(topBar = { TabTopBar(stringResource(R.string.tab_radar), lang, vm::setLang, onSettings, onSearch = onSearch) }) { padding ->
        RefreshBox(isRefreshing = ui.refreshing && ui.loaded, onRefresh = vm::load, modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                !ui.loaded && ui.error != null -> CenterMessage(errorText(ui.error!!, hasData = false), onRetry = vm::load)
                !ui.loaded -> CenterMessage(stringResource(R.string.loading), loading = true)
                else -> Column(Modifier.fillMaxSize()) {
                    ui.error?.let { ErrorBanner(errorText(it, hasData = true), vm::load) }
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(Space.l),
                        verticalArrangement = Arrangement.spacedBy(Space.s),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        val width = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth()
                        if (ui.rows.isEmpty()) {
                            item(key = "empty") { EmptyRadar(width) }
                            if (ui.hints.isNotEmpty()) {
                                item(key = "hints-h") { SectionHead(stringResource(R.string.radar_hints), width.padding(top = Space.m)) }
                                items(ui.hints, key = { "hint-" + it.key }) { h ->
                                    FollowRow(
                                        label = h.label,
                                        kind = stringResource(if (h.comet) R.string.radar_comet else R.string.radar_supernova),
                                        icon = if (h.comet) "☄" else "✦", count = null, burning = false, modifier = width,
                                    ) { actions.topic(TopicRef(h.key, h.label, !h.comet)) }
                                }
                            }
                        } else {
                            item(key = "follows-h") { SectionHead(stringResource(R.string.radar_follows), width, hint = stringResource(R.string.radar_follows_hint)) }
                            items(ui.rows, key = { "f-" + it.follow.kind + it.follow.ref }) { r ->
                                val topic = r.follow.kind == FollowRepository.TOPIC
                                val kind = when {
                                    r.supernova -> pluralStringResource(R.plurals.radar_burning, r.sources, r.sources)
                                    r.comet -> stringResource(R.string.radar_comet)
                                    topic -> stringResource(R.string.radar_topic)
                                    else -> stringResource(R.string.radar_source)
                                }
                                FollowRow(
                                    label = r.follow.label, kind = kind,
                                    icon = when { r.supernova -> "✦"; r.comet -> "☄"; topic -> "#"; else -> r.follow.label.take(2) },
                                    count = if (r.failed) -1 else r.fresh, burning = r.supernova, modifier = width,
                                ) {
                                    if (topic) actions.topic(TopicRef(r.follow.ref, r.follow.label, r.supernova))
                                    else actions.source(Source(r.follow.ref, r.follow.label))
                                }
                            }
                            if (ui.items.isNotEmpty()) {
                                // “New for you” only when something is new since the last visit, else simply the latest
                                val anyNew = ui.rows.any { it.fresh > 0 }
                                item(key = "items-h") { SectionHead(stringResource(if (anyNew) R.string.radar_items else R.string.radar_latest), width.padding(top = Space.m)) }
                                items(ui.items, key = { "i-" + it.id }) { ItemCard(it, width) }
                            }
                        }
                        item(key = "end") { Box(Modifier.padding(bottom = Space.l)) }
                    }
                }
            }
        }
    }
}

/** A followed topic or source (or a suggestion): sign, name, kind, and on the right how many posts are new. */
@Composable
private fun FollowRow(label: String, kind: String, icon: String, count: Int?, burning: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val planet = LocalPlanetColors.current
    Card(
        onClick = onClick, modifier = modifier, shape = Radius.card,
        colors = CardDefaults.cardColors(containerColor = if (burning) planet.night else MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        ListItem(
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
                headlineColor = if (burning) Color.White else MaterialTheme.colorScheme.onSurface,
                supportingColor = if (burning) Color(0xFFC9CEE3) else MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            leadingContent = {
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    Text(icon, style = MaterialTheme.typography.titleMedium, color = if (burning) planet.supernova else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            headlineContent = { Text(label, fontWeight = FontWeight.Bold) },
            supportingContent = { Text(kind, style = MetaStyle) },
            trailingContent = when {
                count == null -> null
                count < 0 -> ({ Text(stringResource(R.string.radar_failed), style = MetaStyle) })
                count == 0 -> ({ Text(stringResource(R.string.radar_nothing_new), style = MetaStyle) })
                else -> ({
                    Text(
                        "+$count", style = MetaStyle, fontWeight = FontWeight.Bold,
                        color = if (burning) Color(0xFF2DD4BF) else planet.aurora,
                    )
                })
            },
        )
    }
}

@Composable
private fun EmptyRadar(modifier: Modifier) {
    Card(modifier, shape = Radius.card, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Icon(painterResource(R.drawable.ic_radar), null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.radar_empty_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.radar_empty_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
