// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.core.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant

/*
 * Objects of the PlanetFOSS app API v1 (docs/02-api.md). They double as the
 * domain model: the API is ours and shaped for the app, so a second set of
 * classes would only copy fields.
 */

/** Content language as on the website: “Alle” (both), German or English. */
enum class ContentLang(val param: String) {
    ALL(""), DE("de"), EN("en");

    companion object {
        fun fromParam(value: String?): ContentLang = entries.firstOrNull { it.param == value } ?: ALL
    }
}

@Serializable
data class Item(
    val id: Long,
    val url: String,
    val title: String,
    val excerpt: String? = null,
    val lang: String,
    @SerialName("published_at") @Serializable(InstantSerializer::class) val publishedAt: Instant,
    @SerialName("is_video") val isVideo: Boolean = false,
    val stars: Int = 0,
    val source: Source,
    val image: ItemImage? = null,
    val categories: List<Category> = emptyList(),
    val topic: TopicRef? = null,
    val bundle: Bundle? = null,
)

@Serializable
data class Source(
    val slug: String,
    val name: String,
    val kind: String? = null,
    val color: String? = null,
    val logo: String? = null,
    /** Only in the head of `source/<slug>`. */
    @SerialName("site_url") val siteUrl: String? = null,
    val lang: String? = null,
    /** Where readers can support the source – or the project it recommends instead; set by PlanetFOSS with the publisher. */
    val support: Support? = null,
    /** The publisher wants its articles in the user's own browser, never in the app's WebView. */
    @SerialName("open_external") val openExternal: Boolean = false,
    /** Only in the source directory: posts of the last 7 days. */
    val week: Int = 0,
)

/**
 * A source's support link. [mode] `self`: the source itself (subscription, donation). `forward`: the source needs
 * no support and recommends another project, [name] – [url] is then that project's page.
 */
@Serializable
data class Support(
    val url: String,
    val mode: String = "self",
    val name: String? = null,
) {
    /** The recommended project when the source passes its support on, else null. */
    val recommended: String? get() = name?.takeIf { mode == "forward" && it.isNotBlank() }
}

@Serializable
data class ItemImage(
    val small: String,
    val large: String,
    val width: Int? = null,
    val height: Int? = null,
    val fit: Boolean = false,
)

@Serializable
data class Category(val slug: String, val name: String)

@Serializable
data class TopicRef(val key: String, val label: String, val supernova: Boolean = false)

@Serializable
data class Bundle(val id: Long, val also: List<BundleReport> = emptyList())

/** Head of a bundle list: the lead report's title, how many sources (and in which language), and the story's topic. */
@Serializable
data class BundleHead(
    val id: Long,
    val title: String,
    val sources: Int = 0,
    val topic: TopicRef? = null,
    val langs: SourceLangs? = null,
    /** Where this story is discussed (Hacker News, Lobsters), the biggest thread per service. */
    val discussions: List<Discussion> = emptyList(),
)

/**
 * “Discussed on”: the biggest thread per service – Hacker News and Lobsters (from one comment on), PlanetFOSS's own posts
 * on Mastodon and Bluesky (always, as an invitation). [points]: points or likes + boosts; [comments]: comments or replies.
 */
@Serializable
data class Discussion(val site: String, val name: String, val url: String, val points: Int = 0, val comments: Int = 0) {
    /** PlanetFOSS's own post (Mastodon, Bluesky), not a thread elsewhere. */
    val own: Boolean get() = site == "mastodon" || site == "bluesky"
}

/**
 * A public reply to PlanetFOSS's own supernova post on Mastodon or Bluesky – the website shows them below the supernova
 * instead of comments. Text only, no avatars; [depth] 1 = reply to the post, 2+ = reply to a reply; [own] = PlanetFOSS.
 */
