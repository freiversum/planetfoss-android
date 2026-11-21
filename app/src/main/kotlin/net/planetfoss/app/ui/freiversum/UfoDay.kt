// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.freiversum

import java.time.Clock
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * World UFO Day: 2 July, and 24 June (the day of Kenneth Arnold's sighting in 1947, the older date of the
 * day). On both days the UFO of freiversum.de flies over Settings each time the screen is opened. The date
 * is the phone's local date, read from [clock] so tests (and debug builds) can choose the day.
 */
object UfoDay {
    fun isToday(clock: Clock): Boolean = isUfoDay(LocalDate.now(clock))

    fun isUfoDay(date: LocalDate): Boolean =
        (date.month == Month.JULY && date.dayOfMonth == 2) || (date.month == Month.JUNE && date.dayOfMonth == 24)

    /**
     * The clock the screen asks. Debug builds can replace it to simulate the day:
     * `adb shell am start -n net.planetfoss.app.debug/net.planetfoss.app.MainActivity --ez ufo_day true`
     * (see [MainActivity]); release builds never touch it.
     */
    @Volatile
    var clock: Clock = Clock.systemDefaultZone()

    /** A clock that stands on the next 2 July in the phone's zone, for the debug simulation. */
    fun simulatedClock(zone: ZoneId = ZoneId.systemDefault()): Clock {
        val now = LocalDate.now(zone)
        val day = LocalDate.of(now.year, Month.JULY, 2)
        return Clock.fixed(ZonedDateTime.of(day.atTime(12, 0), zone).toInstant(), zone)
    }
}
