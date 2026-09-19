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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.app.AlertDialog
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.graphics.drawable.GradientDrawable

class MainActivity : AppCompatActivity() {
    private lateinit var store: ResumeStore
    private val donateUrl = "https://khahdihdz.github.io"

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); store = ResumeStore(this); render(); checkForUpdate() }
    override fun onResume() { super.onResume(); if (::store.isInitialized) render() }

    private fun openUrl(url: String) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

    private fun showNavMenu() {
        val labels = arrayOf("Trang chủ", "Cài đặt Accessibility", "❤️ Donate / Ủng hộ", "Kiểm tra cập nhật")
        AlertDialog.Builder(this).setTitle("FBResume").setItems(labels) { _, which ->
            when (which) {
                0 -> render()
                1 -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                2 -> openUrl(donateUrl)
                3 -> checkForUpdate(true)
            }
        }.show()
    }

    private fun checkForUpdate(manual: Boolean = false) {
        Thread {
            try {
                val url = java.net.URL("https://api.github.com/repos/khahdihdz/fbresume/releases/latest")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.connectTimeout = 4000; connection.readTimeout = 4000
                val json = connection.inputStream.bufferedReader().use { it.readText() }
                val tag = Regex(""""tag_name"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1) ?: return@Thread
                val apk = Regex(""browser_download_url"\\s*:\\s*"([^"]*FBResume\\.apk)"").find(json)?.groupValues?.get(1) ?: return@Thread
                val latest = tag.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
                val current = packageManager.getPackageInfo(packageName, 0).versionName ?: "0.0.0".split(".").mapNotNull { it.toIntOrNull() }
                val newer = latest.zip(current).firstOrNull { it.first != it.second }?.let { it.first > it.second } ?: (latest.size > current.size)
                if (newer) runOnUiThread { AlertDialog.Builder(this).setTitle("Có phiên bản mới").setMessage("FBResume $tag đã có sẵn. Bạn có muốn mở trang tải bản cập nhật?").setNegativeButton("Để sau", null).setPositiveButton("Cập nhật") { _, _ -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(apk))) }.show() }
            } catch (_: Exception) { }
        }.start()
    }

    private fun render() {
        val items = store.all()
        val enabled = isAccessibilityEnabled()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(247,249,252)) }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(root) }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20),dp(18),dp(20),dp(28)) }

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(ImageView(this).apply { setImageResource(R.drawable.ic_fbresume) }, LinearLayout.LayoutParams(dp(54),dp(54)))
        val titleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14),0,0,0) }
        titleBox.addView(label("FBResume",26f,Color.rgb(20,30,45),true))
        titleBox.addView(label("Ghi nhớ video Facebook",14f,Color.rgb(105,115,130),false))
        header.addView(titleBox, LinearLayout.LayoutParams(0,-2,1f)); header.addView(TextView(this).apply { text="☰"; textSize=28f; gravity=Gravity.CENTER; setTextColor(Color.rgb(35,45,60)); setOnClickListener { showNavMenu() } }, LinearLayout.LayoutParams(dp(48),dp(54))); content.addView(header)

        content.addView(ImageView(this).apply { setImageResource(R.drawable.fbresume_hero); adjustViewBounds=true; scaleType=ImageView.ScaleType.CENTER_INSIDE; setPadding(0,dp(10),0,dp(4)) }, LinearLayout.LayoutParams(-1,dp(155)))

        val status = card()
        val row = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        row.addView(View(this).apply { background=pill(if(enabled) Color.rgb(46,190,105) else Color.rgb(245,166,35)) }, LinearLayout.LayoutParams(dp(12),dp(12)))
        val st = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(12),0,0,0) }
        st.addView(label("Accessibility",15f,Color.rgb(35,45,60),true))
        st.addView(label(if(enabled) "Đang hoạt động • Sẵn sàng ghi nhớ" else "Chưa bật • Cần cấp quyền",13f,Color.rgb(100,110,125),false))
        row.addView(st,LinearLayout.LayoutParams(0,-2,1f)); status.addView(row); content.addView(status,marginBottom())

        content.addView(Button(this).apply {
            text=if(enabled) "Mở cài đặt Accessibility" else "Bật Accessibility ngay"; isAllCaps=false; textSize=15f; setTextColor(Color.WHITE)
            background=rounded(Color.rgb(24,119,242),16); setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        },LinearLayout.LayoutParams(-1,dp(52)).apply{bottomMargin=dp(18)})

        val stats=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        stats.addView(statCard(items.size.toString(),"Video đã lưu"))
        stats.addView(statCard(if(items.isEmpty()) "—" else formatTime(items.first().positionMs),"Vị trí gần nhất"),LinearLayout.LayoutParams(0,-2,1f).apply{leftMargin=dp(8)})
        content.addView(stats,marginBottom())
        content.addView(label("Video gần đây",18f,Color.rgb(25,35,50),true),marginBottom())

        if(items.isEmpty()){
            val empty=card()
            empty.addView(label("Chưa có video được lưu",16f,Color.rgb(50,60,75),true))
            empty.addView(label("Mở Facebook, bật Accessibility và xem video vài giây. FBResume sẽ tự ghi nhớ vị trí.",14f,Color.rgb(105,115,130),false))
            content.addView(empty)
        } else items.take(10).forEach {
            val rowCard=card()
            rowCard.addView(label(it.title.take(70),15f,Color.rgb(35,45,60),true))
            rowCard.addView(label(formatTime(it.positionMs)+" / "+formatTime(it.durationMs),13f,Color.rgb(100,110,125),false))
            content.addView(rowCard,marginBottom(8))
        }

        val donate = card().apply { setOnClickListener { openUrl(donateUrl) }; addView(label("❤️ Donate / Ủng hộ",17f,Color.rgb(35,45,60),true)); addView(label("Ủng hộ tác giả tại khahdihdz.github.io",13f,Color.rgb(100,110,125),false)); addView(label(donateUrl,13f,Color.rgb(24,119,242),false)) }
        content.addView(donate, marginBottom(10))
        content.addView(label("FBResume • tự động lưu tiến độ xem",12f,Color.rgb(145,152,165),false).apply{gravity=Gravity.CENTER;setPadding(0,dp(20),0,0)})
        root.addView(content); setContentView(scroll)
    }

    private fun card()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(15),dp(16),dp(15));background=rounded(Color.WHITE,18);elevation=dp(2).toFloat()}
    private fun statCard(value:String,caption:String)=card().apply{addView(label(value,22f,Color.rgb(24,119,242),true));addView(label(caption,12f,Color.rgb(105,115,130),false));layoutParams=LinearLayout.LayoutParams(0,-2,1f)}
    private fun label(text:String,size:Float,color:Int,bold:Boolean)=TextView(this).apply{this.text=text;textSize=size;setTextColor(color);typeface=Typeface.create("sans",if(bold)Typeface.BOLD else Typeface.NORMAL);setPadding(0,dp(2),0,dp(2))}
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