@Serializable
data class FediReply(
    val site: String,
    val name: String,
    val handle: String,
    @SerialName("author_url") val authorUrl: String = "",
    val text: String,
    val url: String,
    @SerialName("posted_at") @Serializable(InstantSerializer::class) val postedAt: Instant,
    val depth: Int = 1,
    val own: Boolean = false,
)

/** Sources per language over all reports of a story – “so far only English sources” on the website's bundle page. */
@Serializable
data class SourceLangs(val de: Int = 0, val en: Int = 0)

@Serializable
data class BundleReport(val source: String, val url: String, val title: String, val lang: String)

@Serializable
data class ItemPage(
    val items: List<Item>,
    @SerialName("has_more") val hasMore: Boolean = false,
)

@Serializable
data class Rubric(
    val slug: String,
    val name: String,
    @SerialName("hot_topics") val hotTopics: List<TopicRef> = emptyList(),
)

@Serializable
data class RubricList(val rubrics: List<Rubric>)

/**
 * A sponsor card or affiliate tile of PlanetFOSS (no ad network, no third-party script): shown after
 * [after] posts, clearly labelled ([label]). [clickUrl] goes through planetfoss.net, which counts the
 * click and forwards; views are reported with [slot] and [lang], nothing else.
 */
@Serializable
data class Ad(
    val id: Long,
    val slot: String,
    val lang: String,
    val after: Int,
    val kind: String = "sponsor",
    val label: String,
    val by: String = "",
    val logo: String? = null,
    val color: String? = null,
    val title: String,
    val body: String? = null,
    val cta: String? = null,
    val note: String? = null,
    val image: AdImage? = null,
    @SerialName("click_url") val clickUrl: String,
    /** 1–10: share when several cards compete for the same slot (the website's rotation). */
    val weight: Int = 5,
    /** `standard` (image, title, text, button) or `compact` (logo, title, one line, button). */
    val format: String = "standard",
    /** Topic keys: a card bound to topics shows only on these topic lists; empty = anywhere. */
    val topics: List<String> = emptyList(),
)

/** Answer of `ads`: the candidates per app slot (`app_feed`, `app_list`); the app rolls the dice itself ([AdPicker]). */
@Serializable
data class AdsAnswer(val ttl: Int = 300, val slots: Map<String, List<Ad>> = emptyMap())

@Serializable
data class AdImage(val small: String, val large: String)

/** Answer of `rubric/<slug>`, `topic/<key>`, `source/<slug>`, `videos`: one head (or none) plus a page. */
@Serializable
data class ListAnswer(
    val rubric: Rubric? = null,
    val topic: TopicHead? = null,
    val source: Source? = null,
    val bundle: BundleHead? = null,
    val items: List<Item>,
    @SerialName("has_more") val hasMore: Boolean = false,
    /** PlanetFOSS's own sponsor card for page 1, only while ads are switched on in its admin. */
    val ad: Ad? = null,
    /** Latest, page 1, Monday to Wednesday: the hint to last week's review (as on the website's start page). */
    val weekly: WeekTeaser? = null,
    /** Latest, page 1: the newest logbook entry while it is at most a day old and not a quiet day. */
    val logbook: LogbookTeaser? = null,
) {
    val page: ItemPage get() = ItemPage(items, hasMore)

    /** Name of the list for its title bar. */
    val title: String? get() = rubric?.name ?: topic?.label ?: source?.name ?: bundle?.title
}

@Serializable
data class SearchResult(val items: List<Item> = emptyList(), val topics: List<TopicRef> = emptyList())

@Serializable
data class Supernova(
    /** Same string as the guid in the RSS feed: tag:planetfoss.net,YYYY:supernova/<key>/<ignition>. */
    val id: String,
    val key: String,
    val label: String,
    @SerialName("ignited_at") @Serializable(InstantSerializer::class) val ignitedAt: Instant,
    @SerialName("announced_at") @Serializable(InstantSerializer::class) val announcedAt: Instant,
    val url: String,
    val summary: String? = null,
    val quote: Quote? = null,
    val stats: NovaStats = NovaStats(),
    val burning: Boolean = false,
    /**
     * False during the 30 minutes between ignition and [announcedAt] (sent with `pending=1` only): the lists show it
     * like the website, but it does not notify yet – a misfire can still be hidden. Missing = announced (older server).
     */
    val announced: Boolean = true,
    /** The first reports, as full items like in the lists. */
    val reports: List<Item> = emptyList(),
)

