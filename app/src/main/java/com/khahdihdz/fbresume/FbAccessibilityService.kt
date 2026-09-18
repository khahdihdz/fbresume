package com.khahdihdz.fbresume

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class FbAccessibilityService : AccessibilityService() {
    private lateinit var store: ResumeStore

    override fun onServiceConnected() {
        super.onServiceConnected()
        store = ResumeStore(this)
        Toast.makeText(this, "FBResume Accessibility đã bật", Toast.LENGTH_SHORT).show()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (!FacebookDetector.isFacebook(event.packageName)) return
        val root = rootInActiveWindow ?: return
        val times = FacebookDetector.findTimeStrings(root)
        if (times.isNotEmpty()) {
            // Timestamp quan sát được sẽ được ghép với video identity ở bước tiếp theo.
            // Không truy cập dữ liệu nội bộ của Facebook.
        }
    }

    override fun onInterrupt() = Unit
}
