// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.core.bookmarks

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.junit4.MockWebServerRule
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BookmarkClientTest {
    @get:Rule
    val rule = MockWebServerRule()
    private val server: MockWebServer get() = rule.server

    private fun target(service: BookmarkService) = BookmarkTarget(service, server.url("/sub/"), "s3cret")
    private fun answer(code: Int, body: String = "") = server.enqueue(MockResponse.Builder().code(code).body(body).build())
    private val client = BookmarkClient(OkHttpClient())

    @Test
    fun karakeepCreatesThenTags() = runTest {
        answer(201, """{"id":"abc","url":"x"}""")
        answer(200, """{"attached":["t1"]}""")
        val r = client.save(target(BookmarkService.KARAKEEP), "https://example.org/a", "Title", listOf("planetfoss"))
        assertEquals(BookmarkResult.Ok, r)
        val create = server.takeRequest()
        assertEquals("/sub/api/v1/bookmarks", create.target)
        assertEquals("Bearer s3cret", create.headers["Authorization"])
        assertEquals("""{"type":"link","url":"https://example.org/a","title":"Title"}""", create.body!!.utf8())
        val tags = server.takeRequest()
        assertEquals("/sub/api/v1/bookmarks/abc/tags", tags.target)
        assertEquals("""{"tags":[{"tagName":"planetfoss"}]}""", tags.body!!.utf8())
    }

    @Test
    fun linkdingUsesTokenPrefixAndTagNames() = runTest {
        answer(201, """{"id":1}""")
        assertEquals(BookmarkResult.Ok, client.save(target(BookmarkService.LINKDING), "https://e.org", "T", listOf("planetfoss", "linux")))
        val r = server.takeRequest()
        assertEquals("/sub/api/bookmarks/", r.target)
        assertEquals("Token s3cret", r.headers["Authorization"])
        assertEquals("""{"url":"https://e.org","title":"T","tag_names":["planetfoss","linux"]}""", r.body!!.utf8())
    }

    @Test
    fun linkwardenAndReadeckFields() = runTest {
        answer(200, """{"response":{"id":5}}""")
        answer(202, """{"status":202,"message":"Link submited for processing"}""")
        client.save(target(BookmarkService.LINKWARDEN), "https://e.org", "T", listOf("planetfoss"))
        client.save(target(BookmarkService.READECK), "https://e.org", "T", listOf("planetfoss"))
        val lw = server.takeRequest()
        assertEquals("/sub/api/v1/links", lw.target)
        assertEquals("""{"url":"https://e.org","name":"T","tags":[{"name":"planetfoss"}]}""", lw.body!!.utf8())
        val rd = server.takeRequest()
        assertEquals("/sub/api/bookmarks", rd.target)
        assertEquals("""{"url":"https://e.org","title":"T","labels":["planetfoss"]}""", rd.body!!.utf8())
    }

    @Test
    fun errorsAreSortedForTheUiAndTheRetry() = runTest {
        answer(401)
        answer(404)
        answer(503)
        answer(400, """{"url":["bad"]}""")
        answer(200, "<html>login</html>")
        val t = target(BookmarkService.LINKDING)
        assertEquals(BookmarkResult.Unauthorized, client.save(t, "u", "t", emptyList()))
        assertEquals(BookmarkResult.WrongAddress, client.save(t, "u", "t", emptyList()))
        assertTrue(client.save(t, "u", "t", emptyList()).retryable)
        assertEquals(BookmarkResult.Refused(400), client.save(t, "u", "t", emptyList()))
        assertEquals(BookmarkResult.WrongAddress, client.test(t))
    }

    @Test
    fun testCallsAReadEndpoint() = runTest {
        answer(200, """{"username":"me"}""")
        assertEquals(BookmarkResult.Ok, client.test(target(BookmarkService.READECK)))
        assertEquals("/sub/api/profile", server.takeRequest().target)
    }

    @Test
    fun addressIsNormalised() {
        assertEquals("https://keep.example.org/", BookmarkClient.parseAddress("keep.example.org")?.toString())
        assertEquals("https://h.org/links/", BookmarkClient.parseAddress(" https://h.org/links/api/v1/bookmarks?x=1#y ")?.toString())
        assertEquals("https://h.org:8443/", BookmarkClient.parseAddress("https://h.org:8443")?.toString())
        assertNull(BookmarkClient.parseAddress("http://192.168.0.5:9090"))
        assertEquals("http://192.168.0.5:9090/", BookmarkClient.parseAddress("http://192.168.0.5:9090", allowHttp = true)?.toString())
        assertNull(BookmarkClient.parseAddress("not an address"))
    }

    @Test
    fun tagsAreCleaned() {
        assertEquals("gnome-49", BookmarkClient.tag("GNOME 49"))
        assertEquals("übersicht", BookmarkClient.tag("Übersicht!"))
        assertNull(BookmarkClient.tag(" – "))
    }
}
