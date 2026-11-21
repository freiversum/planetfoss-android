// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.freiversum

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** World UFO Day: the UFO visits Settings on 2 July and 24 June, by the phone's local date. */
class UfoDayTest {
    private fun clock(iso: String, zone: String) = Clock.fixed(Instant.parse(iso), ZoneId.of(zone))

    @Test
    fun bothDays() {
        assertTrue(UfoDay.isToday(clock("2027-07-02T10:00:00Z", "UTC")))
        assertTrue(UfoDay.isToday(clock("2027-06-24T10:00:00Z", "UTC")))
        assertTrue(UfoDay.isUfoDay(LocalDate.of(2028, 7, 2)))   // leap year
        assertTrue(UfoDay.isUfoDay(LocalDate.of(2028, 6, 24)))
    }

    @Test
    fun dayBeforeAndAfter() {
        for (d in listOf("2027-07-01", "2027-07-03", "2027-06-23", "2027-06-25", "2027-02-07", "2027-01-01")) {
            assertFalse(d, UfoDay.isToday(clock("${d}T12:00:00Z", "UTC")))
        }
    }

    @Test
    fun theLocalDateCounts() {
        // 1 July 23:30 UTC is already 2 July in Berlin, but still 1 July in New York
        val instant = "2027-07-01T23:30:00Z"
        assertTrue(UfoDay.isToday(clock(instant, "Europe/Berlin")))
        assertFalse(UfoDay.isToday(clock(instant, "America/New_York")))
        // 2 July 23:30 in New York is 3 July in UTC: still the day there
        assertTrue(UfoDay.isToday(clock("2027-07-03T03:30:00Z", "America/New_York")))
        assertFalse(UfoDay.isToday(clock("2027-07-03T03:30:00Z", "UTC")))
        // the first minute and the last minute of 24 June in Tokyo
        assertTrue(UfoDay.isToday(clock("2027-06-23T15:00:00Z", "Asia/Tokyo")))
        assertTrue(UfoDay.isToday(clock("2027-06-24T14:59:00Z", "Asia/Tokyo")))
        assertFalse(UfoDay.isToday(clock("2027-06-24T15:00:00Z", "Asia/Tokyo")))
    }

    @Test
    fun simulatedClockStandsOnTheDay() {
        val c = UfoDay.simulatedClock(ZoneOffset.ofHours(14))
        assertEquals(LocalDate.of(LocalDate.now(c).year, 7, 2), LocalDate.now(c))
        assertTrue(UfoDay.isToday(c))
    }
}
