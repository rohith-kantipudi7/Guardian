package com.guardian.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Satellite
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guardian.app.data.settings.DeviceSource
import com.guardian.app.domain.LinkState
import com.guardian.app.domain.Scenarios
import com.guardian.app.ui.GuardianViewModel
import com.guardian.app.ui.components.Pill
import com.guardian.app.ui.components.SectionCard
import com.guardian.app.ui.components.StatusRow
import com.guardian.app.ui.components.formatCoords
import com.guardian.app.ui.theme.Critical
import com.guardian.app.ui.theme.Muted
import com.guardian.app.ui.theme.Ok
import com.guardian.app.ui.theme.Warn
import java.text.DateFormat
import java.util.Date

private val SCENARIOS = listOf(
    "Normal drive" to Scenarios.NORMAL,
    "Hard brake" to Scenarios.HARD_BRAKE,
    "Pothole" to Scenarios.POTHOLE,
    "Minor impact" to Scenarios.MINOR_IMPACT,
    "Rollover" to Scenarios.ROLLOVER,
)

@Composable
fun DeviceScreen(vm: GuardianViewModel, onPair: () -> Unit) {
    val link by vm.link.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val crash by vm.crash.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = status
    val connected = link == LinkState.CONNECTED

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Vehicle device", style = MaterialTheme.typography.headlineSmall)

        SectionCard("Phone ⇄ device link") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (connected) Icons.Rounded.Bluetooth else Icons.Rounded.BluetoothDisabled,
                    null,
                    tint = if (connected) Ok else Warn,
                    modifier = Modifier.size(36.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when (link) {
                            LinkState.CONNECTED -> "Connected"
                            LinkState.CONNECTING -> "Connecting…"
                            LinkState.SCANNING -> "Scanning…"
                            LinkState.DISCONNECTED -> "Not connected"
                        },
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                    )
                    Text(settings.deviceSource.detail, color = Muted, fontSize = 12.sp)
                }
                Pill(settings.deviceSource.label.substringBefore(' '), Muted)
            }
            if (settings.deviceSource == DeviceSource.VIRTUAL) {
                Text(
                    "Virtual device at ${settings.virtualDeviceUrl}. It plays the role of the ESP32: " +
                        "it detects crashes and reports them to the Guardian cloud over its own (simulated) LTE.",
                    color = Muted,
                    fontSize = 12.sp,
                )
            }
            if (connected) {
                OutlinedButton(onClick = { vm.forcePhoneDisconnected() }, Modifier.fillMaxWidth()) { Text("Disconnect phone") }
            } else {
                Button(onClick = onPair, Modifier.fillMaxWidth()) { Text("Connect") }
            }
        }

        SectionCard("Live device status") {
            StatusRow("Device", s?.deviceId?.ifEmpty { null } ?: settings.deviceId, icon = Icons.Rounded.Memory)
            StatusRow("Vehicle", s?.vehicleId?.ifEmpty { null } ?: "—", icon = Icons.Rounded.DirectionsCar)
            StatusRow("Mobile link (LTE)", s?.cellular ?: "—", s?.let { it.cellular == "CONNECTED" }, Icons.Rounded.CellTower)
            StatusRow("GPS", s?.gps ?: "—", s?.let { it.gps == "AVAILABLE" }, Icons.Rounded.Satellite)
            StatusRow("Location", formatCoords(s?.latitude, s?.longitude), icon = Icons.Rounded.Place)
            StatusRow("Backup battery", s?.battery?.let { "$it%" } ?: "—", s?.battery?.let { it > 20 }, Icons.Rounded.BatteryStd)
            StatusRow("Crash engine", (crash?.state ?: s?.crashState)?.name?.replace('_', ' ') ?: "—", icon = Icons.Rounded.Sensors)
            StatusRow("Waiting to send", "${s?.pendingEvents ?: 0}", s?.let { it.pendingEvents == 0 }, Icons.Rounded.Inventory2)
            StatusRow(
                "Last update",
                s?.lastUpdateMs?.takeIf { it > 0 }?.let { DateFormat.getTimeInstance().format(Date(it)) } ?: "—",
                icon = Icons.Rounded.Schedule,
            )
        }

        if (settings.demoMode && settings.deviceSource != DeviceSource.BLE) {
            SectionCard("Test the device (demo)") {
                Text(
                    "Feeds recorded sensor patterns into the device's real crash engine. Nothing is crashed. " +
                        "Only crashes call for help — the rest are rejected as normal driving.",
                    color = Muted,
                    fontSize = 12.sp,
                )
                SCENARIOS.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (label, scenario) ->
                            OutlinedButton(onClick = { vm.simulate(scenario) }, enabled = connected, modifier = Modifier.weight(1f)) {
                                Text(label)
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                Button(
                    onClick = { vm.simulate(Scenarios.SEVERE_CRASH) },
                    enabled = connected,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Critical),
                ) { Text("Severe crash", fontWeight = FontWeight.Black) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.cellularOff() }, enabled = connected, modifier = Modifier.weight(1f)) { Text("Dead zone") }
                    OutlinedButton(onClick = { vm.cellularOn() }, enabled = connected, modifier = Modifier.weight(1f)) { Text("Coverage back") }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
