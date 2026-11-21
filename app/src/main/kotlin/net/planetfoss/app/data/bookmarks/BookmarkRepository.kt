// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.data.bookmarks

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import net.planetfoss.app.data.db.SavedDao
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import net.planetfoss.core.api.Item
import net.planetfoss.core.bookmarks.BookmarkClient
import net.planetfoss.core.bookmarks.BookmarkResult
import net.planetfoss.core.bookmarks.BookmarkService
import net.planetfoss.core.bookmarks.BookmarkTarget
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The bookmark service as the settings show it. [hasToken] only: the token itself never leaves [BookmarkRepository].
 * [tagPlanetfoss] adds the tag `planetfoss`, [tagTopic] the article's topic and rubrics.
 */
data class BookmarkConfig(
    val service: BookmarkService? = null,
    val address: String = "",
    val hasToken: Boolean = false,
    val tagPlanetfoss: Boolean = true,
    val tagTopic: Boolean = false,
) {
    val active: Boolean get() = service != null && address.isNotEmpty() && hasToken
}

/** How far a saved article got to the bookmark service (column `remote` of `saved_item`). */
object RemoteState {
    /** No service set up when it was saved, or it was saved before the feature existed. */
    const val NONE = 0
    const val PENDING = 1
    const val SENT = 2
    /** Gave up: wrong token, wrong address, refused, or too many attempts. The Saved tab offers to try again. */
    const val FAILED = 3
}

/**
 * Sends saved articles to the reader's own bookmark service (docs/10-bookmark-services.md). Directly from the phone to
 * the address the reader entered; planetfoss.net never sees the token or the bookmarks. One-way: removing an article
 * from Saved leaves the bookmark in the service alone.
 */
