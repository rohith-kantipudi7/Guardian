package com.guardian.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.CarCrash
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardian.app.domain.CrashState
import com.guardian.app.ui.EmergencyAlert
import com.guardian.app.ui.components.ResponderRow
import com.guardian.app.ui.components.formatCoords
import com.guardian.app.ui.components.severityColor
import com.guardian.app.ui.theme.Muted
import com.guardian.app.ui.theme.Warn
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun EmergencyScreen(alert: EmergencyAlert, onImSafe: () -> Unit, onDismiss: () -> Unit) {
    val color = severityColor(alert.severity)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "scale",
    )
    val elapsed = ((now - alert.startedAtMs) / 1000).coerceAtLeast(0)
    val headline = when {
        alert.state == CrashState.VERIFYING ->
            "Calling for help in ${((alert.verifyRemainingMs ?: 0) / 1000.0).roundToInt()}s unless you are safe"
        alert.helpComingFrom != null -> "Alert seen by ${alert.helpComingFrom}"
        alert.delivered -> "Police, ambulance and your family have been alerted"
        alert.state == CrashState.WAIT_FOR_ACK -> "No coverage — the device will keep trying to send"
        else -> "Sending from the vehicle device…"
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF4A0D0D), Color(0xFF140708))))
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.size(88.dp).scale(pulse).background(color.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.CarCrash, null, tint = color, modifier = Modifier.size(48.dp)) }
        Text("CRASH DETECTED", fontSize = 32.sp, fontWeight = FontWeight.Black, color = Color(0xFFFF6B6B))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(alert.severity?.name ?: "—", fontSize = 20.sp, fontWeight = FontWeight.Black, color = color)
            Text(
                "  ·  ${alert.confidence?.let { "${(it * 100).roundToInt()}%" } ?: "—"}  ·  ${elapsed / 60}m ${elapsed % 60}s ago",
                color = Color(0xFFFECACA),
            )
        }

        Box(
            Modifier.fillMaxWidth().background(Color(0x33000000), androidx.compose.material3.MaterialTheme.shapes.medium).padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!alert.delivered) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Warn, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                }
                Text(headline, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        alert.aiAssessment?.let { ai ->
            Column(
                Modifier.fillMaxWidth()
                    .background(Color(0x22000000), androidx.compose.material3.MaterialTheme.shapes.medium)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Psychology, null, tint = Color(0xFF93C5FD), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("ON-DEVICE AI", color = Color(0xFF93C5FD), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                }
                Text(ai.whatHappened, color = Color.White, fontSize = 14.sp)
                Text(ai.occupantState, color = Color(0xFFFECACA), fontSize = 13.sp)
                Text(ai.recommendation, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        alert.blackBox?.let { bb ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.GraphicEq, null, tint = Color(0xFF93C5FD), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (bb.audioPath != null) "In-cabin audio captured (${bb.audioDurationSec}s)"
                        else "Black box: sensor log stored",
                        color = Color(0xFFFECACA),
                        fontSize = 13.sp,
                    )
                }
                if (bb.syncedToDevice) {
                    Text("Black box synced to vehicle device", color = Color(0xFF86EFAC), fontSize = 12.sp)
                }
            }
        }

        if (alert.responders.isNotEmpty()) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("WHO HAS BEEN ALERTED", color = Color(0xFFFECACA), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                alert.responders.forEach { ResponderRow(it) }
                Text(
                    "They received your location, vehicle and crash details by SMS. \"Seen\" means they opened it.",
                    color = Muted,
                    fontSize = 12.sp,
                )
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.LocationOn, null, tint = Color(0xFFFECACA))
            Spacer(Modifier.width(8.dp))
            Text(
                if (alert.latitude != null) formatCoords(alert.latitude, alert.longitude) else "Location sent by the vehicle device",
                color = Color.White,
            )
        }

        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onImSafe,
            modifier = Modifier.fillMaxWidth().height(76.dp),
            shape = androidx.compose.material3.MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A), contentColor = Color.White),
        ) { Text("I'M SAFE", fontSize = 28.sp, fontWeight = FontWeight.Black) }
        Text("Tapping cancels the emergency and tells every responder to stand down.", color = Muted, fontSize = 12.sp)
        TextButton(onClick = onDismiss) { Text("Hide (emergency stays active)", color = Color(0xFFFECACA)) }
    }
}
