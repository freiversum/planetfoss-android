// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.adblock

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.planetfoss.app.BuildConfig
import net.planetfoss.core.adblock.FilterRequest
import net.planetfoss.core.adblock.FilterSet
import net.planetfoss.core.adblock.InvalidFilterList
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A filter list the app can use. It is downloaded only while the tracker protection is on;
 * nothing is shipped in the APK.
 */
enum class FilterList(val url: String, val title: String) {
    EASYPRIVACY("https://easylist.to/easylist/easyprivacy.txt", "EasyPrivacy"),
}

data class ListStatus(val lastSuccess: Long?, val lastError: String?, val rules: Int)

data class AdblockState(
    /** Visible “block trackers”: EasyPrivacy, third-party requests only – the sources' ads and own statistics stay. */
    val trackers: Boolean = false,
    /** Sites (hosts) where the user switched the blocker off; subdomains included. */
    val exceptions: Set<String> = emptySet(),
    val lists: Map<FilterList, ListStatus> = emptyMap(),
) {
    /** Any feature that looks at the browser's requests is on. */
    val active: Boolean get() = trackers

    /** Lists the switched-on features need. */
    val neededLists: List<FilterList> get() = if (trackers) listOf(FilterList.EASYPRIVACY) else emptyList()

    /** A needed list was never downloaded. */
    val missingLists: Boolean get() = neededLists.any { lists[it]?.lastSuccess == null }

    fun isException(pageHost: String?): Boolean =
        pageHost != null && exceptions.any { pageHost == it || pageHost.endsWith(".$it") }
}

/**
 * Tracker protection for the internal browser: settings, list file, updates and the parsed
 * [FilterSet]. Requests are decided locally; no URL ever leaves the phone for this, the
 * only network traffic is fetching EasyPrivacy from easylist.to.
 */