@Serializable
data class Quote(val text: String, val source: String)

@Serializable
data class NovaStats(val items: Int = 0, val sources: Int = 0)

/** A rising topic (status “Komet”): counts and reports of the last 24 hours. */
@Serializable
data class Comet(
    val key: String,
    val label: String,
    val url: String,
    @SerialName("first_at") @Serializable(InstantSerializer::class) val firstAt: Instant,
    @SerialName("last_at") @Serializable(InstantSerializer::class) val lastAt: Instant,
    val stats: NovaStats = NovaStats(),
    val reports: List<Item> = emptyList(),
)

@Serializable
data class SupernovaList(val supernovas: List<Supernova>, val comets: List<Comet> = emptyList())

/** ISO 8601 instants (`2026-10-29T16:27:14Z`) as [Instant]. */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): Instant = decoder.decodeString().let { s ->
        // the logbook sends offsets (`2026-11-07T05:00:00+00:00`), the lists a trailing Z
        runCatching { Instant.parse(s) }.getOrElse { java.time.OffsetDateTime.parse(s).toInstant() }
    }
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
}

/**
 * Head of a topic list, as the website's topic page shows it: status (supernova, komet, nachgluehen or none),
 * introduction, numbers in the chosen language, rubric, parent topic and related topics.
 */
@Serializable
data class TopicHead(
    val key: String,
    val label: String,
    val supernova: Boolean = false,
    val status: String? = null,
    val intro: String? = null,
    val stats: TopicStats? = null,
    val rubric: Category? = null,
    val parent: TopicRef? = null,
    val related: List<TopicRef> = emptyList(),
    /**
     * A burning topic whose supernova is one story (the price rise, not every Raspberry Pi 5 add-on): the ids of that
     * story's posts, which page 1 lists first; the rest of the topic follows under “More on …”. Empty otherwise.
     */
    val story: List<Long> = emptyList(),
    /** “Discussed on” for burning topics and supernovas with an own post. */
    val discussions: List<Discussion> = emptyList(),
    /** Public replies to the own supernova posts, oldest first. */
    val replies: List<FediReply> = emptyList(),
)

@Serializable
data class TopicStats(
    val items: Int = 0,
    val sources: Int = 0,
    @Serializable(InstantSerializer::class) val first: Instant? = null,
)

/** `topics`: “In orbit” (the website's trend bar) and the evergreen topics with their own page. */
@Serializable
data class TopicsAnswer(val orbit: List<OrbitTopic> = emptyList(), val topics: List<EvergreenTopic> = emptyList())

@Serializable
data class OrbitTopic(val key: String, val label: String, val supernova: Boolean = false, val status: String? = null) {
    val ref: TopicRef get() = TopicRef(key, label, supernova)
}

@Serializable
data class EvergreenTopic(val key: String, val label: String, @SerialName("posts_30d") val posts30d: Int = 0) {
    val ref: TopicRef get() = TopicRef(key, label)
}

/** `sources`: the source directory (“The solar system”), grouped as on the website. */
@Serializable
data class SourcesAnswer(val groups: List<SourceGroup> = emptyList())

@Serializable
data class SourceGroup(val key: String, val name: String, val sources: List<Source> = emptyList())

/**
 * `front`: the website's start page as a front page – lead story by weight (sources, stars, freshness; picked by
 * nobody, not for sale) with three side stories, rubric modules, “From the projects” and videos.
 */
@Serializable
data class FrontPage(
    val lead: Item? = null,
    val side: List<Item> = emptyList(),
    val rubrics: List<RubricModule> = emptyList(),
    val projects: List<Item> = emptyList(),
    val videos: List<Item> = emptyList(),
)

