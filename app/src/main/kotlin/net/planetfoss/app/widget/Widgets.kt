// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.GlanceId
import androidx.compose.ui.unit.DpSize
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.appwidget.SizeMode
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import net.planetfoss.app.MainActivity
import net.planetfoss.app.R
import net.planetfoss.app.data.FeedRepository
import net.planetfoss.app.data.LogbookWidgetData
import net.planetfoss.app.data.RadarCounter
import net.planetfoss.app.data.RadarWidgetData
import net.planetfoss.app.data.WidgetStore
import net.planetfoss.app.data.prefs.SettingsRepository
import net.planetfoss.core.api.Use

/** What the widgets need from the app (Glance runs outside Hilt's reach). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun widgets(): WidgetStore
    fun feed(): FeedRepository
    fun settings(): SettingsRepository
    fun radar(): RadarCounter
}

private fun entry(context: Context) = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)

// the website's night sky, in light and dark mode alike (as the logbook head in the app)
private val Night = Color(0xFF0B1026)
private val Moon = Color(0xFFF5F6FB)
private val Moon2 = Color(0xFFC9CEE3)
private val Moon3 = Color(0xFF9EA6C4)
private val Venus = Color(0xFFFFE7A8)
private val Nova = Color(0xFFFF8A4A)
private val Aurora = Color(0xFF2DD4BF)
private val Nebula = Color(0xFFB3A4FF)
private val NovaLight = Color(0xFFFFB199)

/** Opens the app on planetfoss://<path> – only links of this package (MainActivity checks the scheme). */
private fun open(context: Context, path: String) = actionStartActivity(
    Intent(Intent.ACTION_VIEW, "planetfoss://$path".toUri(), context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
)

@Composable
private fun Small(text: String, color: Color = Moon3, bold: Boolean = false, size: Int = 11) =
    Text(text, maxLines = 1, style = TextStyle(color = ColorProvider(color), fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal))

/** The kicker in letter-spaced capitals, as the logbook head (“LOGBUCH · FR. 6.11.”). */
@Composable
private fun Kicker(text: String, color: Color) =
    Text(text.uppercase(), maxLines = 1, style = TextStyle(color = ColorProvider(color), fontSize = 10.sp, fontWeight = FontWeight.Bold))

/** One line of variant C: a coloured sign, a bold name and quiet facts. */
@Composable
private fun Line(sign: String, signColor: Color, name: String, facts: String) {
    Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(sign, style = TextStyle(color = ColorProvider(signColor), fontSize = 12.sp, fontWeight = FontWeight.Bold), modifier = GlanceModifier.width(18.dp))
        Text(name, maxLines = 1, style = TextStyle(color = ColorProvider(Moon), fontSize = 12.sp, fontWeight = FontWeight.Medium))
        if (facts.isNotEmpty()) {
            Spacer(GlanceModifier.width(8.dp))
            Text(facts, maxLines = 1, style = TextStyle(color = ColorProvider(Moon3), fontSize = 11.sp))
        }
    }
}

private val SMALL = DpSize(110.dp, 110.dp)
private val WIDE = DpSize(250.dp, 110.dp)

/**
 * The logbook widget (variant C of the mockup, 6.11.2026): night sky with Venus, kicker and headline on top, below one line
 * each for supernova, comet and new versions. 4 × 2 by default; at 2 × 2 the facts are left out. Data from the app's last
 * logbook load; older than two hours (or none), the widget asks the API itself – marked as background (no view).
 */
class LogbookWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ep = entry(context)
        var data = ep.widgets().logbook()
        if (data == null || System.currentTimeMillis() - data.at > WidgetStore.STALE_MS) {
            runCatching {
                val lang = ep.settings().settings.first().contentLang
                ep.widgets().saveLogbook(ep.feed().logbook(null, lang, Use.BACKGROUND), redraw = false)
            }
            data = ep.widgets().logbook() ?: data
        }
        provideContent { LogbookContent(context, data, wide = LocalSize.current.width >= WIDE.width) }
    }
}

