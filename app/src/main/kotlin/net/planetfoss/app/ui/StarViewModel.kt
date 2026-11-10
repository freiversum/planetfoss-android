// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.planetfoss.app.data.StarRepository
import net.planetfoss.app.ui.components.StarUi
import javax.inject.Inject

/** Stars for every card in the app (scoped to the navigation root, like the saved ids). */
@HiltViewModel
class StarViewModel @Inject constructor(private val repo: StarRepository) : ViewModel() {
    val ui: StateFlow<StarUi> = combine(repo.ids, repo.counts, repo.busy) { mine, counts, busy -> StarUi(mine, counts, busy) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, StarUi())

    private val _failed = Channel<Unit>(Channel.CONFLATED)

    /** A star did not get through; the screen says so once. */
    val failed = _failed.receiveAsFlow()

    fun toggle(itemId: Long) {
        viewModelScope.launch { runCatching { repo.toggle(itemId) }.onFailure { _failed.trySend(Unit) } }
    }
}
