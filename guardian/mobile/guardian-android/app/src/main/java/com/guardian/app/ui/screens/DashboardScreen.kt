package com.guardian.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Satellite
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guardian.app.data.settings.DeviceSource
import com.guardian.app.domain.LinkState
import com.guardian.app.ui.GuardianViewModel
import com.guardian.app.ui.components.CaptureEvidence
import com.guardian.app.ui.components.ChainLink
import com.guardian.app.ui.components.ChainNode
import com.guardian.app.ui.components.ConnectionChain
import com.guardian.app.ui.components.Pill
import com.guardian.app.ui.components.SectionCard
import com.guardian.app.ui.components.StatusRow
import com.guardian.app.ui.components.formatTimestampMs
import com.guardian.app.ui.theme.BrandBrush
import com.guardian.app.ui.theme.Critical
import com.guardian.app.ui.theme.Line
import com.guardian.app.ui.theme.Muted
import com.guardian.app.ui.theme.Ok
import com.guardian.app.ui.theme.Warn

@Composable
fun DashboardScreen(vm: GuardianViewModel, onPair: () -> Unit, onOpenHistory: () -> Unit) {
    val link by vm.link.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val anomalies by vm.anomalies.collectAsStateWithLifecycle()
    val sensing by vm.phoneSensing.collectAsStateWithLifecycle()
    val demoCapture by vm.demoCapture.collectAsStateWithLifecycle()
    val capturing by vm.capturing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val camerasArmed = ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
    val linked = link == LinkState.CONNECTED
    val s = status
    val lte = s?.cellular == "CONNECTED"
    val deviceReady = s != null && lte
    // Mobile is primary: the car is protected whenever the phone's own crash detection is running.
    val protected = sensing.active

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).background(BrandBrush, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Shield, null, tint = Color(0xFF06121F), modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("GUARDIAN", style = MaterialTheme.typography.headlineSmall, letterSpacing = 2.sp)
                Text("${settings.vehicleName} · ${settings.vehicleRegistration}", color = Muted, fontSize = 13.sp)
            }
            Pill(if (protected) "Protected" else "Unprotected", if (protected) Ok else Warn)
        }

        // Hero: is the car protected right now?
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf((if (protected) Ok else Warn).copy(alpha = 0.22f), MaterialTheme.colorScheme.surface),
                    ),
                    MaterialTheme.shapes.large,
                )
                .border(1.dp, (if (protected) Ok else Warn).copy(alpha = 0.35f), MaterialTheme.shapes.large)
                .padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(64.dp).background((if (protected) Ok else Warn).copy(alpha = 0.16f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (protected) Icons.Rounded.GppGood else Icons.Rounded.GppMaybe,
                            null,
                            tint = if (protected) Ok else Warn,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            if (protected) "PROTECTED" else "NOT PROTECTED",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = if (protected) Ok else Warn,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (protected) "Phone crash detection is live." +
                                (if (deviceReady) " Backup device online." else " No backup device connected.")
                            else "Phone sensors unavailable — crash detection is off.",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatChip("Impact", "%.1f g".format(sensing.accelG), Icons.Rounded.Speed, if (protected) Ok else Muted)
                    StatChip("Rotation", "%.1f".format(sensing.rotationRadS), Icons.Rounded.ScreenRotation, if (protected) Ok else Muted)
                    StatChip("Cameras", if (camerasArmed) "Armed" else "Off", Icons.Rounded.PhotoCamera, if (camerasArmed) Ok else Warn)
                }
            }
        }

        SectionCard("Emergency path") {
            ConnectionChain(
                nodes = listOf(
                    ChainNode(Icons.Rounded.Smartphone, "Phone", sensing.active),
                    ChainNode(Icons.Rounded.Router, "Device", s != null),
                    ChainNode(Icons.Rounded.Groups, "Responders", null),
                ),
                links = listOf(
                    ChainLink("Backup", s != null),
                    ChainLink("Alert", if (s == null) null else lte),
                ),
            )
            if (!linked) {
                Button(onClick = onPair, Modifier.fillMaxWidth()) {
                    Text("Connect backup device")
                }
            }
        }

        SectionCard("Backup device (IoT)") {
            if (s == null) {
                Text("No Guardian IoT device connected. The phone protects you on its own; pair the device as a backup black box.", color = Muted, fontSize = 13.sp)
            } else {
                StatusRow("Mobile link (LTE)", s.cellular?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "—", lte, Icons.Rounded.CellTower)
                StatusRow("GPS", s.gps?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "—", s.gps == "AVAILABLE", Icons.Rounded.Satellite)
                StatusRow("Backup battery", s.battery?.let { "$it%" } ?: "—", s.battery?.let { it > 20 }, Icons.Rounded.BatteryStd)
                if ((s.pendingEvents ?: 0) > 0) {
                    StatusRow("Waiting to send", "${s.pendingEvents} crash report(s)", false, Icons.Rounded.Inventory2)
                }
            }
        }

        val lastAnomaly = anomalies.firstOrNull()
        SectionCard("Recent activity", trailing = {
            Text("All", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onOpenHistory))
            Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }) {
            if (lastAnomaly == null) {
                Text("No abnormal activity detected. Drive safe.", color = Muted)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Warning,
                        null,
                        tint = if (lastAnomaly.critical) Critical else Warn,
                        modifier = Modifier.size(20.dp).padding(end = 4.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            lastAnomaly.type.label,
                            color = if (lastAnomaly.critical) Critical else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Black,
                        )
                        Text("${lastAnomaly.detail} · ${formatTimestampMs(lastAnomaly.timestampMs)}", color = Muted, fontSize = 13.sp)
                    }
                }
            }
        }

        if (settings.demoMode) {
            SectionCard("Black-box capture · demo (temporary)") {
                Text(
                    "Opens both cameras + the mic now and records voice before and after — the real crash pipeline, no impact needed.",
                    color = Muted,
                    fontSize = 13.sp,
                )
                Button(
                    onClick = { vm.runCaptureDemo() },
                    enabled = !capturing,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    if (capturing) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Capturing…", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Rounded.PhotoCamera, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Run capture test", fontWeight = FontWeight.Bold)
                    }
                }
                demoCapture?.let { bb ->
                    CaptureEvidence(
                        cabinPath = bb.cabinImagePath,
                        roadPath = bb.roadImagePath,
                        audioPath = bb.audioPath,
                        audioDurationSec = bb.audioDurationSec,
                        preAudioPath = bb.preAudioPath,
                        preAudioDurationSec = bb.preAudioDurationSec,
                    )
                    TextButton(onClick = { vm.clearDemoCapture() }) { Text("Clear") }
                }
                OutlinedButton(onClick = { vm.sendBlackBoxToDevice() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Send, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Send black box → device")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun RowScope.StatChip(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    Column(
        Modifier
            .weight(1f)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .border(1.dp, Line, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black, fontSize = 15.sp)
        Text(label, color = Muted, fontSize = 11.sp)
    }
}
