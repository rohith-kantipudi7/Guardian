package com.guardian.app.data.alert

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Full-screen alert drawn above the system in-call screen, so the occupant keeps seeing Guardian while
 * the calls run underneath. Needs "Display over other apps"; without it the caller falls back to
 * pulling the activity forward.
 */
class AlertOverlay(private val context: Context, private val onImSafe: () -> Unit) {

    private val wm = context.getSystemService(WindowManager::class.java)
    private var root: LinearLayout? = null
    private var list: LinearLayout? = null

    fun canShow() = Settings.canDrawOverlays(context)

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(context).apply {
        this.text = s
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    fun show(entries: List<CallEntry>, location: String?) {
        if (root != null) { update(entries); return }
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#2A1B4D"))
            setPadding(dp(24), dp(72), dp(24), dp(24))
        }
        col.addView(text("COLLISION\nDETECTED", 34f, Color.parseColor("#FF5A6A"), true))
        col.addView(text("Calling for help", 15f, Color.parseColor("#C9B8F0")).apply { setPadding(0, dp(8), 0, dp(20)) })
        val l = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        list = l
        col.addView(l, LinearLayout.LayoutParams(-1, -2))
        col.addView(
            Button(context).apply {
                this.text = "I'M SAFE"
                textSize = 18f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#2FA36B"))
                setOnClickListener { onImSafe() }
            },
            LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(24) },
        )
        col.addView(
            text("Location: ${location ?: "unavailable"}", 13f, Color.parseColor("#C9B8F0")).apply { setPadding(0, dp(20), 0, 0) },
        )
        val lp = WindowManager.LayoutParams(
            -1, -1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.OPAQUE,
        )
        runCatching { wm.addView(col, lp); root = col }
        update(entries)
    }

    fun update(entries: List<CallEntry>) {
        val l = list ?: return
        l.removeAllViews()
        entries.forEach { e ->
            val (label, color) = when (e.status) {
                CallStatus.QUEUED -> "Queued" to "#C9B8F0"
                CallStatus.CALLING -> "Calling" to "#FF5A6A"
                CallStatus.DONE -> "Called" to "#2FA36B"
                CallStatus.FAILED -> "Failed" to "#F0A63A"
                CallStatus.TEST_ONLY -> "Test mode" to "#C9B8F0"
            }
            val row = LinearLayout(context).apply { setPadding(0, dp(8), 0, dp(8)) }
            row.addView(
                TextView(context).apply { text = "${e.label}  ${e.number}"; setTextColor(Color.WHITE); textSize = 16f },
                LinearLayout.LayoutParams(0, -2, 1f),
            )
            row.addView(TextView(context).apply { text = label; setTextColor(Color.parseColor(color)); textSize = 14f; setTypeface(typeface, Typeface.BOLD) })
            l.addView(row)
        }
    }

    fun hide() {
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        list = null
    }
}
