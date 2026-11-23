// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.planetfoss.app.BuildConfig
import net.planetfoss.app.R
import net.planetfoss.app.ui.components.openOutsideApp
import net.planetfoss.app.ui.setup.MoonMuted
import net.planetfoss.app.ui.setup.MoonText
import net.planetfoss.app.ui.setup.NightBand
import net.planetfoss.app.ui.setup.StarYellow
import net.planetfoss.app.ui.setup.SystemBarIcons
import net.planetfoss.app.ui.setup.nightSky
import net.planetfoss.app.ui.theme.ContentMaxWidth
import net.planetfoss.app.ui.theme.LocalPlanetColors
import net.planetfoss.app.ui.theme.MetaStyle
import net.planetfoss.app.ui.theme.Radius
import net.planetfoss.app.ui.theme.Space
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** What a line of the release notes is: something new, something that got better, or a fault that is gone. */
enum class NoteKind(@StringRes val label: Int) { NEW(R.string.whatsnew_new), BETTER(R.string.whatsnew_better), FIXED(R.string.whatsnew_fixed) }

/** One release as readers see it: a headline in a sentence and at most five short lines. */
data class ReleaseNotes(val version: String, val date: LocalDate, @StringRes val title: Int, val notes: List<Pair<NoteKind, Int>>)

/**
 * The notes of the releases, newest first – written for readers, not copied from CHANGELOG.md. A release adds its entry
 * on top (WhatsNewTest fails when the version being built has none); the page shows the newest [SHOWN].
 */
val RELEASE_NOTES = listOf(
    ReleaseNotes(
        "0.6.6", LocalDate.of(2026, 11, 26), R.string.whatsnew_066_title,
        listOf(
            NoteKind.NEW to R.string.whatsnew_066_5, NoteKind.NEW to R.string.whatsnew_066_6,
            NoteKind.NEW to R.string.whatsnew_066_1, NoteKind.BETTER to R.string.whatsnew_066_2,
            NoteKind.BETTER to R.string.whatsnew_066_3, NoteKind.NEW to R.string.whatsnew_066_4,
        ),
    ),
    ReleaseNotes(
        "0.6.5", LocalDate.of(2026, 11, 20), R.string.whatsnew_065_title,
        listOf(NoteKind.BETTER to R.string.whatsnew_065_1, NoteKind.NEW to R.string.whatsnew_065_2),
    ),
    ReleaseNotes(
        "0.6.4", LocalDate.of(2026, 11, 19), R.string.whatsnew_064_title,
        listOf(
            NoteKind.NEW to R.string.whatsnew_064_1, NoteKind.NEW to R.string.whatsnew_064_2, NoteKind.NEW to R.string.whatsnew_064_3,
            NoteKind.BETTER to R.string.whatsnew_064_4, NoteKind.FIXED to R.string.whatsnew_064_5,
        ),
    ),
    ReleaseNotes("0.6.3", LocalDate.of(2026, 11, 17), R.string.whatsnew_063_title, listOf(NoteKind.NEW to R.string.whatsnew_063_1, NoteKind.FIXED to R.string.whatsnew_063_2)),
    ReleaseNotes("0.6.2", LocalDate.of(2026, 11, 16), R.string.whatsnew_062_title, listOf(NoteKind.BETTER to R.string.whatsnew_062_1)),
    ReleaseNotes("0.6.1", LocalDate.of(2026, 11, 16), R.string.whatsnew_061_title, listOf(NoteKind.NEW to R.string.whatsnew_061_1, NoteKind.FIXED to R.string.whatsnew_061_2)),
    ReleaseNotes(
        "0.6.0", LocalDate.of(2026, 11, 10), R.string.whatsnew_060_title,
        listOf(
            NoteKind.NEW to R.string.whatsnew_060_1, NoteKind.NEW to R.string.whatsnew_060_2, NoteKind.NEW to R.string.whatsnew_060_3,
            NoteKind.BETTER to R.string.whatsnew_060_4,
        ),
    ),
)

private const val SHOWN = 5
private const val CHANGELOG_URL = "https://github.com/linuxundich/planetfoss-android/blob/main/CHANGELOG.md"

/** The version without the suffix of development builds ("0.6.4-dev+12.gabc" is 0.6.4). */
fun baseVersion(name: String): String = name.substringBefore('-')

/**
 * "What is new" (mockup docs/whats-new-mockup.html, 2026-11-19): the last five releases as a timeline under a night sky
 * head, newest first. The installed version is the card in the night sky with the golden star.
 */
