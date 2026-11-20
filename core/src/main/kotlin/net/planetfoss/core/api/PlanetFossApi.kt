// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.core.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * What a list request is for, so planetfoss.net counts only views (like page views on the web): [VIEW] a screen the
 * user opened, [PART] only a part of a screen (Latest's Sternstunden, orbit and supernova band – like a page's images),
 * [BACKGROUND] a check of its own (supernova notifications, followed topics), even while the app is open. [RADAR]: the
 * one request per opening of Radar that stands for the screen – Radar asks for every followed list, and each of them
 * counted as a view of its own before; the others are [PART].
 */
enum class Use(val header: String?, val value: String = "1") {
    VIEW(null),
    PART("X-PlanetFOSS-Part"),
    BACKGROUND("X-PlanetFOSS-Background"),
    RADAR("X-PlanetFOSS-Screen", "radar"),
}

/** Why a request failed, in the terms the UI shows. */
sealed class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** No connection, DNS, timeout, TLS. */
    class Network(cause: IOException) : ApiException(cause.message ?: "network", cause)

    /** The server answered, but not with 2xx. */
    class Http(val code: Int) : ApiException("HTTP $code")

    /** 2xx, but the body is not what v1 promises. */
    class Malformed(cause: Throwable) : ApiException(cause.message ?: "malformed", cause)
}

/** JSON settings shared by the client and the local cache: unknown fields are fine (v1 may grow). */
val ApiJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/**
 * Client for `/api/app/v1/` on planetfoss.net. Plain OkHttp: its HTTP cache
 * handles ETag/304, and there are few enough endpoints that Retrofit would only
 * add a layer. The client sends no identifiers – only the parameters below.
 */
