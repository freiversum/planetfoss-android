// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.planetfoss.app.R
import net.planetfoss.app.data.bookmarks.BookmarkConfig
import net.planetfoss.app.data.bookmarks.BookmarkRepository
import net.planetfoss.app.ui.components.Choice
import net.planetfoss.app.ui.components.ChoiceItem
import net.planetfoss.app.ui.theme.ContentMaxWidth
import net.planetfoss.app.ui.theme.Space
import net.planetfoss.core.bookmarks.BookmarkResult
import net.planetfoss.core.bookmarks.BookmarkService
import javax.inject.Inject

/** What the last “connect” came to; null while nothing was tried, [busy] while it runs. */
data class ConnectState(val busy: Boolean = false, val result: BookmarkResult? = null, val connected: Boolean = false, val localDenied: Boolean = false)

/** Android 17's permission for addresses in the home network (the constant is only in SDK 37). */
const val LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

@HiltViewModel
class BookmarkServiceViewModel @Inject constructor(private val repo: BookmarkRepository) : ViewModel() {
    val config: StateFlow<BookmarkConfig?> = repo.config.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _state = MutableStateFlow(ConnectState())
    val state: StateFlow<ConnectState> = _state.asStateFlow()

    fun validAddress(text: String) = repo.parseAddress(text) != null

    /** Android 17+ and an address in the home network: the “local network” permission is needed first. */
    suspend fun needsLocalNetwork(context: android.content.Context, address: String): Boolean =
        android.os.Build.VERSION.SDK_INT >= 37 &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, LOCAL_NETWORK) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
            repo.isLocal(address)

    /** The reader said no to the “local network” permission: say why nothing works instead of a timeout. */
    fun localNetworkDenied() {
        _state.value = ConnectState(localDenied = true)
    }

    /** Try the service; only when it answers with the token is it stored. A blank [token] keeps the stored one. */
    fun connect(service: BookmarkService, address: String, token: String) {
        if (_state.value.busy) return
        _state.value = ConnectState(busy = true)
        viewModelScope.launch {
            val result = repo.test(service, address, token)
            if (result == BookmarkResult.Ok) repo.connect(service, address, token)
            _state.value = ConnectState(result = result, connected = result == BookmarkResult.Ok)
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            repo.disconnect()
            _state.value = ConnectState()
        }
    }

    fun setTagPlanetfoss(on: Boolean) {
        viewModelScope.launch { repo.setTagPlanetfoss(on) }
    }

    fun setTagTopic(on: Boolean) {
        viewModelScope.launch { repo.setTagTopic(on) }
    }
}

/** Where the reader finds the token in each service (its own menu names, as the services show them in English). */
private fun tokenWhere(service: BookmarkService) = when (service) {
    BookmarkService.KARAKEEP -> R.string.bookmark_token_where_karakeep
    BookmarkService.LINKDING -> R.string.bookmark_token_where_linkding
    BookmarkService.LINKWARDEN -> R.string.bookmark_token_where_linkwarden
    BookmarkService.READECK -> R.string.bookmark_token_where_readeck
}

