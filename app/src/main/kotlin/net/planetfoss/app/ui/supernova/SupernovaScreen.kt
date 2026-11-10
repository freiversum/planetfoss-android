// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.supernova

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import net.planetfoss.app.ui.components.RefreshBox
import net.planetfoss.app.ui.components.ScrollToTopOnTabTap
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.planetfoss.app.R
import net.planetfoss.app.ui.theme.ContentMaxWidth
import net.planetfoss.app.ui.theme.Radius
import net.planetfoss.app.ui.theme.Space
import net.planetfoss.app.ui.components.CenterMessage
import net.planetfoss.app.ui.components.ErrorBanner
import net.planetfoss.app.ui.components.LocalItemActions
import net.planetfoss.app.ui.components.CometCard
import net.planetfoss.app.ui.components.NovaCard
import net.planetfoss.app.ui.components.TabTopBar
import net.planetfoss.app.ui.components.errorText
import net.planetfoss.app.ui.components.openNotificationSettings
import net.planetfoss.app.ui.components.rememberNotifyRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupernovaScreen(onSettings: () -> Unit, onTopic: (String, String) -> Unit, onBack: (() -> Unit)? = null, vm: SupernovaViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val lang by vm.lang.collectAsStateWithLifecycle()
    val notifyOn by vm.notifyOn.collectAsStateWithLifecycle()
    val actions = LocalItemActions.current
    val scroll = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val listState = rememberLazyListState()
    ScrollToTopOnTabTap(listState, scroll)

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = { TabTopBar(stringResource(R.string.tab_supernovas), lang, vm::setLang, onSettings, scroll, onBack = onBack) },
    ) { padding ->
        RefreshBox(
            isRefreshing = state.refreshing && state.items.isNotEmpty(),
            onRefresh = vm::refresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            when {
                !state.loaded -> Unit
                state.items.isEmpty() && state.error != null -> CenterMessage(errorText(state.error!!, hasData = false), onRetry = vm::refresh)
                state.items.isEmpty() && (state.refreshing || !state.fetched) -> CenterMessage(stringResource(R.string.loading), loading = true)
                else -> Column(Modifier.fillMaxSize()) {
                    state.error?.let { ErrorBanner(errorText(it, hasData = true), vm::refresh) }
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(Space.l),
                        verticalArrangement = Arrangement.spacedBy(Space.m),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (notifyOn == false) item(key = "notify") { NotifyOffer(vm::enableNotify, Modifier.widthIn(max = ContentMaxWidth)) }
                        if (state.items.none { it is Sky.Nova }) {
                            item(key = "empty") {
                                Text(stringResource(R.string.empty_supernovas), color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.widthIn(max = ContentMaxWidth).padding(vertical = Space.xl))
                            }
                        }
                        // the first supernova in full, the others compact (as on the website), comets in between
                        itemsIndexed(state.items, key = { _, it -> it.key }) { i, entry ->
                            val wide = Modifier.widthIn(max = ContentMaxWidth)
                            when (entry) {
                                is Sky.Nova -> entry.nova.let { nova ->
                                    NovaCard(nova, onOpen = { actions.openFrom(it, "supernova") }, onAll = { onTopic(nova.key, nova.label) }, modifier = wide, compact = i > 0)
                                }
                                is Sky.Rising -> entry.comet.let { c -> CometCard(c, onOpen = { actions.openFrom(it, "supernova") }, onTopic = { onTopic(c.key, c.label) }, modifier = wide) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Offer to notify, until the user turns it on or dismisses the card (for this screen visit). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NotifyOffer(enable: () -> Unit, modifier: Modifier) {
    var dismissed by rememberSaveable { mutableStateOf(false) }
    var denied by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val request = rememberNotifyRequest { granted -> if (granted) enable() else denied = true }
    if (dismissed) return
    Card(modifier.fillMaxWidth(), shape = Radius.card, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Text(stringResource(R.string.notify_offer_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(if (denied) R.string.notify_denied else R.string.notify_offer_text), style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.padding(top = Space.xs), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                if (denied) {
                    Button(onClick = { openNotificationSettings(context) }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.open_system_settings)) }
                } else {
                    Button(onClick = request, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.notify_offer_yes)) }
                }
                TextButton(onClick = { dismissed = true }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.not_now)) }
            }
        }
    }
}
