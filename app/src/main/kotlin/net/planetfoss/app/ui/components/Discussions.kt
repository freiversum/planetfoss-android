// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.components

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.planetfoss.app.R
import net.planetfoss.app.ui.theme.MetaStyle
import net.planetfoss.app.ui.theme.Radius
import net.planetfoss.app.ui.theme.Space
import net.planetfoss.core.api.Discussion
import net.planetfoss.core.api.FediReply
import java.time.Instant

/** Brand-ish colour per service, as on the website's `.disc` pills; Lobsters lighter in dark mode for contrast. */
@Composable
private fun siteColor(site: String): Color = when (site) {
    "hn" -> Color(0xFFE86A1A)
    "lobsters" -> if (isSystemInDarkTheme()) Color(0xFFFF7A6E) else Color(0xFFAC130D)
    "bluesky" -> Color(0xFF1185FE)
    else -> MaterialTheme.colorScheme.primary
}

/** “12 points · 3 comments” for threads elsewhere, “2 replies” or “join in” for PlanetFOSS's own posts. */
@Composable
private fun facts(d: Discussion): String = when {
    d.own && d.comments > 0 -> pluralStringResource(R.plurals.disc_replies, d.comments, d.comments)
    d.own -> stringResource(R.string.disc_join)
    else -> pluralStringResource(R.plurals.disc_points, d.points, d.points) + " · " + pluralStringResource(R.plurals.disc_comments, d.comments, d.comments)
}

/**
 * “Discussed on”: the biggest thread per service as outlined pills (the website's bundle and topic head). A tap opens the
 * thread in the browser mode the user chose; the app itself never talks to these services.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscussionRow(list: List<Discussion>, modifier: Modifier = Modifier) {
    if (list.isEmpty()) return
    val actions = LocalItemActions.current
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        Text(
            stringResource(R.string.disc_on).uppercase(),
            style = MetaStyle.copy(fontSize = 11.sp, letterSpacing = 0.8.sp),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        val context = androidx.compose.ui.platform.LocalContext.current
        list.forEach { d ->
            val text = facts(d)
            OutlinedCard(
                onClick = { actions.open(d.url) },
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.heightIn(min = 40.dp).semantics { contentDescription = "${d.name}, $text" },
            ) {
                Row(Modifier.heightIn(min = 40.dp).padding(horizontal = Space.m), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Text(d.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = siteColor(d.site))
                    Text(text, style = MetaStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        // boost without an account in the app (STRATEGIE.md 4.2): the share sheet hands PlanetFOSS's own post to the user's
        // Mastodon or Bluesky app, which opens it for boosting or quoting
        list.firstOrNull { it.own && it.site == "mastodon" }?.let { m ->
            TextButton(onClick = { shareUrl(context, m.url) }, modifier = Modifier.align(Alignment.CenterVertically)) {
                Text(stringResource(R.string.disc_share))
            }
        }
    }
}

private fun relative(time: Instant): String =
    DateUtils.getRelativeTimeSpanString(time.toEpochMilli(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

/**
 * Replies from the fediverse below a supernova (the website's `.fedi`): name, handle, service, time and text, no avatars;
 * replies to replies indented, PlanetFOSS's own ones tinted. A tap opens the reply; below, links to the own posts to join in.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RepliesBlock(replies: List<FediReply>, posts: List<Discussion>, modifier: Modifier = Modifier, onBlock: (FediReply) -> Unit = {}) {
    if (replies.isEmpty()) return
    val actions = LocalItemActions.current
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        SectionHead(stringResource(R.string.fedi_replies), Modifier.padding(top = Space.s), hint = stringResource(R.string.fedi_hint))
        replies.forEach { r ->
            val service = if (r.site == "bluesky") "Bluesky" else "Mastodon"
            Card(
                onClick = { actions.open(r.url) },
                shape = Radius.card,
                colors = CardDefaults.cardColors(
                    containerColor = if (r.own) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                modifier = Modifier.fillMaxWidth().padding(start = (16 * (r.depth - 1).coerceIn(0, 2)).dp)
                    .semantics(mergeDescendants = true) { contentDescription = "${r.name}, ${r.handle}, $service, ${relative(r.postedAt)}: ${r.text}" },
            ) {
                Column(Modifier.padding(horizontal = Space.l, vertical = Space.m), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Row(verticalAlignment = Alignment.Top) {
                        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                            Text(r.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(r.handle, style = MetaStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.align(Alignment.CenterVertically))
                            Text("$service · ${relative(r.postedAt)}", style = MetaStyle, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.CenterVertically))
                        }
                        // Play's policy for user-generated content: report a reply, block an account (left out on the phone only)
                        if (!r.own) ReplyMenu(r, onBlock = { onBlock(r) }, onReport = { reportReply(context, r) })
                    }
                    Text(r.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        val own = posts.filter { it.own }
        if (own.isNotEmpty()) {
            FlowRow(verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.fedi_join), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically))
                own.forEach { d -> TextButton(onClick = { actions.open(d.url) }) { Text(d.name + " →") } }
            }
        }
    }
}

/** The menu of one reply: report it to PlanetFOSS or block the account. */
@Composable
private fun ReplyMenu(reply: FediReply, onBlock: () -> Unit, onReport: () -> Unit) {
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.MoreVert, stringResource(R.string.reply_menu, reply.handle), Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.reply_report)) }, onClick = { open = false; onReport() })
            DropdownMenuItem(text = { Text(stringResource(R.string.reply_block, reply.handle)) }, onClick = { open = false; onBlock() })
        }
    }
}

/** Where reports about replies go (the address of the website's imprint). */
internal const val REPORT_ADDRESS = "info@planetfoss.net"

/** Opens the mail app with a report about [reply]; the address, the reply's link and the account are filled in. */
private fun reportReply(context: android.content.Context, reply: FediReply) {
    val body = context.getString(R.string.reply_report_body, reply.url, reply.handle, reply.site)
    val mail = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:"))
        .putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf(REPORT_ADDRESS))
        .putExtra(android.content.Intent.EXTRA_SUBJECT, context.getString(R.string.reply_report_subject))
        .putExtra(android.content.Intent.EXTRA_TEXT, body)
    try {
        context.startActivity(mail)
    } catch (_: android.content.ActivityNotFoundException) {
        android.widget.Toast.makeText(context, context.getString(R.string.reply_report_no_mail, REPORT_ADDRESS), android.widget.Toast.LENGTH_LONG).show()
    }
}
