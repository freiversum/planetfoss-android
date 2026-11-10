// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.planetfoss.app.R
import net.planetfoss.app.ui.theme.Radius
import net.planetfoss.app.ui.theme.Space

/** One option of a [ChoiceItem]: its label and, where the choice needs it, what it does. */
data class Choice<T>(val value: T, val label: String, val detail: String? = null)

/**
 * A single choice in the settings: one row with the current value; a tap opens a Material
 * dialog with the options as radio buttons. Picking one applies it and closes the dialog
 * (Android's settings pattern) – three to six radio rows no longer stretch the settings page.
 */
@Composable
fun <T> ChoiceItem(
    title: String, choices: List<Choice<T>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier,
    /** Inside a card that has its own colour the row must not paint the screen colour over it. */
    transparent: Boolean = false,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val current = choices.firstOrNull { it.value == selected }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = current?.let { { Text(it.label) } },
        colors = if (transparent) androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent) else androidx.compose.material3.ListItemDefaults.colors(),
        modifier = modifier.clickable(role = Role.Button) { open = true },
    )
    if (open) {
        ChoiceDialog(title, choices, selected, onSelect = { open = false; if (it != selected) onSelect(it) }, onDismiss = { open = false })
    }
}

@Composable
private fun <T> ChoiceDialog(title: String, choices: List<Choice<T>>, selected: T, onSelect: (T) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            // scrolls if a large font makes the options taller than the dialog
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                choices.forEach { c ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(Radius.s)
                            .selectable(selected = c.value == selected, role = Role.RadioButton) { onSelect(c.value) }
                            .padding(vertical = Space.s),
                        verticalAlignment = if (c.detail == null) Alignment.CenterVertically else Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(Space.m),
                    ) {
                        RadioButton(selected = c.value == selected, onClick = null)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                            Text(c.label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                            c.detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
            }
        },
        // picking applies at once; the only button leaves everything as it was
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
