// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import net.planetfoss.core.api.ContentLang
import javax.inject.Inject
import javax.inject.Singleton

data class Settings(
    val contentLang: ContentLang = ContentLang.ALL,
    /** Supernova notifications: off until the user turns them on. */
    val novaNotify: Boolean = false,
    /** Morning notification for the new logbook entry: off until the user turns it on. */
    val logbookNotify: Boolean = false,
    /** Check interval in minutes; WorkManager's minimum is 15, Android may still delay it. */
    val novaIntervalMinutes: Int = DEFAULT_INTERVAL,
    /** How articles open; Custom Tabs by default – the user's own browser with its own protection. */
    val browserMode: BrowserMode = BrowserMode.CUSTOM_TAB,
    /** JavaScript in the internal browser. On by default: most sources need it for their layout. */
    val browserJavaScript: Boolean = true,
    /** Comets (rising topics) between the supernovas of the Supernovas tab. */
    val showComets: Boolean = true,
    /**
     * Opt-in: report opened articles and seen sponsor cards to planetfoss.net for the sources' reach
     * (anonymous daily totals). Off by default – without it the app reports nothing about what you read.
     */
    val countReach: Boolean = false,
    /** Light, dark or as the system; applied through Android's per-app night mode (also reaches the app's browser). */
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** The intro hint on top of Latest was closed (or help was opened): new users only see it once. */
    val introClosed: Boolean = false,
    /** The first-start assistant was finished or skipped. */
    val setupDone: Boolean = false,
    /** Day (YYYY-MM-DD) of the newest logbook entry the user opened: Latest then shows it as a marker, not a card. */
    val logbookRead: String = "",
    /** False only for the placeholder before DataStore has answered (the assistant must not flash up on it). */
    val loaded: Boolean = false,
    /** Sources and rubrics the user does not want in the lists – stored on the phone only, like the website's localStorage. */
    val hidden: List<Hidden> = emptyList(),
    /** Mark posts that are new since the app was last opened (the time stays on the phone). On by default. */
    val markNew: Boolean = true,
) {
    /** True when [item] comes from a hidden source or carries a hidden rubric; [except] is the list on screen (its own source or rubric). */
    fun hides(item: net.planetfoss.core.api.Item, except: Hidden? = null): Boolean = hidden.any { h ->
        h != except && when (h.kind) {
            Hidden.SOURCE -> item.source.slug == h.slug
            Hidden.ACCOUNT -> false
            else -> item.categories.any { it.slug == h.slug }
        }
    }

    companion object {
        const val DEFAULT_INTERVAL = 30
        val INTERVALS = listOf(15, 30, 60, 180)
    }
}

/**
 * A hidden source or rubric, or a blocked fediverse account: [kind] [SOURCE], [RUBRIC] or [ACCOUNT], its slug (for an account
 * “service:handle”, see [account]) and name (for the list in the settings).
 */
data class Hidden(val kind: String, val slug: String, val name: String) {
    companion object {
        const val SOURCE = "s"
        const val RUBRIC = "c"
        const val ACCOUNT = "a"

        /** The slug that names a fediverse account on one service. */
        fun account(site: String, handle: String) = "$site:$handle"

        /** Stored as “kind<TAB>slug<TAB>name” in a string set. */
        fun parse(raw: String): Hidden? = raw.split('\t', limit = 3).takeIf { it.size == 3 && it[0] in setOf(SOURCE, RUBRIC, ACCOUNT) && it[1].isNotEmpty() }
            ?.let { Hidden(it[0], it[1], it[2]) }
    }

    fun raw() = "$kind\t$slug\t$name"
}

/** The app's design: follow the system, or always light or dark (the website's toggle). */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Where articles open: the app's own WebView, a Custom Tab of the default browser, or an app the user picks. */
enum class BrowserMode { INTERNAL, CUSTOM_TAB, CHOOSER }

/** Persistent user settings (DataStore). The tracker protection keeps its own (AdblockRepository). */
@Singleton
class SettingsRepository @Inject constructor(private val store: DataStore<Preferences>) {
    val settings: Flow<Settings> = store.data
        .map {
            Settings(
                contentLang = ContentLang.fromParam(it[CONTENT_LANG]),
                novaNotify = it[NOVA_NOTIFY] ?: false,
                logbookNotify = it[LOGBOOK_NOTIFY] ?: false,
                novaIntervalMinutes = (it[NOVA_INTERVAL] ?: Settings.DEFAULT_INTERVAL).takeIf { m -> m in Settings.INTERVALS } ?: Settings.DEFAULT_INTERVAL,
                browserMode = BrowserMode.entries.firstOrNull { m -> m.name == it[BROWSER_MODE] } ?: BrowserMode.CUSTOM_TAB,
                browserJavaScript = it[BROWSER_JS] ?: true,
                showComets = it[SHOW_COMETS] ?: true,
                countReach = it[COUNT_REACH] ?: false,
                theme = ThemeMode.entries.firstOrNull { m -> m.name == it[THEME] } ?: ThemeMode.SYSTEM,
                introClosed = it[INTRO_CLOSED] ?: false,
                setupDone = it[SETUP_DONE] ?: false,
                logbookRead = it[LOGBOOK_READ].orEmpty(),
                loaded = true,
                hidden = it[HIDDEN].orEmpty().mapNotNull(Hidden::parse).sortedBy { h -> h.name.lowercase() },
                markNew = it[MARK_NEW] ?: true,
            )
        }
        .distinctUntilChanged()

