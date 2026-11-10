// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.planetfoss.app.data.FeedRepository
import net.planetfoss.app.data.prefs.BrowserMode
import net.planetfoss.app.data.prefs.Settings
import net.planetfoss.app.data.prefs.SettingsRepository
import net.planetfoss.app.data.prefs.ThemeMode
import net.planetfoss.app.ui.settings.applyThemeMode
import net.planetfoss.core.api.ContentLang
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(private val repo: SettingsRepository, feed: FeedRepository) : ViewModel() {
    val settings = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    /** Sponsor cards count with the articles only once ads are on at planetfoss.net (as in Settings). */
    val adsShown = feed.adsShown.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setLang(lang: ContentLang) { viewModelScope.launch { repo.setContentLang(lang) } }

    fun setTheme(context: android.content.Context, mode: ThemeMode) {
        applyThemeMode(context, mode)
        viewModelScope.launch { repo.setTheme(mode) }
    }

    fun setBrowserMode(mode: BrowserMode) { viewModelScope.launch { repo.setBrowserMode(mode) } }
    fun setNotify(on: Boolean) { viewModelScope.launch { repo.setNovaNotify(on) } }
    fun setLogbookNotify(on: Boolean) { viewModelScope.launch { repo.setLogbookNotify(on) } }
    fun setCountReach(on: Boolean) { viewModelScope.launch { repo.setCountReach(on) } }

    /** Finished, skipped or left with Back: the assistant does not come back by itself. */
    fun finish() { viewModelScope.launch { repo.finishSetup() } }
}
