// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.components

import android.graphics.Bitmap
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.graphics.toColorInt
import coil3.compose.AsyncImage
import coil3.toBitmap
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextAlign
import net.planetfoss.app.R
import net.planetfoss.app.ui.theme.LocalPlanetColors
import net.planetfoss.app.ui.theme.MetaStyle
import net.planetfoss.app.ui.theme.Radius
import net.planetfoss.app.ui.theme.Space
import net.planetfoss.core.api.Ad
import net.planetfoss.core.api.BundleReport
import net.planetfoss.core.api.Comet
import net.planetfoss.core.api.Item
import net.planetfoss.core.api.Supernova
import net.planetfoss.core.api.Support
import net.planetfoss.core.url.UrlPolicy
import java.time.Instant

@Composable
private fun relative(time: Instant): String {
    val german = LocalConfiguration.current.locales[0].language == "de"
    // Android capitalises German (“Vor 2 Stunden”); here it always follows a comma or a dot
    return DateUtils.getRelativeTimeSpanString(time.toEpochMilli(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
        .let { if (german) it.replaceFirstChar(Char::lowercaseChar) else it }
}

/** Wide windows (tablets, landscape) get the 960 px image, phones the 480 px one. */
@Composable
private fun wide(): Boolean = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() } > 480.dp

/** Play sign on a video's image, as on the website (`.video .img::before`): night-blue circle, white triangle. */
@Composable
private fun PlayMark(size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(Color(0xB30B1026)), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(size * 0.6f))
    }
}

/** Thumbnail of a compact post card (4:3, as the website's compact rows). */
private val ThumbWidth = 104.dp

/** Large font scales: the excerpt gives way so cards stay scannable. */
@Composable
private fun largeFont(): Boolean = LocalDensity.current.fontScale >= 1.6f

/**
 * One post: source and time, title, teaser, topic and rubric, other sources of the bundle, actions.
 * [lead] (the first post of a list, like the website's lead card) shows the image across the card;
 * every other post is compact with a 4:3 thumbnail beside the title (the website's `.cd.kr` row), so a
 * phone screen shows three or four posts instead of one and a half.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemCard(item: Item, modifier: Modifier = Modifier, lead: Boolean = false) {
    val actions = LocalItemActions.current
    val saved = item.id in LocalSavedIds.current
    val stars = LocalStars.current
    val time = relative(item.publishedAt)
    val img = item.image
    Card(
        onClick = { actions.openItem(item) },
        modifier = modifier.fillMaxWidth().clip(Radius.card).openBeside("article:" + item.url),
        shape = Radius.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        if (lead) {
            Box(contentAlignment = Alignment.Center) {
                if (img != null) {
                    AsyncImage(
                        model = if (wide()) img.large else img.small,
                        contentDescription = null,
                        contentScale = if (img.fit) ContentScale.Fit else ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                } else {
                    // the front page's lead without a picture (Kuketz-Blog, Phoronix …): the website's motif, not a bare card
                    SourceMotif(item, Modifier.fillMaxWidth().aspectRatio(16f / 9f), large = true)
                }
                if (item.isVideo) PlayMark(56.dp)
            }
        }
        Column(Modifier.padding(start = Space.l, end = Space.l, top = Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            // the meta line spans the card: beside the thumbnail the time was cut (“vor 1 St…”)
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                SourceLine(item, time, stars.count(item))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    Text(
                        item.title,
                        style = if (lead) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (!lead && img != null) {
                        Box(Modifier.padding(top = Space.xxs), contentAlignment = Alignment.Center) {
                            AsyncImage(
                                model = img.small,
                                contentDescription = null,
                                contentScale = if (img.fit) ContentScale.Fit else ContentScale.Crop,
                                modifier = Modifier.width(ThumbWidth).aspectRatio(4f / 3f).clip(Radius.s)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            )
                            if (item.isVideo) PlayMark(36.dp)
                        }
                    }
                }
            }
            if (item.excerpt != null && !largeFont()) {
                Text(
                    item.excerpt!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (lead) 3 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        item.bundle?.takeIf { it.also.isNotEmpty() }?.let { b -> BundleReports(b.also, actions.open) { actions.bundle(b.id) } }
        // chips start on the text edge; the icon buttons' 48 dp targets reach into the card's right padding
        Row(Modifier.fillMaxWidth().padding(start = Space.l, end = Space.xs, bottom = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            // one line beside star, bookmark and menu: what does not fit is left out (video first, then the rubric)
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Space.s), maxLines = 1) {
                item.topic?.let { t -> ChipTag(t.label, t.supernova) { actions.topic(t) } }
                item.categories.firstOrNull()?.let { c -> ChipTag(c.name, false) { actions.rubric(c) } }
                if (item.isVideo) ChipTag(stringResource(R.string.video), false, onClick = null)
            }
            StarButton(item.id in stars.mine, item.id in stars.busy) { actions.toggleStar(item) }
            IconButton(onClick = { actions.toggleSave(item) }) {
                Icon(
                    painterResource(if (saved) R.drawable.ic_bookmark else R.drawable.ic_bookmark_border),
                    stringResource(if (saved) R.string.unsave else R.string.save),
                    tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ItemMenu(item, actions)
        }
    }
}

/** “+ N more sources”, expands to the other reports of the bundle. */
@Composable
private fun BundleReports(also: List<BundleReport>, onOpen: (String) -> Unit, onAll: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = Space.l)) {
        // the website's bundle tag: aurora dot with a soft ring, the count in mono
        val aurora = LocalPlanetColors.current.aurora
        TextButton(onClick = { open = !open }, contentPadding = PaddingValues(horizontal = 0.dp), colors = ButtonDefaults.textButtonColors(contentColor = aurora)) {
            Box(Modifier.padding(end = Space.s).size(14.dp).drawBehind {
                drawCircle(aurora.copy(alpha = 0.22f), radius = 7.dp.toPx())
                drawCircle(aurora, radius = 4.dp.toPx())
            })
            Text(pluralStringResource(R.plurals.bundle_more, also.size, also.size).removePrefix("+ "), style = MetaStyle, fontWeight = FontWeight.Bold)
        }
        if (open) {
            also.forEach { ReportRow(it, onOpen) }
            // the website's bundle page: every report of the story as its own card
            TextButton(onClick = onAll, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text(stringResource(R.string.bundle_all), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ItemMenu(item: Item, actions: ItemActions) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more_actions)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.share)) },
                leadingIcon = { Icon(Icons.Filled.Share, null) },
                onClick = { open = false; shareLink(context, item.title, item.url) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.copy_link)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_link), null) },
                onClick = { open = false; copyLink(context, item.url) },
            )
            // a source that wants the user's browser is never offered “open in the app”
            if (!item.source.openExternal) {
                DropdownMenuItem(
                    text = { Text(stringResource(actions.openOtherLabel)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_open_in_new), null) },
                    onClick = { open = false; actions.openOther(item.url) },
                )
            }
            // videos: straight to the app made for them (YouTube, NewPipe …)
            if (item.isVideo || isVideoSite(item.url)) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.open_in_video_app)) },
                    leadingIcon = { Icon(Icons.Filled.PlayArrow, null) },
                    onClick = { open = false; openInHandlerApp(context, item.url) },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.more_from, item.source.name)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_article), null) },
                onClick = { open = false; actions.source(item.source) },
            )
            item.source.support?.takeIf { UrlPolicy.isWebUrl(it.url) }?.let { support ->
                HorizontalDivider()
                SupportMenuItem(item.source.name, support) { open = false; openOutsideApp(context, support.url) }
            }
        }
    }
}

