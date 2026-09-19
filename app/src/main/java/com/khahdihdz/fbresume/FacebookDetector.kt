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
        "Reels", "Shorts", "Video", "Videos", "Watch", "Save", "Copy link",
        "Send", "Reply", "Translate", "See more", "See less", "Hide",
        "Report", "Notifications", "Menu"
    )
    private val technicalTextRegex = Regex(
        "(?i)(component(spec)?|attachmentcomponent|recycler(view)?|viewholder|com\\.|androidx?\\.|fbshorts|stick(er)?|resource|contentdescription|accessibility|menuitem|testid)"
    )
    private val titleHintRegex = Regex("(?i)(title|video|caption|description|reel|short)")
    private val captionHintRegex = Regex(
        "(?i)(caption|description|post|content|message|status|text|reel|video)"
    )
    private val urlOnlyRegex = Regex("(?i)^(https?://|www\\.)\\S+$")

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
        return VideoState(current, duration, title, stableKey(title, duration), seekNode)
    }

    /**
     * Extracts the Facebook post/caption associated with the current video.
     * Accessibility trees vary between Facebook versions, so candidates are
     * scored by semantic hints, position relative to the video and text shape.
     * If no caption/post is exposed, it falls back to the best video title.
     */
    fun findVideoTitle(root: AccessibilityNodeInfo?, seekNode: AccessibilityNodeInfo? = null): String {
        if (root == null) return "Facebook video"

        val anchor = Rect().also { seekNode?.getBoundsInScreen(it) }
        val candidates = mutableListOf<Candidate>()

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
                    val horizontalDistance =
                        kotlin.math.abs((rect.centerX() - anchor.centerX()).toLong())
                            .coerceAtMost(1400L).toInt()
                    val verticalDistance =
                        kotlin.math.abs((rect.centerY() - anchor.centerY()).toLong())
                            .coerceAtMost(2200L).toInt()
                    score += 900 - horizontalDistance / 5 - verticalDistance / 8

                    // Facebook commonly exposes the post caption above the video.
                    if (rect.bottom <= anchor.top) score += 300
                    if (rect.bottom in (anchor.top - 700)..anchor.top) score += 180
                    if (rect.top >= anchor.bottom) score -= 180
                }

                if (captionHintRegex.containsMatchIn(cls) || captionHintRegex.containsMatchIn(desc)) {
                    score += 180
                }
                if (titleHintRegex.containsMatchIn(cls) || titleHintRegex.containsMatchIn(desc)) {
                    score += 80
                }
                if (node.isVisibleToUser) score += 50
                if (node.isClickable) score -= 35
                if (node.isFocusable) score -= 15

                // Prefer real sentences/captions over one-word UI labels.
                if (text.any { it.isWhitespace() }) score += 55
                if (text.any { it in ".!?。！？" }) score += 35
                if (text.any { it == '#' || it == '@' }) score += 25
                score += text.length.coerceAtMost(600) / 8

                if (text.count { it == '_' } >= 2) score -= 100
                if (text.matches(Regex("[A-Za-z0-9_]+"))) score -= 40
                if (urlOnlyRegex.matches(text)) score -= 160

                candidates += Candidate(text, score, rect, node)
            }
        }

        fun walk(node: AccessibilityNodeInfo) {
            addCandidate(node)
            for (i in 0 until node.childCount) node.getChild(i)?.let(::walk)
        }
        walk(root)

        val best = candidates.maxByOrNull { it.score } ?: return "Facebook video"
        val merged = mergeCaptionParts(candidates, best, anchor)
        return merged.ifBlank { best.text }
    }

    private data class Candidate(
        val text: String,
        val score: Int,
        val rect: Rect,
        val node: AccessibilityNodeInfo
    )

    /**
     * Some Facebook builds expose a caption as several adjacent TextViews.
     * Merge nearby caption fragments only when they occupy the same text region.
     */
    private fun mergeCaptionParts(
        candidates: List<Candidate>,
        best: Candidate,
        anchor: Rect
    ): String {
        if (best.text.length >= 120) return best.text

        val parts = candidates
            .asSequence()
            .filter { it !== best }
            .filter { it.score >= best.score - 220 }
            .filter { it.text.length >= 3 }
            .filter { !genericText.contains(it.text) }
            .filter {
                if (anchor.width() <= 0) true
                else it.rect.bottom <= anchor.top + 40
            }
            .filter {
                kotlin.math.abs(it.rect.centerX() - best.rect.centerX()) <= 700
            }
            .filter {
                kotlin.math.abs(it.rect.centerY() - best.rect.centerY()) <= 180
            }
            .sortedBy { it.rect.left }
            .map { it.text }
            .distinct()
            .toList()

        if (parts.isEmpty()) return best.text

        val all = (parts + best.text).distinct()
        val merged = all.joinToString(" ").replace(Regex("\\s+"), " ").trim()
        return if (merged.length > best.text.length && merged.length <= 1200) merged else best.text
    }

    private fun isTitleCandidate(text: String): Boolean {
        if (text.length !in 5..1200) return false
        if (genericText.contains(text)) return false
        if (timeRegex.containsMatchIn(text)) return false
        if (text.contains("$") || text.contains("{") || text.contains("}")) return false
        if (technicalTextRegex.containsMatchIn(text)) return false
        if (text.count { it.isWhitespace() } > 220) return false
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

        walk(root)
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

    private fun stableKey(title: String, durationMs: Long): String {
        val normalized = title.trim().lowercase().replace(Regex("\\s+"), " ")
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest((normalized + "|" + durationMs).toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(24)
    }
}
