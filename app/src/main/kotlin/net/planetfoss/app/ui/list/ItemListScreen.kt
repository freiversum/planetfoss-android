// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import net.planetfoss.core.url.UrlPolicy
import net.planetfoss.app.ui.components.SupportBox
import net.planetfoss.app.ui.components.AdCard
import net.planetfoss.core.api.Ad
import androidx.compose.material3.TopAppBarDefaults
import net.planetfoss.app.ui.components.RefreshBox
import net.planetfoss.app.ui.components.ScrollToTopOnTabTap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.planetfoss.app.data.prefs.Hidden
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.planetfoss.app.R
import androidx.compose.ui.graphics.painter.Painter
import net.planetfoss.app.ui.theme.ContentMaxWidth
import net.planetfoss.app.ui.theme.Space
import net.planetfoss.app.ui.components.CenterMessage
import net.planetfoss.app.ui.components.ErrorBanner
import net.planetfoss.app.ui.components.ItemCard
import net.planetfoss.app.ui.components.LocalItemActions
import net.planetfoss.app.ui.components.OrbitRow
import net.planetfoss.app.ui.components.TopicHeader
import net.planetfoss.app.ui.components.StarredBlock
import net.planetfoss.app.ui.components.RailBlock
import net.planetfoss.core.api.Category
import net.planetfoss.core.api.FrontPage
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import net.planetfoss.app.ui.components.ListState
import net.planetfoss.app.ui.components.TabTopBar
import net.planetfoss.app.ui.components.UiError
import net.planetfoss.app.ui.components.errorText
import net.planetfoss.core.api.Item
import net.planetfoss.core.api.ListTarget
import net.planetfoss.core.api.TopicRef
import net.planetfoss.app.ui.components.NovaCard
import net.planetfoss.app.ui.components.SectionHead
import net.planetfoss.app.ui.theme.LocalPlanetColors
import net.planetfoss.app.ui.theme.Radius
import net.planetfoss.app.ui.theme.rubricColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Close