@Serializable
data class RubricModule(val rubric: Category, val items: List<Item> = emptyList())

/** Answer of `weekly` and `weekly/<YYYY-WW>`: one finished calendar week, as the website's /rueckblick/. */
@Serializable
data class WeeklyReview(
    val week: String,
    val label: String,
    /** Opening paragraph, built from the week's numbers in fixed sentences (no AI text); in the UI language. */
    val lede: String? = null,
    val numbers: WeekNumbers = WeekNumbers(),
    val supernovas: List<WeekNova> = emptyList(),
    /** The biggest stories; the first is the cover story. */
    val stories: List<WeekStory> = emptyList(),
    /** Versions first seen this week that at least two sources covered. */
    val releases: List<WeekRelease> = emptyList(),
    /** For the busiest sections, the post most sources picked up. */
    val rubrics: List<WeekRubric> = emptyList(),
    /** Posts per day, Monday to Sunday. */
    val days: List<WeekDay> = emptyList(),
    val sections: List<WeekSection> = emptyList(),
    @SerialName("top_sources") val topSources: List<WeekSource> = emptyList(),
    val texts: WeekTexts = WeekTexts(),
    val topics: List<WeekTopic> = emptyList(),
    val videos: List<Item> = emptyList(),
    val starred: List<Item> = emptyList(),
    /** The last finished weeks (YYYY-WW), newest first. */
    val archive: List<String> = emptyList(),
    /** Logbook entries of this week (Tuesday to the Monday after, each 6:00 Berlin); empty before the logbook existed. */
    val logbook: List<WeekLogDay> = emptyList(),
)

/** One logbook entry in a week's review: its day, headline and how many supernovas and comets it had. */
@Serializable
data class WeekLogDay(val day: String, val headline: String, val novas: Int = 0, val comets: Int = 0, val cover: Boolean = false)

@Serializable
data class WeekNumbers(
    val items: Int = 0,
    val sources: Int = 0,
    val de: Int = 0,
    val en: Int = 0,
    val videos: Int = 0,
    @SerialName("prev_items") val prevItems: Int = 0,
    /** Change against the week before in percent; null without a week to compare. */
    val trend: Int? = null,
    val supernovas: Int = 0,
)

/**
 * A topic that went supernova this week. [cover]: it belongs to the cover story and is shown there; [card]: shown as a card
 * with [image] and [quote]; the others only as a chip.
 */
@Serializable
data class WeekNova(
    val key: String,
    val label: String,
    val items: Int = 0,
    val sources: Int = 0,
    val card: Boolean = false,
    val cover: Boolean = false,
    val image: ItemImage? = null,
    val quote: WeekQuote? = null,
)

/** Hint to last week's review on Latest: the week, its numbers, the cover story and a picture. */
@Serializable
data class WeekTeaser(
    val week: String,
    val items: Int = 0,
    val sources: Int = 0,
    val supernovas: Int = 0,
    val story: String? = null,
    val image: ItemImage? = null,
)

@Serializable
data class WeekQuote(val text: String, val by: String = "")

/** One of the week's biggest stories: a bundle of at least three sources. */
@Serializable
data class WeekStory(
    val id: Long,
    val name: String,
    val title: String,
    val url: String,
    val sources: Int = 0,
    val langs: SourceLangs = SourceLangs(),
    val lead: Item? = null,
    val supernova: TopicRef? = null,
    /** Other sources' reports, one per source, without the lead. */
    val reports: List<WeekReport> = emptyList(),
)

@Serializable
data class WeekReport(val source: String, val logo: String? = null, val color: String? = null, val title: String, val url: String, val lang: String)

@Serializable
data class WeekRelease(val key: String, val label: String, val entity: String, val version: String? = null, val sources: Int = 0)

@Serializable
data class WeekRubric(val slug: String, val name: String, val items: Int = 0, val item: Item)

