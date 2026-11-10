// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.list

import net.planetfoss.core.api.Comet
import net.planetfoss.core.api.ContentLang
import net.planetfoss.core.api.LogbookTeaser
import net.planetfoss.core.api.OrbitTopic
import net.planetfoss.core.api.Supernova
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Rules of the Latest tab (docs/09-latest-tab.md): what stands on top and where the logbook goes. Pure functions,
 * so they are tested without Compose.
 */
object Latest {
    val Berlin: ZoneId = ZoneId.of("Europe/Berlin")

    /** The logbook appears at 6:00 Berlin time and covers the 24 hours before. */
    fun logbookAt(day: String): Instant? =
        runCatching { LocalDate.parse(day).atTime(6, 0).atZone(Berlin).toInstant() }.getOrNull()

    /**
     * D-L3: the entry stands on top as a card while it is today's, unread and it is not yet noon; afterwards it is a
     * marker at its 6:00 between the posts.
     */
    fun logbookAsCard(t: LogbookTeaser?, read: String, now: ZonedDateTime = ZonedDateTime.now(Berlin)): Boolean {
        if (t == null || t.day == read) return false
        val here = now.withZoneSameInstant(Berlin)
        return here.toLocalDate().toString() == t.day && here.hour < 12
    }

    /** Where the logbook marker goes: before the first post that is not newer than 6:00; null when it would not sit between posts. */
    fun logbookIndex(published: List<Instant>, at: Instant): Int? =
        published.indexOfFirst { !it.isAfter(at) }.takeIf { it > 0 }

    /** D-L4: burning supernovas, newest first – the first as the big card, the others compact. */
    fun burning(novas: List<Supernova>): List<Supernova> = novas.filter { it.burning }.sortedByDescending { it.ignitedAt }

    /** Supernovas of the last seven days that no longer burn: the chip row “This week in the sky”. */
    fun cooled(novas: List<Supernova>): List<Supernova> = novas.filterNot { it.burning }.sortedByDescending { it.ignitedAt }

    /**
     * “Rising”: comets of the last 24 hours first, then the orbit topics; nothing twice and nothing that already
     * stands on top as a burning supernova.
     */
    fun rising(comets: List<Comet>, orbit: List<OrbitTopic>, burning: List<Supernova>): List<OrbitTopic> {
        val taken = burning.map { it.key }.toMutableSet()
        val out = mutableListOf<OrbitTopic>()
        comets.forEach { c -> if (taken.add(c.key)) out += OrbitTopic(c.key, c.label, supernova = false, status = "komet") }
        orbit.forEach { o -> if (taken.add(o.key)) out += o }
        return out
    }

    /**
     * D-L2: a supernova without a report in the chosen content language still shows, with a note. Returns the language
     * the reports are in ("en" or "de") when none is in [lang]; null when there is nothing to say.
     */
    fun otherLanguageOnly(nova: Supernova, lang: ContentLang): String? {
        val want = when (lang) {
            ContentLang.DE -> "de"
            ContentLang.EN -> "en"
            else -> return null
        }
        if (nova.reports.isEmpty() || nova.reports.any { it.lang == want }) return null
        return if (want == "de") "en" else "de"
    }
}