/**
 * A list of items. As the Latest tab ([onBack] null) it has the tab bar with language and
 * search; pushed (rubric, topic, source, videos) it has a back arrow and the list's name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemListScreen(
    onSettings: () -> Unit,
    onSearch: () -> Unit,
    onBack: (() -> Unit)? = null,
    fallbackTitle: String = "",
    /** Opens the full Sternstunden list (from the block in Latest). */
    onStarred: () -> Unit = {},
    /** Opens the topics overview (from “In orbit” in Latest). */
    onTopics: () -> Unit = {},
    /** Opens the videos list (from the video rail in Latest). */
    onVideos: () -> Unit = {},
    /** Opens the help (from the intro hint in Latest). */
    onHelp: () -> Unit = {},
    /** Opens a week's review (from the hint in Latest, Monday to Wednesday). */
    onWeekly: (String) -> Unit = {},
    /** Latest: the supernova sky (end of the row “This week in the sky”) and a logbook entry (card or marker). */
    onSky: () -> Unit = {},
    onLogbook: (String) -> Unit = {},
    vm: ItemListViewModel = hiltViewModel(),
) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val lang by vm.lang.collectAsStateWithLifecycle()
    val introOpen by vm.introOpen.collectAsStateWithLifecycle()
    val blocked by vm.blockedAccounts.collectAsStateWithLifecycle()
    val scroll = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val listState = rememberLazyStaggeredGridState()
    val state = ui.list
    val title = when {
        onBack == null -> stringResource(R.string.tab_latest)
        vm.target == ListTarget.Videos -> stringResource(R.string.videos)
        vm.target == ListTarget.Starred -> stringResource(R.string.starred)
        // a topic opened by a link (notification) has no title in its route: the head's label (6.11.: follows were stored without a name)
        else -> ui.title ?: ui.head?.label?.takeIf { vm.target is ListTarget.Topic } ?: fallbackTitle
    }

    LoadMoreAtEnd(listState, state.hasMore, vm::loadMore)
    ScrollToTopOnTabTap(listState, scroll)

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            if (onBack == null) {
                TabTopBar(title, lang, vm::setLang, onSettings, scroll, onSearch = onSearch, brand = true)
            } else {
                TopAppBar(
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                    actions = {
                        if (vm.hideable != null) HideButton(vm, title)
                        if (vm.followable != null) FollowButton(vm, title)
                    },
                    scrollBehavior = scroll,
                )
            }
        },
    ) { padding ->
        RefreshBox(
            isRefreshing = state.refreshing && state.items.isNotEmpty(),
            onRefresh = vm::refresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            // a source's list starts with its support box when it has a link (subscription, donation, or a recommended project)
            val source = state.items.firstOrNull()?.source?.takeIf { vm.target is ListTarget.Source }
            val support = source?.support?.takeIf { UrlPolicy.isWebUrl(it.url) }
            val actions = LocalItemActions.current
            val home = vm.target == ListTarget.Home
            // Latest (docs/09-latest-tab.md): burning supernovas on top, then “Rising” – comets first, then the orbit topics
            val burning = ui.burning.takeIf { home }.orEmpty()
            val rising = if (home) Latest.rising(ui.comets, ui.orbit, burning).takeIf { it.isNotEmpty() } else null
            // the newest logbook entry: a card on top in the morning until read (D-L3), otherwise a marker at its 6:00
            val logbookCard = home && Latest.logbookAsCard(ui.logbook, ui.logbookRead)
            val logbookMark = ui.logbook?.takeIf { home && !logbookCard }
            // the parts on top arrive after the cached posts: until the user scrolls, Latest stays at its top, otherwise the
            // grid keeps the first post in view and the supernova lands above it, out of sight
            if (home) {
                var touched by remember { mutableStateOf(false) }
                LaunchedEffect(listState.isScrollInProgress) { if (listState.isScrollInProgress) touched = true }
                LaunchedEffect(burning.map { it.key }, rising?.size, logbookCard, ui.weekly != null) { if (!touched) listState.scrollToItem(0) }
            }
            val head = ui.head?.takeIf { vm.target is ListTarget.Topic }
            // a bundle: how many sources report, in which language (the website's bundle page)
            val bundle = ui.bundle?.takeIf { vm.target is ListTarget.Bundle }
            val hiddenNote = ui.hiddenCount.takeIf { it > 0 }
            // Latest, Monday to Wednesday: last week's review, as on the website's start page
            val weekly = ui.weekly?.takeIf { vm.target == ListTarget.Home }
            // Discover: posts new since the last visit, counted among the loaded ones
            val newSince = net.planetfoss.app.ui.components.LocalNewSince.current
            val allSeen = net.planetfoss.app.ui.components.LocalAllSeen.current
            val fresh = if (home && newSince != null) state.items.count { net.planetfoss.app.data.SeenRepository.isNew(it.publishedAt.toEpochMilli(), newSince) } else 0
            val header: (LazyStaggeredGridScope.() -> Unit)? =
                if (!home && !introOpen && weekly == null && head == null && bundle == null && hiddenNote == null && (source == null || support == null)) null else ({
                bundle?.let { b -> item(key = "bundle", span = StaggeredGridItemSpan.FullLine) { BundleHeadBox(b) } }
                if (introOpen) item(key = "intro", span = StaggeredGridItemSpan.FullLine) {
                    IntroHint(onHelp = { vm.closeIntro(); onHelp() }, onClose = vm::closeIntro)
                }
                // D-L4: the newest burning supernova in full, further ones compact
                burning.forEachIndexed { i, n ->
                    item(key = "nova-" + n.key, span = StaggeredGridItemSpan.FullLine) {
                        NovaCard(
                            n, onOpen = { actions.openFrom(it, "supernova") }, onAll = { actions.topic(TopicRef(n.key, n.label, true)) }, compact = i > 0,
                            note = when (Latest.otherLanguageOnly(n, lang)) {
                                "en" -> stringResource(R.string.bundle_only_en)
                                "de" -> stringResource(R.string.bundle_only_de)
                                else -> null
                            },
                        )
                    }
                }
                rising?.let { r -> item(key = "rising", span = StaggeredGridItemSpan.FullLine) { OrbitRow(r, actions.topic, onTopics, title = stringResource(R.string.rising)) } }
                // the logbook card carries last week's review beside it (Monday to Wednesday); without the card the review stands alone
                val logbook = ui.logbook
                if (logbookCard && logbook != null) {
                    item(key = "logbook", span = StaggeredGridItemSpan.FullLine) {
                        net.planetfoss.app.ui.logbook.LogbookBand(logbook, weekly, onLogbook = { onLogbook(logbook.day) }, onWeekly = onWeekly)
                    }
                } else {
                    // the same room below as the logbook band leaves
                    weekly?.let { w ->
                        item(key = "weekly", span = StaggeredGridItemSpan.FullLine) {
                            androidx.compose.foundation.layout.Box(Modifier.padding(bottom = Space.s)) { net.planetfoss.app.ui.weekly.WeeklyTeaserCard(w) { onWeekly(w.week) } }
                        }
                    }
                }
                if (fresh > 0 && newSince != null) item(key = "new-since", span = StaggeredGridItemSpan.FullLine) {
                    // same room below as the band above leaves, otherwise the bar sits on the first post
                    NewSinceBar(fresh, more = fresh == state.items.size && state.hasMore, since = newSince, onAllSeen = allSeen, modifier = Modifier.padding(bottom = Space.s))
                }
                head?.let { h -> item(key = "head", span = StaggeredGridItemSpan.FullLine) { TopicHeader(h, actions.topic, actions.rubric) } }
                if (source != null && support != null) item(key = "support", span = StaggeredGridItemSpan.FullLine) { SupportBox(source.name, support) }
                // last, right above the posts: inserted on top it would sit above the scroll anchor (the intro) and stay out of view
                hiddenNote?.let { n -> item(key = "hidden", span = StaggeredGridItemSpan.FullLine) { HiddenNote(n, onSettings) } }
            })
            ItemListContent(
                state, listState, vm::refresh, ad = ui.ad, onAdSeen = vm::adSeen,
                // a bundle's reports are equals, oldest first: no lead card
                leadFirst = vm.target !is ListTarget.Bundle,
                emptyText = stringResource(if (vm.target == ListTarget.Starred) R.string.starred_empty else R.string.empty_home),
                starred = ui.starred, onAllStarred = onStarred,
                front = ui.front.takeIf { vm.target == ListTarget.Home }, onVideos = onVideos, onRubric = actions.rubric,
                header = header,
                // a supernova's story first, then the rest of the topic – as on the website's topic page
                storyIds = head?.story.orEmpty().toSet(), restTitle = head?.let { stringResource(R.string.topic_also_about, it.label) },
                // replies to the own supernova posts, after the story (website: before “More on …”)
                replies = head?.replies.orEmpty().filter { Hidden.account(it.site, it.handle) !in blocked }, replyPosts = head?.discussions.orEmpty(),
                onBlockReply = vm::blockAccount,
                readLine = home,
                logbookMark = logbookMark, onLogbook = onLogbook,
                weekSky = if (home) ui.cooled else emptyList(), onSky = onSky,
            )
        }
    }
}

