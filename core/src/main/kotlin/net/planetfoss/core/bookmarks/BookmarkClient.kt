// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.core.bookmarks

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

/**
 * The reader's own bookmark service (docs/10-bookmark-services.md, phase 1). All four take a token in a header and one
 * JSON call to create a bookmark; they differ in path, header prefix and field names. [id] is what the settings store.
 */
enum class BookmarkService(val id: String, val label: String) {
    KARAKEEP("karakeep", "Karakeep"),
    LINKDING("linkding", "linkding"),
    LINKWARDEN("linkwarden", "Linkwarden"),
    READECK("readeck", "Readeck"),
    ;

    companion object {
        fun of(id: String?): BookmarkService? = entries.firstOrNull { it.id == id }
    }
}

/** Where to send bookmarks: the service, the address of the reader's instance and its API token. */
data class BookmarkTarget(val service: BookmarkService, val base: HttpUrl, val token: String)

/** What one call to the service came to, in the terms the app shows (and the send job retries on). */
sealed interface BookmarkResult {
    data object Ok : BookmarkResult

    /** 401/403: wrong or expired token. Retrying does not help. */
    data object Unauthorized : BookmarkResult

    /** 404/405 or an answer that is not the service's JSON: wrong address, wrong service or a login page in front. */
    data object WrongAddress : BookmarkResult

    /** Any other 4xx: the service refused this bookmark. */
    data class Refused(val code: Int) : BookmarkResult

    /** 5xx, 429, no connection, timeout, TLS: worth trying again later. [code] only for [Why.SERVER]. */
    data class Unreachable(val why: Why, val code: Int = 0) : BookmarkResult

    /** Why the service could not be reached, in words a reader can act on. */
    enum class Why { UNKNOWN_HOST, NO_CONNECTION, TIMEOUT, CERTIFICATE, SERVER, OTHER }

    /** True for failures a later attempt may get past. */
    val retryable: Boolean get() = this is Unreachable
}

/**
 * Creates bookmarks in the reader's service. Talks only to the address the reader entered; planetfoss.net never sees
 * the token or the bookmark. [tags] go with the bookmark (Karakeep needs a second call for them; a failure there does
 * not undo the bookmark).
 */
class BookmarkClient(private val client: OkHttpClient, private val json: Json = Json { ignoreUnknownKeys = true }) {
    /** One harmless read call that needs the token: tells a wrong address apart from a wrong token. */
    suspend fun test(target: BookmarkTarget): BookmarkResult {
        val path = when (target.service) {
            BookmarkService.KARAKEEP -> "api/v1/users/me"
            BookmarkService.LINKDING -> "api/user/profile/"
            BookmarkService.LINKWARDEN -> "api/v1/tags"
            BookmarkService.READECK -> "api/profile"
        }
        return call(target, request(target, path).get().build()) { body -> body.isJsonObject() }
    }

    suspend fun save(target: BookmarkTarget, url: String, title: String, tags: List<String>): BookmarkResult {
        val body = when (target.service) {
            BookmarkService.KARAKEEP -> buildJsonObject {
                put("type", "link")
                put("url", url)
                put("title", title)
            }
            BookmarkService.LINKDING -> buildJsonObject {
                put("url", url)
                put("title", title)
                putJsonArray("tag_names") { tags.forEach { add(it) } }
            }
            BookmarkService.LINKWARDEN -> buildJsonObject {
                put("url", url)
                put("name", title)
                putJsonArray("tags") { tags.forEach { addJsonObject { put("name", it) } } }
            }
            BookmarkService.READECK -> buildJsonObject {
                put("url", url)
                put("title", title)
                putJsonArray("labels") { tags.forEach { add(it) } }
            }
        }
        val path = when (target.service) {
            BookmarkService.KARAKEEP -> "api/v1/bookmarks"
            BookmarkService.LINKDING -> "api/bookmarks/"
            BookmarkService.LINKWARDEN -> "api/v1/links"
            BookmarkService.READECK -> "api/bookmarks"
        }
        var karakeepId: String? = null
        val result = call(target, request(target, path).post(body.toString().toRequestBody(JSON)).build()) { text ->
            // Readeck answers 202 with a short status object; the others the new bookmark
            if (target.service == BookmarkService.KARAKEEP) {
                karakeepId = runCatching { json.parseToJsonElement(text).jsonObject["id"]?.jsonPrimitive?.content }.getOrNull()
                karakeepId != null
            } else {
                text.isBlank() || text.isJsonObject()
            }
        }
        val id = karakeepId
        if (result == BookmarkResult.Ok && id != null && tags.isNotEmpty()) {
            val tagBody = buildJsonObject { putJsonArray("tags") { tags.forEach { addJsonObject { put("tagName", it) } } } }
            // best effort: the bookmark is there, tags are a bonus
            call(target, request(target, "api/v1/bookmarks/$id/tags").post(tagBody.toString().toRequestBody(JSON)).build()) { true }
        }
        return result
    }

