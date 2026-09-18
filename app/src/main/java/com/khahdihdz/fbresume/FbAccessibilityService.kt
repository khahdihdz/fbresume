package com.khahdihdz.fbresume

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class FbAccessibilityService : AccessibilityService() {
    private lateinit var store: ResumeStore
    private val handler = Handler(Looper.getMainLooper())
    private var lastKey: String? = null
    private var lastSaveMs = 0L
    private var resumedKey: String? = null
    private var resumeAttempts = 0

    private val scan = object : Runnable {
        override fun run() {
            inspect()
            handler.postDelayed(this, 1500L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        store = ResumeStore(this)
        handler.post(scan)
        Toast.makeText(this, "FBResume Accessibility đã bật", Toast.LENGTH_SHORT).show()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName == null || !FacebookDetector.isFacebook(event.packageName)) return
        handler.removeCallbacks(scan)
        handler.postDelayed(scan, 250L)
    }

    private fun inspect() {
        val root = rootInActiveWindow ?: return
        if (!FacebookDetector.isFacebook(root.packageName)) return
        val state = FacebookDetector.findVideoState(root) ?: return
        val now = System.currentTimeMillis()

        if (state.key != lastKey || now - lastSaveMs >= 5_000L) {
            store.save(ResumeItem(state.key, state.title, "", state.currentMs, state.durationMs, now))
            lastKey = state.key
            lastSaveMs = now
        }

        val saved = store.get(state.key) ?: return
        val shouldResume = saved.positionMs >= 5_000L &&
            state.currentMs < (saved.positionMs - 3_000L).coerceAtLeast(5_000L) &&
            state.currentMs <= saved.durationMs * 0.15

        if (shouldResume && resumedKey != state.key && resumeAttempts < 3) {
            resumeAttempts++
            if (FacebookDetector.seek(state.seekNode, saved.positionMs, saved.durationMs)) {
                resumedKey = state.key
                Toast.makeText(this, "Tiếp tục: " + formatTime(saved.positionMs) + " — " + saved.title.take(45), Toast.LENGTH_SHORT).show()
            }
        }
        if (state.key != resumedKey && state.currentMs > saved.positionMs + 30_000L) resumeAttempts = 0
    }

    private fun formatTime(ms: Long): String {
        val total = ms / 1000L
        val h = total / 3600L
        val m = (total % 3600L) / 60L
        val s = total % 60L
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    override fun onInterrupt() { handler.removeCallbacksAndMessages(null) }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy() }
}
