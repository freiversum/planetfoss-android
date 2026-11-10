// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.adblock

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import net.planetfoss.core.adblock.FilterRequest
import net.planetfoss.core.adblock.ResourceType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AdblockRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var repo: AdblockRepository
    private val scope = TestScope(UnconfinedTestDispatcher())

    /** A valid list: header, 1200 host rules, one pattern rule, one exception, padding to pass the size check. */
    private fun list(extra: String = "") = buildString {
        append("[Adblock Plus 2.0]\n! Title: Test\n")
        for (i in 1..1200) append("||ads$i.example^\n")
        append("||doubleclick.net^\n/banner/ad_\n@@||ads1.example/ok/\n").append(extra)
        while (length < 12_000) append("! padding\n")
    }

    private fun req(url: String, page: String = "news.example.org") =
        FilterRequest(url, url.substringAfter("://").substringBefore('/'), page, ResourceType.SCRIPT)

    @Before
    fun setUp() {
        // on the device androidx.startup does this (okhttp's PlatformInitializer); Robolectric does not run it
        okhttp3.OkHttp.initialize(ApplicationProvider.getApplicationContext())
        server.start()
        val store = PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile("a.preferences_pb") }
        repo = AdblockRepository(ApplicationProvider.getApplicationContext(), store, OkHttpClient(), Clock.fixed(Instant.parse("2026-10-29T20:00:00Z"), ZoneOffset.UTC))
        repo.urlOf = { server.url("/${it.name.lowercase()}.txt").toString() }
        File(ApplicationProvider.getApplicationContext<android.content.Context>().filesDir, "adblock").deleteRecursively()
        runBlocking { repo.setTrackers(true) }
    }

    @After
    fun tearDown() = server.close()

    private fun answer(body: String, etag: String? = null) = repeat(1) {
        server.enqueue(MockResponse.Builder().body(body).apply { if (etag != null) setHeader("ETag", etag) }.build())
    }

    @Test
    fun downloadValidatesAndBlocks() = runTest {
        assertNull(repo.decide(req("https://stats.g.doubleclick.net/x.js")))
        answer(list(), etag = "\"v1\"")
        assertTrue(repo.update())
        assertNotNull(repo.decide(req("https://stats.g.doubleclick.net/x.js")))
        assertNotNull(repo.decide(req("https://x.org/banner/ad_1.png")))
        assertNull(repo.decide(req("https://ads1.example/ok/x.js")))
        assertNull(repo.decide(req("https://planetfoss.net/img/a.webp")))
        val s = repo.state.first()
        assertFalse(s.missingLists)
        assertEquals(1203, s.lists.getValue(FilterList.EASYPRIVACY).rules)
        assertNull(s.lists.getValue(FilterList.EASYPRIVACY).lastError)
    }

    @Test
    fun brokenDownloadKeepsThePreviousList() = runTest {
        answer(list())
        repo.update()
        answer("<html><body>Maintenance</body></html>" + "x".repeat(20_000))
        repo.update()
        assertNotNull("old list still active", repo.decide(req("https://doubleclick.net/a.js")))
        assertTrue(repo.state.first().lists.getValue(FilterList.EASYPRIVACY).lastError!!.startsWith("invalid"))
    }

    @Test
    fun tooSmallOrTooFewRulesIsRejected() = runTest {
        answer("[Adblock Plus 2.0]\n||a.example^\n")
        assertFalse(repo.update())
        assertTrue(repo.state.first().lists.getValue(FilterList.EASYPRIVACY).lastError!!.contains("too small"))
        assertNull(repo.decide(req("https://a.example/x")))
    }

    @Test
    fun serverErrorIsRecordedNotFatal() = runTest {
        server.enqueue(MockResponse.Builder().code(503).build())
        assertFalse(repo.update())
        assertTrue(repo.state.first().lists.getValue(FilterList.EASYPRIVACY).lastError!!.startsWith("network"))
    }

    @Test
    fun notModifiedSendsEtagAndKeepsTheFile() = runTest {
        answer(list(), etag = "\"v1\"")
        repo.update()
        server.enqueue(MockResponse.Builder().code(304).build())
        assertTrue(repo.update())
        server.takeRequest()
        assertEquals("\"v1\"", server.takeRequest().headers["If-None-Match"])
        assertNotNull(repo.decide(req("https://doubleclick.net/a.js")))
    }

    @Test
    fun exceptionsCoverSubdomainsAndAreNormalised() = runTest {
        repo.addException("https://www.Heise.de/newsticker/")
        repo.addException("not a host")
        val s = repo.state.first()
        assertEquals(setOf("heise.de"), s.exceptions)
        assertTrue(s.isException("www.heise.de"))
        assertTrue(s.isException("heise.de"))
        assertFalse(s.isException("notheise.de"))
        repo.removeException("heise.de")
        assertTrue(repo.state.first().exceptions.isEmpty())
    }

    @Test
    fun clearingListsFreesTheRules() = runTest {
        answer(list())
        repo.update()
        repo.clearLists()
        assertNull(repo.decide(req("https://doubleclick.net/a.js")))
        assertTrue(repo.state.first().missingLists)
    }

    @Test
    fun requestTypes() {
        assertEquals(ResourceType.SCRIPT, RequestTypes.of("https://x/a.js?v=1", "*/*"))
        assertEquals(ResourceType.STYLESHEET, RequestTypes.of("https://x/a", "text/css,*/*;q=0.1"))
        assertEquals(ResourceType.IMAGE, RequestTypes.of("https://x/pixel", "image/avif,image/webp,*/*"))
        assertEquals(ResourceType.FONT, RequestTypes.of("https://x/f.woff2", null))
        assertEquals(ResourceType.SUBDOCUMENT, RequestTypes.of("https://x/frame", "text/html,application/xhtml+xml"))
        assertEquals(ResourceType.OTHER, RequestTypes.of("https://x/api", "application/json"))
    }

    @Test
    fun trackerProtectionBlocksThirdPartyTrackersOnly() = runTest {
        assertEquals("tracker protection needs EasyPrivacy only", listOf(FilterList.EASYPRIVACY), repo.state.first().neededLists)
        server.enqueue(MockResponse.Builder().body(list()).build())
        assertTrue(repo.update())
        assertEquals("/easyprivacy.txt", server.takeRequest().url.encodedPath)
        // a tracker of another site is blocked; first-party requests (the source's own statistics and ads) are not
        assertNotNull(repo.decide(FilterRequest("https://stats.g.doubleclick.net/x.js", "stats.g.doubleclick.net", "news.example", ResourceType.SCRIPT)))
        assertNull(repo.decide(FilterRequest("https://doubleclick.net/a.js", "doubleclick.net", "doubleclick.net", ResourceType.SCRIPT)))
        assertNull(repo.decide(FilterRequest("https://news.example/banner/ad_1.png", "news.example", "news.example", ResourceType.IMAGE)))
    }

    @Test
    fun switchingOffNeedsNoList() = runTest {
        repo.setTrackers(false)
        val s = repo.state.first()
        assertFalse(s.active)
        assertTrue(s.neededLists.isEmpty())
    }
}
