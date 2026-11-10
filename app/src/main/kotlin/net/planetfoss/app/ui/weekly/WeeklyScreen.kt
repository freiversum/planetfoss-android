// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.weekly

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.planetfoss.app.R
import net.planetfoss.app.data.FeedRepository
import net.planetfoss.app.data.prefs.SettingsRepository
import net.planetfoss.app.ui.components.SourceMotif
import net.planetfoss.app.ui.components.CenterMessage
import net.planetfoss.app.ui.components.ChipTag
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
import net.planetfoss.app.ui.theme.LocalPlanetColors
import net.planetfoss.app.ui.theme.MetaStyle
import net.planetfoss.app.ui.theme.Radius
import net.planetfoss.app.ui.theme.Space
import net.planetfoss.app.ui.theme.rubricColor
import net.planetfoss.core.api.Item
import net.planetfoss.core.api.SourceLangs
import net.planetfoss.core.api.TopicRef
import net.planetfoss.core.api.WeekNova
import net.planetfoss.core.api.WeekReport
import net.planetfoss.core.api.WeekStory
import net.planetfoss.core.api.WeekTeaser
import net.planetfoss.core.api.WeeklyReview
import java.text.NumberFormat
import javax.inject.Inject
import kotlin.math.abs

data class WeeklyUi(val review: WeeklyReview? = null, val loading: Boolean = true, val error: UiError? = null)

/** The weekly review of one finished week; starts with the last one, the archive row switches weeks. */
@HiltViewModel
class WeeklyViewModel @Inject constructor(
    private val repo: FeedRepository,
    private val settings: SettingsRepository,
    handle: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(WeeklyUi())
    val state: StateFlow<WeeklyUi> = _state.asStateFlow()
    private var week: String? = handle.get<String>("week")?.takeIf { it.isNotEmpty() }

    init {
        load(week)
    }

    fun load(w: String? = week) {
        week = w
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val lang = settings.settings.first().contentLang
            val r = runCatching { repo.weekly(w, lang) }
            _state.update { WeeklyUi(r.getOrNull() ?: it.review, loading = false, error = r.exceptionOrNull()?.let(UiError::from)) }
        }
    }
}

