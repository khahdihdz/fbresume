package com.khahdihdz.fbresume

import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import java.security.MessageDigest

data class VideoState(
    val currentMs: Long,
    val durationMs: Long,
    val title: String,
    val key: String,
    val url: String,
    val seekNode: AccessibilityNodeInfo?
)

/** Metadata of a video resolved from a shared Facebook link. */
data class SharedVideoMeta(
    val url: String,
    val title: String,
    val durationMs: Long
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

    private val facebookUrlRegex = Regex("""(?i)https?://(?:(?:www|m|web)\.)?facebook\.com/(?:reel(?:s)?(?:/|\?|$)|watch(?:/|\?|$)|videos?(?:/|\?|$)|share/(?:v|r)(?:/|\?|$)|story(?:\.php)?(?:/|\?|$))[^\s<>"']*""")
    private val fbWatchRegex = Regex("""(?i)https?://(?:www\.)?fb\.watch/[A-Za-z0-9_-]+/?""")
    private val hrefRegex = Regex("""(?i)href\s*=\s*["'](https?://[^"']+)["']""")

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

    /**
     * Xây dựng VideoState trong MỘT lượt đi cây Accessibility duy nhất.
     * Trước đây mỗi thành phần (timestamp, tiêu đề, seek bar, URL) tự đi cây
     * riêng nên tổng cộng 4 lượt quét mỗi chu kỳ — nguyên nhân chính gây lag
     * trong ứng dụng Facebook.
     */
    fun findVideoState(root: AccessibilityNodeInfo?): VideoState? {
        if (root == null) return null
        val scan = scanTree(root)

        val times = scan.timeValues.toList()
        if (times.size < 2) return null
        val duration = times.maxOrNull() ?: return null
        val current = times.filter { it < duration }.maxOrNull() ?: return null
        if (duration < 5_000L || current >= duration) return null

        val seekNode = scan.seekNode
        val title = resolveVideoTitle(scan, seekNode)
        return VideoState(current, duration, title, stableKey(title, duration), scan.url, seekNode)
    }

    //region Single-pass accessibility scan

    /** Thu thập mọi dữ liệu cần thiết từ cây trong một lượt duy nhất. */
    private class TreeScan {
        val timeValues = LinkedHashSet<Long>()
        val rawCandidates = mutableListOf<RawCandidate>()
        var seekNode: AccessibilityNodeInfo? = null
        var seekScore = Int.MIN_VALUE
        var url = ""
    }

    private class RawCandidate(
        val text: String,
        val rect: Rect,
        val className: String,
        val description: String,
        val visible: Boolean,
        val clickable: Boolean,
        val focusable: Boolean
    )

    private fun scanTree(root: AccessibilityNodeInfo): TreeScan {
        val scan = TreeScan()

        fun acceptUrl(value: String) {
            val cleaned = value.trimEnd('.', ',', ';', ':', ')', ']', '}', '"')
            if (isUsefulVideoUrl(cleaned) && cleaned.length > scan.url.length) scan.url = cleaned
        }

        fun considerUrl(value: String) {
            if (value.isBlank()) return
            val normalized = value.replace("&amp;", "&")
            facebookUrlRegex.findAll(normalized).forEach { acceptUrl(it.value) }
            fbWatchRegex.findAll(normalized).forEach { acceptUrl(it.value) }
            hrefRegex.findAll(normalized).forEach { acceptUrl(it.groupValues[1]) }
        }

        fun inspectExtras(bundle: Bundle?) {
            if (bundle == null) return
            for (key in bundle.keySet()) {
                val value = bundle.get(key)
                when (value) {
                    is CharSequence -> considerUrl(value.toString())
                    is String -> considerUrl(value)
                    is Bundle -> inspectExtras(value)
                    is Array<*> -> value.forEach { item -> if (item is CharSequence) considerUrl(item.toString()) }
                }
            }
        }

        fun walk(node: AccessibilityNodeInfo) {
            val text = node.text?.toString().orEmpty()
            val desc = node.contentDescription?.toString().orEmpty()

            // Timestamp MM:SS / HH:MM:SS
            if (text.isNotEmpty()) {
                timeRegex.findAll(text).forEach { m ->
                    parseTimeMs(m.value)?.let(scan.timeValues::add)
                }
            }
            if (desc.isNotEmpty()) {
                timeRegex.findAll(desc).forEach { m ->
                    parseTimeMs(m.value)?.let(scan.timeValues::add)
                }
            }

            // URL video trong mọi thuộc tính có thể chứa link
            considerUrl(text)
            considerUrl(desc)
            considerUrl(node.viewIdResourceName.orEmpty())
            inspectExtras(node.extras)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                considerUrl(node.hintText?.toString().orEmpty())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                considerUrl(node.paneTitle?.toString().orEmpty())
                considerUrl(node.tooltipText?.toString().orEmpty())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                considerUrl(node.stateDescription?.toString().orEmpty())
            }

            // Seek/progress bar điều khiển được
            val visible = node.isVisibleToUser
            if (visible && node.isEnabled) {
                val cls = node.className?.toString()?.lowercase().orEmpty()
                val lowerDesc = desc.lowercase()
                if (cls.contains("seekbar") || cls.contains("progressbar") ||
                    lowerDesc.contains("seek") || lowerDesc.contains("progress")) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    val score = rect.width() + if (node.rangeInfo != null) 1000 else 0
                    if (score > scan.seekScore) {
                        scan.seekNode = node
                        scan.seekScore = score
                    }
                }
            }

            // Ứng viên tiêu đề/caption video
            val values = LinkedHashSet<String>()
            text.trim().takeIf { it.isNotEmpty() }?.let(values::add)
            desc.trim().takeIf { it.isNotEmpty() }?.let(values::add)
            if (values.isNotEmpty()) {
                val rect = Rect().also { node.getBoundsInScreen(it) }
                val cls = node.className?.toString()?.lowercase().orEmpty()
                val lowerDesc = desc.lowercase()
                val clickable = node.isClickable
                val focusable = node.isFocusable
                for (raw in values) {
                    val normalizedText = raw.replace(Regex("\\s+"), " ").trim()
                    if (isTitleCandidate(normalizedText)) {
                        scan.rawCandidates += RawCandidate(
                            normalizedText, rect, cls, lowerDesc,
                            visible, clickable, focusable
                        )
                    }
                }
            }

            for (i in 0 until node.childCount) node.getChild(i)?.let(::walk)
        }

        walk(root)
        return scan
    }

    /**
     * Extracts the Facebook post/caption associated with the current video.
     * Accessibility trees vary between Facebook versions, so candidates are
     * scored by semantic hints, position relative to the video and text shape.
     * If no caption/post is exposed, it falls back to the best video title.
     */
    private fun resolveVideoTitle(scan: TreeScan, seekNode: AccessibilityNodeInfo?): String {
        val anchor = Rect().also { seekNode?.getBoundsInScreen(it) }
        val candidates = mutableListOf<Candidate>()

        for (raw in scan.rawCandidates) {
            val text = raw.text
            var score = 0
            if (anchor.width() > 0) {
                val horizontalDistance =
                    kotlin.math.abs((raw.rect.centerX() - anchor.centerX()).toLong())
                        .coerceAtMost(1600L).toInt()

                // Facebook normally exposes the reel caption immediately
                // above the video. Text inside/below the video is usually
                // controls, overlays, comments or unrelated UI.
                val isAboveVideo = raw.rect.bottom <= anchor.top
                val isInsideVideo = Rect.intersects(raw.rect, anchor)
                val gap = (anchor.top - raw.rect.bottom).coerceAtLeast(0)

                if (isInsideVideo) {
                    score -= 900
                } else if (isAboveVideo) {
                    score += 1200
                    score += (700 - gap.coerceAtMost(700)) / 2
                } else {
                    score -= 650
                }

                score -= horizontalDistance / 7

                val horizontalOverlap = maxOf(
                    0,
                    minOf(raw.rect.right, anchor.right) - maxOf(raw.rect.left, anchor.left)
                )
                if (horizontalOverlap > 0) score += 180
                if (raw.rect.width() >= (anchor.width() * 0.45f).toInt()) score += 100
            }

            if (captionHintRegex.containsMatchIn(raw.className) ||
                captionHintRegex.containsMatchIn(raw.description)) {
                score += 180
            }
            if (titleHintRegex.containsMatchIn(raw.className) ||
                titleHintRegex.containsMatchIn(raw.description)) {
                score += 80
            }
            if (raw.visible) score += 50
            if (raw.clickable) score -= 35
            if (raw.focusable) score -= 15

            // Prefer real post captions over usernames, counters and
            // accessibility labels.
            if (text.any { it.isWhitespace() }) score += 90
            if (text.any { it in ".!?。！？" }) score += 55
            if (text.any { it == '#' || it == '@' }) score += 35
            if (text.length in 20..300) score += 90
            score += text.length.coerceAtMost(600) / 6

            val lower = text.lowercase()
            if (lower.contains("phần ") || lower.contains("part ") ||
                lower.contains("tập ") || lower.contains("episode ")) score += 100
            if (lower.split(Regex("\\s+")).count { it.length >= 2 } >= 5) score += 80

            // Common Facebook metadata is not the reel caption.
            if (Regex("(?i)^(\\d+[smhdwy]|\\d+\\s*(giờ|phút|ngày|tuần|tháng|năm))$").matches(text)) score -= 500
            if (Regex("(?i)^(\\d+[.,]?\\d*\\s*(likes?|comments?|shares?|lượt thích|bình luận|lượt chia sẻ))$").matches(text)) score -= 500
            if (Regex("(?i)^(follow|following|đang theo dõi|theo dõi|facebook user)$").matches(text)) score -= 500
            if (text.count { it == '_' } >= 2) score -= 180
            if (text.matches(Regex("[A-Za-z0-9_]+"))) score -= 100
            if (urlOnlyRegex.matches(text)) score -= 300

            candidates += Candidate(text, score, raw.rect)
        }

        val best = candidates.maxByOrNull { it.score } ?: return "Facebook video"
        val merged = mergeCaptionParts(candidates, best, anchor)
        return merged.ifBlank { best.text }
    }

    /** Wrapper tương thích: chấm điểm tiêu đề từ cây bất kỳ (không dùng trong luồng nóng). */
    fun findVideoTitle(root: AccessibilityNodeInfo?, seekNode: AccessibilityNodeInfo? = null): String {
        if (root == null) return "Facebook video"
        val scan = scanTree(root)
        return resolveVideoTitle(scan, seekNode)
    }

    private fun isUsefulVideoUrl(url: String): Boolean {
        val lower = url.lowercase()
        if (lower.contains("facebook.com/watch")) return true
        if (lower.contains("facebook.com/reel")) return true
        if (lower.contains("facebook.com/videos/")) return true
        if (lower.contains("facebook.com/share/v/")) return true
        if (lower.contains("facebook.com/share/r/")) return true
        if (lower.contains("facebook.com/story")) return true
        return lower.contains("fb.watch/")
    }

    //region Shared link handling

    private val sharedUrlRegex = Regex(
        """(?i)(?:https?://)?(?:[\w-]+\.)?(?:facebook\.com|fb\.watch|fb\.me)/[^\s<>"']+"""
    )
    private val trailingPunctuation = charArrayOf('.', ',', ';', ':', ')', ']', '}', '"', '\'', '…')

    /** User-Agent của crawler — Facebook trả og: metadata đầy đủ cho UA này. */
    private const val SCRAPER_UA =
        "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)"
    private const val MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Version/4.0 Chrome/124.0.0.0 Mobile Safari/537.36"

    /** Extracts the first Facebook video URL from text shared via ACTION_SEND. */
    fun extractSharedVideoUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val source = text.replace("&amp;", "&")
        var fallback: String? = null
        for (match in sharedUrlRegex.findAll(source)) {
            var candidate = match.value.trim().trimEnd(*trailingPunctuation)
            if (candidate.startsWith("http")) {
                // keep as-is
            } else if (candidate.startsWith("//")) {
                candidate = "https:$candidate"
            } else {
                candidate = "https://$candidate"
            }
            val lower = candidate.lowercase()
            if (!lower.contains("facebook.com") && !lower.contains("fb.watch") && !lower.contains("fb.me")) continue
            if (isUsefulVideoUrl(candidate)) return candidate
            if (fallback == null) fallback = candidate
        }
        return fallback
    }

    /** Normalizes a Facebook video URL by dropping tracking query parameters. */
    fun normalizeVideoUrl(url: String): String {
        var value = url.trim().replace("&amp;", "&")
        val fragment = value.indexOf('#')
        if (fragment >= 0) value = value.substring(0, fragment)
        val queryStart = value.indexOf('?')
        if (queryStart >= 0) {
            val path = value.substring(0, queryStart)
            val params = value.substring(queryStart + 1).split('&').filter { it.isNotBlank() }
            val keepV = params.firstOrNull { it.substringBefore('=').lowercase() == "v" }?.substringAfter('=')
            value = if (!keepV.isNullOrBlank()) "$path?v=$keepV" else path
        }
        return value.trimEnd('/')
    }

    /** Stable identifier of the video inside a Facebook URL, or null when unknown. */
    fun videoIdOf(url: String): String? {
        if (url.isBlank()) return null
        val value = url.replace("&amp;", "&")
        val patterns = listOf(
            Regex("""(?i)/reels?/([a-z0-9_-]+)"""),
            Regex("""(?i)/videos?/(\d+)"""),
            Regex("""(?i)/share/[vr]/([a-z0-9_-]+)"""),
            Regex("""(?i)story\.php\?v=(\d+)"""),
            Regex("""(?i)[?&]v=(\d+)"""),
            Regex("""(?i)fb\.watch/([a-z0-9_-]+)""")
        )
        for (pattern in patterns) {
            val match = pattern.find(value) ?: continue
            return match.groupValues[1]
        }
        return null
    }

    /** Key used to pre-register a video discovered from a shared link. */
    fun urlKey(url: String): String {
        val id = videoIdOf(url)
        if (!id.isNullOrBlank()) return "vid_" + id.lowercase()
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(normalizeVideoUrl(url).lowercase().toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(24)
    }

    /**
     * Resolves a shared Facebook video link: follows redirects (fb.watch,
     * share/v, share/r) and reads page metadata to detect the total video
     * duration and title.
     *
     * Dùng 2 lượt tải: đầu tiên với UA crawler `facebookexternalhit` (Facebook
     * trả og:video:duration đầy đủ cho UA này với video công khai), nếu chưa có
     * thời lượng thì thử lại với UA trình duyệt mobile.
     */
    fun fetchVideoMeta(rawUrl: String): SharedVideoMeta? {
        if (rawUrl.isBlank()) return null

        var durationMs = 0L
        var title = ""
        var finalUrl = ""

        val scraperPass = fetchVideoHtml(rawUrl, SCRAPER_UA)
        if (scraperPass != null) {
            finalUrl = scraperPass.first
            durationMs = parseDurationMsFromHtml(scraperPass.second)
            title = parseTitleFromHtml(scraperPass.second)
        }

        if (durationMs <= 0L) {
            val mobilePass = fetchVideoHtml(rawUrl, MOBILE_UA)
            if (mobilePass != null) {
                val altDuration = parseDurationMsFromHtml(mobilePass.second)
                if (altDuration > 0L) {
                    durationMs = altDuration
                    finalUrl = mobilePass.first
                }
                if (title.isBlank()) title = parseTitleFromHtml(mobilePass.second)
            }
        }

        if (durationMs <= 0L && title.isBlank()) return null
        if (finalUrl.isBlank()) finalUrl = rawUrl
        return SharedVideoMeta(
            url = normalizeVideoUrl(finalUrl),
            title = title.ifBlank { "Video Facebook" },
            durationMs = durationMs
        )
    }

    /** Fetches page HTML while following HTTP redirects manually. */
    private fun fetchVideoHtml(rawUrl: String, userAgent: String): Pair<String, String>? {
        var url = rawUrl
        var redirects = 0
        while (redirects < 6) {
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", userAgent)
                connection.setRequestProperty("Accept-Language", "vi,en-US;q=0.9,en;q=0.8")
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000
                val code = connection.responseCode
                val location = connection.getHeaderField("Location")
                val body = try {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } catch (_: Exception) {
                    ""
                }
                if (code in 300..399 && !location.isNullOrBlank()) {
                    url = java.net.URL(java.net.URL(url), location).toString()
                    redirects++
                    continue
                }
                return url to body
            } finally {
                connection.disconnect()
            }
        }
        return null
    }

    /** Reads the total video duration from page HTML. Returns milliseconds, 0 if unknown. */
    fun parseDurationMsFromHtml(html: String): Long {
        if (html.isBlank()) return 0L
        var result = parseDurationPatterns(html)
        if (result <= 0L) {
            // JSON lồng trong HTML thường escape dấu quote thành \" — unescape rồi thử lại.
            result = parseDurationPatterns(html.replace("\\\"", "\""))
        }
        return result
    }

    private fun parseDurationPatterns(html: String): Long {
        // pattern to isMilliseconds
        val patterns = listOf(
            Regex("""(?i)og:video:duration[^>]*?content=["']?(\d+)""") to false,
            Regex("""(?i)content=["']?(\d+)["']?[^>]*?og:video:duration""") to false,
            Regex("""(?i)\\?"og:video:duration\\?"\s*:\s*\\?"?(\d+)""") to false,
            Regex("""(?i)"video_durationSeconds"\s*:\s*"?(\d+)""") to false,
            Regex("""(?i)"playable_url_duration_in_ms"\s*:\s*(\d+)""") to true,
            Regex("""(?i)"playable_duration_in_ms"\s*:\s*(\d+)""") to true,
            Regex("""(?i)"duration_in_ms"\s*:\s*(\d+)""") to true,
            Regex("""(?i)"estimated_lossless_duration"\s*:\s*"?(\d+)""") to false,
            Regex("""(?i)"playable_duration"\s*:\s*"?(\d+)""") to false,
            Regex("""(?i)"playback_duration"\s*:\s*"?(\d+)""") to false,
            Regex("""(?i)"videoDuration"\s*:\s*"?(\d+)""") to false,
            Regex("""(?i)"video_duration"\s*:\s*"?(\d+)""") to false,
            Regex("""(?i)"length_in_second"\s*:\s*"?(\d+)""") to false
        )
        for ((pattern, isMilliseconds) in patterns) {
            val match = pattern.find(html) ?: continue
            val value = match.groupValues[1].toLongOrNull() ?: continue
            if (value <= 0L) continue
            return if (isMilliseconds) value else value * 1000L
        }

        // ISO-8601: PT#H#M#S (ví dụ og metadata dạng "PT4M37S")
        val iso = Regex("""(?i)(duration|thời l[ưượng])[^>]{0,80}?\bPT(?:(\d{1,4})H)?(?:(\d{1,4})M)?(\d{1,4})S""").find(html)
        if (iso != null) {
            val h = iso.groupValues[2].toLongOrNull() ?: 0L
            val m = iso.groupValues[3].toLongOrNull() ?: 0L
            val s = iso.groupValues[4].toLongOrNull() ?: 0L
            val total = (h * 3600L + m * 60L + s) * 1000L
            if (total > 0L) return total
        }
        return 0L
    }

    /** Reads the video title (og:title / twitter:title) from page HTML. */
    fun parseTitleFromHtml(html: String): String {
        val ogTitle = extractMetaContent(html, "og:title", "property")
        val ogDescription = extractMetaContent(html, "og:description", "property")
        val twitterTitle = extractMetaContent(html, "twitter:title", "name")

        // Facebook thường nhét bộ đếm "N lượt xem · N cảm xúc" và hậu tố
        // " | Facebook" vào og:title, còn og:description chứa caption thật.
        val fromTitle = cleanFacebookTitle(ogTitle)
        val fromDescription = cleanFacebookTitle(ogDescription)
        val fromTwitter = cleanFacebookTitle(twitterTitle)

        if (fromDescription.isNotBlank()) return fromDescription
        if (fromTitle.isNotBlank()) return fromTitle
        if (fromTwitter.isNotBlank()) return fromTwitter

        val match = Regex("""(?i)<title[^>]*>([^<]{3,})</title>""").find(html)
        if (match != null) {
            val value = cleanFacebookTitle(match.groupValues[1])
            if (value.isNotBlank()) return value
        }
        return ""
    }

    private fun extractMetaContent(html: String, key: String, attr: String): String {
        // attr trước content
        val first = Regex("""(?i)<meta[^>]*$attr=["']$key["'][^>]*content=["']([^"']*)["']""").find(html)
        // content trước attr
        val second = first ?: Regex("""(?i)<meta[^>]*content=["']([^"']*)["'][^>]*$attr=["']$key["']""").find(html)
        return second?.groupValues?.get(1).orEmpty()
    }

    /** Tiêu đề boilerplate của Facebook — không dùng làm tên video. */
    private fun isJunkTitle(value: String): Boolean {
        val junk = setOf(
            "facebook", "facebook watch", "log into facebook", "log in to facebook",
            "log in or sign up", "sign up for facebook", "facebook - log in or sign up",
            "facebook – log in or sign up", "đăng nhập facebook", "you must log in to continue"
        )
        if (value.lowercase() in junk) return true
        return Regex("(?i)^(log ?(in|into)|đăng nhập|you must log in|vui lòng đăng nhập).{0,50}facebook").containsMatchIn(value)
    }

    private fun cleanFacebookTitle(raw: String): String {
        var value = decodeHtmlEntities(raw)
            .replace('\u00a0', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        if (value.isBlank() || isJunkTitle(value)) return ""

        // Hậu tố " | Facebook" / "- Facebook Watch" / "· Facebook"
        value = value.replace(Regex("(?i)\\s*[|·–-]\\s*Facebook( Watch)?\\s*$"), "").trim()
        if (value.isBlank() || isJunkTitle(value)) return ""

        // Tiền tố bộ đếm: "2,4K lượt xem · 38 cảm xúc | "
        value = value.replace(
            Regex("(?i)^[^|]{0,120}?\\b(lượt xem|views|cảm xúc|reactions?|likes?|shares?|comments?|lượt thích)\\b[^|]{0,120}?\\|\\s*"),
            ""
        ).trim()
        if (value.isBlank() || isJunkTitle(value)) return ""
        return value
    }

    private fun decodeHtmlEntities(value: String): String {
        var out = Regex("&#x([0-9a-fA-F]+);").replace(value) { m ->
            decodeNumericEntity(m.groupValues[1], 16) ?: m.value
        }
        out = Regex("&#(\\d+);").replace(out) { m ->
            decodeNumericEntity(m.groupValues[1], 10) ?: m.value
        }
        return out
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&#x27;", "'")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
    }

    private fun decodeNumericEntity(digits: String, radix: Int): String? {
        val code = digits.toIntOrNull(radix) ?: return null
        if (code !in 1..0x10FFFF || code in 0xD800..0xDFFF) return null
        return String(Character.toChars(code))
    }

    //endregion


    private data class Candidate(
        val text: String,
        val score: Int,
        val rect: Rect
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
                else it.rect.bottom <= anchor.top + 10
            }
            .filter {
                kotlin.math.abs(it.rect.centerX() - best.rect.centerX()) <= 650
            }
            .filter {
                kotlin.math.abs(it.rect.centerY() - best.rect.centerY()) <= 220
            }
            .filter {
                it.text.any { ch -> ch.isWhitespace() } || it.text.length >= 12
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
