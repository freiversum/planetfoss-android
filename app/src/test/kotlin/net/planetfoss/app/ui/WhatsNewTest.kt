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
    /** A release without notes would show an older version as the newest: the build's version must be the first entry. */
    @Test
    fun theVersionBeingBuiltHasNotes() {
        assertEquals(baseVersion(BuildConfig.VERSION_NAME), RELEASE_NOTES.first().version)
    }

    @Test
    fun notesAreShortAndNewestFirst() {
        assertTrue(RELEASE_NOTES.size >= 5)
        assertTrue(RELEASE_NOTES.all { it.notes.size in 1..5 })
        assertEquals(RELEASE_NOTES.map { it.date }.sortedDescending(), RELEASE_NOTES.map { it.date })
        assertEquals("0.6.4", baseVersion("0.6.4-dev+12.gabc.dirty-debug"))
    }
}