/**
 * Loading, error, empty or the list itself – shared by every item list. One column of at most
 * [ContentMaxWidth] on phones; from [TwoColumnsFrom] of width (tablets, phones in landscape) two
 * staggered columns, so cards of different heights leave no gaps. [header] items should span both
 * columns (`span = StaggeredGridItemSpan.FullLine`).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ItemListContent(
    state: ListState<Item>,
    listState: LazyStaggeredGridState,
    onRetry: (() -> Unit)?,
    emptyText: String,
    header: (LazyStaggeredGridScope.() -> Unit)? = null,
    /** PlanetFOSS's sponsor card, placed after [Ad.after] posts; [onAdSeen] once it was half on screen. */
    ad: Ad? = null,
    onAdSeen: (Ad) -> Unit = {},
    /** The first post with its image across the card (feeds); off for search hits and saved posts. */
    leadFirst: Boolean = true,
    emptyIcon: Painter? = null,
    /** Latest only: the Sternstunden block after [StarredAfter] posts, as on the website's start page. */
    starred: List<Item> = emptyList(),
    onAllStarred: () -> Unit = {},
    /** Latest only: the front page – lead and side stories on top, its modules between the posts. */
    front: FrontPage? = null,
    onVideos: () -> Unit = {},
    onRubric: (Category) -> Unit = {},
    /** Topic lists: the posts of a supernova's story, listed first; [restTitle] heads the posts after them. */
    storyIds: Set<Long> = emptySet(),
    restTitle: String? = null,
    /** Topic lists: replies from the fediverse to the own supernova posts, placed after the story; [replyPosts] links the posts. */
    replies: List<net.planetfoss.core.api.FediReply> = emptyList(),
    replyPosts: List<net.planetfoss.core.api.Discussion> = emptyList(),
    onBlockReply: (net.planetfoss.core.api.FediReply) -> Unit = {},
    /** Latest and Radar: a line “read up to here” between the posts new since the last visit and the older ones. */
    readLine: Boolean = false,
    /** Latest: the newest logbook entry as a marker at its 6:00 between the posts (once it is no card on top). */
    logbookMark: net.planetfoss.core.api.LogbookTeaser? = null,
    onLogbook: (String) -> Unit = {},
    /** Latest: supernovas of the week that no longer burn, as a chip row after the read line. */
    weekSky: List<net.planetfoss.core.api.Supernova> = emptyList(),
    onSky: () -> Unit = {},
) {
    val newSince = net.planetfoss.app.ui.components.LocalNewSince.current
    when {
        !state.loaded -> Unit
        // “not found” does not get better by retrying
        state.items.isEmpty() && state.error != null ->
            CenterMessage(errorText(state.error, hasData = false), onRetry = onRetry.takeIf { state.error != UiError.NotFound })
        state.items.isEmpty() && (state.refreshing || !state.fetched) -> CenterMessage(stringResource(R.string.loading), loading = true)
        state.items.isEmpty() && header == null -> CenterMessage(emptyText, onRetry = onRetry, icon = emptyIcon)
        else -> Column(Modifier.fillMaxSize()) {
            state.error?.let { ErrorBanner(errorText(it, hasData = true), onRetry ?: {}) }
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                val two = maxWidth >= TwoColumnsFrom
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(if (two) 2 else 1),
                    state = listState,
                    contentPadding = PaddingValues(Space.l),
                    verticalItemSpacing = Space.m,
                    horizontalArrangement = Arrangement.spacedBy(Space.m),
                    modifier = Modifier.fillMaxHeight().widthIn(max = (if (two) TwoColumnsMaxWidth else ContentMaxWidth) + Space.l * 2),
                ) {
                    header?.invoke(this)
                    // the front page on top, as the website's start page: lead story by weight and three side stories,
                    // then “Latest” in time order without those four
                    val frontIds = front?.takeIf { it.lead != null }?.let { f -> (listOfNotNull(f.lead) + f.side).map { it.id }.toSet() }.orEmpty()
                    if (front?.lead != null) {
                        item(key = "front-lead", contentType = "lead") { ItemCard(front.lead!!, lead = true) }
                        items(front.side, key = { "front-" + it.id }, contentType = { "item" }) { ItemCard(it) }
                        item(key = "latest-h", contentType = "heading", span = StaggeredGridItemSpan.FullLine) { LatestHeading() }
                    }
                    val shown = if (frontIds.isEmpty()) state.items else state.items.filter { it.id !in frontIds }
                    // only a first post with an image becomes the lead card (not below a front page, which has its own)
                    val lead = shown.firstOrNull()?.takeIf { leadFirst && frontIds.isEmpty() && it.image != null }?.id
                    // extras between the posts: sponsor card, Sternstunden, and the front page's modules – as on the website
                    val extras = sortedMapOf<Int, String>().apply {
                        fun place(at: Int?, key: String) {
                            var p = at ?: return
                            while (containsKey(p)) p++
                            if (p <= shown.size) put(p, key)
                        }
                        // first, so it sits right after the story; only if posts follow
                        val story = shown.count { it.id in storyIds }
                        val split = restTitle != null && story in 1 until shown.size
                        if (split) put(story, "rest")
                        // replies right before “More on …” (the “rest” slot emits them), otherwise after the first posts
                        if (replies.isNotEmpty() && !split) place(minOf(shown.size, RepliesAfter), "replies")
                        place(ad?.after, "ad")
                        if (starred.isNotEmpty()) place(StarredAfter, "stars")
                        if (!front?.projects.isNullOrEmpty()) place(StarredAfter * 2, "projects")
                        front?.rubrics?.forEach { place(StarredAfter * 3, "rubric:" + it.rubric.slug) }
                        if (!front?.videos.isNullOrEmpty()) place(StarredAfter * 4, "videos")
                        // new posts first (the list is newest first): the line sits after the last new one
                        var readAt: Int? = null
                        if (readLine && newSince != null) {
                            val fresh = shown.takeWhile { net.planetfoss.app.data.SeenRepository.isNew(it.publishedAt.toEpochMilli(), newSince) }.size
                            if (fresh in 1 until shown.size) { var p = fresh; while (containsKey(p)) p++; put(p, "read"); readAt = p }
                        }
                        // the week's sky right after the read line (emitted with it), else after the first posts
                        if (weekSky.isNotEmpty() && readAt == null) place(minOf(shown.size, RepliesAfter), "weeksky")
                        // the logbook marker by time: before the first post not newer than the entry's 6:00 (not moved by other extras)
                        logbookMark?.let { t -> Latest.logbookAt(t.day)?.let { at -> Latest.logbookIndex(shown.map { it.publishedAt }, at) } }
                            ?.takeIf { !containsKey(it) }?.let { put(it, "logbook") }
                    }
                    var from = 0
                    (extras.keys + shown.size).forEach { until ->
                        // animateItem: a post that is unsaved or arrives on refresh slides instead of jumping
                        items(shown.subList(from, until), key = { it.id }, contentType = { if (it.id == lead) "lead" else "item" }) { item ->
                            ItemCard(item, modifier = Modifier.animateItem(), lead = item.id == lead)
                        }
                        val key = extras[until]
                        when {
                            key == "rest" -> {
                                if (replies.isNotEmpty()) item(key = "replies", contentType = "replies", span = StaggeredGridItemSpan.FullLine) {
                                    net.planetfoss.app.ui.components.RepliesBlock(replies, replyPosts, onBlock = onBlockReply)
                                }
                                item(key = "rest-h", contentType = "heading", span = StaggeredGridItemSpan.FullLine) {
                                    SectionHead(restTitle!!, Modifier.padding(top = Space.s))
                                }
                            }
                            key == "replies" -> item(key = "replies", contentType = "replies", span = StaggeredGridItemSpan.FullLine) {
                                net.planetfoss.app.ui.components.RepliesBlock(replies, replyPosts, onBlock = onBlockReply)
                            }
                            key == "read" -> {
                                item(key = "read", contentType = "read", span = StaggeredGridItemSpan.FullLine) { ReadLine() }
                                if (weekSky.isNotEmpty()) item(key = "weeksky", contentType = "rail", span = StaggeredGridItemSpan.FullLine) { WeekSkyRow(weekSky, onSky) }
                            }
                            key == "weeksky" -> item(key = "weeksky", contentType = "rail", span = StaggeredGridItemSpan.FullLine) { WeekSkyRow(weekSky, onSky) }
                            key == "logbook" -> logbookMark?.let { t ->
                                item(key = "logbook-mark", contentType = "mark", span = StaggeredGridItemSpan.FullLine) { LogbookMarker(t) { onLogbook(t.day) } }
                            }
                            key == "ad" -> ad?.let { a -> item(key = "ad-${a.id}", contentType = "ad") { AdCard(a, onSeen = onAdSeen) } }
                            key == "stars" -> item(key = "starred", contentType = "starred", span = StaggeredGridItemSpan.FullLine) {
                                StarredBlock(starred, onAll = onAllStarred)
                            }
                            key == "projects" -> item(key = "projects", contentType = "rail", span = StaggeredGridItemSpan.FullLine) {
                                RailBlock(stringResource(R.string.from_projects), front!!.projects)
                            }
                            key == "videos" -> item(key = "videos", contentType = "rail", span = StaggeredGridItemSpan.FullLine) {
                                RailBlock(stringResource(R.string.videos), front!!.videos, onAll = onVideos)
                            }
                            key != null && key.startsWith("rubric:") -> front?.rubrics?.firstOrNull { "rubric:" + it.rubric.slug == key }?.let { m ->
                                item(key = key, contentType = "rail", span = StaggeredGridItemSpan.FullLine) {
                                    // tinted in the rubric's colour, as the website's rubric modules
                                    RailBlock(m.rubric.name, m.items, onAll = { onRubric(m.rubric) }, tint = rubricColor(m.rubric.slug))
                                }
                            }
                        }
                        from = until
                    }
                    if (state.loadingMore) {
                        item(key = "more", contentType = "more", span = StaggeredGridItemSpan.FullLine) {
                            Box(Modifier.fillMaxWidth().padding(Space.l), contentAlignment = Alignment.Center) { LoadingIndicator() }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Follow a topic or source: new posts are announced (the app's answer to the website's RSS feeds). Following asks for
 * the notification permission first; without it nothing is followed and a short note says why.
 */
@Composable
private fun FollowButton(vm: ItemListViewModel, title: String) {
    val followed by vm.followed.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val request = net.planetfoss.app.ui.components.rememberNotifyRequest { granted ->
        if (granted) vm.toggleFollow(title)
        else android.widget.Toast.makeText(context, R.string.follow_needs_notifications, android.widget.Toast.LENGTH_LONG).show()
    }
    IconButton(onClick = { if (followed) vm.toggleFollow(title) else request() }) {
        Icon(
            if (followed) Icons.Filled.Notifications else Icons.Outlined.Notifications,
            stringResource(if (followed) R.string.unfollow else R.string.follow),
            tint = if (followed) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.LocalContentColor.current,
        )
    }
}

/** Hide this source or rubric from all other lists (stored on the phone only); its own list keeps showing everything. */
@Composable
private fun HideButton(vm: ItemListViewModel, title: String) {
    val hidden by vm.hidden.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val kindSource = vm.hideable?.kind == net.planetfoss.app.data.prefs.Hidden.SOURCE
    // resolved here (configuration-aware), the click only picks one
    val msg = stringResource(
        when {
            hidden -> R.string.hide_undone
            kindSource -> R.string.hide_done_source
            else -> R.string.hide_done_rubric
        },
        title,
    )
    IconButton(onClick = {
        vm.toggleHidden(title)
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
    }) {
        Icon(
            androidx.compose.ui.res.painterResource(R.drawable.ic_visibility_off),
            stringResource(if (hidden) R.string.unhide_named else if (kindSource) R.string.hide_source else R.string.hide_rubric, title),
            tint = if (hidden) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.LocalContentColor.current,
        )
    }
}

/** Posts of hidden sources or rubrics are left out of this list – said once on top, with the way to undo it. */
@Composable
private fun HiddenNote(count: Int, onSettings: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onSettings, contentPadding = PaddingValues(horizontal = 0.dp)) {
        Text(
            androidx.compose.ui.res.pluralStringResource(R.plurals.hidden_note, count, count),
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
        )
    }
}

/** Head of a bundle: how many sources report – and in which language, like the website's bundle page. */
@Composable
private fun BundleHeadBox(b: net.planetfoss.core.api.BundleHead) {
    val langs = b.langs
    val langText = when {
        langs == null -> null
        langs.de > 0 && langs.en > 0 -> stringResource(R.string.bundle_langs, langs.de, langs.en)
        langs.en > 0 -> stringResource(R.string.bundle_only_en)
        langs.de > 0 -> stringResource(R.string.bundle_only_de)
        else -> null
    }
    Column(Modifier.fillMaxWidth().padding(bottom = Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
        Text(
            androidx.compose.ui.res.pluralStringResource(R.plurals.bundle_sources, b.sources, b.sources),
            style = net.planetfoss.app.ui.theme.MetaStyle,
            color = LocalPlanetColors.current.violet,
        )
        langText?.let { Text(it, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant) }
        // where this story is discussed (Hacker News, Lobsters), as on the website's bundle page
        net.planetfoss.app.ui.components.DiscussionRow(b.discussions, Modifier.padding(top = Space.xs))
    }
}

/** “Latest” below the front page: from here on in time order. */
@Composable
private fun LatestHeading() {
    SectionHead(stringResource(R.string.tab_home), Modifier.padding(top = Space.s))
}

/**
 * For new users on top of Latest, like the website's intro box: what PlanetFOSS is, the way to the help, and a close button.
 * Gone for good once closed or once the help was opened.
 */
@Composable
private fun IntroHint(onHelp: () -> Unit, onClose: () -> Unit) {
    val planet = LocalPlanetColors.current
    androidx.compose.material3.Card(
        shape = Radius.card,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLowest),
        border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        androidx.compose.foundation.layout.Row(
            Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min),
        ) {
            // the violet edge of the website's intro box
            Box(Modifier.width(4.dp).fillMaxHeight().background(planet.violet))
            Column(Modifier.weight(1f).padding(start = Space.l, top = Space.m, bottom = Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                Text(stringResource(R.string.intro_text), style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                androidx.compose.material3.TextButton(onClick = onHelp, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text(stringResource(R.string.intro_link) + " →")
                }
            }
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.intro_close)) }
        }
    }
}

/** The Sternstunden block in Latest follows this many posts (the website: after its first 3 + 4 cards). */
private const val StarredAfter = 7

/** Replies from the fediverse on a topic without a story split: after this many posts (website: after the list). */
private const val RepliesAfter = 6

/** Two columns from this width of the list (tablets, phones in landscape beside the rail). */
private val TwoColumnsFrom = 700.dp

/** Two columns are never wider than this together, so cards keep a readable line length. */
private val TwoColumnsMaxWidth = 1200.dp

/** Ask for the next page when the last five cards come into view. */
@Composable
fun LoadMoreAtEnd(listState: LazyStaggeredGridState, hasMore: Boolean, loadMore: () -> Unit) {
    val nearEnd by remember {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= listState.layoutInfo.totalItemsCount - 5 } == true }
    }
    LaunchedEffect(nearEnd, hasMore) { if (nearEnd && hasMore) loadMore() }
}

