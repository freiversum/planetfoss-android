// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.core.bookmarks

import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Against real instances (local containers, docs/10-bookmark-services.md “Testing”). Skipped unless
 * `-Pplanetfoss.bookmarks=<file>` names a file with one line per service: `<service id> <base url> <token>`.
 */
class BookmarkLiveTest {
    private val lines = System.getProperty("planetfoss.bookmarks")?.let(::File)?.takeIf { it.exists() }?.readLines().orEmpty()
        .map { it.trim().split(Regex("\\s+")) }.filter { it.size == 3 }

    @Test
    fun everyServiceTestsSavesAndRefusesABadToken() = runTest {
        assumeTrue("no -Pplanetfoss.bookmarks", lines.isNotEmpty())
        val client = BookmarkClient(OkHttpClient())
        for ((id, base, token) in lines) {
            val target = BookmarkTarget(BookmarkService.of(id)!!, base.toHttpUrl(), token)
            println("$id: test")
            assertEquals(id, BookmarkResult.Ok, client.test(target))
            println("$id: save")
            assertEquals(id, BookmarkResult.Ok, client.save(target, "https://planetfoss.net/?live=$id", "PlanetFOSS live test", listOf("planetfoss", "gnome-49")))
            assertEquals(id, BookmarkResult.Unauthorized, client.test(target.copy(token = "wrong")))
            assertEquals(id, BookmarkResult.WrongAddress, client.test(target.copy(base = base.toHttpUrl().resolve("/nothing-here/")!!)))
        }
    }
}
