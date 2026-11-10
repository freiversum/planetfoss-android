// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.planetfoss.app.data.FeedRepository
import net.planetfoss.app.data.SeenRepository
import net.planetfoss.app.data.prefs.Settings
import net.planetfoss.app.data.prefs.SettingsRepository
import javax.inject.Inject

/** Settings the navigation root needs for every card (how articles open), and counting opened articles. */
@HiltViewModel
class AppViewModel @Inject constructor(settings: SettingsRepository, private val feed: FeedRepository, private val seen: SeenRepository) : ViewModel() {
    val settings: StateFlow<Settings> = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    /** End of the previous visit: posts after it are marked “new” (null = off or first start). */
    val newSince: StateFlow<Long?> = seen.mark.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** “All seen”: the markers go, everything up to now counts as known. */
    fun allSeen() {
        viewModelScope.launch { seen.allSeen() }
    }

    /** An article was opened: only if the user opted in, planetfoss.net adds it to the source's daily click total. */
    fun opened(url: String, place: String) {
        val s = settings.value
        if (s.countReach) viewModelScope.launch { feed.opened(url, place, s.contentLang) }
    }
}
