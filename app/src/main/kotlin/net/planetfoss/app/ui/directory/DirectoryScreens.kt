// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.directory

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.planetfoss.app.R
import net.planetfoss.app.data.FeedRepository
import net.planetfoss.app.data.prefs.SettingsRepository
import net.planetfoss.app.ui.components.CenterMessage
import net.planetfoss.app.ui.components.ChipTag
import net.planetfoss.app.ui.components.openBeside
import net.planetfoss.app.ui.components.LogoDot
import net.planetfoss.app.ui.components.RefreshBox
import net.planetfoss.app.ui.components.TopicStatusBadge
import net.planetfoss.app.ui.components.UiError
import net.planetfoss.app.ui.components.errorText
import net.planetfoss.app.ui.theme.ContentMaxWidth
import net.planetfoss.app.ui.theme.MetaStyle
import net.planetfoss.app.ui.theme.Space
import net.planetfoss.core.api.Source
import net.planetfoss.core.api.SourcesAnswer
import net.planetfoss.core.api.TopicRef
import net.planetfoss.core.api.TopicsAnswer
import javax.inject.Inject

/** One answer of the API, loaded on open and on pull-to-refresh, in the content language. */
data class Loaded<T>(val data: T? = null, val refreshing: Boolean = false, val error: UiError? = null, val since: Long? = null) {
    companion object {
        /** A fresh or cached answer, or an error that keeps what was there. */
        fun <T> of(r: Result<net.planetfoss.core.api.Fetched<T>>, old: Loaded<T>): Loaded<T> {
            val got = r.getOrNull()
            return Loaded(
                got?.value ?: old.data,
                false,
                r.exceptionOrNull()?.let(UiError::from) ?: UiError.Offline.takeIf { got?.cachedAt != null },
                got?.cachedAt ?: if (got != null) null else old.since,
            )
        }
    }
}

@HiltViewModel
class TopicsViewModel @Inject constructor(private val repo: FeedRepository, private val settings: SettingsRepository) : ViewModel() {
    private val _state = MutableStateFlow(Loaded<TopicsAnswer>())
    val state: StateFlow<Loaded<TopicsAnswer>> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true, error = null) }
            val r = runCatching { repo.topicsOrCached(settings.settings.first().contentLang) }
            _state.update { Loaded.of(r, it) }
        }
    }
}

@HiltViewModel
class SourcesViewModel @Inject constructor(private val repo: FeedRepository, private val settings: SettingsRepository) : ViewModel() {
    private val _state = MutableStateFlow(Loaded<SourcesAnswer>())
    val state: StateFlow<Loaded<SourcesAnswer>> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true, error = null) }
            val r = runCatching { repo.sourcesOrCached(settings.settings.first().contentLang) }
            _state.update { Loaded.of(r, it) }
        }
    }
}

/** Topics like the website's /themen/: “In orbit” with each topic's status, then the evergreen topics with their posts of 30 days. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TopicsScreen(onBack: () -> Unit, onTopic: (TopicRef) -> Unit, vm: TopicsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    DirectoryScaffold(stringResource(R.string.topics_title), onBack, state, vm::refresh) { data ->
        LazyColumn(contentPadding = PaddingValues(vertical = Space.s), horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
            if (data.orbit.isNotEmpty()) {
                item(key = "orbit-h") { SectionTitle(stringResource(R.string.in_orbit)) }
                items(data.orbit, key = { "o-" + it.key }) { t ->
                    ListItem(
                        headlineContent = { Text(t.label) },
                        trailingContent = { TopicStatusBadge(t.status) },
                        modifier = Modifier.widthIn(max = ContentMaxWidth).openBeside("topic:" + t.key).clickable(role = Role.Button) { onTopic(t.ref) },
                    )
                }
            }
            if (data.topics.isNotEmpty()) {
                item(key = "ever-h") { SectionTitle(stringResource(R.string.evergreen_topics)) }
                item(key = "ever") {
                    FlowRow(
                        Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = Space.l),
                        horizontalArrangement = Arrangement.spacedBy(Space.s),
                    ) {
                        data.topics.forEach { t -> ChipTag(t.label, false) { onTopic(t.ref) } }
                    }
                }
            }
        }
    }
}

/**
 * The source directory like the website's /quellen/ (“The solar system”): every source by group, with its
 * posts of the week; a filter shows the sources that can be supported (subscription, donation, recommendation).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(onBack: () -> Unit, onSource: (Source) -> Unit, vm: SourcesViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var supportOnly by rememberSaveable { mutableStateOf(false) }
    DirectoryScaffold(stringResource(R.string.sources_title), onBack, state, vm::refresh) { data ->
        LazyColumn(contentPadding = PaddingValues(bottom = Space.l), horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
            item(key = "filter") {
                Row(Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s)) {
                    FilterChip(
                        selected = supportOnly,
                        onClick = { supportOnly = !supportOnly },
                        label = { Text(stringResource(R.string.sources_support_filter)) },
                        leadingIcon = { Icon(Icons.Filled.Favorite, null, Modifier.size(18.dp)) },
                    )
                }
            }
            data.groups.forEach { g ->
                val list = if (supportOnly) g.sources.filter { it.support != null } else g.sources
                if (list.isNotEmpty()) {
                    item(key = "g-" + g.key) { SectionTitle(g.name + " · " + list.size) }
                    items(list, key = { g.key + "-" + it.slug }) { s -> SourceRow(s) { onSource(s) } }
                }
            }
        }
    }
}

@Composable
private fun SourceRow(s: Source, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(s.name) },
        supportingContent = {
            Text(
                listOfNotNull(s.lang?.uppercase(), pluralStringResource(R.plurals.posts_week, s.week, s.week)).joinToString(" · "),
                style = MetaStyle,
            )
        },
        leadingContent = {
            val logo = s.logo
            if (logo != null) {
                LogoDot(logo, Modifier.size(28.dp).clip(CircleShape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
            } else {
                val color = s.color?.let { runCatching { Color(it.toColorInt()) }.getOrNull() } ?: MaterialTheme.colorScheme.primary
                Box(Modifier.size(28.dp).clip(CircleShape).background(color))
            }
        },
        trailingContent = if (s.support != null) ({
            Icon(Icons.Filled.Favorite, stringResource(R.string.sources_can_support), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }) else null,
        modifier = Modifier.widthIn(max = ContentMaxWidth).openBeside("source:" + s.slug).clickable(role = Role.Button, onClick = onClick),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth()
            .padding(start = Space.l, end = Space.l, top = Space.l, bottom = Space.xs).semantics { heading() },
    )
}

/** Top bar with back arrow, pull-to-refresh, loading and error states – the frame of both directory screens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> DirectoryScaffold(title: String, onBack: () -> Unit, state: Loaded<T>, onRefresh: () -> Unit, content: @Composable (T) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        RefreshBox(isRefreshing = state.refreshing && state.data != null, onRefresh = onRefresh, modifier = Modifier.padding(padding).fillMaxSize()) {
            val data = state.data
            when {
                data != null -> Column(Modifier.fillMaxSize()) {
                    state.error?.let { net.planetfoss.app.ui.components.ErrorBanner(errorText(it, hasData = true, since = state.since), onRefresh) }
                    content(data)
                }
                state.error != null -> CenterMessage(errorText(state.error, hasData = false), onRetry = onRefresh)
                else -> CenterMessage(stringResource(R.string.loading), loading = true)
            }
        }
    }
}
