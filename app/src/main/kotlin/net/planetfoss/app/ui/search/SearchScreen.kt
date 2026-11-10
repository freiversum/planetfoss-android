// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import net.planetfoss.app.R
import net.planetfoss.app.ui.theme.ContentMaxWidth
import net.planetfoss.app.ui.theme.Space
import net.planetfoss.app.data.FeedRepository
import net.planetfoss.app.data.prefs.SettingsRepository
import net.planetfoss.app.ui.components.ChipTag
import net.planetfoss.app.ui.components.ListState
import net.planetfoss.app.ui.components.UiError
import net.planetfoss.app.ui.list.ItemListContent
import net.planetfoss.core.api.Item
import net.planetfoss.core.api.TopicRef
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

/** The website's kinds of source, in its order (`sources.kind`). */
private val SOURCE_KINDS = listOf("verlag", "magazin", "blog", "projekt", "verein", "anleitung", "video")

@Composable
private fun kindLabel(kind: String): String = stringResource(
    when (kind) {
        "verlag" -> R.string.kind_verlag
        "magazin" -> R.string.kind_magazin
        "blog" -> R.string.kind_blog
        "projekt" -> R.string.kind_projekt
        "verein" -> R.string.kind_verein
        "anleitung" -> R.string.kind_anleitung
        else -> R.string.kind_video
    },
)

/** Time filter over the hits; the server already sorts by relevance × recency. */
enum class Period(val duration: Duration?) { ANY(null), DAY(Duration.ofDays(1)), WEEK(Duration.ofDays(7)), MONTH(Duration.ofDays(30)) }

/** Filters work on the hits the server returned (at most 60) – no extra requests. */
data class SearchFilters(val rubric: String? = null, val period: Period = Period.ANY, val supernovaOnly: Boolean = false, val kind: String? = null)

data class SearchUi(
    val query: String = "",
    val hits: ListState<Item> = ListState(),
    val topics: List<TopicRef> = emptyList(),
    /** Rubrics present in the hits, for the filter chips. */
    val rubrics: List<Pair<String, String>> = emptyList(),
    /** Kinds of source present in the hits (verlag, blog, projekt …), as on the website's source filter. */
    val kinds: List<String> = emptyList(),
    val filters: SearchFilters = SearchFilters(),
)

