package com.focusguard.app

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

class CountdownOverlay(private val service: AccessibilityService) {

    private val handler = Handler(Looper.getMainLooper())
    private var overlayView: TextView? = null
    private var tickRunnable: Runnable? = null
    private var endTimeMs = 0L

    private val wm: WindowManager
        get() = service.getSystemService(
            AccessibilityService.WINDOW_SERVICE
        ) as WindowManager

    fun show(durationMs: Long) {
        hide()
        endTimeMs = System.currentTimeMillis() + durationMs

        val tv = TextView(service).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = pillBackground()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(48)
        }

        try {
            wm.addView(tv, params)
            overlayView = tv
            startTick()
        } catch (e: Exception) {
            FocusGuardLog.e("Overlay add failed: ${e.message}")
        }
    }

    fun hide() {
        tickRunnable?.let { handler.removeCallbacks(it) }
        tickRunnable = null
        overlayView?.let {
            try {
                wm.removeView(it)
            } catch (_: Exception) {
            }
        }
        overlayView = null
        endTimeMs = 0L
    }

    private fun startTick() {
        val runnable = object : Runnable {
            override fun run() {
                val remaining = endTimeMs - System.currentTimeMillis()
                if (remaining <= 0) {
                    hide()
                    return
                }
                val secs = (remaining / 1000).toInt()
                val min = secs / 60
                val sec = secs % 60
                val text = if (min > 0) {
                    "\u23F1 ${min}m ${sec}s"
                } else {
                    "\u23F1 ${sec}s"
                }
                overlayView?.text = text
                handler.postDelayed(this, 500)
            }
        }
        tickRunnable = runnable
        runnable.run()
    }

    private fun pillBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(20).toFloat()
            setColor(Color.argb(200, 30, 30, 30))
        }
    }

    private fun dp(value: Int): Int {
        val density = service.resources.displayMetrics.density
        return (value * density + 0.5f).toInt()
    }
}