@Composable
private fun LogbookContent(context: Context, d: LogbookWidgetData?, wide: Boolean) {
    val res = context.resources
    // opens the entry itself (the logbook is no tab since 10.11.2026); without data the Latest tab
    Box(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_sky)).clickable(open(context, d?.day?.let { "logbook/$it" } ?: "today"))) {
        // Venus top right, as in the logbook head
        Box(GlanceModifier.fillMaxSize().padding(top = 16.dp, end = 16.dp), contentAlignment = Alignment.TopEnd) {
            Spacer(GlanceModifier.size(14.dp).background(ImageProvider(R.drawable.widget_venus)))
        }
        Column(GlanceModifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Kicker(context.getString(R.string.logbook_title) + (d?.let { " · " + shortDay(it.day) } ?: ""), Venus)
            Spacer(GlanceModifier.height(4.dp))
            if (d == null) {
                Text(context.getString(R.string.widget_loading), style = TextStyle(color = ColorProvider(Moon2), fontSize = 14.sp))
                return@Column
            }
            Text(d.headline, maxLines = if (wide) 1 else 2,
                style = TextStyle(color = ColorProvider(Moon), fontSize = if (wide) 20.sp else 16.sp, fontWeight = FontWeight.Bold))
            // the lines sit at the bottom: the space between head and lines grows with the widget
            Spacer(GlanceModifier.defaultWeight())
            d.nova?.let { n ->
                val name = if (n == d.headline) context.getString(R.string.radar_supernova) else n
                val facts = res.getQuantityString(R.plurals.n_sources, d.novaSources, d.novaSources) + if (d.langs.isNotEmpty()) " · " + d.langs else ""
                if (wide) Line("✦", Nova, name, facts) else Line("✦", Nova, res.getQuantityString(R.plurals.n_sources, d.novaSources, d.novaSources), "")
            }
            d.comets.firstOrNull()?.let { c ->
                val n = d.cometSources.firstOrNull() ?: 0
                Line("☄", Nebula, c, if (wide && n > 0) context.getString(R.string.radar_comet) + " · " + res.getQuantityString(R.plurals.n_sources, n, n) else "")
            }
            if (d.releases > 0 && wide) {
                Line("⬆", Aurora, res.getQuantityString(R.plurals.widget_releases, d.releases, d.releases), d.releaseNames.joinToString(", "))
            }
            if (d.nova == null && d.comets.isEmpty() && d.releases == 0) {
                Small(context.getString(R.string.widget_quiet), Moon2, size = 12)
            }
        }
    }
}

/**
 * The Radar widget (2 × 2 or 4 × 1), in the logbook widget's style: up to four followed topics or sources with the number of
 * new posts since the last visit, burning ones in supernova orange, thin lines between. Counted when the app shows Radar;
 * older than two hours, the widget counts itself (the follow check's requests).
 */
class RadarWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ep = entry(context)
        var data = ep.widgets().radar()
        if (data == null || System.currentTimeMillis() - data.at > WidgetStore.STALE_MS) {
            runCatching { ep.radar().count(Use.BACKGROUND, redraw = false) }
            data = ep.widgets().radar() ?: data
        }
        provideContent { RadarContent(context, data) }
    }
}

@Composable
private fun RadarContent(context: Context, d: RadarWidgetData?) {
    Column(
        GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_sky)).padding(horizontal = 16.dp, vertical = 14.dp)
            .clickable(open(context, "radar")),
    ) {
        Kicker(context.getString(R.string.tab_radar), Aurora)
        Spacer(GlanceModifier.height(6.dp))
        when {
            d == null -> Small(context.getString(R.string.widget_loading), Moon2, size = 12)
            d.follows == 0 -> Text(context.getString(R.string.widget_radar_empty), maxLines = 6, style = TextStyle(color = ColorProvider(Moon2), fontSize = 13.sp))
            else -> d.rows.forEachIndexed { i, r ->
                if (i > 0) Spacer(GlanceModifier.fillMaxWidth().height(1.dp).background(ImageProvider(R.drawable.widget_divider)))
                Row(GlanceModifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        r.label + if (r.supernova) " ✦" else "", maxLines = 1, modifier = GlanceModifier.defaultWeight(),
                        style = TextStyle(color = ColorProvider(if (r.supernova) NovaLight else if (r.fresh > 0) Moon else Moon3), fontSize = 13.sp),
                    )
                    Text(
                        if (r.fresh > 0) "+${r.fresh}" else "–",
                        style = TextStyle(color = ColorProvider(if (r.fresh > 0) Aurora else Moon3), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }
}

/** “6.11.” from “2026-11-06”. */
private fun shortDay(day: String): String = day.split('-').let { p -> if (p.size == 3) "${p[2].toInt()}.${p[1].toInt()}." else day }

class LogbookWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LogbookWidget()
}

class RadarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RadarWidget()
}
