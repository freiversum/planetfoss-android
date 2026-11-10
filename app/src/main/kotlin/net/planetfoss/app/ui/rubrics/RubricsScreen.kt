// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.rubrics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import net.planetfoss.app.ui.components.RefreshBox
import net.planetfoss.app.ui.components.ScrollToTopOnTabTap
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.planetfoss.app.R
import net.planetfoss.app.ui.theme.rubricColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import net.planetfoss.app.ui.theme.ContentMaxWidth
import net.planetfoss.app.ui.theme.Space
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import net.planetfoss.app.data.FeedRepository
import net.planetfoss.app.data.prefs.SettingsRepository
import net.planetfoss.app.ui.components.CenterMessage
import net.planetfoss.app.ui.components.ChipTag
import net.planetfoss.app.ui.components.ErrorBanner
import net.planetfoss.app.ui.components.ListState
import net.planetfoss.app.ui.components.TabTopBar
import net.planetfoss.app.ui.components.UiError
import net.planetfoss.app.ui.components.errorText
import net.planetfoss.core.api.ContentLang
import net.planetfoss.core.api.Rubric
import net.planetfoss.core.api.TopicRef
import javax.inject.Inject

@HiltViewModel
class RubricsViewModel @Inject constructor(
    private val repo: FeedRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ListState<Rubric>())
    val state: StateFlow<ListState<Rubric>> = _state.asStateFlow()
    val lang: StateFlow<ContentLang> = settings.settings.map { it.contentLang }.stateIn(viewModelScope, SharingStarted.Eagerly, ContentLang.ALL)

    init {
        refresh()
    }

    fun refresh() {
        if (_state.value.refreshing) return
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true, error = null) }
            val result = runCatching { repo.rubrics() }
            _state.update {
                it.copy(
                    items = result.getOrDefault(it.items),
                    loaded = true,
                    fetched = result.isSuccess || it.fetched,
                    refreshing = false,
                    error = result.exceptionOrNull()?.let(UiError::from),
                )
            }
        }
    }

    fun setLang(lang: ContentLang) {
        viewModelScope.launch { settings.setContentLang(lang) }
    }
}