private data class Answer(val items: List<Item> = emptyList(), val topics: List<TopicRef> = emptyList(), val searching: Boolean = false, val error: UiError? = null, val done: Boolean = false)

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repo: FeedRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val handle: SavedStateHandle,
) : ViewModel() {
    val query: StateFlow<String> = handle.getStateFlow("q", "")
    private val filters = MutableStateFlow(SearchFilters())
    private val retry = MutableStateFlow(0)

    private val answer = combine(query.map { it.trim() }.debounce(350).distinctUntilChanged(), retry) { q, _ -> q }
        .mapLatest { q ->
            if (q.length < 2) return@mapLatest Answer()
            val lang = settings.settings.first().contentLang
            runCatching { repo.search(q, lang) }.fold(
                onSuccess = { Answer(it.items, it.topics, done = true) },
                onFailure = { Answer(error = UiError.from(it), done = true) },
            )
        }

    val state: StateFlow<SearchUi> = combine(query, answer, filters) { q, a, f ->
        val now = clock.instant()
        val shown = a.items.filter { item ->
            (f.rubric == null || item.categories.any { it.slug == f.rubric }) &&
                (f.period.duration == null || item.publishedAt >= now.minus(f.period.duration)) &&
                (!f.supernovaOnly || item.topic?.supernova == true) &&
                (f.kind == null || item.source.kind == f.kind)
        }
        SearchUi(
            query = q,
            hits = ListState(items = shown, loaded = a.done, fetched = a.done, error = a.error),
            topics = a.topics,
            rubrics = a.items.flatMap { it.categories }.distinctBy { it.slug }.map { it.slug to it.name },
            kinds = a.items.mapNotNull { it.source.kind }.distinct().filter { it in SOURCE_KINDS }.sortedBy { SOURCE_KINDS.indexOf(it) },
            filters = f,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUi())

    fun setQuery(q: String) {
        handle["q"] = q.take(80)
        filters.value = SearchFilters()
    }

    fun setFilters(f: SearchFilters) {
        filters.value = f
    }

    fun retry() {
        retry.value++
    }
}

/** Search over PlanetFOSS (titles and teasers, as /suche/ on the website) with local filters. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(onBack: () -> Unit, onTopic: (TopicRef) -> Unit, vm: SearchViewModel = hiltViewModel()) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyStaggeredGridState()
    LaunchedEffect(Unit) { if (query.isEmpty()) focus.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                title = {
                    TextField(
                        value = query,
                        onValueChange = vm::setQuery,
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        trailingIcon = if (query.isEmpty()) null else ({
                            IconButton(onClick = { vm.setQuery(""); focus.requestFocus() }) { Icon(Icons.Filled.Clear, stringResource(R.string.search_clear)) }
                        }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                },
            )
        },
    ) { padding ->
        androidx.compose.foundation.layout.Box(Modifier.padding(padding).fillMaxSize()) {
            if (ui.query.trim().length < 2) {
                net.planetfoss.app.ui.components.CenterMessage(stringResource(R.string.search_intro), icon = rememberVectorPainter(Icons.Filled.Search))
                return@Box
            }
            if (!ui.hits.loaded) {
                net.planetfoss.app.ui.components.CenterMessage(stringResource(R.string.loading), loading = true)
                return@Box
            }
            val noHits = ui.hits.items.isEmpty() && ui.topics.isEmpty() && ui.hits.error == null
            ItemListContent(
                state = ui.hits,
                listState = listState,
                onRetry = vm::retry,
                emptyText = stringResource(R.string.search_none),
                leadFirst = false,
                header = if (ui.hits.error != null) null else ({
                    if (ui.topics.isNotEmpty()) {
                        item(key = "topics", span = StaggeredGridItemSpan.FullLine) {
                            FlowRow(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                                Text(stringResource(R.string.search_topics), style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.fillMaxWidth().semantics { heading() })
                                ui.topics.forEach { t -> ChipTag(t.label, t.supernova) { onTopic(t) } }
                            }
                        }
                    }
                    item(key = "filters", span = StaggeredGridItemSpan.FullLine) { Filters(ui, vm::setFilters) }
                    if (noHits || ui.hits.items.isEmpty()) {
                        item(key = "none", span = StaggeredGridItemSpan.FullLine) {
                            Text(stringResource(R.string.search_none), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = Space.xl))
                        }
                    }
                }),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Filters(ui: SearchUi, set: (SearchFilters) -> Unit) {
    val f = ui.filters
    val daySpoken = stringResource(R.string.period_day_spoken)
    Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        // period: one connected button group, exactly one selected; each button as wide as its label
        // (equal widths cut “Jederzeit” once the selected button grows), scrolls at large font sizes
        Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Period.entries.forEachIndexed { i, p ->
                ToggleButton(
                    checked = f.period == p,
                    onCheckedChange = { set(f.copy(period = p)) },
                    shapes = when (i) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        Period.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    // “24 h” fits the narrow button; TalkBack says “24 hours”
                    modifier = Modifier.semantics {
                        role = Role.RadioButton
                        if (p == Period.DAY) contentDescription = daySpoken
                    },
                ) {
                    Text(
                        stringResource(when (p) {
                            Period.ANY -> R.string.period_any
                            Period.DAY -> R.string.period_day
                            Period.WEEK -> R.string.period_week
                            Period.MONTH -> R.string.period_month
                        }),
                        maxLines = 1,
                    )
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            FilterChip(selected = f.supernovaOnly, onClick = { set(f.copy(supernovaOnly = !f.supernovaOnly)) }, label = { Text(stringResource(R.string.filter_supernova)) })
            ui.rubrics.forEach { (slug, name) ->
                FilterChip(selected = f.rubric == slug, onClick = { set(f.copy(rubric = if (f.rubric == slug) null else slug)) }, label = { Text(name) })
            }
        }
        // kind of source (publishers, blogs, projects …) – only when the hits come from more than one kind
        if (ui.kinds.size > 1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                ui.kinds.forEach { k ->
                    FilterChip(selected = f.kind == k, onClick = { set(f.copy(kind = if (f.kind == k) null else k)) }, label = { Text(kindLabel(k)) })
                }
            }
        }
    }
}
