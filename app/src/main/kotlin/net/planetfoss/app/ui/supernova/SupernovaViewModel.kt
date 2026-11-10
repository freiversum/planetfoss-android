// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.supernova

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.planetfoss.app.data.FeedRepository
import net.planetfoss.app.data.prefs.SettingsRepository
import net.planetfoss.app.ui.components.ListState
import net.planetfoss.app.ui.components.UiError
import net.planetfoss.core.api.ContentLang
import java.time.Clock
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SupernovaViewModel @Inject constructor(
    private val repo: FeedRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    private val refreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<UiError?>(null)

    val lang: StateFlow<ContentLang> = settings.settings.map { it.contentLang }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ContentLang.ALL)

    /** null until DataStore answered – the offer card must not flash for users who already said yes. */
    val notifyOn: StateFlow<Boolean?> = settings.settings.map<net.planetfoss.app.data.prefs.Settings, Boolean?> { it.novaNotify }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun enableNotify() {
        viewModelScope.launch { settings.setNovaNotify(true) }
    }

    private val showComets = settings.settings.map { it.showComets }

    val state: StateFlow<ListState<Sky>> = settings.settings.map { it.contentLang }.distinctUntilChanged()
        .flatMapLatest { lang ->
            repo.observeSupernovas(lang).also { flow ->
                viewModelScope.launch {
                    val fetched = flow.first().second
                    if (fetched == null || clock.millis() - fetched > STALE_MS) refresh(lang)
                }
            }
        }
        .combine(combine(refreshing, error, showComets) { r, e, c -> Triple(r, e, c) }) { (list, fetched), (r, e, comets) ->
            ListState(items = skyOf(list.supernovas, if (comets) list.comets else emptyList()), loaded = true, fetched = fetched != null, refreshing = r, error = e)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListState())

    fun refresh() {
        viewModelScope.launch { refresh(settings.settings.first().contentLang) }
    }

    fun setLang(lang: ContentLang) {
        viewModelScope.launch { settings.setContentLang(lang) }
    }

    private suspend fun refresh(lang: ContentLang) {
        if (refreshing.value) return
        refreshing.value = true
        error.value = runCatching { repo.refreshSupernovas(lang) }.exceptionOrNull()?.let(UiError::from)
        refreshing.value = false
    }

    private companion object {
        const val STALE_MS = 2 * 60 * 1000L
    }
}
