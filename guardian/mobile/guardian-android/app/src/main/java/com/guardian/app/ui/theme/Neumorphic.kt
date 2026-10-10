package com.guardian.app.ui.theme

import android.graphics.Paint
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Raised neumorphic surface: a dark shadow bottom-right and a light one top-left behind a rounded rect. */
fun Modifier.neu(corner: Dp = 20.dp, depth: Dp = 6.dp, fill: Color = Bg): Modifier = drawBehind {
    val r = corner.toPx()
    val d = depth.toPx()
    drawIntoCanvas { canvas ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).also { it.color = fill.toArgb() }
        paint.setShadowLayer(d * 1.8f, d, d, ShadowDark.toArgb())
        canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
        paint.setShadowLayer(d * 1.8f, -d, -d, ShadowLight.toArgb())
        canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
    }
}