/** “read up to here”: a thin aurora line with a label between new and known posts. */
@Composable
internal fun ReadLine() {
    val aurora = net.planetfoss.app.ui.theme.LocalPlanetColors.current.aurora
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        androidx.compose.material3.HorizontalDivider(Modifier.weight(1f), color = aurora.copy(alpha = 0.5f))
        Text(stringResource(R.string.read_up_to_here), style = net.planetfoss.app.ui.theme.MetaStyle, color = aurora)
        androidx.compose.material3.HorizontalDivider(Modifier.weight(1f), color = aurora.copy(alpha = 0.5f))
    }
}

/** “23 new since yesterday, 21:40” with “All seen” – above Discover's posts while anything is new. */
@Composable
internal fun NewSinceBar(count: Int, more: Boolean, since: Long, onAllSeen: () -> Unit, modifier: Modifier = Modifier) {
    val planet = net.planetfoss.app.ui.theme.LocalPlanetColors.current
    val time = sinceText(since)
    androidx.compose.material3.Surface(color = planet.aurora.copy(alpha = 0.14f), shape = net.planetfoss.app.ui.theme.Radius.s, modifier = modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Row(Modifier.padding(start = Space.m), verticalAlignment = Alignment.CenterVertically) {
            Text(
                androidx.compose.ui.res.pluralStringResource(if (more) R.plurals.new_since_more else R.plurals.new_since, count, count, time),
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = Space.s),
            )
            androidx.compose.material3.TextButton(onClick = onAllSeen) { Text(stringResource(R.string.all_seen)) }
        }
    }
}

