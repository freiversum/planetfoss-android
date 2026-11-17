// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.core.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class FilterSetTest {
    private fun set(vararg rules: String) = FilterSet.parse(listOf("[Adblock Plus 2.0]\n" + rules.joinToString("\n")), validate = false)

    private fun req(url: String, page: String? = "news.example.org", type: ResourceType = ResourceType.SCRIPT): FilterRequest {
        val host = url.substringAfter("://").substringBefore('/').substringBefore(':')
        return FilterRequest(url, host, page, type)
    }

    private fun blocked(s: FilterSet, url: String, page: String? = "news.example.org", type: ResourceType = ResourceType.SCRIPT) =
        s.match(req(url, page, type)) != null

    @Test
    fun hostRulesBlockTheHostAndItsSubdomains() {
        val s = set("||ads.example.com^", "||doubleclick.net^")
        assertTrue(blocked(s, "https://ads.example.com/x.js"))
        assertTrue(blocked(s, "https://stats.g.doubleclick.net/collect?x=1"))
        assertFalse(blocked(s, "https://example.com/ads.example.com.js"))
        assertFalse(blocked(s, "https://notdoubleclick.net/x"))
        assertFalse(blocked(s, "https://doubleclick.network/x"))
    }

    @Test
    fun patternsWithWildcardsSeparatorsAndAnchors() {
        val s = set("/banner/*/ad_", "||cdn.example.com/track^", "|https://evil.example/", "swf|", "&ad_type=")
        assertTrue(blocked(s, "https://site.org/banner/123/ad_top.png"))
        assertTrue(blocked(s, "https://cdn.example.com/track?u=1"))
        assertTrue(blocked(s, "https://cdn.example.com/track"))
        assertFalse(blocked(s, "https://cdn.example.com/tracker.js"))
        assertTrue(blocked(s, "https://evil.example/a"))
        assertFalse(blocked(s, "https://x.org/?r=https://evil.example/"))
        assertTrue(blocked(s, "https://x.org/movie.swf"))
        assertFalse(blocked(s, "https://x.org/movie.swf?x"))
        assertTrue(blocked(s, "https://x.org/a?b=1&ad_type=video"))
    }

    @Test
    fun exceptionsWinUnlessImportant() {
        val s = set("||ads.example.com^", "@@||ads.example.com/allowed/", "||tracker.example^\$important", "@@||tracker.example^")
        assertTrue(blocked(s, "https://ads.example.com/a.js"))
        assertFalse(blocked(s, "https://ads.example.com/allowed/b.js"))
        assertTrue(blocked(s, "https://tracker.example/t.js"))
    }

    @Test
    fun thirdPartyAndDomainOptions() {
        val s = set("||cdn.ads.net^\$third-party", "/widget.js\$domain=blog.example|~safe.blog.example", "||own.example^\$~third-party")
        assertTrue(blocked(s, "https://cdn.ads.net/a.js", page = "news.org"))
        assertFalse(blocked(s, "https://cdn.ads.net/a.js", page = "www.ads.net")) // same site
        assertFalse(blocked(s, "https://cdn.ads.net/a.js", page = null)) // unknown page: party unknown
        assertTrue(blocked(s, "https://x.org/widget.js", page = "www.blog.example"))
        assertFalse(blocked(s, "https://x.org/widget.js", page = "safe.blog.example"))
        assertFalse(blocked(s, "https://x.org/widget.js", page = "other.org"))
        assertTrue(blocked(s, "https://own.example/x", page = "www.own.example"))
        assertFalse(blocked(s, "https://own.example/x", page = "news.org"))
    }

    @Test
    fun typeOptions() {
        val s = set("||img.example^\$image", "||noscript.example^\$~script")
        assertTrue(blocked(s, "https://img.example/a.png", type = ResourceType.IMAGE))
        assertFalse(blocked(s, "https://img.example/a.js", type = ResourceType.SCRIPT))
        assertFalse(blocked(s, "https://noscript.example/a.js", type = ResourceType.SCRIPT))
        assertTrue(blocked(s, "https://noscript.example/a.png", type = ResourceType.IMAGE))
    }

    @Test
    fun documentExceptionSwitchesAPageOff() {
        val s = set("||ads.example.com^", "@@||trusted.example^\$document")
        assertTrue(blocked(s, "https://ads.example.com/a.js", page = "news.org"))
        assertFalse(blocked(s, "https://ads.example.com/a.js", page = "www.trusted.example"))
        assertTrue(s.pageAllowed("trusted.example"))
    }

    @Test
    fun unsupportedSyntaxIsSkippedNeverBroadened() {
        val s = set(
            "example.com##.ad", "example.com#@#.ad", "/^https?:\\/\\/[a-z]+\\.ads\\./", "||x.example^\$popup", "||y.example^\$redirect=noop.js",
            "||z.example^\$csp=script-src 'none'", "||q.example^\$unknownoption", "*", "^", "||w.example^\$websocket",
        )
        assertEquals(0, s.stats.network)
        assertEquals(2, s.stats.cosmetic)
        for (u in listOf("https://x.example/", "https://y.example/", "https://z.example/", "https://q.example/", "https://anything.org/", "https://w.example/")) {
            assertFalse(u, blocked(s, u))
        }
    }

    @Test
    fun matchingIsCaseInsensitive() {
        val s = set("||Ads.Example.COM^", "/BANNER/AD")
        assertTrue(blocked(s, "https://ADS.example.com/x"))
        assertTrue(blocked(s, "https://x.org/banner/ad_top.png"))
    }

    @Test
    fun registrableDomains() {
        assertEquals("example.com", Domains.registrable("a.b.example.com"))
        assertEquals("example.co.uk", Domains.registrable("www.example.co.uk"))
        assertEquals("user.github.io", Domains.registrable("user.github.io"))
        assertEquals("localhost", Domains.registrable("localhost"))
        // from the Public Suffix List, not a hand-made list
        assertEquals("example.com.ar", Domains.registrable("cdn.example.com.ar"))
        assertEquals("foo.blogspot.com", Domains.registrable("www.foo.blogspot.com"))
    }

    @Test
    fun validationRejectsNonLists() {
        try {
            FilterSet.parse(listOf("<html>Not a list</html>"))
            throw AssertionError("accepted HTML")
        } catch (e: InvalidFilterList) {
            assertTrue(e.message!!.contains("header"))
        }
        try {
            FilterSet.parse(listOf("[Adblock Plus 2.0]\n||a.example^\n||b.example^"))
            throw AssertionError("accepted a tiny list")
        } catch (e: InvalidFilterList) {
            assertTrue(e.message!!.contains("network rules"))
        }
        val ok = FilterSet.parse(listOf("[Adblock Plus 2.0]\n" + (1..1000).joinToString("\n") { "||ad$it.example^" }))
        assertEquals(1000, ok.stats.network)
    }

    @Test
    fun pathologicalPatternStaysFast() {
        val s = set("a*a*a*a*a*a*a*a*b")
        val url = "https://x.org/" + "a".repeat(5000)
        val t = System.nanoTime()
        assertFalse(blocked(s, url))
        assertTrue("took ${(System.nanoTime() - t) / 1_000_000} ms", System.nanoTime() - t < 2_000_000_000L)
    }

    /** The real EasyPrivacy list, when present (-Dplanetfoss.lists=<dir>): parse time, rule counts, known hits and misses. */
    @Test
    fun realLists() {
        val file = System.getProperty("planetfoss.lists")?.let { File(it, "easyprivacy.txt") }
        assumeTrue("set -Dplanetfoss.lists to a folder with easyprivacy.txt", file?.exists() == true)
        val texts = listOf(file!!.readText())
        val rt = Runtime.getRuntime()
        repeat(3) { System.gc() }
        val before = rt.totalMemory() - rt.freeMemory()
        val t0 = System.nanoTime()
        val s = FilterSet.parse(texts)
        val parseMs = (System.nanoTime() - t0) / 1_000_000
        repeat(3) { System.gc() }
        val heapMb = (rt.totalMemory() - rt.freeMemory() - before) / 1024 / 1024
        println("parse: $parseMs ms, heap: $heapMb MB, ${s.stats}")
        assertTrue(s.stats.network >= FilterSet.MIN_RULES)
        val page = "www.heise.de"
        assertNotNull(s.match(req("https://www.googletagmanager.com/gtag/js?id=G-1", page)))
        assertNotNull(s.match(req("https://www.google-analytics.com/analytics.js", page)))
        assertNull(s.match(req("https://www.heise.de/assets/heise/css/main.css", page, ResourceType.STYLESHEET)))
        assertNull(s.match(req("https://planetfoss.net/img/2026/10/abc-480.webp", "planetfoss.net", ResourceType.IMAGE)))
        assertNull(s.match(req("https://linuxiac.com/wp-content/themes/x/style.css", "linuxiac.com", ResourceType.STYLESHEET)))
        val urls = (1..2000).map { "https://cdn$it.example.org/static/js/app.$it.min.js?v=$it" }
        val t1 = System.nanoTime()
        urls.forEach { s.match(req(it, page)) }
        val perUs = (System.nanoTime() - t1) / 1000 / urls.size
        println("match: $perUs µs per request")
        assertTrue("match too slow: $perUs µs", perUs < 2000)
    }
}