@Composable
private fun SourceLine(item: Item, time: String, stars: Int = item.stars) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        val logo = item.source.logo
        if (logo != null) {
            AsyncImage(model = logo, contentDescription = null, modifier = Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)))
        } else {
            val color = item.source.color?.let { runCatching { Color(it.toColorInt()) }.getOrNull() }
            Surface(color = color ?: MaterialTheme.colorScheme.primary, shape = CircleShape, modifier = Modifier.padding(vertical = 3.dp).size(10.dp)) {}
        }
        // new since the last visit: a small aurora pill before the source (STRATEGIE.md 4.2, Radar)
        val fresh = net.planetfoss.app.data.SeenRepository.isNew(item.publishedAt.toEpochMilli(), LocalNewSince.current)
        if (fresh) NewMark(Modifier.padding(top = 1.dp))
        // readers' stars on planetfoss.net, as on the website's cards; TalkBack says “3 stars”, not the star sign
        val by = stringResource(R.string.item_by, item.source.name, time)
        val said = if (stars > 0) by + ", " + pluralStringResource(R.plurals.stars_n, stars, stars) else by
        val spoken = if (fresh) stringResource(R.string.new_mark) + ", " + said else said
        // one line, always: a long source name is cut (“Digitale Gesellschaft Sch…”), the time and the stars stay whole
        val name = item.source.name
        val tail = (if (by.startsWith(name)) by.removePrefix(name) else ", $time") + if (stars > 0) " · ★ $stars" else ""
        Row(Modifier.weight(1f).clearAndSetSemantics { contentDescription = spoken }) {
            Text(
                name, modifier = Modifier.weight(1f, fill = false), style = MetaStyle, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(tail, style = MetaStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
        }
    }
}

/** “NEW”: the marker of a post published since the last visit (the website mockup's `.newmark`). */
@Composable
fun NewMark(modifier: Modifier = Modifier) {
    Surface(color = LocalPlanetColors.current.aurora, contentColor = Color.White, shape = CircleShape, modifier = modifier.clearAndSetSemantics { }) {
        Text(stringResource(R.string.new_mark).uppercase(), style = MetaStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 6.dp))
    }
}

/**
 * Star for the Sternstunden, as the website's ☆ on every card: filled in star colour once starred from
 * this phone; while the task is being solved (under two seconds) a small indicator takes its place.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StarButton(starred: Boolean, busy: Boolean, onClick: () -> Unit) {
    val label = stringResource(if (busy) R.string.star_busy else if (starred) R.string.unstar else R.string.star)
    IconButton(onClick = onClick, enabled = !busy, modifier = Modifier.semantics { contentDescription = label }) {
        when {
            busy -> androidx.compose.material3.LoadingIndicator(Modifier.size(24.dp))
            starred -> Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.tertiary)
            else -> Icon(painterResource(R.drawable.ic_star_border), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Topic or rubric as a chip that opens its list; the supernova topic in star colour. */
