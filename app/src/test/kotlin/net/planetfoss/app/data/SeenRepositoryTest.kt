// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.planetfoss.app.data.prefs.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** “New since the last visit”: the mark moves only after a real absence, “all seen” and the switch. */
class SeenRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private var now = 1_000_000L
    private val clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant(): Instant = Instant.ofEpochMilli(now)
    }

    @Test
    fun markIsTheEndOfTheLastRealVisit() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = TestScope(UnconfinedTestDispatcher())) { tmp.newFile("s.preferences_pb") }
        val settings = SettingsRepository(store)
        val seen = SeenRepository(store, settings, clock)
        // first start: nothing to compare with, nothing is new
        seen.started()
        assertNull(seen.mark.first())
        seen.stopped()
        val left = now
        // back after ten seconds (a look at an article in the browser): still the same visit
        now += 10_000
        seen.started()
        assertNull(seen.mark.first())
        seen.stopped()
        val leftAgain = now
        // back the next morning: the last visit ended when the app went away
        now += 10 * 60 * 60 * 1000L
        seen.started()
        assertEquals(leftAgain, seen.mark.first())
        assertTrue(leftAgain > left)
        // “All seen”: everything up to now is known
        seen.allSeen()
        assertEquals(now, seen.mark.first())
        // switched off: no markers at all
        settings.setMarkNew(false)
        assertNull(seen.mark.first())
    }

    @Test
    fun onlyLaterPostsAreNew() {
        assertTrue(SeenRepository.isNew(2_000, 1_000))
        assertFalse(SeenRepository.isNew(1_000, 1_000))
        assertFalse(SeenRepository.isNew(2_000, null))
    }
}
