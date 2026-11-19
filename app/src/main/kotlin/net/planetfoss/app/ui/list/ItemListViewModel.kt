// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.planetfoss.app.data.FeedRepository
import net.planetfoss.app.data.FollowRepository
import net.planetfoss.app.data.prefs.SettingsRepository
import net.planetfoss.app.ui.components.ListState
import net.planetfoss.app.ui.components.UiError
import net.planetfoss.core.api.ContentLang
import net.planetfoss.core.api.Ad
import net.planetfoss.core.api.Item
import net.planetfoss.core.api.ListTarget
import net.planetfoss.core.api.Use
import net.planetfoss.core.api.OrbitTopic
import net.planetfoss.core.api.FrontPage
import net.planetfoss.core.api.Supernova
import net.planetfoss.core.api.TopicHead
import net.planetfoss.core.api.BundleHead
import net.planetfoss.core.api.WeekTeaser
import net.planetfoss.core.api.LogbookTeaser
import net.planetfoss.app.data.prefs.Hidden
import net.planetfoss.app.data.CachedList
import javax.inject.Inject
import kotlin.random.Random
import net.planetfoss.core.ads.AdPicker
import net.planetfoss.core.ads.adSlot

private data class Extras(
    val list: CachedList, val ad: FeedRepository.AdChoices, val stars: List<Item>, val head: TopicHead?, val orbit: List<OrbitTopic>, val front: FrontPage?, val sky: net.planetfoss.core.api.SupernovaList?,
    val bundle: BundleHead?, val weekly: WeekTeaser?, val logbook: LogbookTeaser?,
)

private data class Activity(val refreshing: Boolean = false, val loadingMore: Boolean = false, val error: UiError? = null)

/**
 * A list screen's state plus the list's name from the API head (rubric, topic or source name), the
 * sponsor card and – on Latest only – the first Sternstunden for their block in the feed.
 */
data class ItemListUi(
    val list: ListState<Item> = ListState(),
    val title: String? = null,
    val ad: Ad? = null,
    val starred: List<Item> = emptyList(),
    /** Topic lists: the head as on the website's topic page. */
    val head: TopicHead? = null,
    /** Latest: “In orbit”, the website's trend bar. */
    val orbit: List<OrbitTopic> = emptyList(),
    /** Latest: the front page on top (lead and side stories) and its modules between the posts. */
    val front: FrontPage? = null,
    /** Latest: supernovas that burn right now, newest first – on top (docs/09-latest-tab.md). */
    val burning: List<Supernova> = emptyList(),
    /** Latest: supernovas of the last seven days that no longer burn – a chip row between the posts. */
    val cooled: List<Supernova> = emptyList(),
    /** Latest: comets of the last 24 hours (empty when switched off in Settings), first in “Rising”. */
    val comets: List<net.planetfoss.core.api.Comet> = emptyList(),
    /** Latest: day of the newest logbook entry the user opened (card or marker, D-L3). */
    val logbookRead: String = "",
    /** Bundle lists: how many sources report and in which language. */
    val bundle: BundleHead? = null,
    /** Latest, Monday to Wednesday: the hint to last week's review. */
    val weekly: WeekTeaser? = null,
    /** Latest: the newest logbook entry (at most a day old, not on quiet days). */
    val logbook: LogbookTeaser? = null,
    /** Posts of hidden sources and rubrics left out of this list (shown as a short note). */
    val hiddenCount: Int = 0,
)

