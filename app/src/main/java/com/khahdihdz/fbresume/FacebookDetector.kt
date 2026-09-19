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
        "Play", "Pause", "Mute", "Unmute", "Close", "Back", "Next",
        "Reels", "Shorts", "Video", "Videos"
    )
    private val technicalTextRegex = Regex(
        "(?i)(component(spec)?|attachmentcomponent|recycler(view)?|viewholder|com\\.|androidx?\\.|fbshorts|stick(er)?|resource|contentdescription|accessibility|menuitem|testid)"
    )
    private val titleHintRegex = Regex("(?i)(title|video|caption|description|reel|short)")

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

        val seekNode = findSeekNode(root)
        val title = findVideoTitle(root, seekNode)
        return VideoState(current, duration, title, stableKey(title), seekNode)
    }

    /**
     * Finds the most likely human-readable video title from the Accessibility tree.
     * Combines visible text/contentDescription, proximity to the seek bar, semantic
     * hints and filters for timestamps, controls and technical component names.
     */
    fun findVideoTitle(root: AccessibilityNodeInfo?, seekNode: AccessibilityNodeInfo? = null): String {
        if (root == null) return "Facebook video"

        val anchor = Rect().also { seekNode?.getBoundsInScreen(it) }
        val candidates = mutableListOf<Pair<String, Int>>()

        fun addCandidate(node: AccessibilityNodeInfo) {
            val values = listOfNotNull(
                node.text?.toString()?.trim(),
                node.contentDescription?.toString()?.trim()
            ).distinct()
            if (values.isEmpty()) return

            val rect = Rect().also { node.getBoundsInScreen(it) }
            val cls = node.className?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""

            for (raw in values) {
                val text = raw.replace(Regex("\\s+"), " ").trim()
                if (!isTitleCandidate(text)) continue

                var score = 0
                if (anchor.width() > 0) {
                    val horizontalDistance = kotlin.math.abs((rect.centerX() - anchor.centerX()).toLong()).coerceAtMost(1000L).toInt()
                    val verticalDistance = kotlin.math.abs((rect.centerY() - anchor.centerY()).toLong()).coerceAtMost(1600L).toInt()
                    score += 1000 - horizontalDistance / 4 - verticalDistance / 6
                    if (rect.bottom <= anchor.top) score += 220
                    if (rect.bottom in (anchor.top - 500)..anchor.top) score += 140
                }
                if (titleHintRegex.containsMatchIn(cls) || titleHintRegex.containsMatchIn(desc)) score += 120
                if (node.isVisibleToUser) score += 40
                if (node.isClickable) score -= 15
                if (node.isFocusable) score -= 10
                score += text.length.coerceAtMost(80) / 4
                if (text.count { it == "_" } >= 2) score -= 100
                if (text.matches(Regex("[A-Za-z0-9_]+"))) score -= 25
                candidates += text to score
            }
        }

        fun walk(node: AccessibilityNodeInfo) {
            addCandidate(node)
            for (i in 0 until node.childCount) node.getChild(i)?.let(::walk)
        }
        walk(root)

        return candidates.maxByOrNull { it.second }?.first ?: "Facebook video"
    }

    private fun isTitleCandidate(text: String): Boolean {
        if (text.length !in 5..180) return false
        if (genericText.contains(text)) return false
        if (timeRegex.containsMatchIn(text)) return false
        if (text.contains("$") || text.contains("{") || text.contains("}")) return false
        if (technicalTextRegex.containsMatchIn(text)) return false
        if (text.count { it.isWhitespace() } > 45) return false
        return true
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