@Serializable
data class WeekDay(val day: String, val weekday: String, val items: Int = 0)

@Serializable
data class WeekSection(val slug: String, val name: String, val items: Int = 0)

@Serializable
data class WeekSource(val slug: String, val name: String, val logo: String? = null, val color: String? = null, val items: Int = 0)

/** One sentence each about the days, sections and sources, in the UI language; empty when there is too little to say. */
@Serializable
data class WeekTexts(val days: String = "", val sections: String = "", val sources: String = "")

@Serializable
data class WeekTopic(val key: String, val label: String, val items: Int = 0, val comet: Boolean = false)

/** Answer of `meta`: settings from PlanetFOSS's backend the app needs. */
@Serializable
data class Meta(val donate: Donate? = null)

/** PlanetFOSS's own donation link; null while it is switched off in the backend. */
@Serializable
data class Donate(val url: String, val name: String = "")

/** Hint to the newest logbook entry on Latest. */
@Serializable
data class LogbookTeaser(val day: String, val headline: String, val supernovas: Int = 0, val comets: Int = 0)

/**
 * Answer of `logbook` and `logbook/<YYYY-MM-DD>`: one morning's entry, as the website's /logbuch/ – the 24 hours up to 6:00
 * Berlin time. Written once at 6:00 and not changed afterwards; [headline] and [lede] come in fixed sentences (no AI text).
 */
@Serializable
data class LogbookEntry(
    val day: String,
    /** End of the 24 hours the entry covers (6:00 Berlin of its day); posts after it are not in the entry. */
    @Serializable(InstantSerializer::class) val to: Instant? = null,
    /** “Thursday, 5 November 2026”, in the UI language. */
    val label: String,
    val url: String,
    val headline: String,
    val lede: String = "",
    /** Neither supernova nor comet nor release: the entry says so instead of filling up. */
    val quiet: Boolean = false,
    val numbers: LogNumbers = LogNumbers(),
    val story: LogStory? = null,
    val supernovas: List<LogNova> = emptyList(),
    val comets: List<LogComet> = emptyList(),
    val releases: List<LogRelease> = emptyList(),
    val also: List<Item> = emptyList(),
    val starred: List<Item> = emptyList(),
    /** Days with an entry, newest first. */
    val archive: List<String> = emptyList(),
    /** Mondays only: the week that just ended. */
    @SerialName("week_done") val weekDone: LogWeekDone? = null,
)

@Serializable
data class LogNumbers(val items: Int = 0, val sources: Int = 0, val de: Int = 0, val en: Int = 0)

/** The biggest story of the entry: [sources] in the 24 hours, [langs] of the whole bundle. */
@Serializable
data class LogStory(val id: Long, val name: String, val sources: Int = 0, val langs: SourceLangs = SourceLangs(), val lead: Item? = null)

/** A supernova of the entry: [new] = ignited in these 24 hours, otherwise still shining with [newReports] new reports. */
@Serializable
data class LogNova(
    val key: String,
    val label: String,
    val new: Boolean = false,
    @SerialName("new_reports") val newReports: Int = 0,
    val items: Int = 0,
    val sources: Int = 0,
    val langs: SourceLangs = SourceLangs(),
    val image: ItemImage? = null,
    val quote: WeekQuote? = null,
    val reports: List<Item> = emptyList(),
)

@Serializable
data class LogComet(val key: String, val label: String, val items: Int = 0, val sources: Int = 0, val reports: List<Item> = emptyList())

@Serializable
data class LogRelease(
    val key: String,
    val label: String,
    val entity: String,
    val version: String? = null,
    val sources: Int = 0,
    val supernova: Boolean = false,
    val comet: Boolean = false,
)

@Serializable
data class LogWeekDone(
    val week: String,
    val items: Int = 0,
    val sources: Int = 0,
    val supernovas: Int = 0,
    val story: String? = null,
    @SerialName("story_sources") val storySources: Int = 0,
)
