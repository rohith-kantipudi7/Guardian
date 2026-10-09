package com.guardian.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalPolice
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardian.app.domain.Responder
import com.guardian.app.domain.Severity
import com.guardian.app.ui.theme.Bad
import com.guardian.app.ui.theme.Critical
import com.guardian.app.ui.theme.Info
import com.guardian.app.ui.theme.Line
import com.guardian.app.ui.theme.Muted
import com.guardian.app.ui.theme.Ok
import com.guardian.app.ui.theme.Surface2
import com.guardian.app.ui.theme.Warn
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier
            .fillMaxWidth()
            .border(1.dp, Line, MaterialTheme.shapes.large),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title.uppercase(),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = Muted,
                    )
                    trailing()
                }
            }
            content()
        }
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
fun StatusRow(label: String, value: String, ok: Boolean? = null, icon: ImageVector? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp), tint = Muted)
            Spacer(Modifier.width(10.dp))
        }
        Text(label, Modifier.weight(1f), color = Muted)
        if (ok != null) {
            Box(Modifier.size(8.dp).background(if (ok) Ok else Bad, CircleShape))
            Spacer(Modifier.width(6.dp))
        }
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

/** One hop of the emergency path, shown as an icon node. */
data class ChainNode(val icon: ImageVector, val label: String, val ok: Boolean?)

/** Link between two nodes, e.g. "Bluetooth". */
data class ChainLink(val label: String, val ok: Boolean?)

private fun stateColor(ok: Boolean?) = when (ok) {
    true -> Ok
    false -> Bad
    null -> Muted
}

/** Visual of how the pieces connect: phone ⇄ device → cloud → responders. */
@Composable
fun ConnectionChain(nodes: List<ChainNode>, links: List<ChainLink>) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        nodes.forEachIndexed { i, n ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
                Box(
                    Modifier
                        .size(46.dp)
                        .background(stateColor(n.ok).copy(alpha = 0.14f), CircleShape)
                        .border(2.dp, stateColor(n.ok), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(n.icon, null, tint = stateColor(n.ok)) }
                Spacer(Modifier.height(6.dp))
                Text(n.label, fontSize = 11.sp, textAlign = TextAlign.Center, lineHeight = 13.sp)
            }
            if (i < links.size) {
                val l = links[i]
                Column(Modifier.weight(1f).padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(l.label, fontSize = 10.sp, color = stateColor(l.ok), fontWeight = FontWeight.Bold, maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth().height(3.dp).background(stateColor(l.ok).copy(alpha = if (l.ok == false) 0.35f else 0.8f), RoundedCornerShape(2.dp)))
                }
            }
        }
    }
}

fun responderIcon(type: String): ImageVector = when (type) {
    "POLICE" -> Icons.Rounded.LocalPolice
    "HOSPITAL" -> Icons.Rounded.LocalHospital
    else -> Icons.Rounded.FamilyRestroom
}

fun responderColor(r: Responder): Color = if (r.seen) Ok else Muted

@Composable
fun ResponderRow(r: Responder) {
    val color = responderColor(r)
    Row(
        Modifier
            .fillMaxWidth()
            .background(Surface2, MaterialTheme.shapes.medium)
            .border(1.dp, Line, MaterialTheme.shapes.medium)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(responderIcon(r.type), null, tint = color)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.label, fontWeight = FontWeight.Bold)
            Text(r.name, fontSize = 12.sp, color = Muted, maxLines = 1)
        }
        Pill(r.progress, color)
    }
}

fun severityColor(s: Severity?): Color = when (s) {
    Severity.CRITICAL -> Critical
    Severity.MODERATE -> Warn
    Severity.MINOR -> Info
    else -> Muted
}

fun formatCoords(lat: Double?, lon: Double?): String =
    if (lat == null || lon == null) "Unavailable" else "%.5f, %.5f".format(lat, lon)

private val dateFmt = DateTimeFormatter.ofPattern("dd MMM, HH:mm").withZone(ZoneId.systemDefault())

fun formatTimestamp(iso: String): String = runCatching { dateFmt.format(Instant.parse(iso)) }.getOrDefault(iso)

fun statusText(status: String): Pair<String, Color> = when (status) {
    "EMERGENCY_ACTIVE" -> "Responders alerted" to Critical
    "ACKNOWLEDGED" -> "Seen by responders" to Ok
    "RESOLVED" -> "Closed" to Ok
    "CANCELLED" -> "Cancelled · safe" to Muted
    "QUEUED_ON_DEVICE" -> "Queued on device" to Warn
    else -> status.lowercase().replace('_', ' ') to Muted
}
