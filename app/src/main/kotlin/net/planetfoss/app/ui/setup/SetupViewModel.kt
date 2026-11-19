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
import net.planetfoss.app.data.FollowRepository
import net.planetfoss.app.sync.AndroidFollowNotifier
import net.planetfoss.core.api.ContentLang
import net.planetfoss.core.api.ListTarget
import net.planetfoss.core.api.TopicRef
import net.planetfoss.core.api.Use
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** The topic step: what rises right now and the evergreen topics with the most posts; [failed] when the API did not answer. */
data class SetupTopics(
    val rising: List<TopicRef> = emptyList(),
    val evergreen: List<TopicRef> = emptyList(),
    /** Keys of the rising topics that are comets (they carry the comet sign, as in Latest). */
    val comets: Set<String> = emptySet(),
    val loaded: Boolean = false,
    val failed: Boolean = false,
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val feed: FeedRepository,
    private val follows: FollowRepository,
    private val followNotifier: AndroidFollowNotifier,
) : ViewModel() {
    val settings = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    private val _topics = MutableStateFlow(SetupTopics())
    val topics: StateFlow<SetupTopics> = _topics

    /** Keys of the topics the user follows (also those followed before the assistant was opened again). */
    val followed: StateFlow<Set<String>> = follows.all.map { all -> all.filter { it.kind == FollowRepository.TOPIC }.map { it.ref }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** The topics to choose from: a part of the assistant, not a view of the topics page. Loaded once, again after a failure. */
    fun loadTopics() {
        if (_topics.value.loaded && !_topics.value.failed) return
        viewModelScope.launch {
            _topics.value = runCatching { feed.topics(repo.settings.first().contentLang, Use.PART) }.fold(
                onSuccess = { a ->
                    val rising = a.orbit.map { it.ref }
                    val keys = rising.map { it.key }.toSet()
                    SetupTopics(rising, a.topics.sortedByDescending { it.posts30d }.map { it.ref }.filter { it.key !in keys }.take(EVERGREEN), a.orbit.filter { it.status == "komet" }.map { it.key }.toSet(), loaded = true)
                },
                onFailure = { SetupTopics(loaded = true, failed = true) },
            )
        }
    }

    /**
     * Follow or unfollow a topic from the assistant. Following here asks for no permission: it fills Radar. While Android
     * would not show notifications anyway, the follow notifications are stored as off, so the switch on the next page is
     * a real choice (and asks for the permission).
     */
    fun toggleTopic(topic: TopicRef) {
        viewModelScope.launch {
            if (topic.key in followed.value) {
                follows.unfollow(FollowRepository.TOPIC, topic.key)
            } else {
                if (!followNotifier.canNotify()) repo.setFollowNotify(false)
                follows.follow(ListTarget.Topic(topic.key), topic.label, null)
            }
        }
    }

    fun setFollowNotify(on: Boolean) {
        viewModelScope.launch {
            if (on) follows.restartMarks()
            repo.setFollowNotify(on)
        }
    }

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

    private companion object {
        /** As many evergreen topics as fit two phone screens of chips. */
        const val EVERGREEN = 20
    }
}
