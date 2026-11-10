// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.core.adblock

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** What a request loads, as far as the browser can tell. [OTHER] = unknown. */
enum class ResourceType { SCRIPT, IMAGE, STYLESHEET, XHR, SUBDOCUMENT, FONT, MEDIA, OBJECT, PING, OTHER }

/** One sub-resource request of a page. [pageHost] is null when unknown (service workers). */
data class FilterRequest(val url: String, val host: String, val pageHost: String?, val type: ResourceType) {
    /** Unknown page = unknown party; rules that need first/third party then do not apply. Computed once per request. */
    val thirdParty: Boolean? by lazy(LazyThreadSafetyMode.NONE) { pageHost?.let { Domains.registrable(it) != Domains.registrable(host) } }
}

/**
 * One network rule in Adblock Plus syntax (the subset EasyList/EasyPrivacy use for
 * requests). Patterns are matched by [Glob], never as regular expressions, so a list
 * can neither run code nor hang the browser with a pathological pattern.
 */
class FilterRule(
    val raw: String,
    val exception: Boolean,
    /** Pattern without the anchors below. */
    val pattern: String,
    /** `||host^…`: matches at the start of the host or after a dot of it. */
    val hostAnchor: Boolean,
    /** `|…`: matches at the start of the URL. */
    val startAnchor: Boolean,
    /** `…|`: matches at the end of the URL. */
    val endAnchor: Boolean,
    /** null = every type; else only these. */
    val types: Set<ResourceType>?,
    /** true = only third-party, false = only first-party, null = both. */
    val thirdParty: Boolean?,
    val includeDomains: Set<String>,
    val excludeDomains: Set<String>,
    /** `$important`: wins over exceptions. */
    val important: Boolean,
    /** `@@…$document`: switches blocking off for whole pages it matches. */
    val document: Boolean,
) {
    fun appliesTo(req: FilterRequest): Boolean {
        if (types != null && req.type !in types) return false
        if (thirdParty != null && req.thirdParty != thirdParty) return false
        if (includeDomains.isNotEmpty() || excludeDomains.isNotEmpty()) {
            val page = req.pageHost ?: return false
            if (excludeDomains.any { Domains.isSameOrSub(page, it) }) return false
            if (includeDomains.isNotEmpty() && includeDomains.none { Domains.isSameOrSub(page, it) }) return false
        }
        return Glob.matches(this, req.url)
    }

    override fun toString() = raw
}

/** Host helpers. The site of a host comes from the Public Suffix List that ships with OkHttp. */
object Domains {
    /** The site a host belongs to (“eTLD+1”): www.example.co.uk → example.co.uk, a.github.io → a.github.io. */
    fun registrable(host: String): String {
        val h = host.lowercase().trimEnd('.')
        return "https://$h/".toHttpUrlOrNull()?.topPrivateDomain() ?: h
    }

    fun isSameOrSub(host: String, domain: String): Boolean = host == domain || host.endsWith(".$domain")
}

/**
 * Adblock Plus glob: `*` = any characters, `^` = a separator (anything but a letter, digit,
 * `_ - . %`, or the end of the URL). Iterative with backtracking on the last `*` only, so
 * matching is linear-ish and cannot explode.
 */
internal object Glob {
    private fun isSeparator(c: Char) = !(c.isLetterOrDigit() || c == '_' || c == '-' || c == '.' || c == '%')

    fun matches(rule: FilterRule, url: String): Boolean {
        val lower = url.lowercase()
        if (rule.hostAnchor) {
            // candidates: start of the host and after every dot inside the host
            val hostStart = lower.indexOf("://").let { if (it < 0) 0 else it + 3 }
            val hostEnd = lower.indexOfAny(charArrayOf('/', '?', '#', ':'), hostStart).let { if (it < 0) lower.length else it }
            var i = hostStart
            while (i < hostEnd) {
                if ((i == hostStart || lower[i - 1] == '.') && matchAt(rule.pattern, lower, i, rule.endAnchor)) return true
                i++
            }
            return false
        }
        if (rule.startAnchor) return matchAt(rule.pattern, lower, 0, rule.endAnchor)
        for (start in 0..lower.length) {
            if (matchAt(rule.pattern, lower, start, rule.endAnchor)) return true
        }
        return false
    }

    /** Does [pattern] match [text] starting at [from]? Without [mustEnd] the rest of the text may follow. */
    fun matchAt(pattern: String, text: String, from: Int, mustEnd: Boolean): Boolean {
        var p = 0
        var t = from
        var starP = -1
        var starT = -1
        while (true) {
            if (p == pattern.length) {
                if (!mustEnd || t == text.length) return true
            } else {
                val pc = pattern[p]
                when {
                    pc == '*' -> {
                        starP = p
                        starT = t
                        p++
                        continue
                    }
                    pc == '^' && (t == text.length || isSeparator(text[t])) -> {
                        p++
                        if (t < text.length) t++
                        continue
                    }
                    t < text.length && pc == text[t] -> {
                        p++
                        t++
                        continue
                    }
                }
            }
            // mismatch: let the last * swallow one more character
            if (starP < 0 || starT >= text.length) return false
            starT++
            t = starT
            p = starP + 1
        }
    }
}