/**
 * One list of items: the Latest tab or a pushed rubric/topic/source/videos list. The
 * target comes from the navigation arguments (ListRoute: kind + ref, TopicRoute: key).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ItemListViewModel @Inject constructor(
    private val repo: FeedRepository,
    private val settings: SettingsRepository,
    private val follows: FollowRepository,
    handle: SavedStateHandle,
) : ViewModel() {
    /** null = the arguments name no valid list (e.g. a broken deep link). */
    val target: ListTarget? = targetFrom(handle.get<String>("kind"), handle.get<String>("ref"), handle.get<String>("key"), handle.get<String>("slug"))

    /** Topic and source lists can be followed; null for every other list. */
    val followable: Pair<String, String>? = target?.let(FollowRepository::kindRef)

    /** The user follows this topic or source. */
    val followed: StateFlow<Boolean> = follows.all.map { all -> followable != null && all.any { it.kind to it.ref == followable } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Follow or unfollow; the posts on screen count as known, so only later ones are announced. */
    fun toggleFollow(label: String) {
        val (kind, ref) = followable ?: return
        val t = target ?: return
        viewModelScope.launch {
            if (followed.value) {
                follows.unfollow(kind, ref)
            } else {
                // the bell asked for Android's permission: it means "tell me", also when the notifications were switched off before
                if (!settings.settings.first().followNotify) {
                    follows.restartMarks()
                    settings.setFollowNotify(true)
                }
                state.value.list.items.let { shown -> follows.follow(t, label, shown.maxOfOrNull { it.publishedAt.toEpochMilli() }, shown.map { it.id }) }
            }
        }
    }

    /** Source and rubric lists can be hidden from the other lists; this list's own entry (without its name). */
    val hideable: Hidden? = when (val t = target) {
        is ListTarget.Source -> Hidden(Hidden.SOURCE, t.slug, "")
        is ListTarget.Rubric -> Hidden(Hidden.RUBRIC, t.slug, "")
        else -> null
    }

    /** The user hid this source or rubric (its own list still shows everything). */
    val hidden: StateFlow<Boolean> = settings.settings.map { s -> hideable != null && s.hidden.any { it.kind == hideable.kind && it.slug == hideable.slug } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun toggleHidden(name: String) {
        val h = hideable ?: return
        viewModelScope.launch { settings.toggleHidden(h.copy(name = name.ifBlank { h.slug })) }
    }

    /** Fediverse accounts the user blocked: their replies are left out (stored on the phone only), as “service:handle”. */
    val blockedAccounts: StateFlow<Set<String>> = settings.settings
        .map { s -> s.hidden.filter { it.kind == Hidden.ACCOUNT }.map { it.slug }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    fun blockAccount(reply: net.planetfoss.core.api.FediReply) {
        viewModelScope.launch { settings.toggleHidden(Hidden(Hidden.ACCOUNT, Hidden.account(reply.site, reply.handle), reply.handle)) }
    }

    private val activity = MutableStateFlow(Activity())
    private val orbit = MutableStateFlow<List<OrbitTopic>>(emptyList())
    private val front = MutableStateFlow<FrontPage?>(null)

    /** Latest: the intro hint for new users, until it is closed. */
    val introOpen: StateFlow<Boolean> = settings.settings.map { target == ListTarget.Home && !it.introClosed }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun closeIntro() {
        viewModelScope.launch { settings.closeIntro() }
    }
    private var job: Job? = null

    /** Seeds the dice of this screen's sponsor card; new on every pull to refresh, so a screen keeps its card while it is open. */
    private var adSeed = Random.nextLong()

    val lang: StateFlow<ContentLang> = settings.settings.map { it.contentLang }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ContentLang.ALL)

    val state: StateFlow<ItemListUi> = settings.settings.map { it.contentLang }
        .flatMapLatest { lang ->
            val t = target ?: ListTarget.Home
            // a language switch shows that language's cache at once and refreshes it if stale
            viewModelScope.launch {
                // a topic list also reloads when its head (status, numbers, related topics) is not in memory yet
                // Latest's hints (logbook, weekly review) are not cached either: its page 1 loads once per session
                val headMissing = ((t is ListTarget.Topic || t is ListTarget.Bundle) && !repo.hasHead(t, lang)) ||
                    (t == ListTarget.Home && !repo.loadedThisSession(t, lang))
                if (target != null && (headMissing || repo.isStale(t, lang))) run(lang, more = false)
            }
            // the sponsor card's candidates (own request, a part of the screen); no ad slot, no request
            if (target?.adSlot != null) viewModelScope.launch { repo.ensureAds() }
            // Latest carries the Sternstunden block like the website's start page
            val starred = if (t == ListTarget.Home) {
                viewModelScope.launch { if (repo.isStale(ListTarget.Starred, lang)) refreshStarred(lang) }
                viewModelScope.launch { refreshOrbit(lang) }
                repo.observe(ListTarget.Starred, lang).map { it.items.take(STARRED_IN_FEED) }
            } else {
                flowOf(emptyList())
            }
            // Latest: the supernova list from its cache (shown at once, refreshed by refreshOrbit)
            val sky: Flow<net.planetfoss.core.api.SupernovaList?> = if (t == ListTarget.Home) repo.observeSupernovas(lang).map { it.first } else flowOf(null)
            combine(repo.observe(t, lang), repo.observeAd(t, lang), starred, repo.observeHead(t, lang),
                combine(listOf<Flow<Any?>>(orbit, front, sky, repo.observeBundleHead(t, lang), repo.observeWeeklyTeaser(t, lang), repo.observeLogbookTeaser(t, lang))) { it.toList() }) { list, ad, stars, head, ofnb ->
                @Suppress("UNCHECKED_CAST")
                Extras(list, ad, stars, head, ofnb[0] as List<OrbitTopic>, ofnb[1] as FrontPage?, ofnb[2] as net.planetfoss.core.api.SupernovaList?, ofnb[3] as BundleHead?, ofnb[4] as WeekTeaser?, ofnb[5] as LogbookTeaser?)
            }
        }
        .combine(activity) { e, act -> e to act }
        .combine(settings.settings) { (e, act), s ->
            val (list, choices, stars, head, orb, fr, sky) = e
            // one card per screen opening: weighted dice from the server's candidates (the website's rotation), the seed changes on refresh;
            // without candidates (the request failed) the card the list answer carried
            val ad = choices.candidates?.let { AdPicker.pick(it, (target as? ListTarget.Topic)?.topicKey, Random(adSeed)) } ?: choices.fallback.takeIf { choices.candidates == null }
            // hidden sources and rubrics leave every list but their own one, and the bundle of one story (asked for explicitly)
            val except = hideable?.let { h -> s.hidden.firstOrNull { it.kind == h.kind && it.slug == h.slug } }
            val keep: (Item) -> Boolean = { target is ListTarget.Bundle || !s.hides(it, except) }
            val items = if (target == null) emptyList() else list.items.filter(keep)
            ItemListUi(
                hiddenCount = list.items.size - items.size,
                bundle = e.bundle,
                weekly = e.weekly,
                logbook = e.logbook,
                list = ListState(
                    items = items,
                    loaded = true,
                    fetched = list.fetchedAt != null,
                    refreshing = act.refreshing,
                    loadingMore = act.loadingMore,
                    hasMore = list.hasMore,
                    error = if (target == null) UiError.NotFound else act.error,
                ),
                title = list.title,
                ad = ad,
                starred = stars.filter(keep),
                head = head,
                orbit = orb,
                front = fr?.let { f ->
                    f.copy(
                        lead = f.lead?.takeIf(keep), side = f.side.filter(keep), projects = f.projects.filter(keep), videos = f.videos.filter(keep),
                        rubrics = f.rubrics.filter { m -> s.hidden.none { it.kind == Hidden.RUBRIC && it.slug == m.rubric.slug } }.map { m -> m.copy(items = m.items.filter(keep)) },
                    )
                },
                burning = Latest.burning(sky?.supernovas.orEmpty()),
                cooled = Latest.cooled(sky?.supernovas.orEmpty()),
                comets = if (s.showComets) sky?.comets.orEmpty() else emptyList(),
                logbookRead = s.logbookRead,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ItemListUi())

    /**
     * “In orbit” is extra as well: without it Latest simply starts with the posts. Orbit and band are parts of Latest
     * (not views of their own); the front page request is the one planetfoss.net counts as a view of Latest.
     */
    private suspend fun refreshOrbit(lang: ContentLang) {
        runCatching { repo.topics(lang, Use.PART).orbit }.onSuccess { orbit.value = it }
        // the front page goes with it; without it (offline) Latest is the plain list as before
        runCatching { repo.front(lang) }.onSuccess { front.value = it }
        // and the supernovas and comets on top (into their cache, observed above); offline: the cached ones
        runCatching { repo.refreshSupernovas(lang, Use.PART) }
    }

    fun refresh() {
        adSeed = Random.nextLong()
        viewModelScope.launch {
            if (target?.adSlot != null) launch { repo.ensureAds() }
            val lang = settings.settings.first().contentLang
            if (target == ListTarget.Home) launch { refreshStarred(lang) }
            if (target == ListTarget.Home) launch { refreshOrbit(lang) }
            run(lang, more = false)
        }
    }

    /** The Sternstunden block is extra: when it cannot be loaded, Latest simply shows none. */
    private suspend fun refreshStarred(lang: ContentLang) {
        runCatching { repo.refresh(ListTarget.Starred, lang, Use.PART) }
    }

    fun loadMore() {
        val s = state.value.list
        if (!s.hasMore || s.loadingMore || s.refreshing) return
        viewModelScope.launch { run(settings.settings.first().contentLang, more = true) }
    }

    /** A sponsor card was half on screen: always reported (decision D1), like ad views on the website; no opt-in. */
    fun adSeen(ad: Ad) {
        viewModelScope.launch { repo.adSeen(ad) }
    }

    fun setLang(lang: ContentLang) {
        viewModelScope.launch { settings.setContentLang(lang) }
    }

    private suspend fun run(lang: ContentLang, more: Boolean) {
        val t = target ?: return
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            activity.update { it.copy(refreshing = !more, loadingMore = more, error = null) }
            val error = runCatching { if (more) repo.loadMore(t, lang) else repo.refresh(t, lang) }
                .exceptionOrNull()?.let(UiError::from)
            activity.update { Activity(error = error) }
        }
        job?.join()
    }

    companion object {
        /** As many Sternstunden as the website's start page shows. */
        const val STARRED_IN_FEED = 5

        fun targetFrom(kind: String?, ref: String?, key: String?, slug: String? = null): ListTarget? = runCatching {
            when {
                key != null -> ListTarget.Topic(key).also { it.endpoint }
                slug != null -> ListTarget.Source(slug).also { it.endpoint }
                kind == null || kind == "home" -> ListTarget.Home
                kind == "videos" -> ListTarget.Videos
                kind == "starred" -> ListTarget.Starred
                kind == "bundle" -> ListTarget.Bundle(ref.orEmpty().toLong())
                kind == "rubric" -> ListTarget.Rubric(ref.orEmpty()).also { it.endpoint }
                kind == "topic" -> ListTarget.Topic(ref.orEmpty()).also { it.endpoint }
                kind == "source" -> ListTarget.Source(ref.orEmpty()).also { it.endpoint }
                else -> null
            }
        }.getOrNull()
    }
}