/**
 * The website's /rueckblick/ as a small magazine issue: masthead with opening paragraph and key figures, cover story,
 * supernovas with picture and quote, the biggest stories, new releases, one post per section, the week in numbers,
 * videos, Sternstunden and topics – compiled automatically from the posts, nothing picked or written by hand.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WeeklyScreen(onBack: () -> Unit, onLogbook: (String) -> Unit = {}, vm: WeeklyViewModel = hiltViewModel()) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val actions = LocalItemActions.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.weekly_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        RefreshBox(isRefreshing = ui.loading && ui.review != null, onRefresh = { vm.load() }, modifier = Modifier.padding(padding).fillMaxSize()) {
            val r = ui.review
            when {
                r == null && ui.error != null -> CenterMessage(errorText(ui.error!!, hasData = false), onRetry = { vm.load() })
                r == null -> CenterMessage(stringResource(R.string.loading), loading = true)
                else -> Column(Modifier.fillMaxSize()) {
                    ui.error?.let { ErrorBanner(errorText(it, hasData = true)) { vm.load() } }
                    LazyColumn(
                        contentPadding = PaddingValues(Space.l),
                        verticalArrangement = Arrangement.spacedBy(Space.m),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        val width = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth()
                        // no duplicates (as on the website): the cover story's supernova is shown with it, the next ones
                        // as cards, the rest as chips; stories of a supernova card are already left out by the server
                        val cover = r.stories.firstOrNull()
                        val novaCards = r.supernovas.filter { it.card }
                        val novaRest = r.supernovas.filter { !it.card && !it.cover }
                        item(key = "head") { Masthead(r, width) }
                        cover?.let { c -> item(key = "cover") { CoverStory(c, width) } }

                        item(key = "novas-h") { SectionHead(stringResource(R.string.weekly_novas), width, hint = stringResource(R.string.weekly_novas_hint)) }
                        if (novaCards.isEmpty() && novaRest.isEmpty()) {
                            item(key = "novas-none") {
                                Text(stringResource(if (r.supernovas.isEmpty()) R.string.weekly_none else R.string.weekly_nova_cover), width,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        items(novaCards, key = { "nova-" + it.key }) { n -> NovaIssueCard(n, width) { actions.topic(TopicRef(n.key, n.label, true)) } }
                        if (novaRest.isNotEmpty()) {
                            item(key = "novas-rest") {
                                Column(width, verticalArrangement = Arrangement.spacedBy(Space.s)) {
                                    if (novaCards.isNotEmpty()) Label(stringResource(R.string.weekly_more_novas))
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                                        novaRest.forEach { n ->
                                            ChipTag(pluralStringResource(R.plurals.weekly_nova_chip, n.sources, n.label, n.sources), supernova = true) { actions.topic(TopicRef(n.key, n.label, true)) }
                                        }
                                    }
                                }
                            }
                        }

                        val more = r.stories.drop(1)
                        if (more.isNotEmpty()) {
                            item(key = "stories-h") { SectionHead(stringResource(R.string.weekly_stories), width, hint = stringResource(R.string.weekly_stories_hint)) }
                            itemsIndexed(more, key = { _, s -> "story-" + s.id }) { i, s -> StoryRow(i + 1, s, width) { actions.bundle(s.id) } }
                        }

                        if (r.releases.isNotEmpty()) {
                            item(key = "rel-h") { SectionHead(stringResource(R.string.weekly_releases), width, hint = stringResource(R.string.weekly_releases_hint)) }
                            item(key = "rel") {
                                FlowRow(width, horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s), maxItemsInEachRow = 2) {
                                    r.releases.forEach { t ->
                                        OutlinedCard(onClick = { actions.topic(TopicRef(t.key, t.label)) }, shape = Radius.s, modifier = Modifier.weight(1f)) {
                                            Row(Modifier.height(IntrinsicSize.Min)) {
                                                Box(Modifier.width(4.dp).fillMaxHeight().background(LocalPlanetColors.current.aurora))
                                                Column(Modifier.padding(horizontal = Space.m, vertical = Space.s)) {
                                                    Text(
                                                        buildString { append(t.entity); t.version?.let { append(" "); append(it) } },
                                                        style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                    )
                                                    Text(pluralStringResource(R.plurals.n_sources, t.sources, t.sources), style = MetaStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (r.rubrics.isNotEmpty()) {
                            item(key = "rub-h") { SectionHead(stringResource(R.string.weekly_rubrics), width, hint = stringResource(R.string.weekly_rubrics_hint)) }
                            items(r.rubrics, key = { "rub-" + it.slug }) { rb ->
                                Column(width, verticalArrangement = Arrangement.spacedBy(Space.s)) {
                                    val tint = rubricColor(rb.slug) ?: MaterialTheme.colorScheme.primary
                                    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(tint))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(rb.name.uppercase(), style = MetaStyle.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), color = tint, modifier = Modifier.weight(1f))
                                        Text(pluralStringResource(R.plurals.n_posts, rb.items, rb.items), style = MetaStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    ItemCard(rb.item, Modifier.fillMaxWidth())
                                }
                            }
                        }

                        if (r.numbers.items > 0 && r.days.isNotEmpty()) {
                            item(key = "num-h") { SectionHead(stringResource(R.string.weekly_numbers), width) }
                            item(key = "num-days") { DaysBox(r, width) }
                            if (r.sections.isNotEmpty()) {
                                item(key = "num-sections") {
                                    BarsBox(stringResource(R.string.weekly_sections), r.sections.take(8).map { Bar(it.name, it.items, rubricColor(it.slug)) }, r.texts.sections, width)
                                }
                            }
                            if (r.topSources.isNotEmpty()) {
                                item(key = "num-sources") {
                                    BarsBox(stringResource(R.string.weekly_top), r.topSources.map { Bar(it.name, it.items, LocalPlanetColors.current.aurora, it.logo, it.color) }, r.texts.sources, width)
                                }
                            }
                        }

                        if (r.videos.isNotEmpty()) {
                            item(key = "videos-h") { SectionHead("▶ " + stringResource(R.string.weekly_videos), width) }
                            items(r.videos, key = { "video-" + it.id }) { ItemCard(it, width) }
                        }
                        if (r.starred.isNotEmpty()) {
                            item(key = "starred-h") { SectionHead("★ " + stringResource(R.string.weekly_starred), width) }
                            items(r.starred, key = { "star-" + it.id }) { ItemCard(it, width) }
                        }
                        if (r.topics.isNotEmpty()) {
                            item(key = "topics-h") { SectionHead(stringResource(R.string.weekly_topics), width, hint = stringResource(R.string.weekly_topics_hint)) }
                            item(key = "topics") {
                                FlowRow(width, horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                                    r.topics.forEach { t -> ChipTag(t.label, supernova = false, comet = t.comet) { actions.topic(TopicRef(t.key, t.label)) } }
                                }
                            }
                        }
                        // the week in the logbook (website: below the review): one card per morning, the cover story marked
                        if (r.logbook.isNotEmpty()) {
                            item(key = "logbook-h") { SectionHead(stringResource(R.string.logbook_week), width, hint = stringResource(R.string.logbook_week_hint)) }
                            item(key = "logbook") {
                                FlowRow(width, horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s), maxItemsInEachRow = 2) {
                                    r.logbook.forEach { d -> LogDayCard(d, Modifier.weight(1f).fillMaxRowHeight()) { onLogbook(d.day) } }
                                    // an odd number: an empty half keeps the last card at half width
                                    if (r.logbook.size % 2 == 1) Box(Modifier.weight(1f))
                                }
                            }
                        }
                        if (r.archive.size > 1) {
                            item(key = "archive-h") { SectionHead(stringResource(R.string.weekly_archive), width) }
                            item(key = "archive") {
                                FlowRow(width, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                                    r.archive.forEach { w ->
                                        FilterChip(
                                            selected = w == r.week,
                                            onClick = { if (w != r.week) vm.load(w) },
                                            label = { Text(stringResource(R.string.weekly_kw, w.substringAfter('-').toInt(), w.substringBefore('-').toInt())) },
                                        )
                                    }
                                }
                            }
                        }
                        item(key = "end") { Box(Modifier.padding(bottom = Space.l)) }
                    }
                }
            }
        }
    }
}

/** Thousands separator of the app's language (“1.200”, “1,200”). */
@Composable
private fun num(n: Int): String = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0]).format(n)

