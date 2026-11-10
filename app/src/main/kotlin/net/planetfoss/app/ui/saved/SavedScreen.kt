// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.saved

import androidx.compose.foundation.layout.fillMaxSize
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
class SavedViewModel @Inject constructor(private val repo: SavedRepository) : ViewModel() {
    val ids: StateFlow<Set<Long>> = repo.ids.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val state: StateFlow<ListState<Item>> = repo.items
        .map { ListState(items = it, loaded = true, fetched = true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListState())

    fun toggle(item: Item) {
        viewModelScope.launch { repo.toggle(item) }
    }
}

/** Saved articles, newest saved first; works without network (stored locally as received). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(onSettings: () -> Unit, vm: SavedViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
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
            ItemListContent(state, listState, onRetry = null, emptyText = stringResource(R.string.empty_saved), leadFirst = false,
                emptyIcon = painterResource(R.drawable.ic_bookmark_border))
        }
    }
}
