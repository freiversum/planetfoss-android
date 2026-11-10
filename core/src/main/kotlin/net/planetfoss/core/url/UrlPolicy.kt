// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.core.url

import java.net.URI
import java.net.URLDecoder

/** What to do with a link the internal browser is asked to follow. */
sealed interface UrlDecision {
    /** http(s) and about:blank: load in the WebView. */
    data class LoadInside(val url: String) : UrlDecision

    /** Another app's scheme (mailto:, tel:, geo:, market:, …): only after the user confirms. */
    data class AskToOpenOutside(val url: String, val scheme: String) : UrlDecision

    /** Never: javascript:, file:, content:, data:, blob:, intent: without a web fallback, broken URLs. */
    data object Block : UrlDecision
}

/**
 * Link rules for the internal browser and for opening articles. Pure, so they are tested
 * without Android. Web pages never reach local files, content providers or other apps
 * on their own; another app only opens after the user said yes.
 */
object UrlPolicy {
    private val BLOCKED = setOf("javascript", "file", "content", "data", "blob", "about", "chrome", "view-source", "ws", "wss")

    /** Article links from the API: only http(s) with a host. */
    fun isWebUrl(url: String): Boolean {
        val uri = parse(url) ?: return false
        return (uri.scheme == "https" || uri.scheme == "http") && !uri.host.isNullOrEmpty()
    }

    fun decide(url: String): UrlDecision {
        if (url == "about:blank") return UrlDecision.LoadInside(url)
        val uri = parse(url) ?: return UrlDecision.Block
        val scheme = uri.scheme?.lowercase() ?: return UrlDecision.Block
        return when {
            scheme == "https" || scheme == "http" -> if (uri.host.isNullOrEmpty()) UrlDecision.Block else UrlDecision.LoadInside(url)
            scheme == "intent" -> intentFallback(url)?.let { UrlDecision.LoadInside(it) } ?: UrlDecision.Block
            scheme in BLOCKED -> UrlDecision.Block
            // a scheme must be letters/digits/+.- – anything else is not a link we hand on
            !scheme.matches(Regex("[a-z][a-z0-9+.-]{0,31}")) -> UrlDecision.Block
            else -> UrlDecision.AskToOpenOutside(url, scheme)
        }
    }

    /** Host for the address line: without “www.”, lower case. */
    fun displayHost(url: String): String =
        parse(url)?.host?.lowercase()?.removePrefix("www.").orEmpty()

    fun isSecure(url: String): Boolean = parse(url)?.scheme?.lowercase() == "https"

    /** http → https (same host, path, query); the internal browser never loads plain HTTP. Other URLs unchanged. */
    fun upgrade(url: String): String =
        if (parse(url)?.scheme?.lowercase() == "http") "https" + url.trim().substring(4) else url

    /**
     * Android intent: links (intent://…#Intent;…;S.browser_fallback_url=…;end) are never
     * launched as intents from web content; only their web fallback is used, if it is http(s).
     */
    private fun intentFallback(url: String): String? {
        val extras = url.substringAfter("#Intent;", "").substringBefore(";end")
        val raw = extras.split(';').firstOrNull { it.startsWith("S.browser_fallback_url=") }?.substringAfter('=') ?: return null
        val fallback = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrNull() ?: return null
        return fallback.takeIf { isWebUrl(it) }
    }

    private fun parse(url: String): URI? = runCatching { URI(url.trim()) }.getOrNull()
}
