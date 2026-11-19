// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import net.planetfoss.core.ads.adSlot
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import net.planetfoss.app.data.db.FeedDao
import net.planetfoss.app.data.db.ItemEntity
import net.planetfoss.app.data.db.NovaListEntity
import net.planetfoss.core.api.ContentLang
import net.planetfoss.core.api.Item
import net.planetfoss.core.api.ListTarget
import net.planetfoss.core.api.FrontPage
import net.planetfoss.core.api.SourcesAnswer
import net.planetfoss.core.api.TopicHead
import net.planetfoss.core.api.TopicsAnswer
import net.planetfoss.core.api.PlanetFossApi
import net.planetfoss.core.api.Use
import net.planetfoss.core.api.Rubric
import net.planetfoss.core.api.SearchResult
import net.planetfoss.core.api.Supernova
import net.planetfoss.core.api.SupernovaList
import net.planetfoss.core.api.Ad
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.Duration
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** A list as stored: items, its name, whether it was ever fetched and whether more pages exist. */
data class CachedList(val items: List<Item>, val fetchedAt: Long?, val pages: Int, val hasMore: Boolean, val title: String? = null)

/**
 * Lists from the app API, cached in Room (stale-while-revalidate): screens observe
 * Room and ask for a refresh; the network result lands in Room and flows back.
 * Every list (home, rubric, topic, source, videos) is cached per content language.
 */
