// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.logbook

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.planetfoss.app.R
import net.planetfoss.app.data.FeedRepository
import net.planetfoss.app.data.prefs.SettingsRepository
import net.planetfoss.app.ui.components.CenterMessage
import net.planetfoss.app.ui.components.ErrorBanner
import net.planetfoss.app.ui.components.ItemCard
import net.planetfoss.app.ui.components.LocalItemActions
import net.planetfoss.app.ui.components.NovaBurst
import net.planetfoss.app.ui.components.NovaInk
import net.planetfoss.app.ui.components.NovaInk2
import net.planetfoss.app.ui.components.NovaInk3
import net.planetfoss.app.ui.components.NovaLine
import net.planetfoss.app.ui.components.NovaSpace
import net.planetfoss.app.ui.components.RefreshBox
import net.planetfoss.app.ui.components.SectionHead
import net.planetfoss.app.ui.components.UiError
import net.planetfoss.app.ui.components.errorText
import net.planetfoss.app.ui.components.novaSky
import net.planetfoss.app.ui.theme.ContentMaxWidth
import net.planetfoss.app.ui.theme.TwoColumnsFrom
import net.planetfoss.app.ui.theme.WideMaxWidth
import net.planetfoss.app.ui.theme.LocalPlanetColors
import net.planetfoss.app.ui.theme.MetaStyle
import net.planetfoss.app.ui.theme.Radius
import net.planetfoss.app.ui.theme.Space
import net.planetfoss.app.ui.weekly.WeeklyTeaserCard
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.material3.TextButton
import net.planetfoss.core.api.Use
import net.planetfoss.core.api.ListTarget
import net.planetfoss.core.api.Item
import net.planetfoss.core.api.LogComet
import net.planetfoss.core.api.LogNova
import net.planetfoss.core.api.LogbookEntry
import net.planetfoss.core.api.LogbookTeaser
import net.planetfoss.core.api.SourceLangs
import net.planetfoss.core.api.TopicRef
import net.planetfoss.core.api.WeekTeaser
import javax.inject.Inject

data class LogbookUi(val entry: LogbookEntry? = null, val loading: Boolean = true, val error: UiError? = null)

/** One logbook entry; starts with the route's day (empty = the newest), the day row switches entries. */
@HiltViewModel
class LogbookViewModel @Inject constructor(
    private val repo: FeedRepository,
    private val settings: SettingsRepository,
    private val widgets: net.planetfoss.app.data.WidgetStore,
    handle: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(LogbookUi())
    val state: StateFlow<LogbookUi> = _state.asStateFlow()
    private var day: String? = handle.get<String>("day")?.takeIf { it.isNotEmpty() }

    init {
        load(day)
    }

    /** Pull to refresh. */
    fun refresh() = load(day)

    fun load(d: String? = day) {
        day = d
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val lang = settings.settings.first().contentLang
            val r = runCatching { repo.logbook(d, lang) }
            // the newest entry also feeds the logbook widget
            if (d == null) r.getOrNull()?.let { runCatching { widgets.saveLogbook(it) } }
            _state.update { LogbookUi(r.getOrNull() ?: it.entry, loading = false, error = r.exceptionOrNull()?.let(UiError::from)) }
            // the newest entry is read now: Latest shows it as a marker instead of the card on top (docs/09-latest-tab.md D-L3)
            r.getOrNull()?.takeIf { d == null || it.archive.firstOrNull() == it.day }?.let { settings.setLogbookRead(it.day) }
        }
    }
}

