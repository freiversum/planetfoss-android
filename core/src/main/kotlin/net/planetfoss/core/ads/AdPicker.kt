// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.core.ads

import net.planetfoss.core.api.Ad
import net.planetfoss.core.api.ListTarget
import kotlin.random.Random

/** Slot names of the app's two ad placements (the server's `Ads::APP_SLOTS`). */
object AdSlot {
    const val FEED = "app_feed"
    const val LIST = "app_list"
}

/**
 * The ad slot of a list: Latest has `app_feed`, rubric, topic, source and video lists `app_list`. The Sternstunden, a story's
 * bundle and every other screen never carry an ad.
 */
val ListTarget.adSlot: String?
    get() = when (this) {
        ListTarget.Home -> AdSlot.FEED
        ListTarget.Videos, is ListTarget.Rubric, is ListTarget.Topic, is ListTarget.Source -> AdSlot.LIST
        else -> null
    }

/**
 * Picks the sponsor card of one screen from the server's candidates, like the website's rotation: weighted dice
 * (`weight` 1–10). A card bound to topics counts only on those topic lists, and where one matches, bound cards go first.
 */
object AdPicker {
    fun pick(candidates: List<Ad>, topicKey: String?, random: Random = Random): Ad? {
        val fits = candidates.filter { it.topics.isEmpty() || (topicKey != null && topicKey in it.topics) }
        val bound = fits.filter { it.topics.isNotEmpty() }
        val pool = bound.ifEmpty { fits }
        if (pool.isEmpty()) return null
        var roll = random.nextInt(pool.sumOf { it.weight.coerceIn(1, 10) }) + 1
        for (ad in pool) {
            roll -= ad.weight.coerceIn(1, 10)
            if (roll <= 0) return ad
        }
        return pool.last()
    }
}
