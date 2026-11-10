// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.planetfoss.core.api.LogbookEntry
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** What the logbook widget shows: the newest entry in short. */
@Serializable
data class LogbookWidgetData(
    val day: String,
    val headline: String,
    /** The first supernova (or the biggest story when none burns), with its sources. */
    val nova: String? = null,
    val novaSources: Int = 0,
    val comets: List<String> = emptyList(),
    /** Sources per comet, in the order of [comets]. */
    val cometSources: List<Int> = emptyList(),
    val releases: Int = 0,
    /** The first new versions by name (“OpenSSH 10.6”). */
    val releaseNames: List<String> = emptyList(),
    /** Languages of the supernova's sources: “DE + EN”, “DE”, “EN” or empty. */
    val langs: String = "",
    val quiet: Boolean = false,
    val at: Long = 0,
)

/** One row of the Radar widget. */
@Serializable
data class RadarWidgetRow(val kind: String, val ref: String, val label: String, val fresh: Int, val supernova: Boolean = false)

@Serializable
data class RadarWidgetData(val rows: List<RadarWidgetRow> = emptyList(), val follows: Int = 0, val at: Long = 0)

/**
 * The widgets' data, kept on the phone (DataStore): saved whenever the app loads the logbook or counts Radar, and by the
 * widgets themselves when it is older than [STALE_MS]. Saving updates the placed widgets.
 */
@Singleton
class WidgetStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: DataStore<Preferences>,
    private val json: Json,
    private val clock: Clock,
) {
    suspend fun logbook(): LogbookWidgetData? = store.data.first()[LOGBOOK]?.let { runCatching { json.decodeFromString(LogbookWidgetData.serializer(), it) }.getOrNull() }

    suspend fun radar(): RadarWidgetData? = store.data.first()[RADAR]?.let { runCatching { json.decodeFromString(RadarWidgetData.serializer(), it) }.getOrNull() }

    /** [redraw] false when a widget saves while it draws itself. */
    suspend fun saveLogbook(e: LogbookEntry, redraw: Boolean = true) {
        val nova = e.supernovas.firstOrNull()
        val data = LogbookWidgetData(
            day = e.day, headline = e.headline,
            nova = nova?.label ?: e.story?.name, novaSources = nova?.sources ?: e.story?.sources ?: 0,
            comets = e.comets.map { it.label }.take(3), cometSources = e.comets.map { it.sources }.take(3),
            releases = e.releases.size, releaseNames = e.releases.map { r -> r.entity + (r.version?.let { " $it" } ?: "") }.take(3),
            langs = nova?.langs?.let { l -> listOfNotNull("DE".takeIf { l.de > 0 }, "EN".takeIf { l.en > 0 }).joinToString(" + ") }
                ?: e.story?.langs?.let { l -> listOfNotNull("DE".takeIf { l.de > 0 }, "EN".takeIf { l.en > 0 }).joinToString(" + ") }.orEmpty(),
            quiet = e.quiet, at = clock.millis(),
        )
        store.edit { it[LOGBOOK] = json.encodeToString(LogbookWidgetData.serializer(), data) }
        if (redraw) runCatching { net.planetfoss.app.widget.LogbookWidget().updateAll(context) }
    }

    suspend fun saveRadar(c: RadarCount, redraw: Boolean = true) {
        val data = RadarWidgetData(
            rows = c.rows.filter { !it.failed }.take(ROWS).map { RadarWidgetRow(it.follow.kind, it.follow.ref, it.follow.label, it.fresh, it.supernova) },
            follows = c.rows.size, at = clock.millis(),
        )
        store.edit { it[RADAR] = json.encodeToString(RadarWidgetData.serializer(), data) }
        if (redraw) runCatching { net.planetfoss.app.widget.RadarWidget().updateAll(context) }
    }

    /**
     * Follows changed (bell on a topic page, settings): the Radar widget's rows are out of date. Marks them stale and redraws –
     * the widget then counts itself (6.11.: changed follows, the widget kept showing the old ones).
     */
    suspend fun radarChanged() {
        val old = radar() ?: RadarWidgetData()
        store.edit { it[RADAR] = json.encodeToString(RadarWidgetData.serializer(), old.copy(at = 0)) }
        runCatching { net.planetfoss.app.widget.RadarWidget().updateAll(context) }
    }

    companion object {
        const val STALE_MS = 2 * 60 * 60 * 1000L
        const val ROWS = 4
        private val LOGBOOK = stringPreferencesKey("widget_logbook")
        private val RADAR = stringPreferencesKey("widget_radar")
    }
}