    suspend fun setBrowserMode(mode: BrowserMode) {
        store.edit { it[BROWSER_MODE] = mode.name }
    }

    suspend fun setBrowserJavaScript(on: Boolean) {
        store.edit { it[BROWSER_JS] = on }
    }

    suspend fun setTheme(mode: ThemeMode) {
        store.edit { it[THEME] = mode.name }
    }

    /** Hide or show again a source or rubric. */
    suspend fun toggleHidden(entry: Hidden) {
        store.edit { p ->
            val now = p[HIDDEN].orEmpty()
            val same = now.filter { Hidden.parse(it)?.let { h -> h.kind == entry.kind && h.slug == entry.slug } == true }.toSet()
            p[HIDDEN] = if (same.isNotEmpty()) now - same else now + entry.raw()
        }
    }

    suspend fun setMarkNew(on: Boolean) {
        store.edit { it[MARK_NEW] = on }
    }

    /** The newest logbook entry was opened (docs/09-latest-tab.md D-L3). */
    suspend fun setLogbookRead(day: String) {
        store.edit { it[LOGBOOK_READ] = day }
    }

    suspend fun closeIntro() {
        store.edit { it[INTRO_CLOSED] = true }
    }

    /** The assistant is over (finished, skipped or left with Back); it also retires the older hint on Discover. */
    suspend fun finishSetup() {
        store.edit {
            it[SETUP_DONE] = true
            it[INTRO_CLOSED] = true
        }
    }

    suspend fun setCountReach(on: Boolean) {
        store.edit { it[COUNT_REACH] = on }
    }

    suspend fun setShowComets(on: Boolean) {
        store.edit { it[SHOW_COMETS] = on }
    }

    suspend fun setContentLang(lang: ContentLang) {
        store.edit { it[CONTENT_LANG] = lang.param }
    }

    /** Turning notifications on starts a fresh baseline: what burns now is seen, not announced. */
    suspend fun setNovaNotify(on: Boolean) {
        store.edit {
            it[NOVA_NOTIFY] = on
            if (on) it[NOVA_BASELINE] = false
        }
    }

    /** Turning the logbook notification on forgets the last entry: the next check stores the current one silently. */
    suspend fun setLogbookNotify(on: Boolean) {
        store.edit {
            it[LOGBOOK_NOTIFY] = on
            if (on) it.remove(LOGBOOK_SEEN)
        }
    }

    /** Day of the last logbook entry the morning check knows (YYYY-MM-DD); null = none yet (baseline). */
    suspend fun logbookSeen(): String? = store.data.first()[LOGBOOK_SEEN]

    suspend fun setLogbookSeen(day: String) {
        store.edit { it[LOGBOOK_SEEN] = day }
    }

    suspend fun setNovaInterval(minutes: Int) {
        store.edit { it[NOVA_INTERVAL] = minutes }
    }

    /** False until the first check after turning notifications on has stored the current supernovas silently. */
    suspend fun novaBaselineDone(): Boolean = store.data.first()[NOVA_BASELINE] ?: false

    suspend fun setNovaBaselineDone() {
        store.edit { it[NOVA_BASELINE] = true }
    }

    private companion object {
        val CONTENT_LANG = stringPreferencesKey("content_lang")
        val NOVA_NOTIFY = booleanPreferencesKey("nova_notify")
        val NOVA_INTERVAL = intPreferencesKey("nova_interval")
        val NOVA_BASELINE = booleanPreferencesKey("nova_baseline")
        val LOGBOOK_NOTIFY = booleanPreferencesKey("logbook_notify")
        val LOGBOOK_SEEN = stringPreferencesKey("logbook_seen")
        val LOGBOOK_READ = stringPreferencesKey("logbook_read")
        val BROWSER_MODE = stringPreferencesKey("browser_mode")
        val BROWSER_JS = booleanPreferencesKey("browser_js")
        val SHOW_COMETS = booleanPreferencesKey("show_comets")
        val COUNT_REACH = booleanPreferencesKey("count_reach")
        val THEME = stringPreferencesKey("theme")
        val INTRO_CLOSED = booleanPreferencesKey("intro_closed")
        val SETUP_DONE = booleanPreferencesKey("setup_done")
        val HIDDEN = stringSetPreferencesKey("hidden")
        val MARK_NEW = booleanPreferencesKey("mark_new")
    }
}