/**
 * The website's /logbuch/: every morning at 6:00 (Berlin) the most important of the 24 hours before – the biggest story,
 * supernovas, comets, new releases, “also worth knowing” and Sternstunden. Written once and never changed; headline and
 * opening paragraph come from the server in fixed sentences.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LogbookScreen(
    onBack: () -> Unit,
    onWeekly: (String) -> Unit,
    vm: LogbookViewModel = hiltViewModel(),
) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val actions = LocalItemActions.current
    val listState = androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState()
    Scaffold(
        topBar = {
            run {
                TopAppBar(
                    title = { Text(stringResource(R.string.logbook_title)) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                )
            }
        },
    ) { padding ->
        RefreshBox(isRefreshing = ui.loading && ui.entry != null, onRefresh = { vm.refresh() }, modifier = Modifier.padding(padding).fillMaxSize()) {
            val e = ui.entry
            when {
                e == null && ui.error != null -> CenterMessage(errorText(ui.error!!, hasData = false), onRetry = { vm.load() })
                e == null -> CenterMessage(stringResource(R.string.loading), loading = true)
                else -> Column(Modifier.fillMaxSize()) {
                    ui.error?.let { ErrorBanner(errorText(it, hasData = true)) { vm.load() } }
                    // wide: the sections' cards in two columns under headings over both (docs/11-tablet-mode.md)
                    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        val two = maxWidth >= TwoColumnsFrom
                        val full = StaggeredGridItemSpan.FullLine
                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Fixed(if (two) 2 else 1),
                            state = listState,
                            contentPadding = PaddingValues(Space.l),
                            // airy (6.11.: “very many details on little room” on the phone): more room between the cards
                            verticalItemSpacing = Space.l,
                            horizontalArrangement = Arrangement.spacedBy(Space.l),
                            modifier = Modifier.fillMaxHeight().widthIn(max = (if (two) WideMaxWidth else ContentMaxWidth) + Space.l * 2),
                        ) {
                            val width = Modifier.fillMaxWidth()
                            item(key = "head", span = full) { Masthead(e, width, onDay = { vm.load(it) }) }
                            // Mondays: the week that just ended, as the hint on Latest (website: band below the head)
                            e.weekDone?.let { w ->
                                item(key = "week", span = full) {
                                    Box(width) {
                                        WeeklyTeaserCard(WeekTeaser(w.week, w.items, w.sources, w.supernovas, w.story)) { onWeekly(w.week) }
                                    }
                                }
                            }
                            if (e.supernovas.isNotEmpty()) {
                                item(key = "novas-h", span = full) { SectionHead("✦ " + stringResource(R.string.logbook_novas), width.padding(top = Space.m)) }
                                // a single supernova over both columns with its picture beside the text, several side by side
                                val alone = two && e.supernovas.size == 1
                                items(e.supernovas, key = { "nova-" + it.key }, span = { if (alone) full else StaggeredGridItemSpan.SingleLane }) { n ->
                                    NovaCard(n, width, side = alone, band = two) { actions.topic(TopicRef(n.key, n.label, true)) }
                                }
                            }
                            if (e.comets.isNotEmpty()) {
                                item(key = "comets-h", span = full) { SectionHead("☄ " + stringResource(R.string.logbook_comets), width.padding(top = Space.m)) }
                                items(e.comets, key = { "comet-" + it.key }) { c -> CometCard(c, width) { actions.topic(TopicRef(c.key, c.label)) } }
                            }
                            if (e.releases.isNotEmpty()) {
                                item(key = "rel-h", span = full) { SectionHead(stringResource(R.string.weekly_releases), width.padding(top = Space.m)) }
                                item(key = "rel", span = full) {
                                    val perRow = if (two) 4 else 2
                                    FlowRow(width, horizontalArrangement = Arrangement.spacedBy(Space.m), verticalArrangement = Arrangement.spacedBy(Space.m), maxItemsInEachRow = perRow) {
                                        e.releases.forEach { t ->
                                            OutlinedCard(onClick = { actions.topic(TopicRef(t.key, t.label, t.supernova)) }, shape = Radius.s, modifier = Modifier.weight(1f)) {
                                                Row(Modifier.height(IntrinsicSize.Min)) {
                                                    Box(Modifier.width(4.dp).fillMaxHeight().background(LocalPlanetColors.current.aurora))
                                                    Column(Modifier.padding(horizontal = Space.m, vertical = Space.m), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                                                        Text(
                                                            buildString { append(t.entity); t.version?.let { append(" "); append(it) } },
                                                            style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                        )
                                                        Text(
                                                            pluralStringResource(R.plurals.n_sources, t.sources, t.sources) + if (t.supernova) " · ✦" else if (t.comet) " · ☄" else "",
                                                            style = MetaStyle, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        // an incomplete last row: empty places keep the last tiles at their width
                                        repeat((perRow - e.releases.size % perRow) % perRow) { Box(Modifier.weight(1f)) }
                                    }
                                }
                            }
                            if (e.also.isNotEmpty()) {
                                item(key = "also-h", span = full) { SectionHead(stringResource(R.string.logbook_also), width.padding(top = Space.m)) }
                                items(e.also, key = { "also-" + it.id }) { ItemCard(it, width) }
                            }
                            if (e.starred.isNotEmpty()) {
                                item(key = "starred-h", span = full) { SectionHead("★ " + stringResource(R.string.weekly_starred), width.padding(top = Space.m)) }
                                items(e.starred, key = { "star-" + it.id }) { ItemCard(it, width) }
                            }
                            if (e.archive.size > 1) {
                                item(key = "archive-h", span = full) { SectionHead(stringResource(R.string.logbook_archive), width.padding(top = Space.m)) }
                                item(key = "archive", span = full) {
                                    FlowRow(width, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                                        e.archive.forEach { d ->
                                            FilterChip(selected = d == e.day, onClick = { if (d != e.day) vm.load(d) }, label = { Text(shortDay(d)) })
                                        }
                                    }
                                }
                            }
                            item(key = "end", span = full) { Box(Modifier.padding(bottom = Space.l)) }
                        }
                    }
                }
            }
        }
    }
}

/** “5.11.” from “2026-11-05”. */
internal fun shortDay(day: String): String = day.split('-').let { p -> if (p.size == 3) "${p[2].toInt()}.${p[1].toInt()}." else day }