@Composable
fun ChipTag(label: String, supernova: Boolean, comet: Boolean = false, onClick: (() -> Unit)?) {
    val planet = LocalPlanetColors.current
    val isComet = comet && !supernova
    val novaWord = stringResource(if (isComet) R.string.comet_badge else R.string.supernova_badge)
    val colors = when {
        supernova -> AssistChipDefaults.assistChipColors(containerColor = planet.supernova, labelColor = planet.onSupernova, leadingIconContentColor = planet.onSupernova)
        // a comet in the website's light star colour (`.burst-komet`)
        isComet -> AssistChipDefaults.assistChipColors(containerColor = planet.comet, labelColor = planet.onSupernova)
        else -> AssistChipDefaults.assistChipColors()
    }
    AssistChip(
        onClick = onClick ?: {},
        enabled = true,
        label = { Text(if (isComet) "☄ $label" else label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = if (supernova) ({ Icon(painterResource(R.drawable.ic_supernova), null, Modifier.size(14.dp)) }) else null,
        colors = colors,
        border = if (supernova || isComet) null else AssistChipDefaults.assistChipBorder(enabled = true),
        modifier = when {
            onClick == null -> Modifier.clearAndSetSemantics { contentDescription = label }
            // the star icon alone tells TalkBack users nothing
            supernova || isComet -> Modifier.semantics { stateDescription = novaWord }
            else -> Modifier
        },
    )
}

/**
 * A supernova like on the website (`.snk`): night-blue card with nova and nebula glow and a few
 * stars, status badge in the burst gradient, name, numbers, one lead image, quote and the first
 * reports with round source logos. Reports carry no thumbnails: they mostly repeat the lead image.
 * [compact] (every supernova after the first, like the website's `.snk` under the band): smaller
 * name with a square image beside it, no quote, two reports by title only.
 */
@Composable
fun NovaCard(
    nova: Supernova, onOpen: (Item) -> Unit, onAll: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false,
    /** A short line under the numbers, e.g. “so far only English sources” when none reports in the content language. */
    note: String? = null,
) {
    if (compact) return NovaCompact(nova, onOpen, onAll, modifier, note)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = Radius.card,
        colors = CardDefaults.cardColors(containerColor = NovaSpace, contentColor = NovaInk),
    ) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().drawBehind { novaSky() }) {
            // wide (tablets, the top of Latest over two columns): the picture beside the reports, so the first
            // reports are in view without scrolling (docs/11-tablet-mode.md)
            val side = maxWidth >= net.planetfoss.app.ui.theme.SideBySideFrom
            Column(
                Modifier.fillMaxWidth().padding(start = Space.l, end = Space.l, top = Space.l, bottom = Space.xs),
                verticalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NovaBurst(nova.burning)
                    Text(relative(nova.ignitedAt), style = NovaMono, color = NovaInk3)
                }
                if (side) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.xl)) {
                        NovaLead(nova.reports, Modifier.weight(5f).aspectRatio(16f / 9f).padding(top = Space.xs))
                        Column(Modifier.weight(7f), verticalArrangement = Arrangement.spacedBy(Space.m)) { NovaBody(nova, note, onOpen, onAll) }
                    }
                } else {
                    NovaBody(nova, note, onOpen, onAll, lead = { NovaLead(nova.reports, Modifier.fillMaxWidth().aspectRatio(16f / 9f)) })
                }
            }
        }
    }
}

/** Name, numbers, quote and reports of the full supernova card; [lead] (the picture) goes between numbers and quote. */
@Composable
private fun NovaBody(nova: Supernova, note: String?, onOpen: (Item) -> Unit, onAll: () -> Unit, lead: @Composable () -> Unit = {}) {
    val planet = LocalPlanetColors.current
    Text(
        nova.label,
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
        modifier = Modifier.semantics { heading() },
    )
    nova.summary?.let { Text(it, style = NovaMono, color = NovaInk2) }
    note?.let { Text(it, style = NovaMono, color = NovaInk3) }
    lead()
    // the quote is a clipped excerpt of one report; once reports show their excerpts it would only repeat it
    val shown = !largeFont()
    nova.quote?.takeUnless { q -> shown && nova.reports.any { it.excerpt?.startsWith(q.text.take(40)) == true } }?.let { q ->
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(planet.supernova))
            Column(Modifier.padding(start = Space.m), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(stringResource(R.string.quote, q.text), style = MaterialTheme.typography.bodyLarge, color = NovaInk2, maxLines = 4, overflow = TextOverflow.Ellipsis)
                Text(q.source, style = NovaMono, fontWeight = FontWeight.Bold, color = planet.supernova)
            }
        }
    }
    Column {
        nova.reports.forEachIndexed { i, r ->
            if (i > 0) HorizontalDivider(color = NovaLine)
            NovaReport(r, onOpen)
        }
        NovaAll(stringResource(R.string.supernova_all_reports), onAll)
    }
}