/** Latest: the logbook entry as a marker at its 6:00 between the posts – “6:00 · Logbook published · headline ›”. */
@Composable
private fun LogbookMarker(t: net.planetfoss.core.api.LogbookTeaser, onClick: () -> Unit) {
    val planet = LocalPlanetColors.current
    androidx.compose.material3.OutlinedCard(
        onClick = onClick,
        shape = Radius.s,
        colors = androidx.compose.material3.CardDefaults.outlinedCardColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.fillMaxWidth(),
    ) {
        androidx.compose.foundation.layout.Row(
            Modifier.padding(horizontal = Space.m, vertical = Space.s).heightIn(min = 32.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            Text("6:00", style = net.planetfoss.app.ui.theme.MetaStyle, color = androidx.compose.material3.MaterialTheme.colorScheme.tertiary)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.logbook_marker), style = net.planetfoss.app.ui.theme.MetaStyle, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                Text(t.headline, style = androidx.compose.material3.MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("›", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = planet.supernova)
        }
    }
}

/** Latest: supernovas of the week that no longer burn, as chips; the row's end opens the whole sky. */
@Composable
private fun WeekSkyRow(novas: List<net.planetfoss.core.api.Supernova>, onSky: () -> Unit) {
    val actions = LocalItemActions.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHead("✦ " + stringResource(R.string.week_sky), Modifier.weight(1f))
            androidx.compose.material3.TextButton(onClick = onSky) { Text(stringResource(R.string.tab_supernovas) + " →") }
        }
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            items(novas.size, key = { novas[it].key }) { i ->
                val n = novas[i]
                net.planetfoss.app.ui.components.ChipTag(n.label, supernova = true) { actions.topic(TopicRef(n.key, n.label, true)) }
            }
        }
    }
}

/** “today, 09:16”, “yesterday, 21:40” or “4 Nov, 21:40” – when the last visit ended. */
@Composable
internal fun sinceText(since: Long): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    val zone = java.time.ZoneId.systemDefault()
    val day = java.time.Instant.ofEpochMilli(since).atZone(zone).toLocalDate()
    val today = java.time.LocalDate.now(zone)
    val clock = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(since))
    return when (day) {
        today -> stringResource(R.string.since_today, clock)
        today.minusDays(1) -> stringResource(R.string.since_yesterday, clock)
        else -> android.text.format.DateUtils.formatDateTime(context, since,
            android.text.format.DateUtils.FORMAT_SHOW_DATE or android.text.format.DateUtils.FORMAT_NO_YEAR or android.text.format.DateUtils.FORMAT_ABBREV_MONTH or android.text.format.DateUtils.FORMAT_SHOW_TIME)
    }
}
