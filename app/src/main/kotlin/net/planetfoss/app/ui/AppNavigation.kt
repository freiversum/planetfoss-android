// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui

import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldValue
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import androidx.window.core.layout.WindowSizeClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import net.planetfoss.app.R
import net.planetfoss.app.ui.theme.PageTransitions
import net.planetfoss.app.ui.components.ItemActions
import net.planetfoss.app.ui.components.LocalItemActions
import net.planetfoss.app.ui.components.LocalSavedIds
import net.planetfoss.app.ui.components.LocalStars
import net.planetfoss.app.ui.components.LocalTabTaps
import net.planetfoss.app.browser.BrowserScreen
import net.planetfoss.app.data.prefs.BrowserMode
import net.planetfoss.app.ui.components.openOutsideApp
import net.planetfoss.app.ui.components.openWithChooser
import net.planetfoss.app.ui.components.isVideoSite
import net.planetfoss.app.ui.components.openInHandlerApp
import net.planetfoss.core.api.Support
import net.planetfoss.core.url.UrlPolicy
import net.planetfoss.app.ui.list.ItemListScreen
import net.planetfoss.app.ui.directory.SourcesScreen
import net.planetfoss.app.ui.directory.TopicsScreen
import net.planetfoss.app.ui.rubrics.RubricsScreen
import net.planetfoss.app.ui.saved.SavedScreen
import net.planetfoss.app.ui.saved.SavedViewModel
import net.planetfoss.app.ui.search.SearchScreen
import net.planetfoss.app.ui.settings.HelpScreen
import net.planetfoss.app.ui.settings.SettingsScreen
import net.planetfoss.app.ui.setup.SetupScreen
import net.planetfoss.app.ui.supernova.SupernovaScreen
import kotlin.reflect.KClass

@Serializable data object HomeRoute
@Serializable data object DiscoverRoute
@Serializable data object RadarRoute
@Serializable data object SupernovasRoute
@Serializable data object SavedRoute
@Serializable data object SettingsRoute
@Serializable data object SearchRoute
@Serializable data object TopicsRoute
@Serializable data object SourcesRoute
@Serializable data object HelpRoute

/** The setup assistant: opens itself at the first start, later from Settings. */
@Serializable data object SetupRoute

/** The weekly review; [week] YYYY-WW, empty = the last finished week. */
@Serializable data class WeeklyRoute(val week: String = "")

/** A logbook entry; [day] YYYY-MM-DD, empty = the newest. Also the target of planetfoss://logbook/<day> (morning notification). */
@Serializable data class LogbookRoute(val day: String = "")

/** A pushed list: kind = rubric | topic | source | videos | starred | bundle, ref = its slug or key, title until the API answers. */
@Serializable data class ListRoute(val kind: String, val ref: String = "", val title: String = "")

/** The internal browser on one URL, with its source's support link (and the project it recommends, if any). */
@Serializable data class BrowserRoute(val url: String, val support: String? = null, val sourceName: String? = null, val supportName: String? = null)

/** A source by slug only – target of planetfoss://source/<slug> (notification of a followed source). */
@Serializable data class SourceLinkRoute(val slug: String)

/** A topic by key only – target of the deep links planetfoss://supernova/<key> and planetfoss://topic/<key>. */
@Serializable data class TopicRoute(val key: String)

/**
 * The tabs since 10.11.2026 (docs/09-latest-tab.md): Latest (what is happening now – burning supernovas, rising topics,
 * every post by time, the logbook as a card or marker), Radar (what the user follows, new since the last visit),
 * Discover (browsing by subject: sky, logbook archive, weekly review, rubrics, topics, sources – no stream) and Saved.
 * The logbook is an edition opened from Latest or Discover, not a tab.
 */
private enum class Tab(val route: Any, val routeClass: KClass<*>, @StringRes val label: Int, val vector: ImageVector? = null, @DrawableRes val drawable: Int = 0) {
    Latest(HomeRoute, HomeRoute::class, R.string.tab_latest, drawable = R.drawable.ic_latest),
    Radar(RadarRoute, RadarRoute::class, R.string.tab_radar, drawable = R.drawable.ic_radar),
    Discover(DiscoverRoute, DiscoverRoute::class, R.string.tab_discover, drawable = R.drawable.ic_explore),
    Saved(SavedRoute, SavedRoute::class, R.string.tab_saved, drawable = R.drawable.ic_bookmark),
}

