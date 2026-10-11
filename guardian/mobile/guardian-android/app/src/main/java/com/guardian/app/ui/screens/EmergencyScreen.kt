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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.guardian.app.data.alert.CallEntry
import com.guardian.app.data.alert.CallStatus
import com.guardian.app.domain.CrashState
import com.guardian.app.ui.EmergencyAlert
import com.guardian.app.ui.components.CaptureEvidence
import com.guardian.app.ui.components.DataTable
import com.guardian.app.ui.components.formatCoords
import com.guardian.app.ui.theme.Bg
import com.guardian.app.ui.theme.Critical
import com.guardian.app.ui.theme.Muted
import com.guardian.app.ui.theme.Ok
import com.guardian.app.ui.theme.TextPrimary
import com.guardian.app.ui.theme.Warn
import com.guardian.app.ui.theme.neu
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val Label = 11.sp

@Composable
fun EmergencyScreen(alert: EmergencyAlert, onImSafe: () -> Unit, onDismiss: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "scale",
    )
    val elapsed = ((now - alert.startedAtMs) / 1000).coerceAtLeast(0)
    val status = when {
        alert.state == CrashState.VERIFYING ->
            "Calling for help in ${((alert.verifyRemainingMs ?: 0) / 1000.0).roundToInt()}s unless you are safe"
        alert.helpComingFrom != null -> "Alert seen by ${alert.helpComingFrom}"
        alert.delivered -> "Help has been alerted"
        else -> "Sending alert…"
    }
    val bb = alert.blackBox
    val hasMedia = bb != null && (bb.cabinImagePath != null || bb.roadImagePath != null || bb.audioPath != null)

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. Collision detected
        Column(
            Modifier.fillMaxWidth().neu(24.dp, 8.dp).padding(vertical = 22.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(80.dp).scale(pulse).neu(40.dp, 5.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.CarCrash, null, tint = Critical, modifier = Modifier.size(44.dp))
                }
            }
            Text(
                "COLLISION\nDETECTED",
                fontSize = 36.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Black,
                color = Critical,
                textAlign = TextAlign.Center,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!alert.delivered) {
                    CircularProgressIndicator(Modifier.size(14.dp), color = Warn, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(status, color = Muted, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
            Text("${elapsed / 60}m ${elapsed % 60}s ago", color = Muted, fontSize = 12.sp)
        }

        // 2. Photos
        if (hasMedia && bb != null) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("CAPTURED", color = Muted, fontSize = Label, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                CaptureEvidence(
                    cabinPath = bb.cabinImagePath,
                    roadPath = bb.roadImagePath,
                    audioPath = bb.audioPath,
                    audioDurationSec = bb.audioDurationSec,
                    accent = Ok,
                    preAudioPath = bb.preAudioPath,
                    preAudioDurationSec = bb.preAudioDurationSec,
                    cabinAll = bb.cabinImages,
                    roadAll = bb.roadImages,
                )
            }
        } else if (alert.state == CrashState.EMERGENCY_ACTIVE) {
            Row(Modifier.fillMaxWidth().neu(16.dp, 3.dp).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), color = Ok, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Capturing cabin and road photos…", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        // 3. Calling the emergency contacts
        if (alert.state == CrashState.EMERGENCY_ACTIVE || alert.state == CrashState.VERIFYING) {
            val photos = (bb?.cabinImages.orEmpty() + bb?.roadImages.orEmpty())
                .ifEmpty { listOfNotNull(bb?.cabinImagePath, bb?.roadImagePath) }
            CallingList(alert.calls, alert.shareText, photos)
        }

        // 4. I'm safe
        Button(
            onClick = onImSafe,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(containerColor = Ok, contentColor = Color.White),
        ) { Text("I'M SAFE", fontSize = 18.sp, fontWeight = FontWeight.Black) }

        // 5. Readings
        val log = bb?.sensorLog.orEmpty()
        val peak = Regex("peak ([0-9.]+) g").find(log)?.groupValues?.get(1)
        val rot = Regex("rot ([0-9.]+) rad/s").find(log)?.groupValues?.get(1)
        Column(Modifier.fillMaxWidth().neu(16.dp, 3.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("READINGS", color = Muted, fontSize = Label, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            DataTable(
                headers = listOf("Reading", "Value"),
                rows = listOf(
                    listOf("Peak impact", peak?.let { "$it g" } ?: "—"),
                    listOf("Rotation", rot?.let { "$it rad/s" } ?: "—"),
                    listOf("Location", if (alert.latitude != null) formatCoords(alert.latitude, alert.longitude) else "—"),
                    listOf("Time", "${elapsed / 60}m ${elapsed % 60}s ago"),
                ),
                weights = listOf(1f, 1.4f),
            )
        }

        TextButton(onClick = onDismiss) { Text("Hide (emergency stays active)", color = Muted) }
    }
}

@Composable
private fun CallingList(calls: List<CallEntry>, shareText: String, photos: List<String>) {
    val context = LocalContext.current
    val ring by rememberInfiniteTransition(label = "ring").animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "ring",
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (calls.any { it.status != CallStatus.QUEUED }) "CALLING" else "WILL CALL", color = Critical, fontSize = Label, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        if (calls.isEmpty()) {
            Text("No emergency contacts saved. Add them in Settings.", color = Muted, fontSize = 13.sp)
        }
        calls.forEach { c ->
            Row(Modifier.fillMaxWidth().neu(16.dp, 3.dp).padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.label, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(c.number, color = Muted, fontSize = 12.sp)
                }
                val (text, tone) = when (c.status) {
                    CallStatus.QUEUED -> "Queued" to Muted
                    CallStatus.CALLING -> "Calling" to Critical
                    CallStatus.DONE -> "Called" to Ok
                    CallStatus.FAILED -> "Failed" to Warn
                    CallStatus.TEST_ONLY -> "Test mode · not dialled" to Muted
                }
                Text(text, color = tone, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (c.status == CallStatus.CALLING) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(10.dp).scale(ring).background(Critical, CircleShape))
                }
            }
        }
    }
}
