package com.guardian.app.ui.components

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NoPhotography
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardian.app.ui.theme.Muted

/** Shows the post-crash evidence: cabin + road stills and playable before/after audio clips. */
@Composable
fun CaptureEvidence(
    cabinPath: String?,
    roadPath: String?,
    audioPath: String?,
    audioDurationSec: Int,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    preAudioPath: String? = null,
    preAudioDurationSec: Int = 0,
    cabinAll: List<String> = emptyList(),
    roadAll: List<String> = emptyList(),
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (cabinAll.size > 1 || roadAll.size > 1) {
            PhotoStrip("Cabin", cabinAll)
            PhotoStrip("Road", roadAll)
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilePhoto(cabinPath, "Cabin", Modifier.weight(1f))
            FilePhoto(roadPath, "Road", Modifier.weight(1f))
        }
        if (preAudioPath != null) AudioPlayback("Before impact", preAudioPath, preAudioDurationSec, accent)
        if (audioPath != null) AudioPlayback("After impact", audioPath, audioDurationSec, accent)
    }
}

@Composable
private fun PhotoStrip(label: String, paths: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${label.uppercase()} · ${paths.size} photos", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            paths.forEachIndexed { i, p -> FilePhoto(p, "${label} ${i + 1}", Modifier.width(132.dp)) }
        }
    }
}

@Composable
private fun FilePhoto(path: String?, label: String, modifier: Modifier = Modifier) {
    val image = remember(path) {
        path?.let {
            runCatching {
                BitmapFactory.decodeFile(it, BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(
        modifier
            .aspectRatio(1.3f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x22000000)),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(image, label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Rounded.NoPhotography, null, tint = Muted)
        }
        Text(
            label.uppercase(),
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .background(Color(0x66000000), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun AudioPlayback(label: String, path: String, durationSec: Int, accent: Color) {
    var playing by remember { mutableStateOf(false) }
    val holder = remember { arrayOfNulls<MediaPlayer>(1) }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { holder[0]?.release() }
            holder[0] = null
        }
    }
    fun toggle() {
        if (playing) {
            runCatching { holder[0]?.release() }
            holder[0] = null
            playing = false
        } else {
            runCatching {
                holder[0] = MediaPlayer().apply {
                    setDataSource(path)
                    setOnCompletionListener {
                        runCatching { release() }
                        holder[0] = null
                        playing = false
                    }
                    prepare()
                    start()
                }
                playing = true
            }
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x22000000))
            .clickable { toggle() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
            null,
            tint = accent,
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(50))
                .background(accent.copy(alpha = 0.18f))
                .padding(4.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("$label · ${durationSec}s", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(if (playing) "Playing…" else "Tap to play the recorded clip", color = Muted, fontSize = 12.sp)
        }
    }
}