@Composable
private fun Label(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) =
    Text(text.uppercase(), style = MetaStyle.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), color = color)

/** Night-blue head like the website's `.lb-mast`: entry, headline, opening paragraph, biggest story and the neighbouring days. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Masthead(e: LogbookEntry, modifier: Modifier, onDay: (String) -> Unit) {
    val planet = LocalPlanetColors.current
    val actions = LocalItemActions.current
    Card(modifier, shape = Radius.card, colors = CardDefaults.cardColors(containerColor = NovaSpace, contentColor = NovaInk)) {
        Column(Modifier.fillMaxWidth().drawBehind { novaSky() }.padding(Space.xl), verticalArrangement = Arrangement.spacedBy(Space.m)) {
            Label(stringResource(R.string.logbook_kicker, e.label), planet.supernova)
            Text(e.headline, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            if (e.lede.isNotEmpty()) Text(e.lede, style = MaterialTheme.typography.bodyLarge, color = NovaInk2)
            // the biggest story only when it is not the headline itself (“OpenSSH 10” stood there three times)
            e.story?.takeIf { it.name != e.headline }?.let { s ->
                Column(
                    Modifier.fillMaxWidth().clip(Radius.s).clickable(role = Role.Button) { actions.bundle(s.id) }.padding(vertical = Space.xs),
                    verticalArrangement = Arrangement.spacedBy(Space.xxs),
                ) {
                    Label(stringResource(R.string.logbook_story), planet.supernova)
                    Text(s.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(langLine(s.langs), style = MetaStyle, color = NovaInk3)
                }
            }
            // the neighbouring entries, as the website's day row
            val i = e.archive.indexOf(e.day)
            val prev = if (i >= 0) e.archive.getOrNull(i + 1) else null
            val next = if (i > 0) e.archive.getOrNull(i - 1) else null
            if (prev != null || next != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.padding(top = Space.xs)) {
                    prev?.let { d -> DayChip("‹ " + shortDay(d), stringResource(R.string.logbook_prev)) { onDay(d) } }
                    next?.let { d -> DayChip(shortDay(d) + " ›", stringResource(R.string.logbook_next)) { onDay(d) } }
                }
            }
        }
    }
}

@Composable
private fun DayChip(text: String, description: String, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent, contentColor = NovaInk2),
        border = BorderStroke(1.dp, NovaLine),
        modifier = Modifier.heightIn(min = 40.dp).semantics { contentDescription = description },
    ) {
        Box(Modifier.heightIn(min = 40.dp).padding(horizontal = Space.m), contentAlignment = Alignment.Center) {
            Text(text, style = MetaStyle, fontWeight = FontWeight.Bold)
        }
    }
}

/** A supernova of the entry: picture, badge, new or still shining, quote, numbers and up to three reports (`.wk-nova`). */
@Composable
private fun NovaCard(n: LogNova, modifier: Modifier, side: Boolean = false, band: Boolean = false, onOpen: () -> Unit) {
    Card(onClick = onOpen, modifier = modifier, shape = Radius.card, colors = CardDefaults.cardColors(containerColor = NovaSpace, contentColor = NovaInk)) {
        val picture = @Composable { m: Modifier ->
            n.image?.let { img ->
                AsyncImage(model = img.large, contentDescription = null, contentScale = if (img.fit) ContentScale.Fit else ContentScale.Crop, modifier = m)
            }
        }
        // side: alone over both columns, the picture on the left; band: in a column on a wide screen, a lower picture
        // so the text is not pushed below the fold (docs/11-tablet-mode.md)
        if (side) {
            Row(Modifier.fillMaxWidth().drawBehind { novaSky() }) {
                if (n.image != null) picture(Modifier.weight(5f).aspectRatio(4f / 3f))
                Box(Modifier.weight(7f)) { NovaText(n) }
            }
        } else {
            Column(Modifier.fillMaxWidth().drawBehind { novaSky() }) {
                picture(Modifier.fillMaxWidth().aspectRatio(if (band) 2f else 16f / 9f))
                NovaText(n)
            }
        }
    }
}