@Composable
private fun Label(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) =
    Text(text.uppercase(), style = MetaStyle.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), color = color)

/** Night-blue head like the website's `.wk-mast`: issue, week, dates, opening paragraph and five key figures. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Masthead(r: WeeklyReview, modifier: Modifier) {
    val planet = LocalPlanetColors.current
    val nb = r.numbers
    Card(modifier, shape = Radius.card, colors = CardDefaults.cardColors(containerColor = NovaSpace, contentColor = NovaInk)) {
        Column(Modifier.fillMaxWidth().drawBehind { novaSky() }.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Label(stringResource(R.string.weekly_kicker, r.week.substringAfter('-').toInt(), r.week.substringBefore('-').toInt()), planet.supernova)
            // “Woche 44 · 26.10.–1.11.2026”: the week as heading, the dates below
            Text(r.label.substringBefore(" · "), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            if (" · " in r.label) Text(r.label.substringAfter(" · "), style = MetaStyle, color = NovaInk2)
            Text(
                r.lede ?: stringResource(R.string.weekly_intro, nb.items, nb.sources),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = Space.xs, bottom = Space.s),
            )
            val trend = nb.trend?.let { t -> stringResource(R.string.weekly_trend, (if (t > 0) "+" else if (t < 0) "−" else "±") + abs(t) + " %", num(nb.prevItems)) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s), maxItemsInEachRow = 2) {
                KeyFigure(stringResource(R.string.weekly_k_items), num(nb.items), Modifier.fillMaxWidth(), trend, (nb.trend ?: 0) > 0)
                KeyFigure(stringResource(R.string.weekly_k_sources), num(nb.sources), Modifier.weight(1f))
                KeyFigure(stringResource(R.string.weekly_k_novas), num(nb.supernovas.takeIf { it > 0 } ?: r.supernovas.size), Modifier.weight(1f))
                KeyFigure(stringResource(R.string.weekly_k_videos), num(nb.videos), Modifier.weight(1f))
                KeyFigure(stringResource(R.string.weekly_k_langs), num(nb.de) + " · " + num(nb.en), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun KeyFigure(label: String, value: String, modifier: Modifier, sub: String? = null, up: Boolean = false) {
    Column(
        modifier
            .clip(Radius.m)
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, NovaLine, Radius.m)
            .padding(horizontal = Space.m, vertical = Space.s)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(label.uppercase(), style = MetaStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp), color = NovaInk3)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1)
        sub?.let { Text(it, style = MetaStyle.copy(fontSize = 11.sp), color = if (up) Color(0xFF5EEAD4) else NovaInk3) }
    }
}

/** Cover story: big picture, name, lead article, teaser and how other sources covered it. */
@Composable
private fun CoverStory(s: WeekStory, modifier: Modifier) {
    val actions = LocalItemActions.current
    val planet = LocalPlanetColors.current
    val lead = s.lead
    Card(modifier, shape = Radius.card, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column {
            lead?.image?.let { img ->
                AsyncImage(
                    model = img.large,
                    contentDescription = null,
                    contentScale = if (img.fit) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f).background(NovaSpace).clickable(role = Role.Button) { actions.bundle(s.id) },
                )
            }
            Column(Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    Label(stringResource(R.string.weekly_cover), MaterialTheme.colorScheme.primary)
                    Text(pluralStringResource(R.plurals.bundle_sources, s.sources, s.sources), style = MetaStyle, color = planet.aurora, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                s.supernova?.let { n -> ChipTag(n.label, supernova = true) { actions.topic(TopicRef(n.key, n.label, true)) } }
                Text(
                    s.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clickable(role = Role.Button) { actions.bundle(s.id) }.semantics { heading() },
                )
                if (lead != null) {
                    Column(
                        Modifier.fillMaxWidth().clip(Radius.s).clickable(role = Role.Button) { actions.openFrom(lead, "liste") }.padding(vertical = Space.xs),
                        verticalArrangement = Arrangement.spacedBy(Space.xxs),
                    ) {
                        Text(lead.source.name, style = MetaStyle, fontWeight = FontWeight.Bold)
                        if (lead.title != s.name) Text(lead.title, style = MaterialTheme.typography.titleMedium)
                        lead.excerpt?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis) }
                    }
                }
                if (s.reports.isNotEmpty()) {
                    Label(stringResource(R.string.weekly_cover_reports))
                    Column {
                        s.reports.forEachIndexed { i, rep ->
                            if (i > 0) HorizontalDivider()
                            CoverReport(rep) { actions.open(rep.url) }
                        }
                    }
                }
                TextButton(onClick = { actions.bundle(s.id) }, contentPadding = PaddingValues(0.dp)) {
                    Text(pluralStringResource(R.plurals.weekly_cover_all, s.sources, s.sources) + " →")
                }
            }
        }
    }
}

