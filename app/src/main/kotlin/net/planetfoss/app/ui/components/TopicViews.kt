// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.planetfoss.app.ui.theme.LocalPlanetColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import net.planetfoss.app.R
import net.planetfoss.app.ui.theme.MetaStyle
import net.planetfoss.app.ui.theme.Radius
import net.planetfoss.app.ui.theme.Space
import net.planetfoss.core.api.Category
import net.planetfoss.core.api.OrbitTopic
import net.planetfoss.core.api.TopicHead
import net.planetfoss.core.api.TopicRef

/** The website's status of a topic: supernova (burning), afterglow, comet – or nothing. */
@Composable
fun TopicStatusBadge(status: String?) {
    when (status) {
        "supernova" -> NovaBurst(burning = true)
        "nachgluehen" -> NovaBurst(burning = false)
        "komet" -> CometBadge()
    }
}

/**
 * Head of a topic list, like the website's topic page: status, numbers (posts, sources, since when),
 * introduction (expands on tap), rubric and parent topic, related topics.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TopicHeader(head: TopicHead, onTopic: (TopicRef) -> Unit, onRubric: (Category) -> Unit, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable(head.key) { mutableStateOf(false) }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = Radius.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            val stats = head.stats
            if (head.status != null || stats != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    TopicStatusBadge(head.status)
                    if (stats != null && stats.items > 0) {
                        val since = stats.first?.let { MONTH.format(it.atZone(ZoneId.systemDefault())) }
                        Text(
                            listOfNotNull(
                                pluralStringResource(R.plurals.n_posts, stats.items, stats.items),
                                pluralStringResource(R.plurals.n_sources, stats.sources, stats.sources),
                                since?.let { stringResource(R.string.topic_since, it) },
                            ).joinToString(" · "),
                            style = MetaStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                }
            }
            head.intro?.let { intro ->
                Text(
                    intro,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(role = Role.Button, onClickLabel = stringResource(if (expanded) R.string.show_less else R.string.show_more)) {
                        expanded = !expanded
                    },
                )
            }
            // where the topic is discussed (website: in the topic head)
            DiscussionRow(head.discussions)
            if (head.rubric != null || head.parent != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    head.rubric?.let { r -> ChipTag(r.name, false) { onRubric(r) } }
                    head.parent?.let { p -> ChipTag(stringResource(R.string.part_of, p.label), p.supernova) { onTopic(p) } }
                }
            }
            if (head.related.isNotEmpty()) {
                Text(stringResource(R.string.related), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    head.related.forEach { t -> ChipTag(t.label, t.supernova) { onTopic(t) } }
                }
            }
        }
    }
}

/**
 * “In orbit” as the website's trend bar under the header: the live label (aurora dot that pulses, mono capitals),
 * then the topics – comets ☄ and supernovas ✦ in star colours – in a row that scrolls sideways.
 */
@Composable
fun OrbitRow(topics: List<OrbitTopic>, onTopic: (TopicRef) -> Unit, onAll: () -> Unit, modifier: Modifier = Modifier, title: String = stringResource(R.string.in_orbit)) {
    val planet = LocalPlanetColors.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            LiveDot(planet.aurora)
            Text(
                title.uppercase(),
                style = MetaStyle.copy(fontSize = 11.sp, letterSpacing = 0.8.sp),
                fontWeight = FontWeight.Bold,
                color = planet.aurora,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            androidx.compose.material3.TextButton(onClick = onAll) { Text(stringResource(R.string.all_topics) + " →") }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.s), contentPadding = PaddingValues(end = Space.l)) {
            items(topics, key = { it.key }) { t -> ChipTag(t.label, t.supernova, comet = t.status == "komet") { onTopic(t.ref) } }
        }
    }
}

/** The website's live dot (`.live::before`): a filled circle with a soft halo that breathes. */
@Composable
private fun LiveDot(color: androidx.compose.ui.graphics.Color) {
    val pulse by rememberInfiniteTransition(label = "live").animateFloat(
        initialValue = 0.15f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "halo",
    )
    Box(Modifier.size(16.dp).drawBehind {
        drawCircle(color.copy(alpha = pulse), radius = 8.dp.toPx())
        drawCircle(color, radius = 4.dp.toPx())
    })
}

private val MONTH = DateTimeFormatter.ofPattern("MM/yyyy")
