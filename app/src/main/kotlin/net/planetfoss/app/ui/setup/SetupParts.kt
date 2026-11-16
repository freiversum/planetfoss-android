// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.setup

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.toggleable
import net.planetfoss.app.R
import net.planetfoss.app.ui.theme.MetaStyle
import net.planetfoss.app.ui.theme.Radius
import net.planetfoss.app.ui.theme.Space

// The night sky of the website's header and the widgets: the same on every page and in light and dark mode.
internal val Night = Color(0xFF0B1026)
internal val NebulaLight = Color(0xFFB3A4FF)
internal val OnNebula = Color(0xFF1B0C6B)
internal val MoonText = Color(0xFFF5F6FB)
internal val MoonMuted = Color(0xFFC9CEE3)
internal val StarYellow = Color(0xFFFCC954)

/** A few stars (x, y as fractions, radius in dp, alpha), like the header's. */
private val Stars = listOf(
    floatArrayOf(.09f, .14f, 1.2f, .7f), floatArrayOf(.26f, .30f, 1f, .5f), floatArrayOf(.47f, .09f, 1.4f, .8f),
    floatArrayOf(.68f, .22f, 1f, .55f), floatArrayOf(.83f, .11f, 1.3f, .7f), floatArrayOf(.92f, .36f, 1f, .5f),
    floatArrayOf(.14f, .62f, 1f, .4f), floatArrayOf(.36f, .78f, 1.2f, .5f), floatArrayOf(.58f, .55f, 1f, .35f),
    floatArrayOf(.76f, .68f, 1.4f, .6f), floatArrayOf(.05f, .88f, 1f, .5f),
)

/** The night sky as a background: deep blue, a violet glow at the bottom right (as in the widgets), a few stars. */
internal fun Modifier.nightSky(): Modifier = drawBehind {
    drawRect(Night)
    drawRect(
        Brush.radialGradient(
            listOf(Color(0xFF4A2F7A), Color(0xFF151C42), Color.Transparent),
            center = Offset(size.width * .85f, size.height * 1.05f),
            radius = maxOf(size.width, size.height) * .85f,
        ),
    )
    Stars.forEach { (x, y, r, a) -> drawCircle(Color.White.copy(alpha = a), radius = r * density, center = Offset(size.width * x, size.height * y)) }
}

/** The head of a setup page: night sky with rounded lower corners, the step, the title and a symbol tile. */
@Composable
internal fun SetupBand(step: Int, steps: Int, title: String, icon: Painter) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp)).nightSky()
            .statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = Space.m, bottom = 24.dp),
    ) {
        Column(Modifier.padding(end = 84.dp), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(stringResource(R.string.setup_step, step, steps), style = MetaStyle, color = NebulaLight)
            Text(title, style = MaterialTheme.typography.headlineMedium, color = MoonText, modifier = Modifier.semantics { heading() })
        }
        Box(
            Modifier.align(Alignment.BottomEnd).size(60.dp).clip(RoundedCornerShape(20.dp))
                .background(NebulaLight.copy(alpha = .16f)).border(1.dp, NebulaLight.copy(alpha = .35f), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(30.dp), tint = Color(0xFFD4CAFF)) }
    }
}

@Composable
internal fun Lead(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Space.xs, vertical = Space.xs).padding(bottom = Space.s))
}

/** A card like the website's: light surface, hairline border, 16 dp corners; rows inside bring no colour of their own. */
@Composable
internal fun SetupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = Radius.card, modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) { Column(content = content) }
}

@Composable
internal fun CardDivider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

@Composable
internal fun rowColors() = ListItemDefaults.colors(containerColor = Color.Transparent)

/** A symbol on a tonal tile, as in the settings of Android 16. */
@Composable
internal fun SymbolTile(icon: Painter) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
}

@Composable
internal fun TipRow(icon: Painter, title: String, text: String) {
    ListItem(
        colors = rowColors(),
        leadingContent = { SymbolTile(icon) },
        headlineContent = { Text(title, style = MaterialTheme.typography.titleSmall) },
        supportingContent = { Text(text) },
    )
}

/** An on/off row for a card (the whole row toggles, the switch shows the state). */
@Composable
internal fun SwitchRow(title: String, text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        colors = rowColors(),
        headlineContent = { Text(title, style = MaterialTheme.typography.titleSmall) },
        supportingContent = { Text(text) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    )
}

/** One choice out of several as its own card: the selected one gets the accent border and tint. */
@Composable
internal fun OptionCard(selected: Boolean, title: String, text: String, badge: String? = null, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = Radius.card,
        colors = CardDefaults.cardColors(containerColor = if (selected) scheme.secondaryContainer else scheme.surfaceContainerLowest),
        border = if (selected) BorderStroke(2.dp, scheme.primary) else BorderStroke(1.dp, scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(bottom = Space.m).selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Row(Modifier.padding(Space.m), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            RadioButton(selected = selected, onClick = null)
            Column(verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                if (badge != null) {
                    Text(
                        badge, style = MaterialTheme.typography.labelSmall, color = scheme.onPrimaryContainer,
                        modifier = Modifier.clip(Radius.s).background(scheme.primaryContainer).padding(horizontal = Space.s, vertical = Space.xxs),
                    )
                }
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
        }
    }
}

/** Page dots: finished ones soft, the current one a wide pill. Read out as one "step x of y". */
@Composable
internal fun StepDots(current: Int, steps: Int) {
    val scheme = MaterialTheme.colorScheme
    val description = stringResource(R.string.setup_step, current, steps)
    Row(Modifier.clearAndSetSemantics { contentDescription = description }, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..steps) {
            val width = animateDpAsState(if (i == current) 22.dp else 7.dp, label = "dot").value
            Box(
                Modifier.size(width, 7.dp).clip(CircleShape)
                    .background(if (i == current) scheme.primary else if (i < current) scheme.primary.copy(alpha = .45f) else scheme.outlineVariant),
            )
        }
    }
}
