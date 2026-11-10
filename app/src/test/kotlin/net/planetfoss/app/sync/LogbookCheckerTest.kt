// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class LogbookCheckerTest {
    @Test
    fun firstCheckAfterTurningOnOnlyStores() {
        assertEquals(true to false, LogbookChecker.decide(null, "2026-11-05", quiet = false))
    }

    @Test
    fun newerEntryIsAnnouncedOnce() {
        assertEquals(true to true, LogbookChecker.decide("2026-11-04", "2026-11-05", quiet = false))
        assertEquals(false to false, LogbookChecker.decide("2026-11-05", "2026-11-05", quiet = false))
    }

    @Test
    fun quietDayIsStoredWithoutNotification() {
        assertEquals(true to false, LogbookChecker.decide("2026-11-04", "2026-11-05", quiet = true))
    }

    @Test
    fun olderAnswerChangesNothing() {
        // a cached answer from yesterday after today's entry was seen
        assertEquals(false to false, LogbookChecker.decide("2026-11-05", "2026-11-04", quiet = false))
    }
}