/**
 * The lighter card for the supernovas after the first, so the list does not stack equal heavy
 * blocks: night-blue while the supernova burns, a light card with a stripe in the burst gradient
 * in the afterglow.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NovaCompact(nova: Supernova, onOpen: (Item) -> Unit, onAll: () -> Unit, modifier: Modifier, note: String? = null) {
    val night = nova.burning
    val tone = if (night) NovaTone.Night else with(MaterialTheme.colorScheme) {
        NovaTone(ink = onSurface, ink2 = onSurfaceVariant, ink3 = onSurfaceVariant, line = outlineVariant, accent = tertiary)
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = Radius.card,
        colors = CardDefaults.cardColors(
            containerColor = if (night) NovaSpace else MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = tone.ink,
        ),
    ) {
        if (!night) Box(Modifier.fillMaxWidth().height(4.dp).background(Brush.horizontalGradient(listOf(Color(0xFFFFD9B8), Color(0xFFFFB27A), NovaOrange))))
        CompositionLocalProvider(LocalNovaTone provides tone) {
            Column(
                Modifier.fillMaxWidth().then(if (night) Modifier.drawBehind { novaSky() } else Modifier)
                    .padding(start = Space.l, end = Space.l, top = Space.l, bottom = Space.xs),
                verticalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                        // beside the square image the time moves under the badge rather than being cut (“vor 17 Stun…”)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            NovaBurst(nova.burning)
                            Text(relative(nova.ignitedAt), style = NovaMono, color = tone.ink3, modifier = Modifier.align(Alignment.CenterVertically))
                        }
                        Text(
                            nova.label,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp,
                            modifier = Modifier.semantics { heading() },
                        )
                        nova.summary?.let { Text(it, style = NovaMono, color = tone.ink2) }
                        note?.let { Text(it, style = NovaMono, color = tone.ink3) }
                    }
                    NovaLead(nova.reports, Modifier.padding(top = 4.dp).size(84.dp))
                }
                Column {
                    nova.reports.take(2).forEach { r ->
                        HorizontalDivider(color = tone.line)
                        NovaReport(r, onOpen, brief = true)
                    }
                    NovaAll(pluralStringResource(R.plurals.supernova_all_n, nova.stats.items, nova.stats.items), onAll)
                }
            }
        }
    }
}

@Composable
private fun NovaAll(label: String, onAll: () -> Unit) = Text(
    label,
    style = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.Bold,
    color = LocalNovaTone.current.accent,
    modifier = Modifier
        .heightIn(min = 48.dp)
        .clip(RoundedCornerShape(8.dp))
        .clickable(role = Role.Button, onClick = onAll)
        .wrapContentHeight(Alignment.CenterVertically),
)

/**
 * A comet (rising topic) between the supernovas: the lightest card – outlined, no image, a
 * comet tail in the website's comet gradient on the left, numbers of the last 24 hours and
 * two reports by title.
 */
@Composable
fun CometCard(comet: Comet, onOpen: (Item) -> Unit, onTopic: () -> Unit, modifier: Modifier = Modifier) {
    val planet = LocalPlanetColors.current
    val tone = with(MaterialTheme.colorScheme) { NovaTone(ink = onSurface, ink2 = onSurfaceVariant, ink3 = onSurfaceVariant, line = outlineVariant, accent = tertiary) }
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = Radius.card,
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = tone.ink),
    ) {
        CompositionLocalProvider(LocalNovaTone provides tone) {
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(Modifier.width(4.dp).fillMaxHeight().background(Brush.verticalGradient(listOf(planet.supernova, Color(0xFFFFE9A8), Color.Transparent))))
                Column(
                    Modifier.weight(1f).padding(start = Space.m, end = Space.l, top = Space.l, bottom = Space.xs),
                    verticalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CometBadge()
                        Text(
                            stringResource(R.string.comet_first_report, relative(comet.firstAt)),
                            style = NovaMono,
                            color = tone.ink3,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(comet.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                    Text(
                        pluralStringResource(R.plurals.comet_posts_24h, comet.stats.items, comet.stats.items) + " · " +
                            pluralStringResource(R.plurals.n_sources, comet.stats.sources, comet.stats.sources),
                        style = NovaMono,
                        color = tone.ink2,
                    )
                    Column {
                        comet.reports.take(2).forEach { r ->
                            HorizontalDivider(color = tone.line)
                            NovaReport(r, onOpen, brief = true)
                        }
                        NovaAll(stringResource(R.string.comet_topic), onTopic)
                    }
                }
            }
        }
    }
}

/** Text colours inside a supernova card: on night blue (website colours) or on the app's light surface. */
@Immutable
private data class NovaTone(val ink: Color, val ink2: Color, val ink3: Color, val line: Color, val accent: Color) {
    companion object {
        val Night = NovaTone(NovaInk, NovaInk2, NovaInk3, NovaLine, Color(0xFFFCC954))
    }
}

private val LocalNovaTone = staticCompositionLocalOf { NovaTone.Night }

// The website's supernova colours (site.css: .snk, .burst, .nv-rep); the card is night-blue in light and dark theme
internal val NovaSpace = Color(0xFF141A44)
internal val NovaInk = Color(0xFFF5F6FB)
internal val NovaInk2 = Color(0xFFC9CEE3)
internal val NovaInk3 = Color(0xFF9EA6C4)
internal val NovaLine = Color(0x1CFFFFFF)
private val NovaOrange = Color(0xFFFF8A4A)
private val NovaViolet = Color(0xFF6E5BEB)
private val NovaPhoto = Color(0xFF232A5A)
private val NovaMono = MetaStyle

