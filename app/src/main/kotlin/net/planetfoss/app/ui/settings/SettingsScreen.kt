// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.settings

import androidx.compose.foundation.clickable
import net.planetfoss.app.ui.theme.Space
import net.planetfoss.app.ui.theme.ContentMaxWidth
import net.planetfoss.app.ui.components.ChoiceItem
import net.planetfoss.app.ui.components.Choice
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.planetfoss.app.AppLanguage
import net.planetfoss.app.BuildConfig
import net.planetfoss.app.R
import net.planetfoss.app.browser.clearBrowserData
import net.planetfoss.app.data.FeedRepository
import net.planetfoss.app.data.prefs.BrowserMode
import net.planetfoss.app.data.prefs.ThemeMode
import net.planetfoss.app.data.prefs.Settings
import net.planetfoss.app.data.prefs.SettingsRepository
import net.planetfoss.app.sync.NovaNotifier
import net.planetfoss.app.ui.components.langLabel
import net.planetfoss.app.ui.components.openNotificationSettings
import net.planetfoss.app.ui.components.rememberNotifyRequest
import net.planetfoss.core.api.ContentLang
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val followRepo: net.planetfoss.app.data.FollowRepository,
    private val feed: FeedRepository,
    private val notifier: NovaNotifier,
    private val saved: net.planetfoss.app.data.SavedRepository,
) : ViewModel() {
    /** Saved articles to a file the user picked (Storage Access Framework); the result as a short message. */
    fun exportSaved(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch {
            val ok = runCatching {
                val text = saved.export()
                context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
            }.isSuccess
            android.widget.Toast.makeText(context, if (ok) R.string.saved_export_done else R.string.saved_export_failed, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun importSaved(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch {
            val text = runCatching { context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }.getOrNull()
            val added = text?.let { saved.import(it) }
            val msg = if (added == null) context.getString(R.string.saved_import_failed)
                else context.resources.getQuantityString(R.plurals.saved_import_done, added, added)
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val settings = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    /** Followed topics and sources, for the list with which they are removed again. */
    val follows = followRepo.all.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Notifications for follows; switching them on starts from now, nothing published meanwhile is announced. */
    fun setFollowNotify(on: Boolean) {
        viewModelScope.launch {
            if (on) followRepo.restartMarks()
            repo.setFollowNotify(on)
        }
    }

    fun unfollow(kind: String, ref: String) {
        viewModelScope.launch { followRepo.unfollow(kind, ref) }
    }

    fun unhide(entry: net.planetfoss.app.data.prefs.Hidden) {
        viewModelScope.launch { repo.toggleHidden(entry) }
    }

    /** PlanetFOSS's own donation link – only while it is switched on in the website's backend. */
    val donate = kotlinx.coroutines.flow.MutableStateFlow<net.planetfoss.core.api.Donate?>(null)

    init {
        viewModelScope.launch { donate.value = feed.meta().donate?.takeIf { net.planetfoss.core.url.UrlPolicy.isWebUrl(it.url) } }
    }

    /** Sponsor cards appear only once ads are on at planetfoss.net; the counting text names them only then. */
    val adsShown = feed.adsShown.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setLang(lang: ContentLang) {
        viewModelScope.launch { repo.setContentLang(lang) }
    }

    fun setCountReach(on: Boolean) {
        viewModelScope.launch { repo.setCountReach(on) }
    }

    /**
     * Android's per-app night mode: the system keeps it across restarts and recreates the screen, so status bar,
     * app browser and the night resources (dark wordmark) follow; DataStore only remembers it for this row.
     */
    fun setTheme(context: android.content.Context, mode: ThemeMode) {
        applyThemeMode(context, mode)
        viewModelScope.launch { repo.setTheme(mode) }
    }

    fun setMarkNew(on: Boolean) {
        viewModelScope.launch { repo.setMarkNew(on) }
    }

    fun setShowComets(on: Boolean) {
        viewModelScope.launch { repo.setShowComets(on) }
    }

    fun setNotify(on: Boolean) {
        viewModelScope.launch { repo.setNovaNotify(on) }
    }

    fun setLogbookNotify(on: Boolean) {
        viewModelScope.launch { repo.setLogbookNotify(on) }
    }

    fun setBrowserMode(mode: BrowserMode) {
        viewModelScope.launch { repo.setBrowserMode(mode) }
    }

    fun setJavaScript(on: Boolean) {
        viewModelScope.launch { repo.setBrowserJavaScript(on) }
    }

    fun setInterval(minutes: Int) {
        viewModelScope.launch { repo.setNovaInterval(minutes) }
    }

    /** Android may block what the app setting allows (permission revoked, channel muted). */
    fun systemAllows(): Boolean = notifier.canNotify()
}

/** Android's per-app night mode (see [SettingsViewModel.setTheme]); shared with the setup assistant. */
internal fun applyThemeMode(context: android.content.Context, mode: ThemeMode) {
    context.getSystemService(android.app.UiModeManager::class.java)?.setApplicationNightMode(
        when (mode) {
            ThemeMode.SYSTEM -> android.app.UiModeManager.MODE_NIGHT_AUTO
            ThemeMode.LIGHT -> android.app.UiModeManager.MODE_NIGHT_NO
            ThemeMode.DARK -> android.app.UiModeManager.MODE_NIGHT_YES
        },
    )
}

/**
 * Settings in the order people look for them: supernovas, language, how articles open, blocking in
 * the app's browser, the sources, about. Single choices are one row each with a dialog
 * ([ChoiceItem]); the page stays centred and no wider than the lists on large screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onHelp: () -> Unit = {}, onSetup: () -> Unit = {}, onWhatsNew: () -> Unit = {}, vm: SettingsViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var systemAllows by remember { mutableStateOf(vm.systemAllows()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { systemAllows = vm.systemAllows() }
    // the user tapped the switch but Android did not allow notifications: say so instead of a silent “off”
    var denied by rememberSaveable { mutableStateOf(false) }
    val request = rememberNotifyRequest { granted ->
        systemAllows = vm.systemAllows()
        denied = !granted
        if (granted) vm.setNotify(true)
    }

    // the same Android permission for the logbook switch: granted = switch on, denied = the hint below the switch
    val requestFollow = rememberNotifyRequest { granted ->
        systemAllows = vm.systemAllows()
        if (granted) vm.setFollowNotify(true)
    }
    var logbookDenied by rememberSaveable { mutableStateOf(false) }
    val requestLogbook = rememberNotifyRequest { granted ->
        systemAllows = vm.systemAllows()
        logbookDenied = !granted
        if (granted) vm.setLogbookNotify(true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            // imePadding: the exception field of the blocking section stays above the keyboard
            Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(bottom = Space.l)) {
                Section(stringResource(R.string.tab_radar), first = true)
                SwitchItem(stringResource(R.string.settings_mark_new), stringResource(R.string.settings_mark_new_hint), settings.markNew, vm::setMarkNew)
                Section(stringResource(R.string.tab_supernovas))
                SwitchItem(stringResource(R.string.comets_switch), stringResource(R.string.comets_explain), settings.showComets, vm::setShowComets)
                SwitchItem(stringResource(R.string.notify_switch), stringResource(R.string.notify_explain), settings.novaNotify) { on ->
                    if (on) request() else vm.setNotify(false)
                }
                if ((settings.novaNotify && !systemAllows) || (denied && !settings.novaNotify)) {
                    ListItem(
                        headlineContent = {
                            Text(stringResource(if (settings.novaNotify) R.string.notify_blocked else R.string.notify_denied), color = MaterialTheme.colorScheme.error)
                        },
                        supportingContent = {
                            TextButton(onClick = { openNotificationSettings(context) }) { Text(stringResource(R.string.open_system_settings)) }
                        },
                    )
                }
                val follows by vm.follows.collectAsStateWithLifecycle()
                // the interval serves the supernova check and the follow check
                if (settings.novaNotify || follows.isNotEmpty()) {
                    ChoiceItem(
                        stringResource(R.string.notify_interval),
                        Settings.INTERVALS.map { minutes ->
                            Choice(
                                minutes,
                                if (minutes < 60) pluralStringResource(R.plurals.every_minutes, minutes, minutes)
                                else pluralStringResource(R.plurals.every_hours, minutes / 60, minutes / 60),
                            )
                        },
                        settings.novaIntervalMinutes,
                        vm::setInterval,
                    )
                }

                Section(stringResource(R.string.logbook_title))
                SwitchItem(stringResource(R.string.logbook_notify_switch), stringResource(R.string.logbook_notify_explain), settings.logbookNotify) { on ->
                    if (on) requestLogbook() else vm.setLogbookNotify(false)
                }
                if ((settings.logbookNotify && !systemAllows) || (logbookDenied && !settings.logbookNotify)) {
                    ListItem(
                        headlineContent = {
                            Text(stringResource(if (settings.logbookNotify) R.string.notify_blocked else R.string.notify_denied), color = MaterialTheme.colorScheme.error)
                        },
                        supportingContent = {
                            TextButton(onClick = { openNotificationSettings(context) }) { Text(stringResource(R.string.open_system_settings)) }
                        },
                    )
                }
                if (follows.isNotEmpty()) {
                    Section(stringResource(R.string.settings_follows))
                    SwitchItem(stringResource(R.string.follow_notify_switch), stringResource(R.string.follow_notify_explain), settings.followNotify && systemAllows) { on ->
                        if (on) requestFollow() else vm.setFollowNotify(false)
                    }
                    follows.forEach { f ->
                        ListItem(
                            headlineContent = { Text(f.label) },
                            supportingContent = { Text(stringResource(if (f.kind == net.planetfoss.app.data.FollowRepository.TOPIC) R.string.follow_kind_topic else R.string.follow_kind_source)) },
                            trailingContent = {
                                IconButton(onClick = { vm.unfollow(f.kind, f.ref) }) {
                                    Icon(Icons.Filled.Close, stringResource(R.string.unfollow_named, f.label))
                                }
                            },
                        )
                    }
                }

                // hidden sources and rubrics (hidden from their list's title bar); stored on the phone only
                if (settings.hidden.isNotEmpty()) {
                    Section(stringResource(R.string.settings_hidden))
                    settings.hidden.forEach { h ->
                        ListItem(
                            headlineContent = { Text(h.name) },
                            supportingContent = {
                                Text(stringResource(when (h.kind) {
                                    net.planetfoss.app.data.prefs.Hidden.SOURCE -> R.string.follow_kind_source
                                    net.planetfoss.app.data.prefs.Hidden.ACCOUNT -> R.string.hidden_kind_account
                                    else -> R.string.hidden_kind_rubric
                                }))
                            },
                            trailingContent = {
                                IconButton(onClick = { vm.unhide(h) }) { Icon(Icons.Filled.Close, stringResource(R.string.unhide_named, h.name)) }
                            },
                        )
                    }
                }

                // saved articles: the app has Android's backup off, so a file is the way to keep them or move them
                Section(stringResource(R.string.tab_saved))
                val exporter = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json"),
                ) { uri -> if (uri != null) vm.exportSaved(context, uri) }
                val importer = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
                ) { uri -> if (uri != null) vm.importSaved(context, uri) }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.saved_export)) },
                    supportingContent = { Text(stringResource(R.string.saved_export_hint)) },
                    modifier = Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button) {
                        exporter.launch("planetfoss-saved-" + java.time.LocalDate.now() + ".json")
                    },
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.saved_import)) },
                    supportingContent = { Text(stringResource(R.string.saved_import_hint)) },
                    modifier = Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button) {
                        importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    },
                )

                Section(stringResource(R.string.settings_display))
                ChoiceItem(
                    stringResource(R.string.theme_title),
                    listOf(
                        Choice(ThemeMode.SYSTEM, stringResource(R.string.theme_system)),
                        Choice(ThemeMode.LIGHT, stringResource(R.string.theme_light)),
                        Choice(ThemeMode.DARK, stringResource(R.string.theme_dark)),
                    ),
                    settings.theme,
                    onSelect = { vm.setTheme(context, it) },
                )

                Section(stringResource(R.string.settings_language))
                ChoiceItem(
                    stringResource(R.string.settings_app_language),
                    listOf(
                        Choice(AppLanguage.Choice.SYSTEM, stringResource(R.string.app_lang_system)),
                        Choice(AppLanguage.Choice.DE, stringResource(R.string.app_lang_de)),
                        Choice(AppLanguage.Choice.EN, stringResource(R.string.app_lang_en)),
                    ),
                    remember { AppLanguage.current(context) },
                    onSelect = { AppLanguage.set(context, it) },
                )
                ChoiceItem(
                    stringResource(R.string.content_lang_title),
                    ContentLang.entries.map { Choice(it, langLabel(it)) },
                    settings.contentLang,
                    vm::setLang,
                )

                Section(stringResource(R.string.settings_browser))
                ChoiceItem(
                    stringResource(R.string.open_with),
                    listOf(
                        Choice(BrowserMode.CUSTOM_TAB, stringResource(R.string.mode_custom_tab), stringResource(R.string.mode_custom_tab_text)),
                        Choice(BrowserMode.INTERNAL, stringResource(R.string.mode_internal), stringResource(R.string.mode_internal_text)),
                        Choice(BrowserMode.CHOOSER, stringResource(R.string.mode_chooser), stringResource(R.string.mode_chooser_text)),
                    ),
                    settings.browserMode,
                    vm::setBrowserMode,
                )
                SwitchItem(stringResource(R.string.browser_js), stringResource(R.string.browser_js_text), settings.browserJavaScript, vm::setJavaScript)
                var cleared by remember { mutableStateOf(false) }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.browser_clear)) },
                    supportingContent = { Text(stringResource(if (cleared) R.string.browser_cleared else R.string.browser_clear_text)) },
                    modifier = Modifier.clickable(role = Role.Button) { clearBrowserData(context) { cleared = true } },
                )

                BlockingSections(section = { Section(it) })

                Section(stringResource(R.string.settings_sources))
                val adsShown by vm.adsShown.collectAsStateWithLifecycle()
                SwitchItem(
                    stringResource(R.string.count_reach_switch),
                    stringResource(if (adsShown) R.string.count_reach_explain_ads else R.string.count_reach_explain),
                    settings.countReach,
                    vm::setCountReach,
                )

                Section(stringResource(R.string.settings_about))
                val donate by vm.donate.collectAsStateWithLifecycle()
                donate?.let { d ->
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.donate_title)) },
                        supportingContent = { Text(if (d.name.isBlank()) stringResource(R.string.donate_text) else stringResource(R.string.donate_text_on, d.name)) },
                        trailingContent = { Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_open_in_new), null) },
                        modifier = Modifier.clickable(role = Role.Button) { net.planetfoss.app.ui.components.openOutsideApp(context, d.url) },
                    )
                }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.setup_settings_title)) },
                    supportingContent = { Text(stringResource(R.string.setup_settings_hint)) },
                    modifier = Modifier.clickable(role = Role.Button, onClick = onSetup),
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.help_title)) },
                    supportingContent = { Text(stringResource(R.string.help_hint)) },
                    modifier = Modifier.clickable(role = Role.Button, onClick = onHelp),
                )
                ListItem(headlineContent = { Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)) })
                ListItem(
                    headlineContent = { Text(stringResource(R.string.whatsnew_title)) },
                    supportingContent = { Text(stringResource(R.string.whatsnew_hint)) },
                    modifier = Modifier.clickable(role = Role.Button, onClick = onWhatsNew),
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_privacy)) },
                    // sponsor cards are only mentioned once planetfoss.net delivers some (as under "Count for the sources")
                    supportingContent = { Text(stringResource(if (adsShown) R.string.settings_privacy_note_ads else R.string.settings_privacy_note)) },
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_licence_title)) },
                    supportingContent = { Text(stringResource(R.string.settings_license)) },
                )
                // imprint and privacy policy of PlanetFOSS: the website's pages, in the user's browser
                LinkItem(stringResource(R.string.mastodon), "https://mastodon.social/@planetfoss")
                LinkItem(stringResource(R.string.imprint), "https://planetfoss.net/impressum/")
                LinkItem(stringResource(R.string.privacy_policy), "https://planetfoss.net/datenschutz/")
                // freiversum: the umbrella of the independent projects (like the website footer), one quiet row
                FreiversumItem()
            }
        }
    }
}

/** A row that opens a page of planetfoss.net in the user's browser. */
@Composable
private fun LinkItem(title: String, url: String) {
    val context = LocalContext.current
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(url.removePrefix("https://").removeSuffix("/"), style = net.planetfoss.app.ui.theme.MetaStyle) },
        trailingContent = { Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_open_in_new), null) },
        modifier = Modifier.clickable(role = Role.Button) { net.planetfoss.app.ui.components.openOutsideApp(context, url) },
    )
}

/** Link to freiversum.de with the constellation mark; opens outside the app, no tracking parameters. */
@Composable
private fun FreiversumItem() {
    val context = LocalContext.current
    val url = "https://freiversum.de/"
    // Shared with the row so a press replays the constellation animation of the mark.
    val interactions = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    ListItem(
        leadingContent = { FreiversumMark(interactions) },
        headlineContent = { Text(stringResource(R.string.freiversum_title)) },
        supportingContent = {
            Column {
                Text(stringResource(R.string.freiversum_text))
                Text(url.removePrefix("https://").removeSuffix("/"), style = net.planetfoss.app.ui.theme.MetaStyle)
            }
        },
        trailingContent = { Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_open_in_new), null) },
        modifier = Modifier.clickable(
            interactionSource = interactions,
            indication = androidx.compose.material3.ripple(),
            role = Role.Button,
        ) { net.planetfoss.app.ui.components.openOutsideApp(context, url) },
    )
}

/** A setting that is on or off: the whole row toggles, the switch only shows the state. */
@Composable
internal fun SwitchItem(title: String, explain: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(explain) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    )
}

@Composable
private fun Section(title: String, first: Boolean = false) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Space.l, end = Space.l, top = if (first) Space.s else Space.xl, bottom = Space.xs).semantics { heading() },
    )
}
