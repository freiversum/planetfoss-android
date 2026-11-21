// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.core.api

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.junit4.MockWebServerRule
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class PlanetFossApiTest {
    @get:Rule
    val rule = MockWebServerRule()
    private val server: MockWebServer get() = rule.server

    private fun fixture(name: String) = javaClass.getResource("/api/$name")!!.readText()
    private fun api() = PlanetFossApi(OkHttpClient(), server.url("/"))
    private fun answer(code: Int, body: String) = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    @Test
    fun homeParsesAllFieldsAndIgnoresUnknownOnes() = runTest {
        answer(200, fixture("home.json"))
        val page = api().home(ContentLang.EN, "de", page = 2)
        val request = server.takeRequest()
        assertEquals("/api/app/v1/home?lang=en&ui=de&page=2", request.target)
        assertTrue(page.hasMore)
        val a = page.items[0]
        assertEquals(24970L, a.id)
        assertEquals(Instant.parse("2026-10-29T16:27:14Z"), a.publishedAt)
        assertEquals("https://planetfoss.net/img/2026/10/abc-480.webp", a.image?.small)
        assertEquals("Security", a.categories.single().name)
        assertEquals("9to5Linux", a.bundle?.also?.single()?.source)
        val b = page.items[1]
        assertNull(b.excerpt)
        assertNull(b.image)
        assertNull(b.source.logo)
    }

    @Test
    fun logbookEntryParses() = runTest {
        answer(200, fixture("logbook.json"))
        val e = api().logbook("2026-11-02", ContentLang.ALL, "de")
        assertEquals("/api/app/v1/logbook/2026-11-02?lang=&ui=de", server.takeRequest().target)
        assertEquals("2026-11-02", e.day)
        assertEquals("Introducing Kwi, a new KDE terminal emulator", e.headline)
        val nova = e.supernovas.single()
        assertEquals("auto-kwi", nova.key)
        assertTrue(nova.new)
        assertEquals(1, e.supernovas.single().reports.size)
        assertEquals("MPV 0.42", e.comets.single().label)
        assertTrue(e.releases.isNotEmpty())
        assertEquals("2026-44", e.weekDone?.week)
        assertEquals(12, e.weekDone?.storySources)
        assertTrue(e.archive.contains("2026-11-02"))
    }

    @Test
    fun logbookWithoutDayAsksForTheNewest() = runTest {
        answer(200, """{"day":"2026-11-05","label":"x","url":"u","headline":"h"}""")
        val e = api().logbook(null, ContentLang.DE, "en", Use.BACKGROUND)
        val r = server.takeRequest()
        assertEquals("/api/app/v1/logbook?lang=de&ui=en", r.target)
        assertEquals("1", r.headers["X-PlanetFOSS-Background"])
        assertTrue(e.supernovas.isEmpty() && !e.quiet && e.weekDone == null)
        try {
            api().logbook("5.11.", ContentLang.ALL, "de")
            fail("bad day accepted")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun discussionsAndRepliesParse() = runTest {
        answer(200, """{"topic":{"key":"fedora-45","label":"Fedora 45","supernova":true,
            "discussions":[{"site":"hn","name":"Hacker News","url":"https://news.ycombinator.com/item?id=1","points":9,"comments":3},
                           {"site":"mastodon","name":"Mastodon","url":"https://mastodon.social/@planetfoss/9","points":4,"comments":2}],
            "replies":[{"site":"mastodon","name":"Maât","handle":"@maat@mastodon.social","author_url":"https://mastodon.social/@maat",
                        "text":"Being dependent on snap …","url":"https://mastodon.social/@maat/10","posted_at":"2026-10-29T07:06:27+00:00","depth":1,"own":false},
                       {"site":"mastodon","name":"PlanetFOSS","handle":"@planetfoss@mastodon.social","text":"I'm not a fan either.",
                        "url":"https://mastodon.social/@planetfoss/11","posted_at":"2026-10-29T08:25:35+00:00","depth":2,"own":true}]},
            "items":[],"has_more":false}""")
        val head = api().list(ListTarget.Topic("fedora-45"), ContentLang.ALL, "de").topic!!
        assertEquals(listOf("hn", "mastodon"), head.discussions.map { it.site })
        assertTrue(head.discussions[1].own && !head.discussions[0].own)
        assertEquals(2, head.replies.size)
        assertEquals(Instant.parse("2026-10-29T07:06:27Z"), head.replies[0].postedAt)
        assertTrue(head.replies[1].own && head.replies[1].depth == 2)
        assertEquals("", head.replies[1].authorUrl)
    }

    @Test
    fun allLanguageSendsEmptyParameter() = runTest {
        answer(200, """{"items":[],"has_more":false}""")
        api().home(ContentLang.ALL, "en")
        assertEquals("/api/app/v1/home?lang=&ui=en&page=1", server.takeRequest().target)
    }

    @Test
    fun supernovasKeepTheFeedGuid() = runTest {
        answer(200, fixture("supernovas.json"))
        val nova = api().supernovas(ContentLang.ALL, "de").supernovas.single()
        // pending=1: the list shows a supernova from its ignition on, like the website
        assertEquals("/api/app/v1/supernovas?lang=&ui=de&pending=1", server.takeRequest().target)
        assertTrue("no announced field = announced (older server)", nova.announced)
        assertEquals("tag:planetfoss.net,2026:supernova/libreoffice-26-8/1793287928", nova.id)
        assertEquals(Instant.parse("2026-10-29T16:02:08Z"), nova.announcedAt)
        assertTrue(nova.burning)
        assertEquals(4, nova.stats.sources)
        // reports are full items: excerpt, image and logo where the source has them
        val (plain, full) = nova.reports
        assertEquals(null, plain.excerpt)
        assertEquals("Die Office-Suite bringt neue Funktionen.", full.excerpt)
        assertEquals("https://planetfoss.net/img/2026/10/lo-480.webp", full.image?.small)
        assertEquals("https://planetfoss.net/img/logos/b.webp", full.source.logo)
    }

    @Test
    fun listsCarryTheSponsorCard() {
        val answer = ApiJson.decodeFromString(
            ListAnswer.serializer(),
            """{"items":[],"ad":{"id":9,"slot":"feed","lang":"de","after":4,"kind":"sponsor","label":"Anzeige","by":"Pinguin-Rechner",
               "color":"#F26B1D","title":"Linux-Notebooks","cta":"Ansehen","image":{"small":"https://p/i-480.webp","large":"https://p/i-960.webp"},
               "click_url":"https://planetfoss.net/a/9?s=feed&l=de"}}""",
        )
        val ad = answer.ad!!
        assertEquals(4, ad.after)
        assertEquals("https://planetfoss.net/a/9?s=feed&l=de", ad.clickUrl)
        assertEquals(null, ApiJson.decodeFromString(ListAnswer.serializer(), """{"items":[],"ad":null}""").ad)
    }

    @Test
    fun adsEndpointCarriesCandidatesPerSlot() = runTest {
        answer(
            200,
            """{"ttl":300,"slots":{"app_feed":[{"id":9,"slot":"app_feed","lang":"de","after":4,"format":"standard","weight":2,"topics":["kde"],
               "kind":"sponsor","label":"Anzeige","by":"Musterhost","title":"Hosting","click_url":"https://planetfoss.net/a/9?s=app_feed&l=de"}],
               "app_list":[{"id":9,"slot":"app_list","lang":"de","after":8,"format":"compact","kind":"sponsor","label":"Anzeige","title":"Hosting",
               "click_url":"https://planetfoss.net/a/9?s=app_list&l=de"}]}}""",
        )
        val a = api().ads("de")
        val r = server.takeRequest()
        assertEquals("/api/app/v1/ads?ui=de", r.target)
        // a part of a screen, not a view or a start
        assertEquals("1", r.headers["X-PlanetFOSS-Part"])
        assertEquals(2, a.slots["app_feed"]!!.single().weight)
        assertEquals(listOf("kde"), a.slots["app_feed"]!!.single().topics)
        // an older answer without the new fields falls back to the standard card
        val list = a.slots["app_list"]!!.single()
        assertEquals("compact", list.format)
        assertEquals(5, list.weight)
        assertTrue(list.topics.isEmpty())
        assertEquals(emptyMap<String, List<net.planetfoss.core.api.Ad>>(), ApiJson.decodeFromString(AdsAnswer.serializer(), "{}").slots)
    }

    @Test
    fun sourceCarriesSupportLinkAndBrowserWish() {
        val plain = ApiJson.decodeFromString(Source.serializer(), """{"slug":"a","name":"A"}""")
        assertEquals(null, plain.support)
        assertEquals(false, plain.openExternal)
        val publisher = ApiJson.decodeFromString(Source.serializer(),
            """{"slug":"h","name":"H","support":{"url":"https://h.example/abo","mode":"self","name":null},"open_external":true}""")
        assertEquals("https://h.example/abo", publisher.support?.url)
        assertEquals(null, publisher.support?.recommended)
        assertTrue(publisher.openExternal)
        val passes = ApiJson.decodeFromString(Source.serializer(),
            """{"slug":"b","name":"B","support":{"url":"https://f-droid.org/donate/","mode":"forward","name":"F-Droid"}}""")
        assertEquals("F-Droid", passes.support?.recommended)
        assertEquals("https://f-droid.org/donate/", passes.support?.url)
    }

    @Test
    fun cometsComeWithTheSupernovas() = runTest {
        answer(200, fixture("supernovas.json"))
        val comet = api().supernovas(ContentLang.ALL, "de").comets.single()
        assertEquals("tor-16", comet.key)
        assertEquals(Instant.parse("2026-10-29T16:27:14Z"), comet.firstAt)
        assertEquals(3, comet.stats.items)
        assertEquals("Tor Browser 16.0 released", comet.reports.single().title)
    }

    @Test
    fun httpErrorBecomesHttpException() = runTest {
        answer(503, """{"error":"unavailable"}""")
        try {
            api().supernovas(ContentLang.DE, "de")
            fail()
        } catch (e: ApiException.Http) {
            assertEquals(503, e.code)
        }
    }

    @Test
    fun brokenJsonBecomesMalformed() = runTest {
        answer(200, """{"items":[{"id":1}]""")
        try {
            api().home(ContentLang.DE, "de")
            fail()
        } catch (e: ApiException.Malformed) {
            // expected
        }
    }

    @Test
    fun badDateBecomesMalformed() = runTest {
        answer(200, fixture("home.json").replace("2026-10-29T16:27:14Z", "yesterday"))
        try {
            api().home(ContentLang.DE, "de")
            fail()
        } catch (e: ApiException.Malformed) {
            // expected
        }
    }

    @Test
    fun noConnectionBecomesNetwork() = runTest {
        val url = server.url("/")
        server.close()
        try {
            PlanetFossApi(OkHttpClient(), url).home(ContentLang.DE, "de")
            fail()
        } catch (e: ApiException.Network) {
            // expected
        }
    }

    @Test
    fun topicListCarriesItsHead() = runTest {
        answer(200, """{"topic":{"key":"gnome-52","label":"GNOME 52","supernova":true},"items":[],"has_more":false}""")
        val answer = api().list(ListTarget.Topic("gnome-52"), ContentLang.DE, "de", page = 2)
        assertEquals("/api/app/v1/topic/gnome-52?lang=de&ui=de&page=2", server.takeRequest().target)
        assertEquals("GNOME 52", answer.title)
        assertTrue(answer.topic!!.supernova)
    }

    @Test
    fun sourceHeadHasSiteUrl() = runTest {
        answer(200, """{"source":{"slug":"linuxiac","name":"Linuxiac","site_url":"https://linuxiac.com/","lang":"en"},"items":[],"has_more":false}""")
        val answer = api().list(ListTarget.Source("linuxiac"), ContentLang.ALL, "en")
        assertEquals("https://linuxiac.com/", answer.source?.siteUrl)
        assertEquals("Linuxiac", answer.title)
    }

    @Test
    fun rubricsWithHotTopics() = runTest {
        answer(200, """{"rubrics":[{"slug":"desktop","name":"Desktop","hot_topics":[{"key":"gnome-52","label":"GNOME 52","supernova":true}]},{"slug":"kernel","name":"Kernel","hot_topics":[]}]}""")
        val list = api().rubrics("de").rubrics
        assertEquals(listOf("desktop", "kernel"), list.map { it.slug })
        assertEquals("gnome-52", list[0].hotTopics.single().key)
    }

    @Test
    fun searchEncodesTheQuery() = runTest {
        answer(200, """{"items":[],"topics":[{"key":"c-sharp","label":"C#"}]}""")
        val result = api().search("C# & .NET", ContentLang.EN, "de")
        assertEquals("/api/app/v1/search?q=C%23%20%26%20.NET&lang=en&ui=de", server.takeRequest().target)
        assertEquals("C#", result.topics.single().label)
    }

    /** One view per opening of Radar and per visit to the search: the rest is marked as a part (19.11.2026). */
    @Test
    fun radarAndFurtherSearchesAreMarked() = runTest {
        repeat(3) { answer(200, """{"items":[],"has_more":false}""") }
        api().list(ListTarget.Topic("gnome"), ContentLang.ALL, "de", use = Use.RADAR)
        val radar = server.takeRequest()
        assertEquals("radar", radar.headers["X-PlanetFOSS-Screen"])
        assertEquals(null, radar.headers["X-PlanetFOSS-Part"])
        api().search("kernel", ContentLang.ALL, "de")
        assertEquals(null, server.takeRequest().headers["X-PlanetFOSS-Part"])
        api().search("kernel 7", ContentLang.ALL, "de", Use.PART)
        assertEquals("1", server.takeRequest().headers["X-PlanetFOSS-Part"])
    }

    @Test
    fun keysWithOtherCharactersNeverReachThePath() {
        for (bad in listOf("../admin", "a/b", "", "A", "x?y=1", "a".repeat(81))) {
            try {
                ListTarget.Topic(bad).endpoint
                fail("accepted $bad")
            } catch (e: IllegalArgumentException) {
                // expected
            }
        }
    }

    @Test
    fun starringSendsTaskAndSolutionAndReadsTheToken() = runTest {
        answer(200, """{"nonce":"5.1700000000.ab.cd","bits":17}""")
        answer(200, """{"ok":true,"token":"t0k3n","stars":4}""")
        answer(200, """{"ok":true,"stars":3}""")
        val ch = api().starChallenge(5)
        assertEquals("/api/star-challenge?id=5", server.takeRequest().target)
        val res = api().star(5, ch.nonce!!, 118744)
        val post = server.takeRequest()
        assertEquals("/api/star", post.target)
        assertEquals("""{"id":5,"nonce":"5.1700000000.ab.cd","solution":"118744"}""", post.body?.utf8())
        assertEquals("t0k3n", res.token)
        assertEquals(4, res.stars)
        assertEquals(3, api().unstar(5, "t0k3n").stars)
        assertEquals("""{"id":5,"token":"t0k3n"}""", server.takeRequest().body?.utf8())
    }

    @Test
    fun unknownItemHasNoTask() = runTest {
        answer(200, """{"error":"unbekannt"}""")
        assertNull(api().starChallenge(9).nonce)
    }

    @Test
    fun topicHeadTopicsAndSourcesParse() = runTest {
        answer(200, """{"topic":{"key":"ubuntu","label":"Ubuntu","supernova":false,"status":null,"intro":"Ubuntu von Canonical.",
            "stats":{"items":77,"sources":27,"first":"2026-09-16T13:56:32Z"},"rubric":{"slug":"distros","name":"Distros"},"parent":null,
            "related":[{"key":"debian","label":"Debian","supernova":false}]},"items":[],"has_more":false}""")
        val head = api().list(ListTarget.Topic("ubuntu"), ContentLang.ALL, "de").topic!!
        assertEquals(77, head.stats?.items)
        assertEquals(Instant.parse("2026-09-16T13:56:32Z"), head.stats?.first)
        assertEquals("debian", head.related.single().key)
        answer(200, """{"orbit":[{"key":"wine","label":"Wine 11.21","supernova":false,"status":"komet"}],"topics":[{"key":"gnome","label":"GNOME","supernova":false,"posts_30d":12}]}""")
        val t = api().topics(ContentLang.ALL, "de")
        assertEquals("komet", t.orbit.single().status)
        assertEquals(12, t.topics.single().posts30d)
        answer(200, """{"groups":[{"key":"blogs","name":"Blogs","sources":[{"slug":"linux-und-ich","name":"Linux und Ich","kind":"blog","lang":"de","week":5,"open_external":false,"support":null}]}]}""")
        assertEquals(5, api().sources(ContentLang.DE, "de").groups.single().sources.single().week)
        assertEquals("/api/app/v1/sources?lang=de&ui=de", server.run { takeRequest(); takeRequest(); takeRequest() }.target)
    }

    @Test
    fun partsAndBackgroundChecksAreMarked() = runTest {
        answer(200, """{"supernovas":[]}""")
        answer(200, """{"supernovas":[]}""")
        answer(200, """{"supernovas":[]}""")
        api().supernovas(ContentLang.ALL, "de", Use.PART)
        api().supernovas(ContentLang.ALL, "de", Use.BACKGROUND)
        api().supernovas(ContentLang.ALL, "de")
        assertEquals("1", server.takeRequest().headers["X-PlanetFOSS-Part"])
        assertEquals("1", server.takeRequest().headers["X-PlanetFOSS-Background"])
        val view = server.takeRequest().headers
        assertNull(view["X-PlanetFOSS-Part"] ?: view["X-PlanetFOSS-Background"])
    }

    @get:Rule
    val tmp = org.junit.rules.TemporaryFolder()

    /** Offline: the last answer from the HTTP cache, with the time it came; without one the network error stands. */
    @Test
    fun rubricsComeFromTheCacheWhenTheNetworkFails() = runTest {
        val client = OkHttpClient.Builder().cache(okhttp3.Cache(tmp.newFolder("http"), 1024 * 1024)).build()
        val api = PlanetFossApi(client, server.url("/"))
        server.enqueue(
            MockResponse.Builder().code(200).body("""{"rubrics":[{"slug":"linux","name":"Linux"}]}""")
                .addHeader("Cache-Control", "public, max-age=0").addHeader("ETag", "\"r1\"").build(),
        )
        val fresh = api.rubricsOrCached("de")
        assertNull(fresh.cachedAt)
        assertEquals("Linux", fresh.value.rubrics.single().name)
        server.close()
        val stale = api.rubricsOrCached("de")
        assertEquals("Linux", stale.value.rubrics.single().name)
        assertTrue(stale.cachedAt != null)
        // nothing cached for this one: the network error stands
        try {
            api.topicsOrCached(ContentLang.ALL, "de")
            fail("expected a network error")
        } catch (e: ApiException.Network) {
            // expected
        }
    }
}
