// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.sync

import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import net.planetfoss.app.data.FeedRepository
import net.planetfoss.app.data.db.AppDatabase
import net.planetfoss.app.data.db.SeenSupernovaEntity
import net.planetfoss.app.data.prefs.SettingsRepository
import net.planetfoss.core.api.Supernova
import net.planetfoss.core.nova.NovaDetector
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/** Shows notifications; an interface so the check can be tested without Android's notification manager. */
interface NovaNotifier {
    /** False when the user blocked notifications (permission or channel) – then nothing is shown. */
    fun canNotify(): Boolean
    fun notify(novas: List<Supernova>)
}

/**
 * One supernova check, as the worker runs it. Fetches the supernova list once (the same
 * request the Supernovas tab makes, so the tab is fresh afterwards), lets [NovaDetector]
 * decide and stores the seen ids in one transaction before notifying – a crash after the
 * transaction loses at most a notification, never repeats one.
 */
@Singleton
class NovaChecker @Inject constructor(
    private val feed: FeedRepository,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val notifier: NovaNotifier,
    private val clock: Clock,
) {
    enum class Outcome { OFF, BASELINE, CHECKED }

    /** Throws [net.planetfoss.core.api.ApiException] on network or server errors (the worker retries). */
    suspend fun check(): Pair<Outcome, List<Supernova>> {
        val s = settings.settings.first()
        if (!s.novaNotify) return Outcome.OFF to emptyList()
        // a check of its own, also when it runs while the app is open: never a view of the supernovas
        val list = feed.refreshSupernovas(s.contentLang, net.planetfoss.core.api.Use.BACKGROUND)
        val baseline = !settings.novaBaselineDone()
        val now = clock.instant()
        val dao = db.seenSupernovaDao()
        val toNotify = db.withTransaction {
            val result = NovaDetector.process(list, dao.ids().toSet(), now, notificationsOn = !baseline && notifier.canNotify())
            val inserted = dao.insert(result.seen.map { SeenSupernovaEntity(it.id, it.key, it.firstSeen.toEpochMilli(), it.notified) })
            // only what this run actually inserted may notify (another run could have been faster)
            val fresh = result.seen.zip(inserted).filter { (_, rowId) -> rowId != -1L }.map { it.first.id }.toSet()
            dao.prune(now.minus(KEEP).toEpochMilli())
            result.notify.filter { it.id in fresh }
        }
        if (baseline) {
            settings.setNovaBaselineDone()
            return Outcome.BASELINE to emptyList()
        }
        if (toNotify.isNotEmpty()) notifier.notify(toNotify)
        return Outcome.CHECKED to toNotify
    }

    private companion object {
        /** PlanetFOSS lists a supernova for 7 days; 30 days of ids is plenty to never see one as new twice. */
        val KEEP: Duration = Duration.ofDays(30)
    }
}
