// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui

import net.planetfoss.app.BuildConfig
import net.planetfoss.app.ui.settings.RELEASE_NOTES
import net.planetfoss.app.ui.settings.baseVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsNewTest {
    /**
     * A release without notes would show an older version as the newest: on a release build (version from the tag) the
     * build's version must be the first entry. Development builds carry the next version already ("0.6.5-dev+…"), their
     * newest entry may be the last release – but never a version beyond the build's.
     */
    @Test
    fun theVersionBeingBuiltHasNotes() {
        val build = baseVersion(BuildConfig.VERSION_NAME)
        val newest = RELEASE_NOTES.first().version
        if ("-dev" in BuildConfig.VERSION_NAME) assertTrue("$newest is beyond $build", number(newest) <= number(build))
        else assertEquals(build, newest)
    }

    private fun number(v: String): Int = v.split('.').fold(0) { n, p -> n * 1000 + (p.toIntOrNull() ?: 0) }

    @Test
    fun notesAreShortAndNewestFirst() {
        assertTrue(RELEASE_NOTES.size >= 5)
        assertTrue(RELEASE_NOTES.all { it.notes.size in 1..5 })
        assertEquals(RELEASE_NOTES.map { it.date }.sortedDescending(), RELEASE_NOTES.map { it.date })
        assertEquals("0.6.4", baseVersion("0.6.4-dev+12.gabc.dirty-debug"))
    }
}
