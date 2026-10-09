package com.guardian.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhoneDisabled
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guardian.app.domain.CrashEventRecord
import com.guardian.app.ui.GuardianViewModel
import com.guardian.app.ui.components.Pill
import com.guardian.app.ui.components.formatCoords
import com.guardian.app.ui.components.formatTimestamp
import com.guardian.app.ui.components.severityColor
import com.guardian.app.ui.components.statusText
import com.guardian.app.ui.theme.Muted
import com.guardian.app.ui.theme.Violet
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(vm: GuardianViewModel) {
    val history by vm.history.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Incident history", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.syncHistory() }) { Icon(Icons.Rounded.Sync, "Sync with Guardian cloud") }
        }
        if (history.isEmpty()) {
            Text("No crashes recorded yet.", color = Muted)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(history, key = { it.eventId }) { HistoryItem(it) }
        }
    }
}

@Composable
private fun HistoryItem(e: CrashEventRecord) {
    val (label, color) = statusText(e.status)
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(severityColor(e.severity), RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp)))
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${e.severity.name} · ${(e.confidence * 100).roundToInt()}%",
                        fontWeight = FontWeight.Black,
                        color = severityColor(e.severity),
                        modifier = Modifier.weight(1f),
                    )
                    Pill(label, color)
                }
                Text(formatTimestamp(e.timestamp), color = Muted, fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (e.phoneConnected) Icons.Rounded.Smartphone else Icons.Rounded.PhoneDisabled,
                        null,
                        tint = if (e.phoneConnected) Muted else Violet,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    Text(
                        if (e.phoneConnected) "Phone was connected" else "Reported without the phone",
                        fontSize = 13.sp,
                        color = if (e.phoneConnected) Muted else Violet,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(formatCoords(e.latitude, e.longitude), fontSize = 12.sp, color = Muted)
                }
            }
        }
    }
}
