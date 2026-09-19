package com.khahdihdz.fbresume

import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import java.security.MessageDigest

data class VideoState(
    val currentMs: Long,
    val durationMs: Long,
    val title: String,
    val key: String,
    val seekNode: AccessibilityNodeInfo?
)

object FacebookDetector {
    const val FACEBOOK_PACKAGE = "com.facebook.katana"
    const val FACEBOOK_LITE_PACKAGE = "com.facebook.lite"
    private val timeRegex = Regex("""(?<!\d)(\d{1,4}):(\d{2})(?::(\d{2}))?(?!\d)""")
    private val genericText = setOf(
        "Facebook", "Like", "Comment", "Share", "Follow", "More", "Options",
        "Play", "Pause", "Mute", "Unmute", "Close", "Back", "Next"
    )
    private val technicalTextRegex = Regex(
        "(?i)(component(spec)?|attachmentcomponent|recycler(view)?|viewholder|com\\.|androidx?\\.|fbshorts|stick(er)?|resource|contentdescription|accessibility|menuitem)"
    )

    fun isFacebook(packageName: CharSequence?): Boolean {
        val name = packageName?.toString() ?: return false
        return name == FACEBOOK_PACKAGE || name == FACEBOOK_LITE_PACKAGE
    }

    fun collectTexts(root: AccessibilityNodeInfo?): List<String> {
        if (root == null) return emptyList()
        val out = mutableListOf<String>()
        fun walk(node: AccessibilityNodeInfo) {
            node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(out::add)
            node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(out::add)
            for (i in 0 until node.childCount) node.getChild(i)?.let(::walk)
        }
        walk(root)
        return out.distinct()
    }

    fun parseTimeMs(value: String): Long? {
        val match = timeRegex.find(value) ?: return null
        val first = match.groupValues[1].toLongOrNull() ?: return null
        val second = match.groupValues[2].toLongOrNull() ?: return null
        val third = match.groupValues[3].takeIf { it.isNotEmpty() }?.toLongOrNull()
        return if (third == null) {
            if (second > 59) null else (first * 60L + second) * 1000L
        } else {
            if (second > 59 || third > 59) null else (first * 3600L + second * 60L + third) * 1000L
        }
    }

    fun findTimeValues(root: AccessibilityNodeInfo?): List<Long> =
        collectTexts(root).flatMap { text ->
            timeRegex.findAll(text).mapNotNull { parseTimeMs(it.value) }.toList()
        }.distinct()

    fun findVideoState(root: AccessibilityNodeInfo?): VideoState? {
        val texts = collectTexts(root)
        val times = texts.flatMap { text ->
            timeRegex.findAll(text).mapNotNull { parseTimeMs(it.value) }
        }.distinct()

        if (times.size < 2) return null
        val duration = times.maxOrNull() ?: return null
        val current = times.filter { it < duration }.maxOrNull() ?: return null
        if (duration < 5_000L || current >= duration) return null

        val title = texts
            .filter { it.length in 5..180 }
            .filterNot { genericText.contains(it) }
            .filterNot { timeRegex.containsMatchIn(it) }
            .filterNot { it.contains('

        return VideoState(current, duration, title, stableKey(title), findSeekNode(root))
    }

    fun findSeekNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null

        var best: AccessibilityNodeInfo? = null
        var bestScore = Int.MIN_VALUE

        fun walk(node: AccessibilityNodeInfo) {
            val cls = node.className?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            if (node.isVisibleToUser && node.isEnabled &&
                (cls.contains("seekbar") || cls.contains("progressbar") || desc.contains("seek") || desc.contains("progress"))) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                val score = rect.width() + if (node.rangeInfo != null) 1000 else 0
                if (score > bestScore) {
                    best = node
                    bestScore = score
                }
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(::walk)
        }

        root?.let(::walk)
        return best
    }

    fun seek(node: AccessibilityNodeInfo?, targetMs: Long, durationMs: Long): Boolean {
        if (node == null || durationMs <= 0L) return false
        val range = node.rangeInfo ?: return false
        val ratio = (targetMs.toDouble() / durationMs.toDouble()).coerceIn(0.0, 1.0)
        val value = (range.min + (range.max - range.min) * ratio).toFloat()
        val args = Bundle().apply {
            putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, value)
        }
        return node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id, args)
    }

    private fun titleScore(text: String): Int {
        var score = text.length
        if (text.count { it == '_' } >= 2) score -= 80
        if (text.count { it.isUpperCase() } > text.count { it.isLowerCase() }) score -= 30
        if (text.matches(Regex("[A-Za-z0-9_]+"))) score -= 15
        return score
    }

    private fun stableKey(title: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(title.trim().lowercase().toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(24)
    }
}
) || it.contains('{') || it.contains('}') }
            .filterNot { technicalTextRegex.containsMatchIn(it) }
            .maxByOrNull { titleScore(it) } ?: "Facebook video"

        return VideoState(current, duration, title, stableKey(title), findSeekNode(root))
    }

    fun findSeekNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null

        var best: AccessibilityNodeInfo? = null
        var bestScore = Int.MIN_VALUE

        fun walk(node: AccessibilityNodeInfo) {
            val cls = node.className?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            if (node.isVisibleToUser && node.isEnabled &&
                (cls.contains("seekbar") || cls.contains("progressbar") || desc.contains("seek") || desc.contains("progress"))) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                val score = rect.width() + if (node.rangeInfo != null) 1000 else 0
                if (score > bestScore) {
                    best = node
                    bestScore = score
                }
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(::walk)
        }

        root?.let(::walk)
        return best
    }

    fun seek(node: AccessibilityNodeInfo?, targetMs: Long, durationMs: Long): Boolean {
        if (node == null || durationMs <= 0L) return false
        val range = node.rangeInfo ?: return false
        val ratio = (targetMs.toDouble() / durationMs.toDouble()).coerceIn(0.0, 1.0)
        val value = (range.min + (range.max - range.min) * ratio).toFloat()
        val args = Bundle().apply {
            putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, value)
        }
        return node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id, args)
    }

    private fun stableKey(title: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(title.trim().lowercase().toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(24)
    }
}