@Singleton
class AdblockRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: DataStore<Preferences>,
    client: OkHttpClient,
    private val clock: Clock,
) {
    /** Lists are 1.5–2 MB: past the shared HTTP cache, with their own ETag handling. */
    private val http = client.newBuilder().cache(null).build()
    private val dir get() = File(context.filesDir, "adblock").apply { mkdirs() }
    private val loadLock = Mutex()
    private val updateLock = Mutex()

    /** Where a list is fetched from; tests point it at a local server. */
    internal var urlOf: (FilterList) -> String = { it.url }

    @Volatile private var trackerFilters: FilterSet? = null
    @Volatile private var filtersVersion = -1L

    val state: Flow<AdblockState> = store.data.map { p ->
        AdblockState(
            trackers = p[TRACKERS] ?: false,
            exceptions = p[EXCEPTIONS] ?: emptySet(),
            lists = FilterList.entries.associateWith {
                ListStatus(p[successKey(it)], p[errorKey(it)], p[rulesKey(it)] ?: 0)
            },
        )
    }.distinctUntilChanged()

    suspend fun setTrackers(on: Boolean) = store.edit { it[TRACKERS] = on }

    suspend fun addException(host: String) {
        val h = normalizeHost(host) ?: return
        store.edit { it[EXCEPTIONS] = (it[EXCEPTIONS] ?: emptySet()) + h }
    }

    suspend fun removeException(host: String) = store.edit { it[EXCEPTIONS] = (it[EXCEPTIONS] ?: emptySet()) - host }

    /**
     * The blocking rule for [req], or null. Null as well while the lists are not loaded yet
     * (the first pages after the app starts load unfiltered rather than waiting).
     */
    fun decide(req: FilterRequest): String? =
        trackerFilters?.takeIf { req.thirdParty == true }?.match(req)?.raw

    /** Parse the stored lists if they changed since the last load. Runs off the main thread. */
    suspend fun ensureLoaded() = loadLock.withLock {
        val s = state.first()
        val version = FilterList.entries.sumOf { file(it).lastModified() } + (if (s.trackers) 4 else 0)
        if (version == filtersVersion) return@withLock
        val start = System.nanoTime()
        val trackerTexts = if (s.trackers) read(listOf(FilterList.EASYPRIVACY)) else emptyList()
        trackerFilters = if (trackerTexts.isEmpty()) null else withContext(Dispatchers.Default) { FilterSet.parse(trackerTexts, validate = false) }
        // debug builds only: how long the phone needs (docs/06-adblock.md); no URLs, nothing leaves the device
        if (BuildConfig.DEBUG) Log.d("PlanetFOSS", "adblock: parsed trackers ${trackerFilters?.stats} in ${(System.nanoTime() - start) / 1_000_000} ms")
        filtersVersion = version
    }

    private suspend fun read(lists: List<FilterList>) = withContext(Dispatchers.IO) { lists.map(::file).filter { it.exists() }.map { it.readText() } }

    /** Drop the parsed rules (tracker protection off): frees the memory they take. */
    fun unload() {
        trackerFilters = null
        filtersVersion = -1
    }

    /**
     * Fetch the needed lists (conditional GET), validate each before it replaces the old file,
     * then reload. A failed or invalid download keeps the previous list and records the error.
     * Returns true if every list is usable afterwards.
     */
    suspend fun update(): Boolean = updateLock.withLock {
        var allOk = true
        for (list in state.first().neededLists) {
            val error = runCatching { fetch(list) }.exceptionOrNull()
            store.edit {
                if (error == null) {
                    it.remove(errorKey(list))
                } else {
                    it[errorKey(list)] = when (error) {
                        is InvalidFilterList -> "invalid: ${error.message}"
                        is IOException -> "network: ${error.message ?: error.javaClass.simpleName}"
                        else -> error.javaClass.simpleName
                    }
                }
            }
            if (error != null && !file(list).exists()) allOk = false
        }
        ensureLoaded()
        allOk
    }

    private suspend fun fetch(list: FilterList) = withContext(Dispatchers.IO) {
        val target = file(list)
        val etag = store.data.first()[etagKey(list)]
        val request = Request.Builder().url(urlOf(list)).apply { if (etag != null && target.exists()) header("If-None-Match", etag) }.build()
        http.newCall(request).execute().use { response ->
            if (response.code == 304) {
                store.edit { it[successKey(list)] = clock.millis() }
                return@withContext
            }
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val length = response.body.contentLength()
            if (length > MAX_BYTES) throw InvalidFilterList("too large")
            val text = response.body.source().use { src ->
                src.request(MAX_BYTES + 1)
                if (src.buffer.size > MAX_BYTES) throw InvalidFilterList("too large")
                src.buffer.readUtf8()
            }
            if (text.length < MIN_BYTES) throw InvalidFilterList("too small")
            // validates the header and the number of usable rules; throws on a broken list
            val rules = FilterSet.parse(listOf(text), validate = true).stats.let { it.network + it.exceptions }
            val tmp = File(target.parentFile, target.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(target)) throw IOException("rename failed")
            store.edit {
                it[successKey(list)] = clock.millis()
                it[rulesKey(list)] = rules
                response.header("ETag")?.let { e -> it[etagKey(list)] = e } ?: it.remove(etagKey(list))
            }
        }
    }

    /** Delete downloaded lists and their status (e.g. when the user wants to start over). */
    suspend fun clearLists() {
        withContext(Dispatchers.IO) { FilterList.entries.forEach { file(it).delete() } }
        store.edit { p -> FilterList.entries.forEach { p.remove(successKey(it)); p.remove(etagKey(it)); p.remove(rulesKey(it)); p.remove(errorKey(it)) } }
        unload()
    }

    private fun file(list: FilterList) = File(dir, list.name.lowercase() + ".txt")

    companion object {
        const val MAX_BYTES = 15L * 1024 * 1024
        const val MIN_BYTES = 10 * 1024
        private val TRACKERS = booleanPreferencesKey("trackers_enabled")
        private val EXCEPTIONS = stringSetPreferencesKey("adblock_exceptions")
        private fun successKey(l: FilterList) = longPreferencesKey("adblock_${l.name}_success")
        private fun etagKey(l: FilterList) = stringPreferencesKey("adblock_${l.name}_etag")
        private fun rulesKey(l: FilterList) = intPreferencesKey("adblock_${l.name}_rules")
        private fun errorKey(l: FilterList) = stringPreferencesKey("adblock_${l.name}_error")

        /** “www.Example.org”, “https://example.org/x” → “example.org”; null if it is no host. */
        fun normalizeHost(input: String): String? {
            val h = input.trim().lowercase().substringAfter("://").substringBefore('/').substringBefore(':').removePrefix("www.")
            return h.takeIf { it.contains('.') && it.all { c -> c.isLetterOrDigit() || c == '.' || c == '-' } && !it.startsWith('.') && !it.endsWith('.') }
        }
    }
}
