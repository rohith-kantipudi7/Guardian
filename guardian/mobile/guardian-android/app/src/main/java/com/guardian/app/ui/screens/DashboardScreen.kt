package com.guardian.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Satellite
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guardian.app.data.settings.DeviceSource
import com.guardian.app.domain.LinkState
import com.guardian.app.ui.GuardianViewModel
import com.guardian.app.ui.components.ChainLink
import com.guardian.app.ui.components.ChainNode
import com.guardian.app.ui.components.ConnectionChain
import com.guardian.app.ui.components.Pill
import com.guardian.app.ui.components.SectionCard
import com.guardian.app.ui.components.StatusRow
import com.guardian.app.ui.components.formatTimestamp
import com.guardian.app.ui.components.severityColor
import com.guardian.app.ui.components.statusText
import com.guardian.app.ui.theme.Critical
import com.guardian.app.ui.theme.Muted
import com.guardian.app.ui.theme.Ok
import com.guardian.app.ui.theme.Warn
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(vm: GuardianViewModel, onPair: () -> Unit, onOpenHistory: () -> Unit) {
    val link by vm.link.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val linked = link == LinkState.CONNECTED
    val s = status
    val lte = s?.cellular == "CONNECTED"
    val protected = s != null && lte

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).background(Critical, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Shield, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("GUARDIAN", style = MaterialTheme.typography.headlineMedium)
                Text("${settings.vehicleName} · ${settings.vehicleRegistration}", color = Muted, fontSize = 13.sp)
            }
            Pill(if (settings.deviceSource == DeviceSource.BLE) "ESP32" else settings.deviceSource.label.substringBefore(' '), Muted)
        }

        // Hero: is the car protected right now?
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf((if (protected) Ok else Warn).copy(alpha = 0.28f), MaterialTheme.colorScheme.surface),
                    ),
                    MaterialTheme.shapes.large,
                )
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (protected) Icons.Rounded.GppGood else Icons.Rounded.GppMaybe,
                    null,
                    tint = if (protected) Ok else Warn,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        if (protected) "PROTECTED" else if (s == null) "DEVICE NOT REACHABLE" else "DEVICE OFFLINE",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = if (protected) Ok else Warn,
                    )
                    Text(
                        if (protected) "Crash detection is running on your vehicle device."
                        else if (s == null) "Connect to the vehicle device to see its status."
                        else "No mobile coverage. Crashes are stored and sent when coverage returns.",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                    )
                }
            }
        }

        SectionCard("How the emergency reaches help") {
            ConnectionChain(
                nodes = listOf(
                    ChainNode(Icons.Rounded.Smartphone, "This\nphone", linked),
                    ChainNode(Icons.Rounded.Router, "Vehicle\ndevice", s != null),
                    ChainNode(Icons.Rounded.Cloud, "Guardian\ncloud", if (s == null) null else lte),
                    ChainNode(Icons.Rounded.Groups, "Police ·\nAmbulance ·\nFamily", null),
                ),
                links = listOf(
                    ChainLink("BLE", linked),
                    ChainLink("LTE", if (s == null) null else lte),
                    ChainLink("SMS", null),
                ),
            )
            Text(
                "The device detects the crash and calls for help over its own mobile link. " +
                    "This phone only shows alerts and lets you say I'M SAFE — it is not needed for help to come.",
                color = Muted,
                fontSize = 12.sp,
            )
            if (!linked) {
                Button(onClick = onPair, Modifier.fillMaxWidth()) {
                    Text(if (settings.deviceSource == DeviceSource.BLE) "Connect Guardian device" else "Connect to vehicle device")
                }
            }
        }

        SectionCard("Vehicle device") {
            StatusRow("Mobile link (LTE)", s?.cellular?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "—", s?.let { lte }, Icons.Rounded.CellTower)
            StatusRow("GPS", s?.gps?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "—", s?.let { it.gps == "AVAILABLE" }, Icons.Rounded.Satellite)
            StatusRow("Backup battery", s?.battery?.let { "$it%" } ?: "—", s?.battery?.let { it > 20 }, Icons.Rounded.BatteryStd)
            if ((s?.pendingEvents ?: 0) > 0) {
                StatusRow("Waiting to send", "${s?.pendingEvents} crash report(s)", false, Icons.Rounded.Inventory2)
            }
        }

        val last = history.firstOrNull()
        SectionCard("Last incident", trailing = {
            Text("History", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onOpenHistory))
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }) {
            if (last == null) {
                Text("No crashes recorded. Drive safe.", color = Muted)
            } else {
                val (label, color) = statusText(last.status)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${last.severity.name} · ${(last.confidence * 100).roundToInt()}%",
                            color = severityColor(last.severity),
                            fontWeight = FontWeight.Black,
                        )
                        Text(formatTimestamp(last.timestamp), color = Muted, fontSize = 13.sp)
                    }
                    Pill(label, color)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
