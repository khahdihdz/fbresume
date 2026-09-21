package com.khahdihdz.fbresume

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.app.AlertDialog
import android.net.Uri
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import androidx.appcompat.app.AppCompatActivity
import android.graphics.drawable.GradientDrawable

class MainActivity : AppCompatActivity() {
    private lateinit var store: ResumeStore
    private val donateUrl = "https://khahdihdz.github.io"

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); store = ResumeStore(this); render(); checkForUpdate() }
    override fun onResume() { super.onResume(); if (::store.isInitialized) render() }

    private fun openUrl(url: String) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

    private fun showNavMenu() {
        val dialogBuilder = AlertDialog.Builder(this)
        lateinit var alert: AlertDialog
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
            background = rounded(surfaceColor, 24)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(8), dp(10))
        }
        val headerBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        headerBox.addView(label("FBResume", 22f, Color.rgb(20, 30, 45), true))
        headerBox.addView(label("Menu điều hướng", 12f, secondaryTextColor, false))
        header.addView(headerBox, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(this).apply {
            text = "×"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(80, 90, 105))
            setOnClickListener { alert.dismiss() }
        }, LinearLayout.LayoutParams(dp(42), dp(42)))
        container.addView(header)

        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(2), dp(8), dp(8))
        }
        val tabContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, dp(8), dp(4))
        }
        val tabScroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            addView(tabContent, LinearLayout.LayoutParams(-1, -2))
        }
        val tabButtons = mutableListOf<TextView>()

        fun addTab(title: String): TextView {
            return TextView(this).apply {
                text = title
                textSize = 13f
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(8), dp(11), dp(8), dp(11))
                background = rounded(backgroundColor, 14)
                setTextColor(Color.rgb(95, 105, 120))
                tabs.addView(this, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                    if (tabButtons.isNotEmpty()) leftMargin = dp(6)
                })
                tabButtons.add(this)
            }
        }

        val overviewTab = addTab("Tổng quan")
        val systemTab = addTab("Hệ thống")
        val otherTab = addTab("Khác")

        fun setActiveTab(index: Int) {
            tabButtons.forEachIndexed { i, tab ->
                if (i == index) {
                    tab.background = rounded(primaryColor, 14)
                    tab.setTextColor(surfaceColor)
                } else {
                    tab.background = rounded(Color.rgb(247, 249, 252), 14)
                    tab.setTextColor(Color.rgb(95, 105, 120))
                }
            }
            tabContent.removeAllViews()

            when (index) {
                0 -> {
                    addNavSection(tabContent, "Trang chủ", "Tổng quan và các video đã lưu", "⌂") {
                        alert.dismiss()
                        render()
                    }
                    addNavSection(tabContent, "Video đã lưu", "Mở danh sách tiến độ đã ghi nhớ", "▶") {
                        alert.dismiss()
                        Toast.makeText(this, "Danh sách video đã lưu nằm ở màn hình chính.", Toast.LENGTH_SHORT).show()
                    }
                }
                1 -> {
                    val enabled = isAccessibilityEnabled()
                    addNavSection(
                        tabContent,
                        if (enabled) "Accessibility đang bật" else "Bật Accessibility",
                        if (enabled) "FBResume đã có quyền theo dõi tiến độ video" else "Cấp quyền để FBResume tự động ghi nhớ vị trí",
                        if (enabled) "✓" else "!"
                    ) {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                    addNavSection(tabContent, "Kiểm tra cập nhật", "Kiểm tra phiên bản FBResume mới nhất", "↻") {
                        alert.dismiss()
                        checkForUpdate(true)
                    }
                }
                2 -> {
                    addNavSection(tabContent, "Donate / Ủng hộ", "Ủng hộ tác giả tại khahdihdz.github.io", "♥") {
                        openUrl(donateUrl)
                    }
                    addNavSection(tabContent, "Giới thiệu", "Thông tin về FBResume và quyền riêng tư", "ⓘ") {
                        showStyledDialog(
                            title = "Giới thiệu FBResume",
                            message = "FBResume giúp ghi nhớ vị trí video Facebook. Ứng dụng dùng Accessibility để theo dõi tiến độ và lưu dữ liệu trên thiết bị."
                        )
                    }
                }
            }
        }

        overviewTab.setOnClickListener { setActiveTab(0) }
        systemTab.setOnClickListener { setActiveTab(1) }
        otherTab.setOnClickListener { setActiveTab(2) }

        container.addView(tabs)
        container.addView(tabScroll, LinearLayout.LayoutParams(-1, dp(190)))

        dialogBuilder.setView(container)
        alert = dialogBuilder.create()
        alert.setOnShowListener {
            alert.window?.setBackgroundDrawableResource(android.R.color.transparent)
            setActiveTab(0)
        }
        alert.setOnDismissListener { }
        alert.show()
    }

    private fun addNavSection(
        parent: LinearLayout,
        title: String,
        subtitle: String,
        icon: String,
        action: () -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(10), dp(10))
            background = rounded(Color.rgb(248, 250, 253), 16)
            isClickable = true
            setOnClickListener { action() }
        }
        row.addView(TextView(this).apply {
            text = icon
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(primaryColor)
            background = rounded(surfaceColor, 12)
        }, LinearLayout.LayoutParams(dp(44), dp(44)))

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(6), 0)
        }
        box.addView(label(title, 15f, Color.rgb(30, 40, 55), true))
        box.addView(label(subtitle, 12f, Color.rgb(105, 115, 130), false))
        row.addView(box, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(TextView(this).apply {
            text = "›"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(150, 158, 170))
        }, LinearLayout.LayoutParams(dp(30), dp(44)))

        row.minimumHeight = dp(72)
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(8)
        })
    }

    private fun showAllVideos() {
        val allItems = store.all()
        lateinit var dialog: AlertDialog
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(12)); background = rounded(surfaceColor, 22) }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val titleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleBox.addView(label("Video đã lưu", 21f, textColor, true))
        titleBox.addView(label("${allItems.size} video được lưu trên thiết bị", 12f, secondaryTextColor, false))
        header.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(this).apply { text = "×"; textSize = 28f; gravity = Gravity.CENTER; setTextColor(secondaryTextColor); setOnClickListener { dialog.dismiss() } }, LinearLayout.LayoutParams(dp(42), dp(42)))
        container.addView(header)
        val search = EditText(this).apply { hint = "Tìm kiếm video đã lưu…"; textSize = 14f; isSingleLine = true; setPadding(dp(14), 0, dp(14), 0); background = rounded(backgroundColor, 14) }
        container.addView(search, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(12) })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(list) }
        container.addView(scroll, LinearLayout.LayoutParams(-1, dp(390)).apply { topMargin = dp(10) })
        fun continueWatching(item: ResumeItem) {
            if (item.url.isNotBlank()) openUrl(item.url) else try {
                val intent = packageManager.getLaunchIntentForPackage(FacebookDetector.FACEBOOK_PACKAGE) ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com/"))
                startActivity(intent)
                Toast.makeText(this@MainActivity, "Đã mở Facebook. Mở video “${item.title.take(45)}”; FBResume sẽ tự tiếp tục tại ${formatTime(item.positionMs)}.", Toast.LENGTH_LONG).show()
            } catch (_: Exception) { openUrl("https://www.facebook.com/") }
        }
        fun renderList(query: String) {
            list.removeAllViews(); val q = query.trim().lowercase()
            val items = allItems.filter { q.isBlank() || it.title.lowercase().contains(q) || it.url.lowercase().contains(q) }
            if (items.isEmpty()) { list.addView(label(if (q.isBlank()) "Chưa có video nào được lưu." else "Không tìm thấy video phù hợp.", 14f, secondaryTextColor, false).apply { setPadding(0, dp(20), 0, dp(20)) }); return }
            items.forEach { item ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(10), dp(8), dp(10)); background = rounded(backgroundColor, 16) }
                val infoRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                info.addView(label(item.title.take(100), 14f, textColor, true))
                info.addView(label("Đã xem ${formatTime(item.positionMs)} / ${formatTime(item.durationMs)}", 12f, secondaryTextColor, false))
                infoRow.addView(info, LinearLayout.LayoutParams(0, -2, 1f)); row.addView(infoRow)
                val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END or Gravity.CENTER_VERTICAL }
                actions.addView(TextView(this).apply { text = "Tiếp tục xem"; textSize = 12f; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); background = rounded(primaryColor, 12); setPadding(dp(14), 0, dp(14), 0); setOnClickListener { continueWatching(item) } }, LinearLayout.LayoutParams(-2, dp(40)).apply { topMargin = dp(8) })
                actions.addView(TextView(this).apply { text = "Xóa"; textSize = 12f; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(210, 55, 65)); background = rounded(Color.rgb(255, 240, 242), 12); setPadding(dp(14), 0, dp(14), 0); setOnClickListener { showStyledDialog(title = "Xóa video?", message = "Xóa \"" + item.title.take(80) + "\" khỏi danh sách video đã lưu?", positiveText = "Xóa", negativeText = "Hủy") { store.remove(item.key); dialog.dismiss(); render(); showAllVideos(); Toast.makeText(this@MainActivity, "Đã xóa video", Toast.LENGTH_SHORT).show() } } }, LinearLayout.LayoutParams(-2, dp(40)).apply { topMargin = dp(8); leftMargin = dp(8) })
                row.addView(actions); list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            }
        }
        search.addTextChangedListener(object : android.text.TextWatcher { override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit; override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { renderList(s?.toString().orEmpty()) }; override fun afterTextChanged(s: android.text.Editable?) = Unit })
        renderList("")
        dialog = AlertDialog.Builder(this).setView(container).create()
        dialog.setOnShowListener { dialog.window?.setBackgroundDrawableResource(android.R.color.transparent) }
        dialog.show()
    }
    private fun showStyledDialog(
        title: String,
        message: String,
        positiveText: String = "Đóng",
        negativeText: String? = null,
        onPositive: (() -> Unit)? = null
    ) {
        lateinit var dialog: AlertDialog
        val dialogContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(22), dp(20), dp(10))
            background = rounded(surfaceColor, 22)
        }

        dialogContent.addView(label(title, 21f, textColor, true))
        dialogContent.addView(label(message, 15f, secondaryTextColor, false).apply {
            setPadding(0, dp(12), 0, dp(8))
        })

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }

        if (negativeText != null) {
            actions.addView(TextView(this).apply {
                text = negativeText
                textSize = 14f
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(secondaryTextColor)
                background = rounded(backgroundColor, 12)
                setPadding(dp(16), 0, dp(16), 0)
                setOnClickListener { dialog.dismiss() }
            }, LinearLayout.LayoutParams(-2, dp(44)).apply {
                rightMargin = dp(8)
            })
        }

        actions.addView(TextView(this).apply {
            text = positiveText
            textSize = 14f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = rounded(primaryColor, 12)
            setPadding(dp(18), 0, dp(18), 0)
            setOnClickListener {
                dialog.dismiss()
                onPositive?.invoke()
            }
        }, LinearLayout.LayoutParams(-2, dp(44)))

        dialogContent.addView(actions)
        dialog = AlertDialog.Builder(this).setView(dialogContent).create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
        dialog.show()
    }

    private fun checkForUpdate(manual: Boolean = false) {
        Thread {
            try {
                val url = java.net.URL("https://api.github.com/repos/khahdihdz/fbresume/releases/latest")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", "FBResume")
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                val json = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                val root = org.json.JSONObject(json)
                val tag = root.optString("tag_name", "").trim()
                val assets = root.optJSONArray("assets")
                var apkUrl: String? = null

                if (assets != null) {
                    val preferredNames = listOf(
                        "FBResume-release-signed.apk",
                        "FBResume.apk",
                        "app-release.apk",
                        "FBResume-debug-signed.apk"
                    )
                    for (preferred in preferredNames) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.optJSONObject(i) ?: continue
                            if (asset.optString("name") == preferred) {
                                apkUrl = asset.optString("browser_download_url", "").takeIf { it.isNotBlank() }
                                if (apkUrl != null) break
                            }
                        }
                        if (apkUrl != null) break
                    }

                    if (apkUrl == null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.optJSONObject(i) ?: continue
                            if (asset.optString("name", "").endsWith(".apk", ignoreCase = true)) {
                                apkUrl = asset.optString("browser_download_url", "").takeIf { it.isNotBlank() }
                                if (apkUrl != null) break
                            }
                        }
                    }
                }

                if (tag.isBlank() || apkUrl.isNullOrBlank()) {
                    if (manual) runOnUiThread {
                        showStyledDialog(
                            title = "Kiểm tra cập nhật",
                            message = "Release mới chưa có APK để tải xuống.",
                            positiveText = "Mở Releases",
                            negativeText = "Đóng"
                        ) {
                            openUrl("https://github.com/khahdihdz/fbresume/releases/latest")
                        }
                    }
                    return@Thread
                }

                val latest = parseVersion(tag)
                val packageInfo = packageManager.getPackageInfo(packageName, 0)
                val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }
                val currentName = packageInfo.versionName ?: "0.0.0"

                val newer = latest.versionCode?.let { it > currentVersionCode }
                    ?: (compareVersions(latest.parts, parseVersion(currentName).parts) > 0)

                runOnUiThread {
                    if (newer) {
                        Toast.makeText(
                            this,
                            "Có bản mới $tag — đang tải cập nhật…",
                            Toast.LENGTH_LONG
                        ).show()
                        downloadAndInstallUpdate(apkUrl!!, tag)
                    } else if (manual) {
                        showStyledDialog(
                            title = "FBResume",
                            message = "Bạn đang dùng phiên bản mới nhất ($currentName)"
                        )
                    }
                }
            } catch (_: Exception) {
                if (manual) runOnUiThread {
                    showStyledDialog(
                        title = "FBResume",
                        message = "Không thể kiểm tra cập nhật lúc này."
                    )
                }
            }
        }.start()
    }

    private data class ParsedVersion(val parts: List<Int>, val versionCode: Long?)

    private fun parseVersion(raw: String): ParsedVersion {
        val cleaned = raw.trim().removePrefix("v").removePrefix("V")
        val parts = cleaned.split(".").mapNotNull { it.toIntOrNull() }
        val code = if (parts.size >= 3 && parts[0] == 1 && parts[1] == 0) parts[2].toLong() else null
        return ParsedVersion(parts, code)
    }

    private fun downloadAndInstallUpdate(apkUrl: String, tag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            showStyledDialog(
                title = "Cho phép tự động cập nhật",
                message = "Android cần cho phép FBResume cài APK từ nguồn này. Bật quyền một lần để các bản cập nhật sau có thể tự tải và mở trình cài đặt.",
                positiveText = "Mở cài đặt",
                negativeText = "Hủy"
            ) {
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            }
            return
        }

        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("FBResume $tag")
            .setDescription("Đang tải bản cập nhật…")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, "FBResume-$tag.apk")

        val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = manager.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) != downloadId) return
                try {
                    val uri = manager.getUriForDownloadedFile(downloadId)
                    if (uri != null) {
                        val installIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/vnd.android.package-archive")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(installIntent)
                    } else {
                        openUrl("https://github.com/khahdihdz/fbresume/releases/latest")
                    }
                } finally {
                    unregisterReceiver(this)
                }
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, filter)
        }
        Toast.makeText(this, "Đang tải FBResume $tag…", Toast.LENGTH_SHORT).show()
    }

    private fun compareVersions(a: List<Int>, b: List<Int>): Int {
        val size = maxOf(a.size, b.size)
        for (i in 0 until size) {
            val av = a.getOrElse(i) { 0 }
            val bv = b.getOrElse(i) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }

    private fun render() {
        val items = store.all()
        val enabled = isAccessibilityEnabled()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(root) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(24))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(ImageView(this).apply { setImageResource(R.drawable.ic_fbresume) },
            LinearLayout.LayoutParams(dp(48), dp(48)))
        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
        }
        titleBox.addView(label("FBResume", 23f, Color.rgb(20, 30, 45), true))
        titleBox.addView(label(
            if (enabled) "Đang hoạt động" else "Chưa bật Accessibility",
            12f,
            if (enabled) Color.rgb(35, 165, 90) else Color.rgb(210, 135, 25),
            true
        ))
        header.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(this).apply {
            text = "☰"; textSize = 27f; gravity = Gravity.CENTER
            setTextColor(Color.rgb(35, 45, 60))
            setOnClickListener { showNavMenu() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        content.addView(header, marginBottom(14))

        val status = card()
        status.addView(label(
            if (enabled) "Sẵn sàng tiếp tục xem" else "Bật Accessibility để bắt đầu",
            20f, textColor, true
        ))
        status.addView(label(
            if (enabled) "FBResume sẽ tự động ghi nhớ vị trí video Facebook để bạn quay lại xem tiếp."
            else "Cấp quyền Accessibility một lần để ứng dụng tự động theo dõi và lưu tiến độ video.",
            13f, Color.rgb(95, 105, 120), false
        ))
        status.addView(Button(this).apply {
            text = if (enabled) "Mở cài đặt Accessibility" else "Bật Accessibility ngay"
            isAllCaps = false; textSize = 14f; setTextColor(Color.WHITE)
            background = rounded(Color.rgb(24, 119, 242), 14)
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(12) })
        content.addView(status, marginBottom(12))

        val stats = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        stats.addView(statCard(items.size.toString(), "Video đã lưu"))
        stats.addView(
            statCard(if (items.isEmpty()) "—" else formatTime(items.first().positionMs), "Vị trí gần nhất"),
            LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) }
        )
        content.addView(stats, marginBottom(16))

        val recentHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        recentHeader.addView(label("Video gần đây", 18f, Color.rgb(25, 35, 50), true),
            LinearLayout.LayoutParams(0, -2, 1f))
        recentHeader.addView(label(
            if (items.isEmpty()) "" else "${items.size} video",
            12f, Color.rgb(120, 130, 145), false
        ))
        content.addView(recentHeader, marginBottom(8))

        if (items.isEmpty()) {
            val empty = card()
            empty.addView(label("Chưa có video nào", 16f, Color.rgb(50, 60, 75), true))
            empty.addView(label(
                "Mở Facebook và xem video. FBResume sẽ tự động lưu vị trí để bạn tiếp tục sau.",
                13f, Color.rgb(105, 115, 130), false
            ))
            content.addView(empty, marginBottom(14))
        } else {
            items.take(5).forEach { item ->
                val rowCard = card()
                rowCard.setPadding(dp(14), dp(12), dp(14), dp(12))

                val infoRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val infoBox = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                }
                infoBox.addView(label(item.title.take(80), 15f, Color.rgb(35, 45, 60), true))
                infoBox.addView(label(
                    "Đã xem ${formatTime(item.positionMs)} / ${formatTime(item.durationMs)}",
                    12f, Color.rgb(100, 110, 125), false
                ))
                infoRow.addView(infoBox, LinearLayout.LayoutParams(0, -2, 1f))

                val deleteButton = TextView(this).apply {
                    text = "Xóa"
                    textSize = 12f
                    gravity = Gravity.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.rgb(210, 55, 65))
                    background = rounded(Color.rgb(255, 240, 242), 12)
                    isClickable = true
                    setPadding(dp(12), 0, dp(12), 0)
                    setOnClickListener {
                        showStyledDialog(
                            title = "Xóa video?",
                            message = "Xóa \"${item.title.take(80)}\" khỏi danh sách video gần đây?\n\nDữ liệu tiến độ của video này cũng sẽ bị xóa.",
                            positiveText = "Xóa",
                            negativeText = "Hủy"
                        ) {
                            store.remove(item.key)
                            render()
                            Toast.makeText(this@MainActivity, "Đã xóa video", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                infoRow.addView(deleteButton, LinearLayout.LayoutParams(dp(58), dp(40)).apply {
                    leftMargin = dp(8)
                })
                rowCard.addView(infoRow)
                content.addView(rowCard, marginBottom(8))
            }
            if (items.size > 5) {
                content.addView(Button(this).apply {
                    text = "Xem tất cả ${items.size} video đã lưu"
                    isAllCaps = false; textSize = 13f
                    setTextColor(Color.rgb(24, 119, 242))
                    background = rounded(Color.rgb(235, 242, 255), 14)
                    setOnClickListener { showAllVideos() }
                }, LinearLayout.LayoutParams(-1, dp(46)).apply { bottomMargin = dp(10) })
            }
        }

        val tip = label("Dữ liệu tiến độ được lưu trên thiết bị • ☰ để mở chức năng",
            11f, Color.rgb(145, 152, 165), false)
        tip.gravity = Gravity.CENTER
        tip.setPadding(0, dp(12), 0, dp(4))
        content.addView(tip)
        root.addView(content)
        setContentView(scroll)
    }

    private val primaryColor = Color.rgb(24, 119, 242)
    private val textColor = Color.rgb(25, 35, 50)
    private val secondaryTextColor = Color.rgb(105, 115, 130)
    private val surfaceColor = Color.WHITE
    private val backgroundColor = Color.rgb(247, 249, 252)

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(15), dp(16), dp(15))
        background = rounded(surfaceColor, 18)
        elevation = dp(2).toFloat()
    }

    private fun statCard(value: String, caption: String) = card().apply {
        addView(label(value, 22f, primaryColor, true))
        addView(label(caption, 12f, secondaryTextColor, false))
        layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        typeface = Typeface.create("sans", if (bold) Typeface.BOLD else Typeface.NORMAL)
        setPadding(0, dp(2), 0, dp(2))
    }
    private fun rounded(color:Int,radius:Int)=GradientDrawable().apply{setColor(color);cornerRadius=dp(radius).toFloat()}
    private fun pill(color:Int)=GradientDrawable().apply{setColor(color);shape=GradientDrawable.OVAL}
    private fun marginBottom(value:Int=16)=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(value)}
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
    private fun isAccessibilityEnabled():Boolean{
        val manager=getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any{it.resolveInfo.serviceInfo.packageName==packageName}
    }
    private fun formatTime(ms:Long):String{val total=ms/1000L;val h=total/3600L;val m=(total%3600L)/60L;val s=total%60L;return if(h>0) "%d:%02d:%02d".format(h,m,s) else "%d:%02d".format(m,s)}
}