@Composable
private fun NovaText(n: LogNova) {
    val planet = LocalPlanetColors.current
    Column(Modifier.padding(Space.xl), verticalArrangement = Arrangement.spacedBy(Space.m)) {
        NovaBurst(burning = true)
        Label(
            if (n.new) stringResource(R.string.logbook_new) else pluralStringResource(R.plurals.logbook_still, n.newReports, n.newReports),
            if (n.new) Color(0xFFFFB199) else NovaInk3,
        )
        Text(n.label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        n.quote?.let { q ->
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(planet.supernova))
                Column(Modifier.padding(start = Space.m), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                    Text(stringResource(R.string.quote, q.text), style = MaterialTheme.typography.bodyMedium, color = NovaInk2, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (q.by.isNotEmpty()) Text(q.by, style = MetaStyle, fontWeight = FontWeight.Bold, color = planet.supernova)
                }
            }
        }
        Text(
            pluralStringResource(R.plurals.n_posts, n.items, n.items) + " · " + pluralStringResource(R.plurals.n_sources, n.sources, n.sources) +
                " · DE ${n.langs.de} · EN ${n.langs.en}",
            style = MetaStyle, color = NovaInk3,
        )
        if (n.reports.size > 1) Reports(n.reports.take(2), NovaInk3, NovaLine)
    }
}

/** A comet: rising, not a supernova yet – outlined in the accent colour like the website's `.lb-comet`. */
@Composable
private fun CometCard(c: LogComet, modifier: Modifier, onOpen: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    OutlinedCard(onClick = onOpen, modifier = modifier, shape = Radius.card, border = BorderStroke(1.5.dp, accent)) {
        Column(Modifier.padding(Space.xl), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Label(pluralStringResource(R.plurals.logbook_comet_n, c.sources, c.sources), accent)
            Text(c.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (c.reports.isNotEmpty()) Reports(c.reports.take(2), MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** Reports as rows: source and title, each opens the article. */
@Composable
private fun Reports(items: List<Item>, sourceColor: Color, divider: Color) {
    val actions = LocalItemActions.current
    Column {
        items.forEach { r ->
            HorizontalDivider(color = divider)
            // source above the title: a fixed source column cut the names (“BleepingCo…”) and crowded the card
            Column(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(Radius.s).clickable(role = Role.Button) { actions.openFrom(r, "logbuch") }
                    .padding(vertical = Space.m).semantics(mergeDescendants = true) { contentDescription = "${r.title}, ${r.source.name}" },
                verticalArrangement = Arrangement.spacedBy(Space.xxs),
            ) {
                Text(r.source.name, style = MetaStyle, fontWeight = FontWeight.Bold, color = sourceColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(r.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** “4 German, 8 English sources” or “so far only English sources”, as on the website. */
@Composable
private fun langLine(l: SourceLangs): String = when {
    l.de > 0 && l.en > 0 -> stringResource(R.string.bundle_langs, l.de, l.en)
    l.en > 0 -> stringResource(R.string.bundle_only_en)
    else -> stringResource(R.string.bundle_only_de)
}

/**
 * Latest: the newest logbook entry as a slim night-blue band; Monday to Wednesday with last week's review as a second
 * target in the same band (the website's `.lb-band.duo`) instead of two cards on top of each other.
 */
@Composable
fun LogbookBand(t: LogbookTeaser, week: WeekTeaser?, onLogbook: () -> Unit, onWeekly: (String) -> Unit) {
    val planet = LocalPlanetColors.current
    // below as much room as “In orbit” leaves above (its row brings its own bottom space), otherwise the band sits on the first post
    Card(Modifier.fillMaxWidth().padding(bottom = Space.s), shape = Radius.card, colors = CardDefaults.cardColors(containerColor = NovaSpace, contentColor = NovaInk)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).drawBehind { novaSky() }) {
            Column(
                Modifier.weight(1f).clickable(role = Role.Button, onClick = onLogbook).padding(horizontal = Space.l, vertical = Space.m),
                verticalArrangement = Arrangement.spacedBy(Space.xxs),
            ) {
                val marks = listOfNotNull(
                    t.supernovas.takeIf { it > 0 }?.let { "$it\u00A0✦" },
                    t.comets.takeIf { it > 0 }?.let { "$it\u00A0☄" },
                ).joinToString(" · ") // no-break spaces: a count never parts from its mark
                Label(stringResource(R.string.logbook_band, shortDay(t.day)) + if (marks.isNotEmpty()) " · $marks" else "", planet.supernova)
                Text(t.headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            week?.let { w ->
                Box(Modifier.width(1.dp).fillMaxHeight().background(NovaLine))
                // short texts on purpose: sized by a long kicker this half squeezed the logbook into a narrow strip
                Column(
                    Modifier.fillMaxHeight().clickable(role = Role.Button) { onWeekly(w.week) }.background(Color(0x2E6E5BEB))
                        .padding(horizontal = Space.m, vertical = Space.m),
                    verticalArrangement = Arrangement.spacedBy(Space.xxs),
                ) {
                    Label(stringResource(R.string.weekly_band_kick), planet.supernova)
                    Text(stringResource(R.string.weekly_band_title, w.week.substringAfter('-').toInt()), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
