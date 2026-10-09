package com.guardian.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CarCrash
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guardian.app.data.sensing.AnomalyEvent
import com.guardian.app.data.sensing.AnomalyType
import com.guardian.app.ui.GuardianViewModel
import com.guardian.app.ui.components.formatTimestampMs
import com.guardian.app.ui.theme.Critical
import com.guardian.app.ui.theme.Muted
import com.guardian.app.ui.theme.Ok
import com.guardian.app.ui.theme.Warn

@Composable
fun HistoryScreen(vm: GuardianViewModel) {
    val anomalies by vm.anomalies.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Abnormal activity", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        }
        Text("Hard braking, sharp swerves, impacts and crashes flagged by the phone.", color = Muted, fontSize = 13.sp)
        if (anomalies.isEmpty()) {
            Text(
                "Nothing abnormal yet — drive normally and events will appear here.",
                color = Muted,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp),
        ) {
            items(anomalies, key = { it.timestampMs }) { AnomalyItem(it) }
        }
    }
}

private fun iconFor(type: AnomalyType): ImageVector = when (type) {
    AnomalyType.HARD_BRAKE -> Icons.Rounded.Speed
    AnomalyType.SHARP_SWERVE -> Icons.Rounded.Air
    AnomalyType.IMPACT -> Icons.Rounded.Bolt
    AnomalyType.CRASH -> Icons.Rounded.CarCrash
    AnomalyType.DEVICE_OFFLINE -> Icons.Rounded.Router
    AnomalyType.SOS -> Icons.Rounded.Campaign
}

@Composable
private fun AnomalyItem(a: AnomalyEvent) {
    val tone: Color = when {
        a.critical || a.type == AnomalyType.CRASH -> Critical
        a.type == AnomalyType.DEVICE_OFFLINE -> Muted
        a.type == AnomalyType.IMPACT -> Warn
        else -> Ok
    }
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(tone, RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp)))
            Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(iconFor(a.type), null, tint = tone)
                Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(a.type.label, fontWeight = FontWeight.Black, color = tone)
                    Text(a.detail, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(formatTimestampMs(a.timestampMs), color = Muted, fontSize = 12.sp)
                }
                if (a.critical) Icon(Icons.Rounded.Warning, null, tint = Critical)
            }
        }
    }
}
