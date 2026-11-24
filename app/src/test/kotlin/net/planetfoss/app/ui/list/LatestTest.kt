// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.list

import net.planetfoss.core.api.Comet
import net.planetfoss.core.api.ContentLang
import net.planetfoss.core.api.Item
import net.planetfoss.core.api.LogbookTeaser
import net.planetfoss.core.api.OrbitTopic
import net.planetfoss.core.api.Source
import net.planetfoss.core.api.Supernova
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZonedDateTime

class LatestTest {
    private val teaser = LogbookTeaser("2026-11-10", "Firefox 160")
    private fun berlin(time: String) = ZonedDateTime.parse("${time}+01:00[Europe/Berlin]")
    private fun item(id: Long, lang: String) = Item(id, "https://example.org/$id", "Item $id", lang = lang, publishedAt = Instant.EPOCH, source = Source("s", "s"))
    private fun nova(key: String, burning: Boolean, at: String = "2026-11-10T10:42:00Z", langs: List<String> = emptyList()) =
        Supernova("id-$key", key, key, Instant.parse(at), Instant.parse(at), "u", burning = burning, reports = langs.mapIndexed { i, l -> item(i.toLong(), l) })

    @Test
    fun logbookIsACardInTheMorningUntilRead() {
        assertTrue(Latest.logbookAsCard(teaser, read = "", now = berlin("2026-11-10T06:05")))
        assertTrue(Latest.logbookAsCard(teaser, read = "2026-11-09", now = berlin("2026-11-10T11:59")))
        // read, past noon, or not today's entry: marker instead
        assertFalse(Latest.logbookAsCard(teaser, read = "2026-11-10", now = berlin("2026-11-10T08:00")))
        assertFalse(Latest.logbookAsCard(teaser, read = "", now = berlin("2026-11-10T12:00")))
        assertFalse(Latest.logbookAsCard(teaser, read = "", now = berlin("2026-11-11T05:30")))
        assertFalse(Latest.logbookAsCard(null, read = "", now = berlin("2026-11-10T07:00")))
    }

    @Test
    fun logbookMarkerSitsAtSixBetweenThePosts() {
        val at = Latest.logbookAt("2026-11-10")!!
        assertEquals(Instant.parse("2026-11-10T05:00:00Z"), at)
        val posts = listOf("2026-11-10T09:00:00Z", "2026-11-10T05:30:00Z", "2026-11-10T05:00:00Z", "2026-11-10T04:00:00Z").map(Instant::parse)
        assertEquals(2, Latest.logbookIndex(posts, at))
        // all newer (page not reaching back) or all older: no marker
        assertNull(Latest.logbookIndex(posts.take(2), at))
        assertNull(Latest.logbookIndex(posts.drop(2), at))
        assertNull(Latest.logbookAt("not a day"))
    }

    @Test
    fun postsOfABurningSupernovaLeaveTheStream() {
        fun post(id: Long, topic: String?) = item(id, "de").copy(topic = topic?.let { net.planetfoss.core.api.TopicRef(it, it) })
        val stream = listOf(post(10, "ladybird"), post(11, null), post(0, null), post(12, "firefox-161"))
        // by topic (10) and as a report of the card (id 0), whatever its topic says
        val nova = nova("ladybird", burning = true, langs = listOf("de"))
        assertEquals(listOf(11L, 12L), Latest.withoutNovaPosts(stream, listOf(nova)).map { it.id })
        // nothing burns: the stream stays as it is
        assertEquals(stream, Latest.withoutNovaPosts(stream, emptyList()))
    }

    @Test
    fun burningNewestFirstAndCooledApart() {
        val list = listOf(nova("a", true, "2026-11-09T10:00:00Z"), nova("b", false), nova("c", true, "2026-11-10T10:00:00Z"))
        assertEquals(listOf("c", "a"), Latest.burning(list).map { it.key })
        assertEquals(listOf("b"), Latest.cooled(list).map { it.key })
    }

    @Test
    fun risingPutsCometsFirstWithoutDuplicates() {
        val comets = listOf(Comet("firefox-160", "Firefox 160", "u", Instant.EPOCH, Instant.EPOCH))
        val orbit = listOf(OrbitTopic("kde-plasma-6-8", "KDE Plasma 6.8", true), OrbitTopic("firefox-160", "Firefox 160"), OrbitTopic("asahi", "Asahi Linux"))
        val rising = Latest.rising(comets, orbit, listOf(nova("kde-plasma-6-8", true)))
        assertEquals(listOf("firefox-160", "asahi"), rising.map { it.key })
        assertEquals("komet", rising[0].status)
    }

    @Test
    fun noteOnlyWhenNoReportInTheContentLanguage() {
        val en = nova("p", true, langs = listOf("en", "en"))
        assertEquals("en", Latest.otherLanguageOnly(en, ContentLang.DE))
        assertNull(Latest.otherLanguageOnly(en, ContentLang.EN))
        assertNull(Latest.otherLanguageOnly(en, ContentLang.ALL))
        assertNull(Latest.otherLanguageOnly(nova("q", true, langs = listOf("en", "de")), ContentLang.DE))
        assertEquals("de", Latest.otherLanguageOnly(nova("r", true, langs = listOf("de")), ContentLang.EN))
    }
}