@Singleton
class BookmarkRepository @Inject constructor(
    private val store: DataStore<Preferences>,
    private val cipher: TokenCipher,
    private val client: BookmarkClient,
    private val dao: SavedDao,
    private val json: Json,
    private val scheduler: BookmarkScheduler,
) : net.planetfoss.app.data.SaveHook {
    /** Plain HTTP addresses: debug builds only (see [parseAddress]); tests switch it off. */
    internal var allowHttp: Boolean = net.planetfoss.app.BuildConfig.DEBUG

    val config: Flow<BookmarkConfig> = store.data.map {
        BookmarkConfig(
            service = BookmarkService.of(it[SERVICE]),
            address = it[ADDRESS].orEmpty(),
            hasToken = !it[TOKEN].isNullOrEmpty(),
            tagPlanetfoss = it[TAG_PLANETFOSS] ?: true,
            tagTopic = it[TAG_TOPIC] ?: false,
        )
    }.distinctUntilChanged()

    /** Saved articles that are on their way or failed: (pending, failed). */
    val pendingAndFailed: Flow<Pair<Int, Int>> = dao.observeRemoteCounts().map { rows ->
        (rows.firstOrNull { it.remote == RemoteState.PENDING }?.n ?: 0) to (rows.firstOrNull { it.remote == RemoteState.FAILED }?.n ?: 0)
    }.distinctUntilChanged()

    /**
     * The address checked and normalised, or null when it is none, or plain HTTP: the app talks HTTPS only. Debug builds
     * accept HTTP for the test containers on the host (their network security config allows 10.0.2.2 and localhost).
     */
    fun parseAddress(input: String) = BookmarkClient.parseAddress(input, allowHttp = allowHttp)

    /**
     * True when the address points into the home network (private IPv4, link-local, IPv6 ULA). Android 17 lets an app
     * reach such addresses only with the “local network” permission. A name that does not resolve counts as not local.
     */
    suspend fun isLocal(address: String): Boolean {
        val host = parseAddress(address)?.host ?: return false
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { java.net.InetAddress.getAllByName(host).any(::isLocalAddress) }.getOrDefault(false)
        }
    }

    /** Try the service with these values without storing them. A blank [token] means the stored one. */
    suspend fun test(service: BookmarkService, address: String, token: String): BookmarkResult {
        val base = parseAddress(address) ?: return BookmarkResult.WrongAddress
        val t = token.ifBlank { storedToken() } ?: return BookmarkResult.Unauthorized
        return client.test(BookmarkTarget(service, base, t))
    }

    /** Store the service; a blank [token] keeps the stored one. */
    suspend fun connect(service: BookmarkService, address: String, token: String) {
        val base = parseAddress(address) ?: return
        val encrypted = token.trim().takeIf { it.isNotEmpty() }?.let(cipher::encrypt)
        store.edit {
            it[SERVICE] = service.id
            it[ADDRESS] = base.toString()
            if (encrypted != null) it[TOKEN] = encrypted
        }
    }

    /** Forget the service and its token; articles on their way stay in Saved, only the sending stops. */
    suspend fun disconnect() {
        store.edit {
            it.remove(SERVICE)
            it.remove(ADDRESS)
            it.remove(TOKEN)
        }
        dao.setRemoteWhere(from = RemoteState.PENDING, to = RemoteState.NONE)
        dao.setRemoteWhere(from = RemoteState.FAILED, to = RemoteState.NONE)
        scheduler.cancelAll()
    }

    suspend fun setTagPlanetfoss(on: Boolean) {
        store.edit { it[TAG_PLANETFOSS] = on }
    }

    suspend fun setTagTopic(on: Boolean) {
        store.edit { it[TAG_TOPIC] = on }
    }

    /** Called right after an article was saved: queue it for the service, if one is set up. */
    override suspend fun onSaved(id: Long) {
        if (!config.first().active) return
        dao.setRemote(id, RemoteState.PENDING)
        scheduler.send(id)
    }

    /** Every failed article once more (the Saved tab's “try again”). */
    suspend fun retryFailed() {
        if (!config.first().active) return
        for (id in dao.idsWithRemote(RemoteState.FAILED)) {
            dao.setRemote(id, RemoteState.PENDING)
            scheduler.send(id)
        }
    }

    /**
     * One attempt for one saved article (the worker). Returns true when it is done – sent, given up, or nothing to
     * send any more – and false when a later attempt may get through.
     */
    suspend fun send(id: Long, lastAttempt: Boolean): Boolean {
        val row = dao.get(id) ?: return true
        if (row.remote != RemoteState.PENDING) return true
        val target = target()
        if (target == null) {
            dao.setRemote(id, RemoteState.NONE)
            return true
        }
        val item = runCatching { json.decodeFromString(Item.serializer(), row.json) }.getOrNull() ?: run {
            dao.setRemote(id, RemoteState.FAILED)
            return true
        }
        val result = client.save(target, item.url, item.title, tags(item))
        return when {
            result == BookmarkResult.Ok -> { dao.setRemote(id, RemoteState.SENT); true }
            result.retryable && !lastAttempt -> false
            else -> { dao.setRemote(id, RemoteState.FAILED); true }
        }
    }

    private suspend fun tags(item: Item): List<String> {
        val c = config.first()
        val tags = mutableListOf<String>()
        if (c.tagPlanetfoss) tags += "planetfoss"
        if (c.tagTopic) {
            item.topic?.label?.let(BookmarkClient::tag)?.let(tags::add)
            item.categories.mapNotNullTo(tags) { BookmarkClient.tag(it.name) }
        }
        return tags.distinct()
    }

    private suspend fun storedToken(): String? = store.data.first()[TOKEN]?.let(cipher::decrypt)

    private suspend fun target(): BookmarkTarget? {
        val p = store.data.first()
        val service = BookmarkService.of(p[SERVICE]) ?: return null
        // stored by connect(), already checked and normalised
        val base = p[ADDRESS]?.toHttpUrlOrNull() ?: return null
        val token = p[TOKEN]?.let(cipher::decrypt) ?: return null
        return BookmarkTarget(service, base, token)
    }

    private companion object {
        val SERVICE = stringPreferencesKey("bookmark_service")
        val ADDRESS = stringPreferencesKey("bookmark_address")
        val TOKEN = stringPreferencesKey("bookmark_token")
        val TAG_PLANETFOSS = booleanPreferencesKey("bookmark_tag_planetfoss")
        val TAG_TOPIC = booleanPreferencesKey("bookmark_tag_topic")
    }
}

/** Private IPv4 (10/8, 172.16/12, 192.168/16), link-local (169.254/16, fe80::/10) and IPv6 ULA (fc00::/7). */
internal fun isLocalAddress(a: java.net.InetAddress): Boolean =
    a.isSiteLocalAddress || a.isLinkLocalAddress || (a is java.net.Inet6Address && (a.address[0].toInt() and 0xfe) == 0xfc)

/** Queues the sending of a saved article (WorkManager in the app, a list in tests). */
interface BookmarkScheduler {
    fun send(id: Long)
    fun cancelAll()
}