/** Nova glow top left, nebula top right, and three stars, as in `.snk` and `.snk::before`. */
internal fun DrawScope.novaSky() {
    drawRect(Brush.radialGradient(listOf(NovaOrange.copy(alpha = 0.25f), Color.Transparent), Offset.Zero, size.width * 0.9f))
    drawRect(Brush.radialGradient(listOf(NovaViolet.copy(alpha = 0.2f), Color.Transparent), Offset(size.width, 0f), size.width))
    listOf(Triple(0.18f, 0.06f, 1f), Triple(0.72f, 0.03f, 1f), Triple(0.88f, 0.09f, 1.5f)).forEach { (x, y, r) ->
        drawCircle(Color.White.copy(alpha = 0.45f), radius = r * density, center = Offset(size.width * x, size.width * y))
    }
}

/** “☄ COMET” in the website's comet gradient; TalkBack reads the word. */
@Composable
internal fun CometBadge() {
    val planet = LocalPlanetColors.current
    val cometWord = stringResource(R.string.comet_badge)
    Text(
        "☄ " + cometWord.uppercase(),
        style = NovaMono.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp),
        color = planet.onSupernova,
        modifier = Modifier.clip(CircleShape).background(Brush.horizontalGradient(listOf(Color(0xFFFFE9A8), planet.supernova)))
            .padding(horizontal = 10.dp, vertical = 5.dp).clearAndSetSemantics { contentDescription = cometWord },
    )
}

/** Status badge in the website's burst gradient: “✦ SUPERNOVA” while burning, “✧ AFTERGLOW” after. */
@Composable
internal fun NovaBurst(burning: Boolean) {
    val planet = LocalPlanetColors.current
    val colors = if (burning) listOf(Color(0xFFFFE08A), planet.supernova, NovaOrange) else listOf(Color(0xFFFFD9B8), Color(0xFFFFB27A), NovaOrange)
    val word = stringResource(if (burning) R.string.supernova_badge else R.string.supernova_afterglow)
    Text(
        (if (burning) "✦ " else "✧ ") + word.uppercase(),
        style = NovaMono.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp),
        color = planet.onSupernova,
        // TalkBack reads the word, not the star sign and spelled-out capitals
        modifier = Modifier.clip(CircleShape).background(Brush.horizontalGradient(colors)).padding(horizontal = 10.dp, vertical = 5.dp)
            .clearAndSetSemantics { contentDescription = word },
    )
}

/** One image for the whole supernova (wide in the full card, square in the compact one): the first report photo (logos and screenshots only if there is no photo). */
@Composable
private fun NovaLead(reports: List<Item>, modifier: Modifier) {
    val img = reports.mapNotNull { it.image }.let { all -> all.firstOrNull { !it.fit } ?: all.firstOrNull() } ?: return
    AsyncImage(
        model = if (wide()) img.large else img.small,
        contentDescription = null,
        contentScale = if (img.fit) ContentScale.Fit else ContentScale.Crop,
        modifier = modifier.clip(Radius.m).background(NovaPhoto),
    )
}