@Composable
fun WhatsNewScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val installed = baseVersion(BuildConfig.VERSION_NAME)
    val releases = RELEASE_NOTES.take(SHOWN)
    // the head is night in the light design too: light status bar icons, as in the setup assistant
    SystemBarIcons(navigationLight = false)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NightBand(stringResource(R.string.settings_version, installed), stringResource(R.string.whatsnew_title), painterResource(R.drawable.ic_supernova), onBack)
        Box(Modifier.weight(1f).fillMaxWidth().navigationBarsPadding(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                contentPadding = PaddingValues(start = Space.l, end = Space.l, top = Space.l, bottom = Space.xl),
                modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxSize(),
            ) {
                itemsIndexed(releases, key = { _, r -> r.version }) { i, r -> Release(r, current = r.version == installed, last = i == releases.lastIndex) }
                item {
                    TextButton(onClick = { openOutsideApp(context, CHANGELOG_URL) }, modifier = Modifier.padding(start = 20.dp)) {
                        Text(stringResource(R.string.whatsnew_older))
                    }
                }
            }
        }
    }
}

/** One release on the timeline: its marker and the line to the next one on the left, head and card on the right. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Release(r: ReleaseNotes, current: Boolean, last: Boolean) {
    val line = MaterialTheme.colorScheme.outlineVariant
    val date = remember(r.date) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(r.date) }
    Row(
        Modifier.fillMaxWidth().drawBehind {
            // the line runs from below the marker to the next release
            if (!last) drawLine(line, Offset(9.dp.toPx(), 24.dp.toPx()), Offset(9.dp.toPx(), size.height), strokeWidth = 2.dp.toPx())
        },
    ) {
        Box(Modifier.width(28.dp).padding(top = 2.dp), contentAlignment = Alignment.TopStart) {
            if (current) Icon(painterResource(R.drawable.ic_supernova), null, Modifier.size(18.dp), tint = StarYellow)
            else Box(Modifier.padding(start = 4.dp, top = 4.dp).size(10.dp).clip(CircleShape).background(line))
        }
        Column(Modifier.weight(1f).padding(bottom = Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), itemVerticalAlignment = Alignment.CenterVertically) {
                Text(r.version, style = MetaStyle, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(date, style = MetaStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (current) {
                    Text(
                        stringResource(R.string.whatsnew_installed), style = MaterialTheme.typography.labelSmall, color = Color(0xFF2A2100),
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(StarYellow).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Card(
                shape = Radius.card, modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = if (current) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLowest),
                border = if (current) null else BorderStroke(1.dp, line),
            ) {
                Column(
                    (if (current) Modifier.nightSky() else Modifier).fillMaxWidth().padding(Space.l),
                    verticalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    Text(
                        stringResource(r.title), style = MaterialTheme.typography.titleMedium,
                        color = if (current) MoonText else MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { heading() },
                    )
                    r.notes.forEach { (kind, text) -> Note(kind, stringResource(text), current) }
                }
            }
        }
    }
}

/** A line of the notes: its kind as a small pill in the flow of the text, so the text keeps the card's whole width. */
@Composable
private fun Note(kind: NoteKind, text: String, onNight: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val label = stringResource(kind.label).uppercase()
    val pill = MetaStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
    val (bg, fg) = when (kind) {
        NoteKind.NEW -> LocalPlanetColors.current.aurora to Color.White
        NoteKind.BETTER -> if (onNight) Color(0xFF3A2A99) to Color(0xFFE6E0FF) else scheme.secondaryContainer to scheme.onSecondaryContainer
        NoteKind.FIXED -> if (onNight) Color.White.copy(alpha = .12f) to MoonMuted else scheme.surfaceContainerHighest to scheme.onSurfaceVariant
    }
    val measurer = rememberTextMeasurer()
    val width = with(LocalDensity.current) { (measurer.measure(label, pill).size.width.toDp() + 12.dp + 6.dp).toSp() }
    val inline = mapOf(
        "kind" to InlineTextContent(Placeholder(width, 16.sp, PlaceholderVerticalAlign.TextCenter)) {
            Box(Modifier.padding(end = 6.dp).fillMaxSize().clip(RoundedCornerShape(6.dp)).background(bg), contentAlignment = Alignment.Center) {
                Text(label, style = pill, color = fg, maxLines = 1)
            }
        },
    )
    Text(
        buildAnnotatedString { appendInlineContent("kind", label + ":"); append(text) },
        inlineContent = inline, style = MaterialTheme.typography.bodyMedium, color = if (onNight) MoonText else scheme.onSurface,
    )
}