    private fun request(target: BookmarkTarget, path: String): Request.Builder {
        val url = target.base.newBuilder().addPathSegments(path).build()
        val prefix = if (target.service == BookmarkService.LINKDING) "Token" else "Bearer"
        return Request.Builder().url(url)
            .header("Authorization", "$prefix ${target.token}")
            .header("Accept", "application/json")
    }

    private suspend fun call(target: BookmarkTarget, request: Request, accept: (String) -> Boolean): BookmarkResult =
        withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { answer(it, accept) }
            } catch (e: IOException) {
                BookmarkResult.Unreachable(why(e))
            }
        }

    private fun answer(response: Response, accept: (String) -> Boolean): BookmarkResult = when (val code = response.code) {
        in 200..299 -> if (accept(response.body.string())) BookmarkResult.Ok else BookmarkResult.WrongAddress
        401, 403 -> BookmarkResult.Unauthorized
        404, 405 -> BookmarkResult.WrongAddress
        408, 429, in 500..599 -> BookmarkResult.Unreachable(BookmarkResult.Why.SERVER, code)
        else -> BookmarkResult.Refused(code)
    }

    private fun why(e: IOException): BookmarkResult.Why = when (e) {
        is java.net.UnknownHostException -> BookmarkResult.Why.UNKNOWN_HOST
        is java.net.ConnectException, is java.net.NoRouteToHostException -> BookmarkResult.Why.NO_CONNECTION
        is java.net.SocketTimeoutException, is java.io.InterruptedIOException -> BookmarkResult.Why.TIMEOUT
        is javax.net.ssl.SSLException -> BookmarkResult.Why.CERTIFICATE
        else -> BookmarkResult.Why.OTHER
    }

    private fun String.isJsonObject(): Boolean = runCatching { json.parseToJsonElement(this) is JsonObject }.getOrDefault(false)

    companion object {
        private val JSON = "application/json".toMediaType()

        /**
         * The address as the reader typed it, as a base for the API paths: scheme added when missing (https), query,
         * fragment and a trailing `api/…` cut off, always ending in `/`. Null when it is no web address, or plain HTTP
         * while [allowHttp] is false.
         */
        fun parseAddress(input: String, allowHttp: Boolean = false): HttpUrl? {
            val text = input.trim().let { if ("://" in it) it else "https://$it" }
            val url = text.toHttpUrlOrNull() ?: return null
            if (url.scheme != "https" && !allowHttp) return null
            if (url.host.isBlank()) return null
            val segments = url.pathSegments.filter { it.isNotEmpty() }.let { s ->
                val api = s.indexOf("api")
                if (api >= 0) s.take(api) else s
            }
            return url.newBuilder().query(null).fragment(null).encodedPath("/")
                .apply { segments.forEach { addPathSegment(it) } }
                .addPathSegment("")
                .build()
        }

        /** A tag as the services accept it: lower case, letters, digits and dashes only (linkding refuses spaces). */
        fun tag(name: String): String? = name.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').takeIf { it.isNotEmpty() }
    }
}