/**
 * Settings › Saved › Bookmark service: one service at a time, its address and API token. “Connect” tries the service
 * first and stores only what worked. The page has the same frame as the other settings pages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkServiceScreen(onBack: () -> Unit, vm: BookmarkServiceViewModel = hiltViewModel()) {
    val config by vm.config.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val c = config ?: return
    var service by rememberSaveable { mutableStateOf(c.service ?: BookmarkService.KARAKEEP) }
    var address by rememberSaveable { mutableStateOf(c.address.removeSuffix("/")) }
    var token by rememberSaveable { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf(!c.active) }
    LaunchedEffect(state.connected) { if (state.connected) { editing = false; token = "" } }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // a service in the home network: Android 17 asks the reader first, then the connect goes on (or explains the “no”)
    val askLocal = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) vm.connect(service, address, token) else vm.localNetworkDenied() }
    val connect: () -> Unit = {
        scope.launch {
            if (vm.needsLocalNetwork(context, address)) askLocal.launch(LOCAL_NETWORK) else vm.connect(service, address, token)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bookmark_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = ContentMaxWidth).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(bottom = Space.l)) {
                Text(
                    stringResource(R.string.bookmark_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = Space.l, vertical = Space.s),
                )

                if (c.active && !editing) {
                    // summary first, fields only after “change” (the pattern of the other settings with a form)
                    Section(stringResource(R.string.bookmark_connected), first = true)
                    androidx.compose.material3.ListItem(
                        headlineContent = { Text(c.service!!.label) },
                        supportingContent = { Text(c.address.removePrefix("https://").removeSuffix("/")) },
                    )
                    Row(Modifier.padding(horizontal = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        OutlinedButton(onClick = { editing = true }) { Text(stringResource(R.string.bookmark_change)) }
                        OutlinedButton(onClick = vm::disconnect) { Text(stringResource(R.string.bookmark_disconnect)) }
                    }
                    Section(stringResource(R.string.bookmark_tags))
                    SwitchItem(stringResource(R.string.bookmark_tag_planetfoss), stringResource(R.string.bookmark_tag_planetfoss_hint), c.tagPlanetfoss, vm::setTagPlanetfoss)
                    SwitchItem(stringResource(R.string.bookmark_tag_topic), stringResource(R.string.bookmark_tag_topic_hint), c.tagTopic, vm::setTagTopic)
                } else {
                    Section(stringResource(R.string.bookmark_setup), first = true)
                    ChoiceItem(
                        stringResource(R.string.bookmark_service),
                        BookmarkService.entries.map { Choice(it, it.label) },
                        service,
                        onSelect = { service = it },
                    )
                    val addressOk = address.isBlank() || vm.validAddress(address)
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text(stringResource(R.string.bookmark_address)) },
                        placeholder = { Text("https://bookmarks.example.org") },
                        supportingText = { Text(stringResource(if (addressOk) R.string.bookmark_address_hint else R.string.bookmark_address_invalid)) },
                        isError = !addressOk,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l),
                    )
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it.trim() },
                        label = { Text(stringResource(R.string.bookmark_token)) },
                        placeholder = if (c.hasToken && c.service == service) ({ Text(stringResource(R.string.bookmark_token_kept)) }) else null,
                        supportingText = { Text(stringResource(tokenWhere(service))) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.xs),
                    )
                    val canConnect = !state.busy && address.isNotBlank() && vm.validAddress(address) && (token.isNotBlank() || (c.hasToken && c.service == service))
                    Row(Modifier.padding(horizontal = Space.l, vertical = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        Button(onClick = connect, enabled = canConnect) {
                            Text(stringResource(if (state.busy) R.string.bookmark_connecting else R.string.bookmark_connect))
                        }
                        if (c.active) OutlinedButton(onClick = { editing = false; token = "" }) { Text(stringResource(R.string.cancel)) }
                    }
                    val problem = if (state.localDenied) stringResource(R.string.bookmark_error_local_denied)
                        else state.result?.takeIf { it != BookmarkResult.Ok }?.let { resultText(it, service) }
                    problem?.let { text ->
                        Text(
                            text,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = Space.l).semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }

                Section(stringResource(R.string.bookmark_good_to_know))
                Text(
                    stringResource(R.string.bookmark_notes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Space.l),
                )
            }
        }
    }
}

@Composable
private fun resultText(result: BookmarkResult, service: BookmarkService): String = when (result) {
    BookmarkResult.Ok -> ""
    BookmarkResult.Unauthorized -> stringResource(R.string.bookmark_error_token)
    BookmarkResult.WrongAddress -> stringResource(R.string.bookmark_error_address, service.label)
    is BookmarkResult.Refused -> stringResource(R.string.bookmark_error_refused, result.code)
    is BookmarkResult.Unreachable -> when (result.why) {
        BookmarkResult.Why.UNKNOWN_HOST -> stringResource(R.string.bookmark_error_host)
        BookmarkResult.Why.NO_CONNECTION -> stringResource(R.string.bookmark_error_connect)
        BookmarkResult.Why.TIMEOUT -> stringResource(R.string.bookmark_error_timeout)
        BookmarkResult.Why.CERTIFICATE -> stringResource(R.string.bookmark_error_certificate)
        BookmarkResult.Why.SERVER -> stringResource(R.string.bookmark_error_server, result.code)
        BookmarkResult.Why.OTHER -> stringResource(R.string.bookmark_error_unreachable)
    }
}
