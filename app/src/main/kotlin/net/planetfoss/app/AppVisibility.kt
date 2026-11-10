// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app

import android.os.SystemClock
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Whether the app is on screen, for planetfoss.net's reach numbers – counted on the server from the list requests the
 * app makes anyway, like the website's access log. Two headers on API requests, nothing stored on the phone:
 *
 * - `X-PlanetFOSS-Background: 1` while no activity is visible, and always on the supernova and follow checks (they set it
 *   themselves, Use.BACKGROUND – after an update they run while the app is open): not a view;
 * - `X-PlanetFOSS-Start: 1` on the first list request after the app came to the front – at its start or after
 *   30 minutes away. Like the website's “entries” (no referrer from PlanetFOSS), an approximation of visits.
 */
object AppVisibility {
    private const val AWAY_MS = 30 * 60 * 1000L

    @Volatile private var visible = false
    @Volatile private var startPending = true
    @Volatile private var stoppedAt = 0L

    fun started() {
        if (stoppedAt != 0L && SystemClock.elapsedRealtime() - stoppedAt >= AWAY_MS) startPending = true
        visible = true
    }

    fun stopped() {
        visible = false
        stoppedAt = SystemClock.elapsedRealtime()
    }

    /** Adds the headers to requests of the list API on [host] only (not to images or filter lists). */
    fun interceptor(host: String) = Interceptor { chain ->
        val request = chain.request()
        if (request.url.host != host || !request.url.encodedPath.startsWith("/api/app/v1/")) return@Interceptor chain.proceed(request)
        val b = request.newBuilder()
        if (request.header("X-PlanetFOSS-Background") != null) {
            // a background check (Use.BACKGROUND) while the app is open: marked already, never the start of a visit
        } else if (!visible) {
            b.header("X-PlanetFOSS-Background", "1")
        } else if (startPending) {
            startPending = false
            b.header("X-PlanetFOSS-Start", "1")
        }
        chain.proceed(b.build())
    }
}
