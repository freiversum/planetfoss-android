// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.core.adblock

/** Why a list was rejected; the previous list stays active. */
class InvalidFilterList(message: String) : Exception(message)

/** Counts of one parse, for the settings screen and the validation. */
data class ParseStats(val network: Int, val exceptions: Int, val skipped: Int, val cosmetic: Int)

/**
 * The parsed rules of one or more lists, indexed for fast lookups:
 * plain `||host^` rules in a host map (walk the host's labels), all others under one
 * literal token of their pattern (looked up by the URL's tokens).
 */
class FilterSet private constructor(
    /** `||host^` without options – the bulk of both lists, kept as bare strings to save memory. */
    private val plainHosts: Set<String>,
    private val hostBlocks: Map<String, List<FilterRule>>,
    private val tokenBlocks: Map<String, List<FilterRule>>,
    private val untokenBlocks: List<FilterRule>,
    private val exceptions: Map<String, List<FilterRule>>,
    private val untokenExceptions: List<FilterRule>,
    private val documentExceptions: List<FilterRule>,
    val stats: ParseStats,
) {
    /** The rule that blocks [req], or null to let it through. */
    fun match(req: FilterRequest): FilterRule? {
        val block = findBlock(req) ?: return null
        if (block.important) return block
        if (req.pageHost != null && pageAllowed(req.pageHost)) return null
        if (findIn(exceptions, untokenExceptions, req) != null) return null
        return block
    }

    /** `@@||site^$document`: the list itself switches blocking off for that page. */
    fun pageAllowed(pageHost: String): Boolean {
        val pageUrl = "https://$pageHost/"
        return documentExceptions.any { Glob.matches(it, pageUrl) && (it.includeDomains.isEmpty() || it.includeDomains.any { d -> Domains.isSameOrSub(pageHost, d) }) }
    }

    private fun findBlock(req: FilterRequest): FilterRule? {
        var h = req.host.lowercase()
        while (true) {
            if (h in plainHosts) return plainHostRule(h)
            hostBlocks[h]?.firstOrNull { it.appliesTo(req) }?.let { return it }
            val dot = h.indexOf('.')
            if (dot < 0) break
            h = h.substring(dot + 1)
        }
        return findIn(tokenBlocks, untokenBlocks, req)
    }

    private fun findIn(index: Map<String, List<FilterRule>>, rest: List<FilterRule>, req: FilterRequest): FilterRule? {
        for (token in tokens(req.url.lowercase())) {
            index[token]?.firstOrNull { it.appliesTo(req) }?.let { return it }
        }
        return rest.firstOrNull { it.appliesTo(req) }
    }

    /** Rule object for a plain host hit, built only when something is blocked. */
    private fun plainHostRule(host: String) =
        FilterRule("||$host^", false, "$host^", true, false, false, null, null, emptySet(), emptySet(), false, false)

    companion object {
        /** Lists smaller than this many network rules are broken or not a filter list. */
        const val MIN_RULES = 1000

        private val KNOWN_TYPES = mapOf(
            "script" to ResourceType.SCRIPT, "image" to ResourceType.IMAGE, "stylesheet" to ResourceType.STYLESHEET,
            "xmlhttprequest" to ResourceType.XHR, "subdocument" to ResourceType.SUBDOCUMENT, "font" to ResourceType.FONT,
            "media" to ResourceType.MEDIA, "object" to ResourceType.OBJECT, "ping" to ResourceType.PING, "other" to ResourceType.OTHER,
        )

        /** Options that change what a rule does in ways we do not implement – such rules are skipped, never broadened. */
        private val UNSUPPORTED = setOf(
            "popup", "redirect", "redirect-rule", "rewrite", "csp", "removeparam", "method", "header", "permissions",
            "replace", "elemhide", "generichide", "genericblock", "specifichide", "websocket", "webrtc", "sitekey", "badfilter",
            "inline-script", "inline-font", "to", "denyallow", "ehide", "ghide", "shide", "frame", "all", "1p", "3p",
        )

        /**
         * Parse lists into one set. [validate]: reject a list that is not a filter list
         * (header) or yields fewer than [MIN_RULES] network rules.
         */
        fun parse(lists: List<String>, validate: Boolean = true): FilterSet {
            val plainHosts = HashSet<String>(131_072)
            val hostBlocks = HashMap<String, MutableList<FilterRule>>()
            val tokenBlocks = HashMap<String, MutableList<FilterRule>>()
            val untokenBlocks = ArrayList<FilterRule>()
            val exceptions = HashMap<String, MutableList<FilterRule>>()
            val untokenExceptions = ArrayList<FilterRule>()
            val documentExceptions = ArrayList<FilterRule>()
            var network = 0
            var exc = 0
            var skipped = 0
            var cosmetic = 0
            for (text in lists) {
                if (validate && !text.trimStart('﻿').startsWith("[Adblock Plus")) throw InvalidFilterList("no Adblock Plus header")
                var listNetwork = 0
                for (rawLine in text.lineSequence()) {
                    val line = rawLine.trim()
                    if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) continue
                    if (isCosmetic(line)) {
                        cosmetic++
                        continue
                    }
                    val rule = parseRule(line)
                    if (rule == null) {
                        skipped++
                        continue
                    }
                    listNetwork++
                    if (rule.exception) {
                        exc++
                        if (rule.document) documentExceptions += rule else index(rule, exceptions, untokenExceptions, null)
                    } else {
                        network++
                        if (isPlainHost(rule)) plainHosts += rule.pattern.removeSuffix("^").intern()
                        else index(rule, tokenBlocks, untokenBlocks, hostBlocks)
                    }
                }
                if (validate && listNetwork < MIN_RULES) throw InvalidFilterList("only $listNetwork network rules")
            }
            return FilterSet(plainHosts, hostBlocks, tokenBlocks, untokenBlocks, exceptions, untokenExceptions, documentExceptions, ParseStats(network, exc, skipped, cosmetic))
        }

        /** `||ads.example.com^` with no options at all. */
        private fun isPlainHost(rule: FilterRule): Boolean {
            if (!rule.hostAnchor || rule.endAnchor || rule.types != null || rule.thirdParty != null || rule.important) return false
            if (rule.includeDomains.isNotEmpty() || rule.excludeDomains.isNotEmpty() || !rule.pattern.endsWith("^")) return false
            val host = rule.pattern.dropLast(1)
            return host.isNotEmpty() && host.all { it.isLetterOrDigit() || it == '.' || it == '-' }
        }

        private fun isCosmetic(line: String): Boolean =
            line.contains("##") || line.contains("#@#") || line.contains("#?#") || line.contains("#$#") || line.contains("#%#")

        /** One line → rule, or null when it uses syntax we do not support (regex, unknown options). */
        internal fun parseRule(line: String): FilterRule? {
            var body = line
            val exception = body.startsWith("@@")
            if (exception) body = body.substring(2)
            if (body.startsWith("/") && body.endsWith("/") && body.length > 2) return null // regex rules: skipped in v1

            var types: MutableSet<ResourceType>? = null
            var notTypes: MutableSet<ResourceType>? = null
            var thirdParty: Boolean? = null
            var include: MutableSet<String>? = null
            var exclude: MutableSet<String>? = null
            var important = false
            var document = false
            val dollar = body.lastIndexOf('$')
            if (dollar >= 0 && dollar < body.length - 1 && !body.substring(dollar + 1).contains('/')) {
                for (opt in body.substring(dollar + 1).split(',')) {
                    val o = opt.trim().lowercase()
                    val neg = o.startsWith("~")
                    val name = o.removePrefix("~").substringBefore('=')
                    when {
                        name == "third-party" -> thirdParty = !neg
                        name == "domain" -> o.substringAfter('=').split('|').forEach { d ->
                            if (d.startsWith("~")) (exclude ?: HashSet<String>().also { exclude = it }) += d.substring(1)
                            else if (d.isNotEmpty()) (include ?: HashSet<String>().also { include = it }) += d
                        }
                        name == "important" -> important = true
                        name == "document" && exception -> document = true
                        name == "match-case" -> Unit
                        name in KNOWN_TYPES -> {
                            val t = KNOWN_TYPES.getValue(name)
                            if (neg) (notTypes ?: HashSet<ResourceType>().also { notTypes = it }) += t
                            else (types ?: HashSet<ResourceType>().also { types = it }) += t
                        }
                        name in UNSUPPORTED || name == "document" -> return null
                        else -> return null // unknown option: skip rather than guess
                    }
                }
                body = body.substring(0, dollar)
            }
            if (notTypes != null) types = (types ?: ResourceType.entries.toMutableSet()).apply { removeAll(notTypes!!) }

            var hostAnchor = false
            var startAnchor = false
            var endAnchor = false
            if (body.startsWith("||")) {
                hostAnchor = true
                body = body.substring(2)
            } else if (body.startsWith("|")) {
                startAnchor = true
                body = body.substring(1)
            }
            if (body.endsWith("|")) {
                endAnchor = true
                body = body.dropLast(1)
            }
            val pattern = body.lowercase().trim('*').ifEmpty { if (document) "" else return null }
            // a rule that would match everything (e.g. "*" or "^") is a list error: never block all requests
            if (!document && pattern.all { it == '*' || it == '^' }) return null
            return FilterRule(line, exception, pattern, hostAnchor, startAnchor, endAnchor, types, thirdParty, include ?: emptySet(), exclude ?: emptySet(), important, document)
        }

        private fun index(rule: FilterRule, tokenIndex: HashMap<String, MutableList<FilterRule>>, rest: MutableList<FilterRule>, hostIndex: HashMap<String, MutableList<FilterRule>>?) {
            // plain host rule: ||ads.example.com^ (optionally with options)
            if (hostIndex != null && rule.hostAnchor && !rule.endAnchor) {
                val host = rule.pattern.removeSuffix("^")
                if (host.isNotEmpty() && host.all { it.isLetterOrDigit() || it == '.' || it == '-' }) {
                    hostIndex.getOrPut(host) { ArrayList(1) } += rule
                    return
                }
            }
            val token = bestToken(rule.pattern, openStart = !rule.hostAnchor && !rule.startAnchor, openEnd = !rule.endAnchor)
            if (token == null) rest += rule else tokenIndex.getOrPut(token) { ArrayList(1) } += rule
        }

        /**
         * Longest run of letters/digits that is surely a whole URL token: not touching a `*`
         * (it could continue there) and not at an open end of the pattern.
         */
        private fun bestToken(pattern: String, openStart: Boolean, openEnd: Boolean): String? {
            var best: String? = null
            val runs = Regex("[a-z0-9%]{3,}").findAll(pattern)
            for (m in runs) {
                val before = m.range.first - 1
                val after = m.range.last + 1
                if (before >= 0 && pattern[before] == '*') continue
                if (after < pattern.length && pattern[after] == '*') continue
                if ((before < 0 && openStart) || (after >= pattern.length && openEnd)) continue
                if (best == null || m.value.length > best.length) best = m.value
            }
            return best
        }

        /** The tokens of a URL, as [bestToken] cuts them from patterns. */
        internal fun tokens(url: String): Sequence<String> = Regex("[a-z0-9%]{3,}").findAll(url).map { it.value }
    }
}