@Composable
private fun ReportRow(report: BundleReport, onOpen: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button) { onOpen(report.url) }
            .padding(vertical = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = "${report.title}, ${report.source}" },
    ) {
        Text(report.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(report.source + " · " + report.lang.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A report of a supernova as on the website (`.nv-rep`): round logo, source in star colour, title, teaser ([brief]: title only). */
@Composable
private fun NovaReport(report: Item, onOpen: (Item) -> Unit, brief: Boolean = false) {
    val planet = LocalPlanetColors.current
    val tone = LocalNovaTone.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button) { onOpen(report) }
            .padding(vertical = 10.dp)
            .semantics(mergeDescendants = true) { contentDescription = "${report.title}, ${report.source.name}" },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val logo = report.source.logo
        val dot = Modifier.padding(top = 2.dp).size(28.dp).clip(CircleShape)
        if (logo != null) {
            LogoDot(logo, dot.border(1.dp, tone.line, CircleShape))
        } else {
            val color = report.source.color?.let { runCatching { Color(it.toColorInt()) }.getOrNull() }
            Box(dot.background(color ?: planet.nebula))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = tone.accent)) { append(report.source.name) }
                    append("  " + report.lang.uppercase() + " · " + relative(report.publishedAt))
                },
                style = NovaMono.copy(fontSize = 11.sp),
                color = tone.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(report.title, style = MaterialTheme.typography.titleSmall, maxLines = if (brief) 2 else 3, overflow = TextOverflow.Ellipsis)
            if (report.excerpt != null && !brief && !largeFont()) {
                Text(report.excerpt!!, style = MaterialTheme.typography.bodySmall, color = tone.ink3, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * The Sternstunden in Latest, like the website's block on its start page: what readers starred on
 * planetfoss.net in the last 7 days, ranked – rank badge on the image, star count, title, source –
 * in a row that scrolls sideways, and the way to the full list.
 */
@Composable
fun StarredBlock(items: List<Item>, onAll: () -> Unit, modifier: Modifier = Modifier) {
    val actions = LocalItemActions.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = Radius.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(vertical = Space.l), verticalArrangement = Arrangement.spacedBy(Space.m)) {
            SectionHead("★ " + stringResource(R.string.starred), Modifier.padding(start = Space.l, end = Space.xs), stringResource(R.string.starred_hint), onAll)
            LazyRow(contentPadding = PaddingValues(horizontal = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                itemsIndexed(items, key = { _, it -> it.id }) { i, item -> StarredEntry(i + 1, item) { actions.openFrom(item, "liste") } }
            }
        }
    }
}

/**
 * Stand-in for a post without an image, as on the website (`pf_motif`, `.img.motif`): a glow in the source's
 * colour fading into the night sky, a few stars, the source's logo, its name and the first rubric.
 * [compact] for small thumbnails: logo only (website `.wk-thumb`); [large] for the lead (website `.hero`).
 */
@Composable
internal fun SourceMotif(item: Item, modifier: Modifier = Modifier, compact: Boolean = false, large: Boolean = false) {
    val night = LocalPlanetColors.current.night
    val c = item.source.color?.let { runCatching { Color(it.toColorInt()) }.getOrNull() } ?: MaterialTheme.colorScheme.primary
    val light = Color.White.copy(alpha = 0.15f).compositeOver(c)
    Box(
        modifier.drawBehind {
            val r = size.maxDimension * 1.1f
            drawRect(Brush.radialGradient(0f to light, 0.35f to c, 1f to night, center = Offset(size.width * 0.85f, size.height * 0.1f), radius = r))
            for ((x, y, a) in listOf(Triple(.18f, .24f, .6f), Triple(.72f, .64f, .55f), Triple(.46f, .82f, .4f), Triple(.88f, .30f, .45f))) {
                drawCircle(Color.White.copy(alpha = a), radius = 1.dp.toPx(), center = Offset(size.width * x, size.height * y))
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.xs), modifier = Modifier.padding(horizontal = Space.m)) {
            item.source.logo?.let {
                val sz = if (large) 64.dp else if (compact) 24.dp else 32.dp
                AsyncImage(model = it, contentDescription = null, modifier = Modifier.size(sz).shadow(4.dp, Radius.s).clip(Radius.s).background(Color.White))
            }
            if (compact) return@Column
            Text(item.source.name, style = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            item.categories.firstOrNull()?.let {
                Text(it.name.uppercase(), style = MetaStyle.copy(fontSize = 10.sp, letterSpacing = 1.sp), fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** One Sternstunde as on the website (`.ss`, since 23.10.): rank as a small star-coloured badge on the image. */
@Composable
private fun StarredEntry(rank: Int, item: Item, onOpen: () -> Unit) {
    val planet = LocalPlanetColors.current
    val spoken = "$rank. ${item.title}, ${item.source.name}, " + pluralStringResource(R.plurals.stars_n, item.stars, item.stars)
    Column(
        Modifier
            .width(StarredWidth)
            .clip(Radius.m)
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(Radius.m).background(MaterialTheme.colorScheme.secondaryContainer)) {
            val img = item.image
            if (img != null) {
                AsyncImage(
                    model = img.small,
                    contentDescription = null,
                    contentScale = if (img.fit) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f),
                )
            } else {
                SourceMotif(item, Modifier.fillMaxWidth().aspectRatio(16f / 10f))
            }
            Text(
                "$rank",
                style = MetaStyle.copy(fontSize = 13.sp, lineHeight = 13.sp),
                fontWeight = FontWeight.Bold,
                color = planet.onSupernova,
                modifier = Modifier.padding(Space.s).shadow(2.dp, CircleShape).clip(CircleShape).background(planet.supernova)
                    .heightIn(min = 26.dp).widthIn(min = 26.dp).padding(horizontal = 7.dp).wrapContentHeight(Alignment.CenterVertically),
                textAlign = TextAlign.Center,
            )
        }
        Text("★ ${item.stars}", style = MetaStyle, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
        Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(
            item.source.name + " · " + item.lang.uppercase(),
            style = MetaStyle.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A module of the front page as a row that scrolls sideways (website: rubric modules, “From the projects”, video rail):
 * title, optional way to the full list, posts with image, title and source.
 */
@Composable
fun RailBlock(title: String, items: List<Item>, modifier: Modifier = Modifier, onAll: (() -> Unit)? = null, tint: Color? = null) {
    val actions = LocalItemActions.current
    val surface = MaterialTheme.colorScheme.surfaceContainerLow
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = Radius.card,
        colors = CardDefaults.cardColors(containerColor = tint?.copy(alpha = 0.09f)?.compositeOver(surface) ?: surface),
        border = tint?.let { BorderStroke(1.dp, it.copy(alpha = 0.22f)) },
    ) {
        // a rubric's module: 4 dp line in its colour on top, as on the website
        if (tint != null) Box(Modifier.fillMaxWidth().height(4.dp).background(tint))
        Column(Modifier.padding(vertical = Space.l), verticalArrangement = Arrangement.spacedBy(Space.m)) {
            SectionHead(title, Modifier.padding(start = Space.l, end = Space.xs), onAll = onAll)
            LazyRow(contentPadding = PaddingValues(horizontal = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                items(items.size, key = { items[it].id }) { i ->
                    val item = items[i]
                    Column(
                        Modifier.width(StarredWidth).clip(Radius.m).clickable(role = Role.Button) { actions.openItem(item) }
                            .semantics(mergeDescendants = true) { contentDescription = "${item.title}, ${item.source.name}" },
                        verticalArrangement = Arrangement.spacedBy(Space.xs),
                    ) {
                        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(Radius.m).background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center) {
                            val img = item.image
                            if (img != null) {
                                AsyncImage(model = img.small, contentDescription = null, contentScale = if (img.fit) ContentScale.Fit else ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f))
                            } else {
                                SourceMotif(item, Modifier.fillMaxWidth().aspectRatio(16f / 10f))
                            }
                            if (item.isVideo) PlayMark(40.dp)
                        }
                        Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(item.source.name, style = MetaStyle.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/**
 * A section's head as on the website's start page: big Space Grotesk title, an optional hint in mono beside it,
 * and “All →” to the full list.
 */
@Composable
fun SectionHead(title: String, modifier: Modifier = Modifier, hint: String? = null, onAll: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            hint?.let { Text(it, style = MetaStyle, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        onAll?.let { TextButton(onClick = it) { Text(stringResource(R.string.all_link) + " →") } }
    }
}

/** One Sternstunde in the row: about 60 % of a phone's width, as on the website's mobile layout. */
private val StarredWidth = 220.dp

/**
 * Head of a source's list when it has a support link, like the website's box (variant A,
 * prototypes/unterstuetzen): heart, “Support <source>”, why it matters, and the button to its page
 * in the user's browser – never the app's own. When the source recommends another project instead
 * (prototypes/weiterempfehlen) the box turns aurora (teal, as on the website) with a hand-and-heart: “Recommended by <source>”,
 * “Support <project> instead”, and the button names the project.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SupportBox(name: String, support: Support, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val project = support.recommended
    val scheme = MaterialTheme.colorScheme
    val planet = LocalPlanetColors.current
    val (container, onContainer) = if (project != null) planet.auroraContainer to planet.onAuroraContainer else scheme.primaryContainer to scheme.onPrimaryContainer
    val (accent, onAccent) = if (project != null) planet.aurora to planet.onAurora else scheme.primary to scheme.onPrimary
    Surface(color = container, shape = Radius.card, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(Space.l), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            Surface(color = accent, shape = CircleShape, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    if (project != null) {
                        Icon(painterResource(R.drawable.ic_volunteer), null, tint = onAccent, modifier = Modifier.size(20.dp))
                    } else {
                        Icon(Icons.Filled.Favorite, null, tint = onAccent, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                if (project != null) {
                    Text(stringResource(R.string.support_fw_by, name), style = MaterialTheme.typography.labelMedium, color = accent)
                }
                Text(if (project != null) stringResource(R.string.support_fw_title, project) else stringResource(R.string.support_named, name),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = onContainer)
                Text(if (project != null) stringResource(R.string.support_fw_text, name, project) else stringResource(R.string.support_box_text),
                    style = MaterialTheme.typography.bodyMedium, color = onContainer)
                Button(
                    onClick = { openOutsideApp(context, support.url) },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = onAccent),
                    modifier = Modifier.padding(top = Space.s),
                ) { Text(if (project != null) stringResource(R.string.support_fw_button, project) else stringResource(R.string.support_button)) }
            }
        }
    }
}

/**
 * Last entry of a card's or the browser's menu, set apart by a divider before it: “Support <source>” with a heart,
 * or – when the source recommends another project – “Support <project>” over “Recommended by <source>”.
 */
@Composable
fun SupportMenuItem(sourceName: String, support: Support, onClick: () -> Unit) {
    val project = support.recommended
    DropdownMenuItem(
        text = {
            if (project != null) {
                Column {
                    Text(stringResource(R.string.support_named, project))
                    Text(stringResource(R.string.support_fw_by, sourceName), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text(stringResource(R.string.support_named, sourceName))
            }
        },
        leadingIcon = {
            if (project != null) Icon(painterResource(R.drawable.ic_volunteer), null, tint = LocalPlanetColors.current.aurora) else Icon(Icons.Filled.Favorite, null)
        },
        onClick = onClick,
    )
}

/**
 * PlanetFOSS's own sponsor card, like the website's K6 card: labelled, the sponsor's colour as a line on top, logo and name,
 * image, title, text, call to action. Two formats: `standard` in Latest (image, text) and `compact` in lists (logo, title, two
 * lines, button below the text). The whole card opens [Ad.clickUrl] in the user's browser (planetfoss.net counts the click and forwards); the
 * (i) next to the label says who pays and what does not happen. [onSeen] once per card when at least half of it was on screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdCard(ad: Ad, onSeen: (Ad) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val color = ad.color?.let { runCatching { Color(it.toColorInt()) }.getOrNull() } ?: MaterialTheme.colorScheme.primary
    var seen by rememberSaveable(ad.id) { mutableStateOf(false) }
    var info by rememberSaveable(ad.id) { mutableStateOf(false) }
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toFloat() }
    val compact = ad.format == "compact"
    Card(
        onClick = { openOutsideApp(context, ad.clickUrl) },
        modifier = modifier.fillMaxWidth().onGloballyPositioned { c ->
            if (seen || c.size.height == 0) return@onGloballyPositioned
            val b = c.boundsInWindow()
            val visible = (minOf(b.bottom, windowHeight) - maxOf(b.top, 0f)).coerceAtLeast(0f)
            if (visible >= c.size.height / 2f) {
                seen = true
                onSeen(ad)
            }
        }.semantics(mergeDescendants = true) { contentDescription = "${ad.label}: ${ad.title}" },
        shape = Radius.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Box(Modifier.fillMaxWidth().height(4.dp).background(color))
        val label: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                Text(
                    ad.label.uppercase(),
                    style = MetaStyle.copy(fontSize = 10.sp, letterSpacing = 0.8.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                if (!compact) {
                    ad.logo?.let { AsyncImage(model = it, contentDescription = null, modifier = Modifier.size(18.dp).clip(RoundedCornerShape(4.dp))) }
                    if (ad.by.isNotEmpty()) {
                        Text(ad.by, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    }
                }
                IconButton(onClick = { info = true }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Outlined.Info, stringResource(R.string.ad_info_cd), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (compact) {
            // the button sits below the text, not beside it: beside it the title was squeezed into a sliver and broke badly
            // ("Linux-Notebooks" / ", vorinstalliert", seen on the Pixel 10, 19.11.2026)
            Row(Modifier.padding(Space.l), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                Box(Modifier.size(44.dp).clip(Radius.m).background(color), contentAlignment = Alignment.Center) {
                    if (ad.logo != null) AsyncImage(model = ad.logo, contentDescription = null, modifier = Modifier.fillMaxSize())
                    else Text(ad.by.ifEmpty { ad.title }.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    label()
                    Text(ad.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    ad.body?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    ad.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    ad.cta?.let {
                        Text(
                            it, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = Space.s).border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        } else {
            Column(Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                label()
                ad.image?.let { img ->
                    AsyncImage(
                        model = if (wide()) img.large else img.small,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(Radius.m).background(color),
                    )
                }
                Text(ad.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                ad.body?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                ad.cta?.let { Text("$it →", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                ad.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
    if (info) AdInfoSheet(ad, onDismiss = { info = false })
}

/** “Why am I seeing this ad?”: who pays, what does not happen, and where to read more or report it. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AdInfoSheet(ad: Ad, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = Space.xl).padding(bottom = Space.xl), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Text(stringResource(R.string.ad_info_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(if (ad.by.isNotEmpty()) stringResource(R.string.ad_info_paid, ad.by) else stringResource(R.string.ad_info_paid_anon), style = MaterialTheme.typography.bodyMedium)
            listOf(R.string.ad_info_1, R.string.ad_info_2, R.string.ad_info_3).forEach {
                Text("• " + stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // whole buttons wrap to the next line; before, "Anzeige melden" broke inside its button
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                OutlinedButton(onClick = { openOutsideApp(context, "https://planetfoss.net/datenschutz/") }) { Text(stringResource(R.string.privacy_policy), maxLines = 1) }
                OutlinedButton(onClick = { openOutsideApp(context, "https://planetfoss.net/impressum/") }) { Text(stringResource(R.string.ad_info_report), maxLines = 1) }
            }
        }
    }
}

/**
 * A source logo in a round dot. Logos with a transparent or white margin sit on white with a little air
 * (as before); logos that fill their square edge to edge (Linux und Ich's dark tile) fill the whole dot,
 * so no dark square shows inside a white ring.
 */
@Composable
internal fun LogoDot(logo: String, modifier: Modifier = Modifier) {
    var tile by remember(logo) { mutableStateOf(false) }
    AsyncImage(
        model = logo,
        contentDescription = null,
        contentScale = if (tile) ContentScale.Crop else ContentScale.Fit,
        onSuccess = { tile = fillsSquare(it.result.image.toBitmap()) },
        modifier = if (tile) modifier else modifier.background(Color.White).padding(3.dp),
    )
}

/** True when every pixel along the edge is opaque and not near-white: the logo is a full tile, not a mark on a margin. */
internal fun fillsSquare(source: Bitmap): Boolean {
    val bmp = if (source.config == Bitmap.Config.HARDWARE) source.copy(Bitmap.Config.ARGB_8888, false) ?: return false else source
    val w = bmp.width
    val h = bmp.height
    if (w < 4 || h < 4) return false
    val step = maxOf(1, minOf(w, h) / 16)
    var light = 0
    var total = 0
    fun check(x: Int, y: Int): Boolean {
        val p = bmp.getPixel(x, y)
        if (android.graphics.Color.alpha(p) < 240) return false
        if (android.graphics.Color.red(p) > 235 && android.graphics.Color.green(p) > 235 && android.graphics.Color.blue(p) > 235) light++
        total++
        return true
    }
    for (x in 0 until w step step) if (!check(x, 0) || !check(x, h - 1)) return false
    for (y in 0 until h step step) if (!check(0, y) || !check(w - 1, y)) return false
    // a white edge already blends with the white dot: keep the air around it
    return light * 2 < total
}
