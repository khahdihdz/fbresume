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
    private var lastPackageName: String? = null
    private var lastInspectMs = 0L

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
        val packageName = event?.packageName?.toString() ?: return
        if (!FacebookDetector.isFacebook(packageName)) return
        lastPackageName = packageName
        handler.removeCallbacks(scan)
        handler.postDelayed(scan, 250L)
    }

    private fun inspect() {
        val now = System.currentTimeMillis()
        if (now - lastInspectMs < 350L) return
        lastInspectMs = now

        val root = rootInActiveWindow ?: return
        val packageName = root.packageName?.toString() ?: return
        if (!FacebookDetector.isFacebook(packageName)) return
        val state = FacebookDetector.findVideoState(root) ?: return
        val now = System.currentTimeMillis()
        var saved = store.get(state.key)

        // URL is optional. Match the current Facebook video by title + duration
        // so old entries and entries detected without a URL keep their progress.
        if (saved == null) {
            val matched = store.findLegacyMatch(state.title, state.durationMs)
            if (matched != null) {
                store.remove(matched.key)
                saved = matched.copy(
                    key = state.key,
                    title = state.title,
                    url = if (state.url.isNotBlank()) state.url else matched.url,
                    durationMs = state.durationMs,
                    updatedAt = now
                )
                store.save(saved)
                if (store.getPendingKey() == matched.key) store.setPendingKey(state.key)
            }
        }

        if (saved == null) {
            saved = ResumeItem(state.key, state.title, state.url, state.currentMs, state.durationMs, now)
            store.save(saved)
        } else {
            if ((state.url.isNotBlank() && state.url != saved.url) ||
                state.title != saved.title || state.durationMs != saved.durationMs) {
                saved = saved.copy(
                    title = state.title,
                    url = if (state.url.isNotBlank()) state.url else saved.url,
                    durationMs = state.durationMs,
                    updatedAt = now
                )
                store.save(saved)
            }

            if (state.key == lastKey && now - lastSaveMs >= 5_000L &&
                state.currentMs > saved.positionMs + 3_000L) {
                saved = saved.copy(positionMs = state.currentMs, updatedAt = now)
                store.save(saved)
                lastSaveMs = now
            }
        }

        lastKey = state.key
        if (now - lastSaveMs >= 5_000L) lastSaveMs = now

        val pendingTarget = store.getPendingKey()
        val pendingItem = pendingTarget?.let { store.get(it) }
        val pendingMatchesCurrent = pendingItem != null &&
            store.titleDurationMatch(pendingItem.title, pendingItem.durationMs, state.title, state.durationMs)
        val isPendingTarget = pendingTarget == state.key || pendingMatchesCurrent
        if (pendingMatchesCurrent && pendingTarget != state.key) {
            store.clearPendingKey()
            store.setPendingKey(state.key)
        }
        val shouldResume = saved.positionMs >= 5_000L &&
            (isPendingTarget || (
                state.currentMs < (saved.positionMs - 3_000L).coerceAtLeast(5_000L) &&
                state.currentMs <= state.durationMs * 0.15
            ))

        if (shouldResume && resumedKey != state.key && resumeAttempts < 4) {
            resumeAttempts++
            if (FacebookDetector.seek(state.seekNode, saved.positionMs, state.durationMs)) {
                resumedKey = state.key
                if (isPendingTarget) store.clearPendingKey()
                Toast.makeText(
                    this,
                    "Tiếp tục: ${formatTime(saved.positionMs)} — ${saved.title.take(45)}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        if (state.key != resumedKey && state.currentMs > saved.positionMs + 30_000L) {
            resumeAttempts = 0
        }
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
