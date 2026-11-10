// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.settings

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.planetfoss.app.R
import net.planetfoss.app.ui.theme.Space
import net.planetfoss.app.adblock.AdblockRepository
import net.planetfoss.app.adblock.AdblockScheduler
import net.planetfoss.app.adblock.AdblockState
import net.planetfoss.app.adblock.FilterList
import java.text.NumberFormat
import javax.inject.Inject

@HiltViewModel
class AdblockSettingsViewModel @Inject constructor(
    private val repo: AdblockRepository,
    private val scheduler: AdblockScheduler,
) : ViewModel() {
    val state = repo.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdblockState())

    fun setTrackers(on: Boolean) {
        viewModelScope.launch { repo.setTrackers(on) }
    }

    fun updateNow() = scheduler.updateNow()

    /** False if the input is no host name. */
    fun addException(input: String): Boolean {
        if (AdblockRepository.normalizeHost(input) == null) return false
        viewModelScope.launch { repo.addException(input) }
        return true
    }

    fun removeException(host: String) {
        viewModelScope.launch { repo.removeException(host) }
    }
}

/**
 * Settings → tracker protection in the app's browser (third-party trackers only, so the sources
 * keep their ads and statistics). While it is on: list update, site exceptions, limits.
 */
@Composable
fun BlockingSections(section: @Composable (String) -> Unit, vm: AdblockSettingsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    section(stringResource(R.string.settings_trackers))
    SwitchItem(stringResource(R.string.trackers_switch), stringResource(R.string.trackers_explain), s.trackers, vm::setTrackers)
    if (s.trackers) ListStatusItem(FilterList.EASYPRIVACY, s)
    if (s.active) BlockingDetails(s, vm)
}

/** List update, sites without blocking and the limits. */
@Composable
private fun BlockingDetails(s: AdblockState, vm: AdblockSettingsViewModel) {
    TextButton(onClick = vm::updateNow, modifier = Modifier.padding(start = Space.xs)) { Text(stringResource(R.string.adblock_update_now)) }

    Text(stringResource(R.string.adblock_exceptions), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = Space.l, top = Space.s))
    if (s.exceptions.isEmpty()) {
        Text(stringResource(R.string.adblock_no_exceptions), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Space.l, vertical = Space.xs))
    }
    s.exceptions.sorted().forEach { host ->
        ListItem(
            headlineContent = { Text(host) },
            trailingContent = {
                IconButton(onClick = { vm.removeException(host) }) { Icon(Icons.Filled.Close, stringResource(R.string.adblock_remove_exception, host)) }
            },
        )
    }
    var input by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }
    val add = { if (vm.addException(input)) { input = ""; invalid = false } else invalid = true }
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.l), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it; invalid = false },
            label = { Text(stringResource(R.string.adblock_add_exception)) },
            placeholder = { Text("example.org") },
            isError = invalid,
            supportingText = if (invalid) ({ Text(stringResource(R.string.adblock_invalid_host)) }) else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = add) { Text(stringResource(R.string.adblock_add)) }
    }
    Column(Modifier.padding(Space.l)) {
        Text(stringResource(R.string.adblock_limits), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ListStatusItem(list: FilterList, s: AdblockState) {
    val context = LocalContext.current
    val st = s.lists[list]
    val status = when {
        st?.lastSuccess == null && st?.lastError == null -> stringResource(R.string.adblock_loading)
        st?.lastSuccess == null -> stringResource(R.string.adblock_failed, st?.lastError.orEmpty())
        else -> stringResource(
            R.string.adblock_list_status,
            NumberFormat.getIntegerInstance().format(st.rules),
            DateUtils.formatDateTime(context, st.lastSuccess, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME),
        ) + (st.lastError?.let { "\n" + stringResource(R.string.adblock_last_error, it) } ?: "")
    }
    ListItem(headlineContent = { Text(list.title) }, supportingContent = { Text(status) })
}