@Composable
private fun CoverReport(r: WeekReport, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(Radius.s).clickable(role = Role.Button, onClick = onOpen).padding(vertical = Space.s)
            .semantics(mergeDescendants = true) { contentDescription = "${r.title}, ${r.source}" },
        horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        SourceDot(r.logo, r.color, Modifier.padding(top = 2.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
            Text(r.source + " · " + r.lang.uppercase(), style = MetaStyle, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(r.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SourceDot(logo: String?, color: String?, modifier: Modifier = Modifier) {
    if (logo != null) {
        AsyncImage(model = logo, contentDescription = null, modifier = modifier.size(18.dp).clip(RoundedCornerShape(4.dp)))
    } else {
        val c = color?.let { runCatching { Color(it.toColorInt()) }.getOrNull() } ?: MaterialTheme.colorScheme.primary
        Box(modifier.padding(4.dp).size(10.dp).clip(CircleShape).background(c))
    }
}

/** A supernova of the week as a night-blue card with picture, badge, name, quote and numbers (`.wk-nova`). */
@Composable
private fun NovaIssueCard(n: WeekNova, modifier: Modifier, onOpen: () -> Unit) {
    val planet = LocalPlanetColors.current
    Card(onClick = onOpen, modifier = modifier, shape = Radius.card, colors = CardDefaults.cardColors(containerColor = NovaSpace, contentColor = NovaInk)) {
        Column(Modifier.fillMaxWidth().drawBehind { novaSky() }) {
            n.image?.let { img ->
                AsyncImage(
                    model = img.large,
                    contentDescription = null,
                    contentScale = if (img.fit) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                )
            }
            Column(Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                NovaBurst(burning = true)
                Text(n.label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                n.quote?.let { q ->
                    Row(Modifier.height(IntrinsicSize.Min)) {
                        Box(Modifier.width(3.dp).fillMaxHeight().background(planet.supernova))
                        Column(Modifier.padding(start = Space.m), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                            Text(stringResource(R.string.quote, q.text), style = MaterialTheme.typography.bodyMedium, color = NovaInk2, maxLines = 5, overflow = TextOverflow.Ellipsis)
                            if (q.by.isNotEmpty()) Text(q.by, style = MetaStyle, fontWeight = FontWeight.Bold, color = planet.supernova)
                        }
                    }
                }
                Text(
                    pluralStringResource(R.plurals.n_posts, n.items, n.items) + " · " + pluralStringResource(R.plurals.n_sources, n.sources, n.sources),
                    style = MetaStyle, color = NovaInk3,
                )
            }
        }
    }
}

/** One of the further stories: number, picture, name, lead title, sources and languages. */
@Composable
private fun StoryRow(n: Int, s: WeekStory, modifier: Modifier, onOpen: () -> Unit) {
    Row(
        modifier.clip(Radius.s).clickable(role = Role.Button, onClick = onOpen).padding(vertical = Space.xs),
        horizontalArrangement = Arrangement.spacedBy(Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$n", style = MaterialTheme.typography.headlineSmall, color = LocalPlanetColors.current.violet, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, modifier = Modifier.width(28.dp))
        // without a picture an empty tile of the same size, so the titles stay in line
        val thumb = Modifier.width(88.dp).aspectRatio(16f / 10f).clip(Radius.s).background(MaterialTheme.colorScheme.surfaceVariant)
        val lead = s.lead
        val img = lead?.image
        when {
            img != null -> AsyncImage(model = img.small, contentDescription = null, contentScale = if (img.fit) ContentScale.Fit else ContentScale.Crop, modifier = thumb)
            lead != null -> SourceMotif(lead, thumb, compact = true)
            else -> Box(thumb)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
            Text(s.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (s.title != s.name) Text(s.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(pluralStringResource(R.plurals.bundle_sources, s.sources, s.sources) + " · " + langLine(s.langs), style = MetaStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Posts per day as bars, Monday to Sunday, and the sentence about the busiest and quietest day. */
@Composable
private fun DaysBox(r: WeeklyReview, modifier: Modifier) {
    val max = (r.days.maxOfOrNull { it.items } ?: 1).coerceAtLeast(1)
    val bar = MaterialTheme.colorScheme.primary
    OutlinedCard(modifier, shape = Radius.card) {
        Column(Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Label(stringResource(R.string.weekly_days))
            Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                r.days.forEach { d ->
                    Column(
                        Modifier.weight(1f).fillMaxHeight().clearAndSetSemantics { contentDescription = "${d.weekday}: ${d.items}" },
                        verticalArrangement = Arrangement.Bottom,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("${d.items}", style = MetaStyle.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(
                            Modifier.fillMaxWidth().fillMaxHeight((d.items.toFloat() / max).coerceAtLeast(0.03f))
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(bar),
                        )
                    }
                }
            }
            // weekdays in their own row: under the tallest bar there is no room left in its column
            Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                r.days.forEach { d ->
                    Text(d.weekday.take(2), style = MetaStyle.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            if (r.texts.days.isNotEmpty()) Text(r.texts.days, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private data class Bar(val name: String, val n: Int, val color: Color?, val logo: String? = null, val dot: String? = null)

/** Horizontal bars (sections, most active sources) with a sentence below, as the website's `.wk-hbars`. */
@Composable
private fun BarsBox(title: String, bars: List<Bar>, text: String, modifier: Modifier) {
    val max = (bars.maxOfOrNull { it.n } ?: 1).coerceAtLeast(1)
    OutlinedCard(modifier, shape = Radius.card) {
        Column(Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Label(title)
            bars.forEach { b ->
                Row(
                    Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "${b.name}: ${b.n}" },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    Row(Modifier.weight(1.3f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (b.logo != null || b.dot != null) SourceDot(b.logo, b.dot)
                        Text(b.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box(Modifier.weight(1f)) {
                        Box(Modifier.fillMaxWidth((b.n.toFloat() / max).coerceAtLeast(0.02f)).height(8.dp).clip(CircleShape).background(b.color ?: MaterialTheme.colorScheme.primary))
                    }
                    Text("${b.n}", style = MetaStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.width(32.dp))
                }
            }
            if (text.isNotEmpty()) Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** One morning of the week in the logbook: weekday and date, headline, supernovas and comets; the cover story outlined in orange. */
@Composable
private fun LogDayCard(d: net.planetfoss.core.api.WeekLogDay, modifier: Modifier, onOpen: () -> Unit) {
    val planet = LocalPlanetColors.current
    OutlinedCard(
        onClick = onOpen,
        modifier = modifier,
        shape = Radius.m,
        border = if (d.cover) androidx.compose.foundation.BorderStroke(2.dp, planet.supernova) else CardDefaults.outlinedCardBorder(),
    ) {
        Column(Modifier.padding(horizontal = Space.m, vertical = Space.s), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
            val weekday = java.time.LocalDate.parse(d.day).dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, LocalConfiguration.current.locales[0])
            Label(weekday + " " + net.planetfoss.app.ui.logbook.shortDay(d.day))
            Text(d.headline, style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            val marks = if (d.cover) stringResource(R.string.weekly_cover)
            else listOfNotNull(d.novas.takeIf { it > 0 }?.let { "$it ✦" }, d.comets.takeIf { it > 0 }?.let { "$it ☄" }).joinToString(" · ")
            if (marks.isNotEmpty()) Text(marks, style = MetaStyle, color = if (d.cover) planet.supernova else MaterialTheme.colorScheme.onSurfaceVariant)
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

/** Latest, Monday to Wednesday: night-blue hint to last week's review with picture, numbers and cover story (`.wk-teaser`). */
@Composable
fun WeeklyTeaserCard(t: WeekTeaser, onOpen: () -> Unit) {
    val planet = LocalPlanetColors.current
    val w = t.week.substringAfter('-').toInt()
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = Radius.card, colors = CardDefaults.cardColors(containerColor = NovaSpace, contentColor = NovaInk)) {
        Row(
            Modifier.fillMaxWidth().drawBehind { novaSky() }.padding(Space.m),
            horizontalArrangement = Arrangement.spacedBy(Space.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            t.image?.let { img ->
                AsyncImage(model = img.small, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.width(84.dp).aspectRatio(16f / 10f).clip(Radius.s).background(NovaLine))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                Label(stringResource(R.string.weekly_teaser_kick, w), planet.supernova)
                Text(stringResource(R.string.weekly_teaser_h, w), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    pluralStringResource(R.plurals.n_posts, t.items, t.items) + " · " + pluralStringResource(R.plurals.n_sources, t.sources, t.sources),
                    style = MaterialTheme.typography.bodySmall, color = NovaInk2,
                )
                t.story?.let { Text(stringResource(R.string.weekly_teaser_story, it), style = MaterialTheme.typography.bodySmall, color = NovaInk3, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}
