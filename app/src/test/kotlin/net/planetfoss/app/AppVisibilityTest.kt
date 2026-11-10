// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppVisibilityTest {
    private val server = MockWebServer()
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server.start()
        client = OkHttpClient.Builder().addNetworkInterceptor(AppVisibility.interceptor(server.hostName)).build()
    }

    @After
    fun tearDown() = server.close()

    private fun get(path: String): Pair<String?, String?> {
        server.enqueue(MockResponse.Builder().body("{}").build())
        client.newCall(Request.Builder().url(server.url(path)).build()).execute().close()
        val r = server.takeRequest()
        return r.headers["X-PlanetFOSS-Start"] to r.headers["X-PlanetFOSS-Background"]
    }

    @Test
    fun backgroundCheckWhileOpenKeepsTheStartForTheNextView() {
        AppVisibility.stopped()
        ShadowSystemClock.advanceBy(Duration.ofMinutes(31))
        AppVisibility.started()
        server.enqueue(MockResponse.Builder().body("{}").build())
        client.newCall(Request.Builder().url(server.url("/api/app/v1/supernovas")).header("X-PlanetFOSS-Background", "1").build()).execute().close()
        val check = server.takeRequest()
        assertEquals("1" to null, check.headers["X-PlanetFOSS-Background"] to check.headers["X-PlanetFOSS-Start"])
        assertEquals("1" to null, get("/api/app/v1/front"))           // the visit starts with the first real view
    }

    @Test
    fun startOncePerVisitBackgroundWhileHidden() {
        AppVisibility.stopped()
        ShadowSystemClock.advanceBy(Duration.ofMinutes(31))            // away long enough: the next opening is a new visit
        assertEquals(null to "1", get("/api/app/v1/supernovas"))      // background check: not a view, start stays pending
        AppVisibility.started()
        assertEquals("1" to null, get("/api/app/v1/home"))            // first list of the visit
        assertEquals(null to null, get("/api/app/v1/rubrics"))
        assertEquals(null to null, get("/img/logo.webp"))               // images get nothing
        AppVisibility.stopped()
        ShadowSystemClock.advanceBy(Duration.ofMinutes(5))
        AppVisibility.started()
        assertEquals(null to null, get("/api/app/v1/home"))           // back after 5 minutes: same visit
        AppVisibility.stopped()
        ShadowSystemClock.advanceBy(Duration.ofMinutes(31))
        AppVisibility.started()
        assertEquals("1" to null, get("/api/app/v1/home"))            // after 30 minutes away: new visit
        assertNull(get("/api/app/v1/home").first)
    }
}