/**
 * Four tabs (bar on phones, rail on wide screens). Lists and search are pushed on top and
 * the tab they came from stays marked; settings hide the bar. [newIntents] carries deep
 * links that reach the running activity (notification taps). As in the Reddit app, the bar
 * slides away while scrolling down and comes back on the way up, and tapping the tab that is
 * already open goes back to its page (from a pushed list) or to the top of its list.
 */
@Composable
fun PlanetFossNavigation(newIntents: Flow<Intent>) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val context = LocalContext.current
    val savedVm: SavedViewModel = hiltViewModel()
    val savedIds by savedVm.ids.collectAsStateWithLifecycle()
    val starVm: StarViewModel = hiltViewModel()
    val stars by starVm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(starVm) {
        starVm.failed.collect { android.widget.Toast.makeText(context, R.string.star_failed, android.widget.Toast.LENGTH_SHORT).show() }
    }
    val appVm: AppViewModel = hiltViewModel()
    val settings by appVm.settings.collectAsStateWithLifecycle()
    val newSince by appVm.newSince.collectAsStateWithLifecycle()
    var lastTab by rememberSaveable { mutableStateOf(Tab.Latest.name) }
    Tab.entries.firstOrNull { destination?.hasRoute(it.routeClass) == true }?.let { lastTab = it.name }
    // a supernova notification lands on TopicRoute (Latest has the supernovas on top); the sky is reached from Discover
    if (destination?.hasRoute(TopicRoute::class) == true) lastTab = Tab.Latest.name
    if (destination?.hasRoute(SupernovasRoute::class) == true) lastTab = Tab.Discover.name

    LaunchedEffect(nav) { newIntents.collect { nav.handleDeepLink(it) } }
    // first start: the assistant goes on top of Latest (once DataStore has answered, so it never flashes for a finished setup)
    LaunchedEffect(settings.loaded, settings.setupDone) {
        if (settings.loaded && !settings.setupDone && nav.currentDestination?.hasRoute(SetupRoute::class) != true) nav.navigate(SetupRoute)
    }

    val inside: (String) -> Unit = { if (UrlPolicy.isWebUrl(it)) nav.navigate(BrowserRoute(it)) }
    val outside: (String) -> Unit = { if (settings.browserMode == BrowserMode.CHOOSER) openWithChooser(context, it) else openOutsideApp(context, it) }
    val actions = remember(nav, settings.browserMode) {
        val internal = settings.browserMode == BrowserMode.INTERNAL
        ItemActions(
            // the only plain-URL opens are a bundle's other reports
            open = { url -> appVm.opened(url, "buendel"); if (internal) inside(url) else outside(url) },
            openFrom = { item, place ->
                appVm.opened(item.url, place)
                when {
                    // videos go to the app made for them (YouTube, NewPipe …), else the browser; the app's own browser only when chosen
                    !internal && settings.browserMode == BrowserMode.CUSTOM_TAB && (item.isVideo || isVideoSite(item.url)) -> openInHandlerApp(context, item.url)
                    item.source.openExternal || !internal -> outside(item.url)
                    UrlPolicy.isWebUrl(item.url) -> nav.navigate(item.source.support?.takeIf { UrlPolicy.isWebUrl(it.url) }.let { s ->
                        BrowserRoute(item.url, s?.url, item.source.name, s?.recommended)
                    })
                }
            },
            openOther = { url -> appVm.opened(url, "liste"); if (internal) openOutsideApp(context, url) else inside(url) },
            openOtherLabel = if (internal) R.string.open_in_browser else R.string.open_in_app,
            toggleSave = savedVm::toggle,
            toggleStar = { starVm.toggle(it.id) },
            rubric = { nav.navigate(ListRoute("rubric", it.slug, it.name)) },
            topic = { nav.navigate(ListRoute("topic", it.key, it.label)) },
            source = { nav.navigate(ListRoute("source", it.slug, it.name)) },
            bundle = { nav.navigate(ListRoute("bundle", it.toString())) },
        )
    }
    val onSettings = { nav.navigate(SettingsRoute) { launchSingleTop = true } }
    val onSearch = { nav.navigate(SearchRoute) { launchSingleTop = true } }
    val back: () -> Unit = { if (!nav.popBackStack()) nav.openTab(HomeRoute) }

    // Settings, help and the internal browser fill the screen without the tab bar. The bar goes only when
    // nothing but such a page is on screen: while one fades in or is swiped away, the page below shows and so
    // does the bar, so the page below never changes size in view (Moshidon's pages simply cover the bar).
    // The page below counts as on screen once it is started: Navigation starts it when a page is pushed on top
    // (until that page has faded in) and when the back gesture begins, before the pop is committed.
    val below = remember(entry) { nav.previousBackStackEntry }
    val noPage = remember { MutableStateFlow(Lifecycle.State.CREATED) }
    val belowState by (below?.lifecycle?.currentStateFlow ?: noPage).collectAsStateWithLifecycle()
    val belowShows = below != null && belowState.isAtLeast(Lifecycle.State.STARTED) && !below.destination.fillsScreen()
    val fullScreen = destination?.fillsScreen() == true && !belowShows
    val shift = with(LocalDensity.current) { 100.dp.roundToPx() } * if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
    val pages = remember(shift) { PageTransitions(shift) { e -> Tab.entries.any { e.destination.hasRoute(it.routeClass) } } }
    // Expressive: short navigation bar on phones, wide navigation rail on large screens – and a rail
    // on a phone in landscape too, where a bar at the bottom would leave room for a single card
    val windowInfo = currentWindowAdaptiveInfo()
    val adaptive = if (!windowInfo.windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)) {
        NavigationSuiteType.WideNavigationRailCollapsed
    } else {
        NavigationSuiteScaffoldDefaults.navigationSuiteType(windowInfo)
    }
    // only the bar at the bottom hides; a rail at the side would shift the page sideways
    val hidesOnScroll = !fullScreen && adaptive in listOf(NavigationSuiteType.ShortNavigationBarCompact, NavigationSuiteType.ShortNavigationBarMedium, NavigationSuiteType.NavigationBar)
    val barState = rememberNavigationSuiteScaffoldState()
    // every page starts with the bar in view
    LaunchedEffect(destination, hidesOnScroll) { barState.show() }
    val scope = rememberCoroutineScope()
    val slop = with(LocalDensity.current) { 12.dp.toPx() }
    val barOnScroll = remember(barState, scope, slop) {
        object : NestedScrollConnection {
            var run = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // down only when the page really moves; up also when pulling at the top
                val dy = if (consumed.y != 0f) consumed.y else available.y.coerceAtLeast(0f)
                if (dy == 0f) return Offset.Zero
                run = if ((run < 0f) == (dy < 0f)) run + dy else dy
                when {
                    run < -slop && barState.targetValue == NavigationSuiteScaffoldValue.Visible -> scope.launch { barState.hide() }
                    run > slop && barState.targetValue == NavigationSuiteScaffoldValue.Hidden -> scope.launch { barState.show() }
                }
                return Offset.Zero
            }
        }
    }
    val tabTaps = remember { Tab.entries.associateWith { MutableSharedFlow<Unit>(extraBufferCapacity = 1) } }
    val onTab: (Tab) -> Unit = { tab ->
        when {
            lastTab != tab.name -> nav.openTab(tab.route)
            destination?.hasRoute(tab.routeClass) == true -> tabTaps.getValue(tab).tryEmit(Unit)
            // a pushed list: back to the tab's page (a notification may have opened it without one below)
            !nav.popBackStack(tab.route, inclusive = false) -> nav.openTab(tab.route)
        }
    }
    @Composable
    fun TabPage(tab: Tab, content: @Composable () -> Unit) = CompositionLocalProvider(LocalTabTaps provides tabTaps.getValue(tab), content = content)
    CompositionLocalProvider(
        LocalItemActions provides actions, LocalSavedIds provides savedIds, LocalStars provides stars,
        net.planetfoss.app.ui.components.LocalNewSince provides newSince, net.planetfoss.app.ui.components.LocalAllSeen provides appVm::allSeen,
    ) {
        NavigationSuiteScaffold(
            layoutType = if (fullScreen) NavigationSuiteType.None else adaptive,
            state = barState,
            navigationSuiteItems = {
                Tab.entries.forEach { tab ->
                    item(
                        selected = lastTab == tab.name,
                        onClick = { onTab(tab) },
                        icon = { if (tab.vector != null) Icon(tab.vector, null) else Icon(painterResource(tab.drawable), null) },
                        // one line: at 200 % font the label is cut rather than broken mid-word
                        label = { Text(stringResource(tab.label), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
                    )
                }
            },
        ) {
            NavHost(
                nav, startDestination = HomeRoute,
                modifier = if (hidesOnScroll) Modifier.nestedScroll(barOnScroll) else Modifier,
                enterTransition = pages.enter, exitTransition = pages.exit,
                popEnterTransition = pages.popEnter, popExitTransition = pages.popExit,
                predictivePopEnterTransition = pages.predictivePopEnter, predictivePopExitTransition = pages.predictivePopExit,
            ) {
                composable<RadarRoute>(deepLinks = listOf(navDeepLink { uriPattern = "planetfoss://radar" })) { TabPage(Tab.Radar) { net.planetfoss.app.ui.radar.RadarScreen(onSettings, onSearch) } }
                // planetfoss://today: the former Today tab (older widgets and links) now opens Latest
                composable<HomeRoute>(deepLinks = listOf(navDeepLink { uriPattern = "planetfoss://today" })) {
                    TabPage(Tab.Latest) {
                        ItemListScreen(
                            onSettings, onSearch, onStarred = { nav.navigate(ListRoute("starred")) }, onTopics = { nav.navigate(TopicsRoute) },
                            onVideos = { nav.navigate(ListRoute("videos")) }, onHelp = { nav.navigate(HelpRoute) }, onWeekly = { nav.navigate(WeeklyRoute(it)) },
                            onSky = { nav.navigate(SupernovasRoute) }, onLogbook = { nav.navigate(LogbookRoute(it)) },
                        )
                    }
                }
                composable<DiscoverRoute> {
                    TabPage(Tab.Discover) {
                        RubricsScreen(
                            onSettings, onSearch,
                            onRubric = { nav.navigate(ListRoute("rubric", it.slug, it.name)) },
                            onTopic = actions.topic,
                            onVideos = { nav.navigate(ListRoute("videos")) },
                            onStarred = { nav.navigate(ListRoute("starred")) },
                            onTopics = { nav.navigate(TopicsRoute) },
                            onSources = { nav.navigate(SourcesRoute) },
                            onWeekly = { nav.navigate(WeeklyRoute()) },
                            onLogbook = { nav.navigate(LogbookRoute()) },
                            onSky = { nav.navigate(SupernovasRoute) },
                        )
                    }
                }
                composable<TopicsRoute> { TopicsScreen(onBack = back, onTopic = actions.topic) }
                composable<SourcesRoute> { SourcesScreen(onBack = back, onSource = actions.source) }
                composable<SupernovasRoute> {
                    SupernovaScreen(onSettings, onTopic = { key, label -> nav.navigate(ListRoute("topic", key, label)) }, onBack = back)
                }
                composable<SavedRoute> { TabPage(Tab.Saved) { SavedScreen(onSettings) } }
                composable<WeeklyRoute> { net.planetfoss.app.ui.weekly.WeeklyScreen(onBack = back, onLogbook = { nav.navigate(LogbookRoute(it)) }) }
                composable<LogbookRoute>(deepLinks = listOf(navDeepLink { uriPattern = "planetfoss://logbook/{day}" })) {
                    net.planetfoss.app.ui.logbook.LogbookScreen(onBack = back, onWeekly = { nav.navigate(WeeklyRoute(it)) })
                }
                composable<SettingsRoute> { SettingsScreen(onBack = back, onHelp = { nav.navigate(HelpRoute) }, onSetup = { nav.navigate(SetupRoute) }) }
                composable<SetupRoute> { SetupScreen(onDone = back) }
                composable<HelpRoute> { HelpScreen(onBack = back) }
                composable<SearchRoute> { SearchScreen(onBack = back, onTopic = actions.topic) }
                composable<ListRoute> { e ->
                    ItemListScreen(onSettings, onSearch, onBack = back, fallbackTitle = e.toRoute<ListRoute>().title)
                }
                composable<BrowserRoute> { e ->
                    val r = e.toRoute<BrowserRoute>()
                    BrowserScreen(r.url, javaScript = settings.browserJavaScript, onClose = back, support = r.support?.let { Support(it, if (r.supportName != null) "forward" else "self", r.supportName) to r.sourceName.orEmpty() })
                }
                composable<TopicRoute>(
                    deepLinks = listOf(
                        navDeepLink { uriPattern = "planetfoss://supernova/{key}" },
                        navDeepLink { uriPattern = "planetfoss://topic/{key}" },
                    ),
                ) { ItemListScreen(onSettings, onSearch, onBack = back) }
                composable<SourceLinkRoute>(deepLinks = listOf(navDeepLink { uriPattern = "planetfoss://source/{slug}" })) {
                    ItemListScreen(onSettings, onSearch, onBack = back)
                }
            }
        }
    }
}

private fun NavDestination.fillsScreen() = hasRoute(SettingsRoute::class) || hasRoute(BrowserRoute::class) || hasRoute(HelpRoute::class) || hasRoute(SetupRoute::class)

private fun NavHostController.openTab(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