/**
 * The Discover tab (docs/09-latest-tab.md): browsing by subject, no stream – search, the sky, the logbook and weekly
 * review, videos and Sternstunden, the rubrics with their hot topics, then topics and sources.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RubricsScreen(
    onSettings: () -> Unit,
    onSearch: () -> Unit,
    onRubric: (Rubric) -> Unit,
    onTopic: (TopicRef) -> Unit,
    onVideos: () -> Unit,
    onStarred: () -> Unit,
    onTopics: () -> Unit,
    onSources: () -> Unit,
    onWeekly: () -> Unit = {},
    onLogbook: () -> Unit = {},
    onSky: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    vm: RubricsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val lang by vm.lang.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    ScrollToTopOnTabTap(listState)
    Scaffold(topBar = { TabTopBar(stringResource(R.string.tab_discover), lang, vm::setLang, onSettings, onSearch = null, onBack = onBack) }) { padding ->
        RefreshBox(isRefreshing = state.refreshing && state.items.isNotEmpty(), onRefresh = vm::refresh, modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                state.items.isEmpty() && state.error != null -> CenterMessage(errorText(state.error!!, hasData = false), onRetry = vm::refresh)
                state.items.isEmpty() -> CenterMessage(stringResource(R.string.loading), loading = true)
                else -> Column(Modifier.fillMaxSize()) {
                    state.error?.let { ErrorBanner(errorText(it, hasData = true), vm::refresh) }
                    LazyColumn(state = listState, contentPadding = PaddingValues(vertical = Space.s), horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
                        // search as a field: on Discover one looks for something rather than reads
                        item(key = "search") {
                            androidx.compose.material3.Surface(
                                onClick = onSearch,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s),
                            ) {
                                Row(Modifier.padding(horizontal = Space.l, vertical = Space.m), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                                    Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    androidx.compose.material3.Text(stringResource(R.string.discover_search), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        // the sky: supernovas and comets of the last seven days
                        item(key = "sky") {
                            ListItem(
                                headlineContent = { androidx.compose.material3.Text(stringResource(R.string.tab_supernovas)) },
                                supportingContent = { androidx.compose.material3.Text(stringResource(R.string.sky_hint)) },
                                leadingContent = { Icon(painterResource(R.drawable.ic_supernova), null) },
                                modifier = Modifier.widthIn(max = ContentMaxWidth).clickable(role = Role.Button, onClick = onSky),
                            )
                        }
                        // the website's logbook (/logbuch/): every morning at 6:00 what mattered in the 24 hours before
                        item(key = "logbook") {
                            ListItem(
                                headlineContent = { androidx.compose.material3.Text(stringResource(R.string.logbook_title)) },
                                supportingContent = { androidx.compose.material3.Text(stringResource(R.string.logbook_hint)) },
                                leadingContent = { Icon(painterResource(R.drawable.ic_logbook), null) },
                                modifier = Modifier.widthIn(max = ContentMaxWidth).clickable(role = Role.Button, onClick = onLogbook),
                            )
                        }
                        // the website's weekly review (/rueckblick/): what the week was about, compiled automatically
                        item(key = "weekly") {
                            ListItem(
                                headlineContent = { androidx.compose.material3.Text(stringResource(R.string.weekly_title)) },
                                supportingContent = { androidx.compose.material3.Text(stringResource(R.string.weekly_hint)) },
                                leadingContent = { Icon(painterResource(R.drawable.ic_calendar), null) },
                                modifier = Modifier.widthIn(max = ContentMaxWidth).clickable(role = Role.Button, onClick = onWeekly),
                            )
                        }
                        item(key = "starred") {
                            ListItem(
                                headlineContent = { androidx.compose.material3.Text(stringResource(R.string.starred)) },
                                supportingContent = { androidx.compose.material3.Text(stringResource(R.string.starred_hint)) },
                                leadingContent = { Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.tertiary) },
                                modifier = Modifier.widthIn(max = ContentMaxWidth).clickable(role = Role.Button, onClick = onStarred),
                            )
                        }
                        item(key = "videos") {
                            ListItem(
                                headlineContent = { androidx.compose.material3.Text(stringResource(R.string.videos)) },
                                leadingContent = { Icon(painterResource(R.drawable.ic_video), null) },
                                modifier = Modifier.widthIn(max = ContentMaxWidth).clickable(role = Role.Button, onClick = onVideos),
                            )
                            HorizontalDivider(Modifier.widthIn(max = ContentMaxWidth))
                        }
                        items(state.items, key = { it.slug }) { rubric ->
                            ListItem(
                                // the rubric's colour on the website, so it is recognised at a glance; in the
                                // headline (not leadingContent) so it stays on the name's line when chips follow
                                headlineContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                                        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                                            Box(Modifier.size(12.dp).clip(CircleShape).background(rubricColor(rubric.slug) ?: MaterialTheme.colorScheme.outline))
                                        }
                                        androidx.compose.material3.Text(rubric.name)
                                    }
                                },
                                supportingContent = if (rubric.hotTopics.isEmpty()) null else ({
                                    // chips start under the name
                                    FlowRow(Modifier.padding(start = 40.dp, top = Space.xs), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                                        rubric.hotTopics.forEach { t -> ChipTag(t.label, t.supernova) { onTopic(t) } }
                                    }
                                }),
                                modifier = Modifier.widthIn(max = ContentMaxWidth).clickable(role = Role.Button) { onRubric(rubric) },
                            )
                        }
                        item(key = "directories-divider") { HorizontalDivider(Modifier.widthIn(max = ContentMaxWidth)) }
                        // the website's overview pages: topics (/themen/) and the source directory (/quellen/)
                        item(key = "topics") {
                            ListItem(
                                headlineContent = { androidx.compose.material3.Text(stringResource(R.string.topics_title)) },
                                supportingContent = { androidx.compose.material3.Text(stringResource(R.string.topics_hint)) },
                                leadingContent = { Icon(painterResource(R.drawable.ic_supernova), null) },
                                modifier = Modifier.widthIn(max = ContentMaxWidth).clickable(role = Role.Button, onClick = onTopics),
                            )
                        }
                        item(key = "sources") {
                            ListItem(
                                headlineContent = { androidx.compose.material3.Text(stringResource(R.string.sources_title)) },
                                supportingContent = { androidx.compose.material3.Text(stringResource(R.string.sources_hint)) },
                                leadingContent = { Icon(painterResource(R.drawable.ic_article), null) },
                                modifier = Modifier.widthIn(max = ContentMaxWidth).clickable(role = Role.Button, onClick = onSources),
                            )
                        }
                    }
                }
            }
        }
    }
}
