// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.saved

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.planetfoss.app.R
import net.planetfoss.app.data.SavedRepository
import net.planetfoss.app.ui.components.ListState
import net.planetfoss.app.ui.components.ScrollToTopOnTabTap
import net.planetfoss.app.ui.list.ItemListContent
import net.planetfoss.core.api.Item
import javax.inject.Inject

/** Saved ids and toggling, for every card in the app (scoped to the navigation root). */
@HiltViewModel
class SavedViewModel @Inject constructor(
    private val repo: SavedRepository,
    private val bookmarks: net.planetfoss.app.data.bookmarks.BookmarkRepository,
) : ViewModel() {
    /** The bookmark service and how many saved articles are on their way to it or failed – for the note on top. */
    val remote: StateFlow<RemoteUi> = kotlinx.coroutines.flow.combine(bookmarks.config, bookmarks.pendingAndFailed) { c, (pending, failed) ->
        RemoteUi(c.service?.label.takeIf { c.active }, pending, failed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemoteUi())

    fun retry() {
        viewModelScope.launch { bookmarks.retryFailed() }
    }

    val ids: StateFlow<Set<Long>> = repo.ids.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val state: StateFlow<ListState<Item>> = repo.items
        .map { ListState(items = it, loaded = true, fetched = true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListState())

    fun toggle(item: Item) {
        viewModelScope.launch { repo.toggle(item) }
    }
}

/** [service] is null when none is set up. */
data class RemoteUi(val service: String? = null, val pending: Int = 0, val failed: Int = 0)

/** Above the saved articles: what is still on its way to the bookmark service, and what failed (with “try again”). */
@Composable
private fun RemoteNote(remote: RemoteUi, onRetry: () -> Unit, onSettings: () -> Unit) {
    val service = remote.service ?: return
    val res = androidx.compose.ui.platform.LocalContext.current.resources
    androidx.compose.material3.Card(
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (remote.failed > 0) androidx.compose.material3.MaterialTheme.colorScheme.errorContainer
                else androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = net.planetfoss.app.ui.theme.Radius.card,
        modifier = Modifier.fillMaxWidth(),
    ) {
        androidx.compose.foundation.layout.Column(Modifier.padding(net.planetfoss.app.ui.theme.Space.l)) {
            if (remote.pending > 0) Text(res.getQuantityString(R.plurals.bookmark_pending, remote.pending, remote.pending, service))
            if (remote.failed > 0) {
                Text(res.getQuantityString(R.plurals.bookmark_failed, remote.failed, remote.failed, service))
                androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(net.planetfoss.app.ui.theme.Space.s)) {
                    androidx.compose.material3.TextButton(onClick = onRetry) { Text(stringResource(R.string.bookmark_retry)) }
                    androidx.compose.material3.TextButton(onClick = onSettings) { Text(stringResource(R.string.settings)) }
                }
            }
        }
    }
}

/** Saved articles, newest saved first; works without network (stored locally as received). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(onSettings: () -> Unit, vm: SavedViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val remote by vm.remote.collectAsStateWithLifecycle()
    val listState = rememberLazyStaggeredGridState()
    ScrollToTopOnTabTap(listState)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_saved)) },
                actions = { IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, stringResource(R.string.settings)) } },
            )
        },
    ) { padding ->
        androidx.compose.foundation.layout.Box(Modifier.padding(padding).fillMaxSize()) {
            val note = remote.service != null && (remote.pending > 0 || remote.failed > 0)
            ItemListContent(state, listState, onRetry = null, emptyText = stringResource(R.string.empty_saved), leadFirst = false,
                emptyIcon = painterResource(R.drawable.ic_bookmark_border),
                header = if (note) ({
                    item(key = "remote", span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                        RemoteNote(remote, vm::retry, onSettings)
                    }
                }) else null)
        }
    }
}