@Singleton
class FeedRepository @Inject constructor(
    private val api: PlanetFossApi,
    private val dao: FeedDao,
    private val json: Json,
    private val clock: Clock,
) {
    fun observe(target: ListTarget, lang: ContentLang): Flow<CachedList> {
        val key = listKey(target, lang)
        return combine(dao.observeList(key), dao.observeMeta(key)) { rows, meta ->
            CachedList(rows.mapNotNull(::decodeItem), meta?.fetchedAt, meta?.pages ?: 0, meta?.hasMore ?: false, meta?.title)
        }
    }

    /**
     * [use]: a view of this list, or only a part of another screen (Latest's Sternstunden), see [Use]. [keepPages]: the
     * list refreshes by itself (not pulled by the user) – further pages that are loaded stay, new posts go on top.
     */
    suspend fun refresh(target: ListTarget, lang: ContentLang, use: Use = Use.VIEW, keepPages: Boolean = false) = loadPage(target, lang, 1, use, keepPages)

    /** Sponsor cards of the lists' first pages, in memory only: no ad is stored, none shows offline. */
    private val ads = MutableStateFlow<Map<String, Ad>>(emptyMap())

    /**
     * What a screen can show as its sponsor card: the candidates of its slot from `ads` (null until that answered, or when it
     * failed) and, as a fallback, the one card the list answer itself carried. Nothing is stored; offline there is no card.
     */
    data class AdChoices(val candidates: List<Ad>?, val fallback: Ad?)

    private val adCandidates = MutableStateFlow<Map<String, List<Ad>>?>(null)
    private var adCandidatesUi: String? = null
    private var adCandidatesAt = 0L
    private var adCandidatesTtl = Duration.ofSeconds(300)

    /** Fetch the candidates if there are none yet, they are older than the server's TTL or the app language changed. Failures are dropped. */
    suspend fun ensureAds() {
        val ui = uiLanguage()
        if (adCandidates.value != null && adCandidatesUi == ui && clock.millis() - adCandidatesAt < adCandidatesTtl.toMillis()) return
        runCatching { api.ads(ui) }.onSuccess { a ->
            adCandidatesUi = ui
            adCandidatesAt = clock.millis()
            adCandidatesTtl = Duration.ofSeconds(a.ttl.toLong().coerceIn(60, 3600))
            adCandidates.value = a.slots
        }
    }

    fun observeAd(target: ListTarget, lang: ContentLang): Flow<AdChoices> {
        val slot = target.adSlot ?: return flowOf(AdChoices(null, null))
        return combine(adCandidates, ads) { c, own -> AdChoices(c?.get(slot), own[listKey(target, lang)]) }
    }

    private val heads = MutableStateFlow<Map<String, TopicHead>>(emptyMap())

    /** Head of a topic list (status, numbers, related topics) from its last load in this session; none offline. */
    fun observeHead(target: ListTarget, lang: ContentLang): Flow<TopicHead?> = heads.map { it[listKey(target, lang)] }

    private val bundleHeads = MutableStateFlow<Map<String, net.planetfoss.core.api.BundleHead>>(emptyMap())

    /** Head of a bundle list (sources, languages, topic) from its last load in this session; none offline. */
    private val weeklyTeasers = MutableStateFlow<Map<String, net.planetfoss.core.api.WeekTeaser>>(emptyMap())

    /** Latest: the hint to last week's review from page 1 (Monday to Wednesday), kept in memory only. */
    fun observeWeeklyTeaser(target: ListTarget, lang: ContentLang): Flow<net.planetfoss.core.api.WeekTeaser?> = weeklyTeasers.map { it[listKey(target, lang)] }

    fun observeBundleHead(target: ListTarget, lang: ContentLang): Flow<net.planetfoss.core.api.BundleHead?> = bundleHeads.map { it[listKey(target, lang)] }

    private val logbookTeasers = MutableStateFlow<Map<String, net.planetfoss.core.api.LogbookTeaser>>(emptyMap())

    /** Latest: the hint to the newest logbook entry from page 1, kept in memory only. */
    fun observeLogbookTeaser(target: ListTarget, lang: ContentLang): Flow<net.planetfoss.core.api.LogbookTeaser?> = logbookTeasers.map { it[listKey(target, lang)] }

    /**
     * A logbook entry (website: /logbuch/); [day] null = the newest. Not cached in Room – an entry never changes once
     * written, OkHttp keeps the answer ten minutes. [use] BACKGROUND for the morning check.
     */
    suspend fun logbook(day: String?, lang: ContentLang, use: Use = Use.VIEW): net.planetfoss.core.api.LogbookEntry = api.logbook(day, lang, uiLanguage(), use)

    /** A finished week (website: /rueckblick/); not cached in Room – it never changes, OkHttp keeps it an hour. */
    suspend fun weekly(week: String?, lang: ContentLang): net.planetfoss.core.api.WeeklyReview = api.weekly(week, lang, uiLanguage())

    /** Backend settings (donation link); failures count as “nothing switched on”. */
    suspend fun meta(): net.planetfoss.core.api.Meta = runCatching { api.meta() }.getOrDefault(net.planetfoss.core.api.Meta())

    /** The head is kept in memory only: after a restart a cached topic list has none until it is loaded again. */
    fun hasHead(target: ListTarget, lang: ContentLang): Boolean =
        listKey(target, lang) in (if (target is ListTarget.Bundle) bundleHeads.value else heads.value)

    /** Lists whose page 1 came from the API in this app session (not only from Room). */
    private val loadedThisSession = MutableStateFlow<Set<String>>(emptySet())

    /**
     * True once page 1 of [target] was fetched in this session. Latest's hints (logbook, weekly review) live in memory only:
     * after a restart they are missing until page 1 is loaded again, so Latest reloads once per session (5.11.: the logbook
     * band was gone after an update until the next pull to refresh).
     */
    fun loadedThisSession(target: ListTarget, lang: ContentLang): Boolean = listKey(target, lang) in loadedThisSession.value

    /** “In orbit” and the evergreen topics. */
    suspend fun topics(lang: ContentLang, use: Use = Use.VIEW): TopicsAnswer = api.topics(lang, uiLanguage(), use)

    /**
     * Page 1 of a followed list for Radar, not stored (Radar only counts and lists the newest). One opening of Radar is
     * one view: the request marked [first] stands for the screen, the other followed lists are parts of it.
     */
    suspend fun radarPage(target: ListTarget, lang: ContentLang, first: Boolean) = api.list(target, lang, uiLanguage(), use = if (first) Use.RADAR else Use.PART)

    /** Page 1 of a list straight from the API, without touching the cache (the follow check: never a view). */
    suspend fun firstPage(target: ListTarget, lang: ContentLang) = api.list(target, lang, uiLanguage(), use = Use.BACKGROUND)

    /** The front page for the top of Latest. */
    suspend fun front(lang: ContentLang): FrontPage = api.front(lang, uiLanguage())

    /** The source directory. */
    suspend fun sources(lang: ContentLang): SourcesAnswer = api.sources(lang, uiLanguage())

    /**
     * The API delivered a sponsor card in this app session – planetfoss.net only does so while ads are
     * switched on in its backend. Until then nothing in the app mentions sponsor cards.
     */
    val adsShown: Flow<Boolean> = ads.map { it.isNotEmpty() }

    /** Count an opened article for the sources' reach; failures are dropped. */
    suspend fun opened(url: String, place: String, lang: ContentLang) {
        runCatching { api.opened(url, place, lang) }
    }

    /** Count a seen sponsor card; failures are dropped (a missed count is better than a retry loop). */
    suspend fun adSeen(ad: Ad) {
        runCatching { api.adView(ad) }
    }

    suspend fun loadMore(target: ListTarget, lang: ContentLang) {
        val pages = dao.meta(listKey(target, lang))?.pages ?: 0
        loadPage(target, lang, pages + 1)
    }

    /** True when page 1 is missing or older than [maxAge]. */
    suspend fun isStale(target: ListTarget, lang: ContentLang, maxAge: Duration = STALE): Boolean {
        val fetched = dao.meta(listKey(target, lang))?.fetchedAt ?: return true
        return clock.millis() - fetched > maxAge.toMillis()
    }

    private suspend fun loadPage(target: ListTarget, lang: ContentLang, page: Int, use: Use = Use.VIEW, keepPages: Boolean = false) {
        val answer = api.list(target, lang, uiLanguage(), page, use)
        val now = clock.millis()
        val rows = answer.items.map { ItemEntity(it.id, json.encodeToString(Item.serializer(), it), it.publishedAt.toEpochMilli(), now) }
        val merged = page == 1 && keepPages && dao.mergeFirstPage(listKey(target, lang), rows, now, answer.title)
        if (!merged) dao.storePage(listKey(target, lang), page, rows, answer.hasMore, now, answer.title)
        if (page == 1) ads.update { m -> answer.ad?.let { m + (listKey(target, lang) to it) } ?: (m - listKey(target, lang)) }
        if (page == 1) answer.topic?.let { h -> heads.update { it + (listKey(target, lang) to h) } }
        if (page == 1) weeklyTeasers.update { m -> answer.weekly?.let { m + (listKey(target, lang) to it) } ?: (m - listKey(target, lang)) }
        if (page == 1) logbookTeasers.update { m -> answer.logbook?.let { m + (listKey(target, lang) to it) } ?: (m - listKey(target, lang)) }
        if (page == 1) answer.bundle?.let { h -> bundleHeads.update { it + (listKey(target, lang) to h) } }
        if (page == 1) dao.pruneItems(now - PRUNE.toMillis())
        if (page == 1) loadedThisSession.update { it + listKey(target, lang) }
    }

    /** Supernovas and comets of the Supernovas tab, with the time of the last fetch. */
    fun observeSupernovas(lang: ContentLang): Flow<Pair<SupernovaList, Long?>> =
        dao.observeNovas(novaKey(lang)).map { row ->
            val list = row?.let { runCatching { json.decodeFromString(SupernovaList.serializer(), it.json) }.getOrNull() }
            (list ?: SupernovaList(emptyList())) to row?.fetchedAt
        }

    suspend fun refreshSupernovas(lang: ContentLang, use: Use = Use.VIEW): List<Supernova> {
        val result = api.supernovas(lang, uiLanguage(), use)
        dao.upsertNovas(NovaListEntity(novaKey(lang), json.encodeToString(SupernovaList.serializer(), result), clock.millis()))
        return result.supernovas
    }

    /** Rubrics are small and change rarely: no Room cache, OkHttp's HTTP cache (10 min) is enough. */
    suspend fun rubrics(): List<Rubric> = api.rubrics(uiLanguage()).rubrics

    /**
     * Search goes to the server every time (no local index); OkHttp caches identical queries. [use] PART for the further
     * queries of one visit to the search screen: it searches while typing, and every pause counted as a view before.
     */
    suspend fun search(query: String, lang: ContentLang, use: Use = Use.VIEW): SearchResult = api.search(query.trim(), lang, uiLanguage(), use)

    private fun decodeItem(raw: String): Item? = runCatching { json.decodeFromString(Item.serializer(), raw) }.getOrNull()

    companion object {
        val STALE: Duration = Duration.ofMinutes(5)
        val PRUNE: Duration = Duration.ofDays(7)

        /**
         * Cache keys carry the content language and the app language: rubric names and
         * supernova texts come in the app language, so a language switch must not show old ones.
         */
        fun listKey(target: ListTarget, lang: ContentLang) = "${target.key}:${lang.param}:${uiLanguage()}"
        fun novaKey(lang: ContentLang) = "nova:${lang.param}:${uiLanguage()}"

        /** Labels (rubric names, supernova text) follow the app language (AppLanguage): German or English. */
        fun uiLanguage(): String = if (Locale.getDefault().language == "de") "de" else "en"
    }
}
