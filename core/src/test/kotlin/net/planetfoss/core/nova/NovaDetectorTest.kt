// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.core.nova

import net.planetfoss.core.api.Supernova
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class NovaDetectorTest {
    private val now = Instant.parse("2026-10-29T18:00:00Z")

    private fun nova(key: String, announcedAgo: Duration, ignition: Long = 1_793_287_928): Supernova {
        val announced = now.minus(announcedAgo)
        return Supernova(
            id = "tag:planetfoss.net,2026:supernova/$key/$ignition",
            key = key,
            label = key,
            ignitedAt = announced.minusSeconds(1800),
            announcedAt = announced,
            url = "https://planetfoss.net/thema/$key/",
        )
    }

    /** Runs the detector and applies its result to [seen], as the repository does. */
    private fun run(current: List<Supernova>, seen: MutableMap<String, SeenNova>, on: Boolean = true): List<Supernova> {
        val result = NovaDetector.process(current, seen.keys, now, on)
        result.seen.forEach { seen.putIfAbsent(it.id, it) }
        return result.notify
    }

    @Test
    fun newSupernovaNotifiesOnce() {
        val seen = mutableMapOf<String, SeenNova>()
        val list = listOf(nova("gnome-52", Duration.ofMinutes(20)))
        assertEquals(listOf("gnome-52"), run(list, seen).map { it.key })
        assertEquals(emptyList<Supernova>(), run(list, seen))
        assertEquals(emptyList<Supernova>(), run(list, seen))
        assertTrue(seen.getValue(list[0].id).notified)
    }

    @Test
    fun duplicateEntriesInOneAnswerNotifyOnce() {
        val n = nova("gnome-52", Duration.ofMinutes(20))
        assertEquals(1, run(listOf(n, n), mutableMapOf()).size)
    }

    @Test
    fun oldAnnouncementsAreStoredButSilent() {
        val seen = mutableMapOf<String, SeenNova>()
        val old = nova("kernel-7-3", Duration.ofDays(3))
        val fresh = nova("gnome-52", Duration.ofHours(47))
        assertEquals(listOf("gnome-52"), run(listOf(old, fresh), seen).map { it.key })
        assertEquals(false, seen.getValue(old.id).notified)
        assertEquals(emptyList<Supernova>(), run(listOf(old, fresh), seen))
    }

    @Test
    fun lateDeliveredEntryWithinWindowStillNotifies() {
        val seen = mutableMapOf<String, SeenNova>()
        run(listOf(nova("gnome-52", Duration.ofMinutes(5))), seen)
        // the feed delivers an older supernova only now (e.g. it was hidden and shown again)
        val late = nova("ubuntu-26-10", Duration.ofHours(30))
        assertEquals(listOf("ubuntu-26-10"), run(listOf(nova("gnome-52", Duration.ofMinutes(5)), late), seen).map { it.key })
    }

    @Test
    fun changedContentOfKnownIdIsNoNewSupernova() {
        val seen = mutableMapOf<String, SeenNova>()
        val first = nova("gnome-52", Duration.ofMinutes(20))
        run(listOf(first), seen)
        val grown = first.copy(label = "GNOME 52 (more sources)", burning = false)
        assertEquals(emptyList<Supernova>(), run(listOf(grown), seen))
    }

    @Test
    fun reignitionAfterSevenDaysIsNew() {
        val seen = mutableMapOf<String, SeenNova>()
        run(listOf(nova("gnome-52", Duration.ofMinutes(20), ignition = 1_790_000_000)), seen)
        val again = nova("gnome-52", Duration.ofMinutes(10), ignition = 1_793_287_928)
        assertEquals(listOf(again.id), run(listOf(again), seen).map { it.id })
    }

    @Test
    fun notificationsOffMarksSeenWithoutNotifying() {
        val seen = mutableMapOf<String, SeenNova>()
        val n = nova("gnome-52", Duration.ofMinutes(20))
        assertEquals(emptyList<Supernova>(), run(listOf(n), seen, on = false))
        // switching notifications on later does not replay what was already seen
        assertEquals(emptyList<Supernova>(), run(listOf(n), seen, on = true))
    }

    @Test
    fun phoneClockBehindServerStillNotifies() {
        val ahead = nova("gnome-52", Duration.ofMinutes(-15))
        assertEquals(1, run(listOf(ahead), mutableMapOf()).size)
    }

    @Test
    fun notificationsComeOldestFirst() {
        val a = nova("a", Duration.ofHours(2))
        val b = nova("b", Duration.ofHours(5))
        assertEquals(listOf("b", "a"), run(listOf(a, b), mutableMapOf()).map { it.key })
    }
}