class PlanetFossApi(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl,
    private val json: Json = ApiJson,
) {
    suspend fun home(lang: ContentLang, ui: String, page: Int = 1): ItemPage =
        get("home", mapOf("lang" to lang.param, "ui" to ui, "page" to page.toString()), ItemPage.serializer())

    /** `pending=1`: also supernovas not yet announced (ignited less than 30 min ago), marked `announced = false`. */
    suspend fun supernovas(lang: ContentLang, ui: String, use: Use = Use.VIEW): SupernovaList =
        get("supernovas", mapOf("lang" to lang.param, "ui" to ui, "pending" to "1"), SupernovaList.serializer(), use)

    suspend fun rubrics(ui: String): RubricList = get("rubrics", mapOf("ui" to ui), RubricList.serializer())

    /** “In orbit” and the evergreen topics (website: trend bar and /themen/). */
    suspend fun topics(lang: ContentLang, ui: String, use: Use = Use.VIEW): TopicsAnswer =
        get("topics", mapOf("lang" to lang.param, "ui" to ui), TopicsAnswer.serializer(), use)

    /** The front page: lead story and side stories by weight, rubric modules, projects, videos (website: start page). */
    suspend fun front(lang: ContentLang, ui: String): FrontPage = get("front", mapOf("lang" to lang.param, "ui" to ui), FrontPage.serializer())

    /** The source directory in groups (website: /quellen/). */
    suspend fun sources(lang: ContentLang, ui: String): SourcesAnswer = get("sources", mapOf("lang" to lang.param, "ui" to ui), SourcesAnswer.serializer())

    /** Items of one rubric, topic, source or the videos, with the head of that list. */
    suspend fun list(target: ListTarget, lang: ContentLang, ui: String, page: Int = 1, use: Use = Use.VIEW): ListAnswer =
        get(target.endpoint, mapOf("lang" to lang.param, "ui" to ui, "page" to page.toString()), ListAnswer.serializer(), use)

    /** A finished week (website: /rueckblick/); [week] null = the last finished one. Weeks are YYYY-WW. */
    suspend fun weekly(week: String?, lang: ContentLang, ui: String): WeeklyReview {
        require(week == null || WEEK.matches(week)) { "bad week" }
        return get(if (week == null) "weekly" else "weekly/$week", mapOf("lang" to lang.param, "ui" to ui), WeeklyReview.serializer())
    }

    /** One logbook entry (website: /logbuch/); [day] null = the newest. Days are YYYY-MM-DD. */
    suspend fun logbook(day: String?, lang: ContentLang, ui: String, use: Use = Use.VIEW): LogbookEntry {
        require(day == null || DAY.matches(day)) { "bad day" }
        return get(if (day == null) "logbook" else "logbook/$day", mapOf("lang" to lang.param, "ui" to ui), LogbookEntry.serializer(), use)
    }

    /** Backend settings the app follows (PlanetFOSS's donation link). */
    suspend fun meta(): Meta = get("meta", emptyMap(), Meta.serializer())

    suspend fun search(query: String, lang: ContentLang, ui: String, use: Use = Use.VIEW): SearchResult =
        get("search", mapOf("q" to query, "lang" to lang.param, "ui" to ui), SearchResult.serializer(), use)

    /**
     * The sponsor card candidates of both app slots. A part of a screen like Latest's Sternstunden, so planetfoss.net counts
     * neither a view nor a start for it.
     */
    suspend fun ads(ui: String): AdsAnswer = get("ads", mapOf("ui" to ui), AdsAnswer.serializer(), Use.PART)

    /** Report that a sponsor card was seen (at least half of it on screen): its id, slot and language – nothing else. */
    suspend fun adView(ad: Ad) {
        val url = baseUrl.newBuilder().addPathSegments("api/ad-view").build()
        val body = """{"views":[{"id":${ad.id},"slot":${json.encodeToString(ad.slot)},"lang":${json.encodeToString(ad.lang)}}]}"""
        client.newCall(Request.Builder().url(url).post(body.toRequestBody(JSON_TYPE)).build()).await().close()
    }

    /**
     * Report that an article was opened, for the sources' reach numbers: its address, where in the app
     * ([place]: liste, supernova, buendel) and the content language – no identifier, counted as a daily total.
     */
    suspend fun opened(url: String, place: String, lang: ContentLang) {
        val target = baseUrl.newBuilder().addPathSegments("api/app-open").build()
        val body = """{"u":${json.encodeToString(url)},"place":${json.encodeToString(place)},"lang":${json.encodeToString(lang.param)}}"""
        client.newCall(Request.Builder().url(target).post(body.toRequestBody(JSON_TYPE)).build()).await().close()
    }

    /** A star task for [itemId] (`/api/star-challenge`); `nonce` is null for an item the server does not know. */
    suspend fun starChallenge(itemId: Long): StarChallenge =
        call(Request.Builder().url(baseUrl.newBuilder().addPathSegments("api/star-challenge").addQueryParameter("id", itemId.toString()).build()).build(),
            StarChallenge.serializer())

    /** Star [itemId] with a solved task (`POST /api/star`). Sends the item, the task and the solution – nothing else. */
    suspend fun star(itemId: Long, nonce: String, solution: Long): StarAnswer =
        post("api/star", """{"id":$itemId,"nonce":${json.encodeToString(nonce)},"solution":"$solution"}""")

    /** Take a star back with the token from [star] (`POST /api/unstar`). */
    suspend fun unstar(itemId: Long, token: String): StarAnswer =
        post("api/unstar", """{"id":$itemId,"token":${json.encodeToString(token)}}""")

    private suspend fun post(path: String, body: String): StarAnswer =
        call(Request.Builder().url(baseUrl.newBuilder().addPathSegments(path).build()).post(body.toRequestBody(JSON_TYPE)).build(), StarAnswer.serializer())

    private suspend fun <T> call(request: Request, strategy: DeserializationStrategy<T>): T {
        val body = client.newCall(request).await().use { response ->
            if (!response.isSuccessful) throw ApiException.Http(response.code)
            withContext(Dispatchers.IO) {
                try {
                    response.body.string()
                } catch (e: IOException) {
                    throw ApiException.Network(e)
                }
            }
        }
        return try {
            json.decodeFromString(strategy, body)
        } catch (e: SerializationException) {
            throw ApiException.Malformed(e)
        } catch (e: IllegalArgumentException) {
            throw ApiException.Malformed(e)
        } catch (e: java.time.format.DateTimeParseException) {
            throw ApiException.Malformed(e)
        }
    }

    private suspend fun <T> get(endpoint: String, params: Map<String, String>, strategy: DeserializationStrategy<T>, use: Use = Use.VIEW): T {
        val url = baseUrl.newBuilder().addPathSegments("api/app/v1").addPathSegments(endpoint).apply {
            params.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        return call(Request.Builder().url(url).apply { use.header?.let { header(it, use.value) } }.build(), strategy)
    }
}

private val JSON_TYPE = "application/json".toMediaType()

/** A calendar week as the API names it. */
private val WEEK = Regex("\\d{4}-\\d{2}")

/** A logbook day as the API names it. */
private val DAY = Regex("\\d{4}-\\d{2}-\\d{2}")

/** Enqueue the call and suspend until it answers; cancelling the coroutine cancels the call. */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) = cont.resume(response) { _, r, _ -> r.close() }
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(ApiException.Network(e))
        }
    })
    cont.invokeOnCancellation { cancel() }
}
