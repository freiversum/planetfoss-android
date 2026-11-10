// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import net.planetfoss.app.data.db.FollowEntity
import net.planetfoss.app.data.prefs.SettingsRepository
import net.planetfoss.core.api.ApiException
import net.planetfoss.core.api.Item
import net.planetfoss.core.api.Use
import javax.inject.Inject
import javax.inject.Singleton

/** One followed topic or source: how many of its newest posts came after the last visit, and whether it burns right now. */
data class RadarRow(val follow: FollowEntity, val fresh: Int, val supernova: Boolean = false, val comet: Boolean = false, val sources: Int = 0, val failed: Boolean = false)

/** Radar's answer: the rows (burning first, then most new) and the posts of everything followed, newest first. */
data class RadarCount(val rows: List<RadarRow>, val items: List<Item>, val error: Throwable? = null)

/**
 * Counts what is new in everything the user follows: page 1 of each followed topic or source in the content language (the
 * same public requests the follow check makes), compared with the end of the last visit. Used by the Radar tab and its
 * widget; the result for the widget is kept in [WidgetStore].
 */
@Singleton
class RadarCounter @Inject constructor(
    private val follows: FollowRepository,
    private val feed: FeedRepository,
    private val settings: SettingsRepository,
    private val seen: SeenRepository,
    private val widgets: WidgetStore,
) {
    /** [use] VIEW from the tab, BACKGROUND from the widget. Throws nothing: failures are in the rows and [RadarCount.error]. */
    suspend fun count(use: Use = Use.VIEW, redraw: Boolean = true): RadarCount = coroutineScope {
        val list = follows.all.first()
        if (list.isEmpty()) return@coroutineScope RadarCount(emptyList(), emptyList()).also { widgets.saveRadar(it, redraw) }
        val lang = settings.settings.first().contentLang
        val since = seen.mark.first()
        val answers = list.map { f ->
            async {
                val target = FollowRepository.targetOf(f.kind, f.ref) ?: return@async f to null
                f to runCatching { if (use == Use.VIEW) feed.radarPage(target, lang) else feed.firstPage(target, lang) }
            }
        }.awaitAll()
        val rows = answers.map { (f, r) ->
            val a = r?.getOrNull()
            if (a == null) {
                RadarRow(f, 0, failed = true)
            } else {
                RadarRow(
                    f, a.items.count { SeenRepository.isNew(it.publishedAt.toEpochMilli(), since) },
                    supernova = a.topic?.supernova == true, comet = a.topic?.status == "komet", sources = a.topic?.stats?.sources ?: 0,
                )
            }
        }.sortedWith(compareByDescending<RadarRow> { it.supernova }.thenByDescending { it.comet }.thenByDescending { it.fresh }.thenBy { it.follow.label.lowercase() })
        val items = answers.flatMap { (_, r) -> r?.getOrNull()?.items.orEmpty() }.distinctBy { it.id }.sortedByDescending { it.publishedAt }.take(MAX_ITEMS)
        val failed = answers.all { it.second?.isSuccess != true }
        val error = if (failed) answers.firstNotNullOfOrNull { it.second?.exceptionOrNull() } ?: ApiException.Network(java.io.IOException("no follow answered")) else null
        RadarCount(rows, items, error).also { if (!failed) widgets.saveRadar(it, redraw) }
    }

    private companion object {
        const val MAX_ITEMS = 40
    }
}
