// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.core.nova

import net.planetfoss.core.api.Supernova
import java.time.Duration
import java.time.Instant

/** A supernova the app has seen once; [notified] says whether that sighting raised a notification. */
data class SeenNova(val id: String, val key: String, val firstSeen: Instant, val notified: Boolean)

/**
 * Decides which supernovas are new and worth a notification. Pure and
 * idempotent: the caller stores [Result.seen] (insert-or-ignore on [SeenNova.id])
 * and notifies [Result.notify] in the same transaction, so a second run with the
 * same answer finds every id already seen and notifies nothing.
 *
 * Identity is the API id (= RSS guid, topic key + ignition time). PlanetFOSS keeps
 * it when a topic re-ignites within 7 days, so that is no new supernova here either.
 */
object NovaDetector {
    /**
     * Older announcements are stored as seen but not notified (fresh install, long offline phase).
     * No upper bound: a phone clock behind the server must not swallow a notification.
     */
    val FRESH: Duration = Duration.ofHours(48)

    data class Result(val seen: List<SeenNova>, val notify: List<Supernova>)

    fun process(current: List<Supernova>, seenIds: Set<String>, now: Instant, notificationsOn: Boolean): Result {
        val fresh = now.minus(FRESH)
        val new = current.distinctBy { it.id }.filter { it.id !in seenIds }
        val notify = if (notificationsOn) new.filter { it.announcedAt >= fresh } else emptyList()
        val notifyIds = notify.mapTo(HashSet()) { it.id }
        return Result(
            seen = new.map { SeenNova(it.id, it.key, now, it.id in notifyIds) },
            notify = notify.sortedBy { it.announcedAt },
        )
    }
}
