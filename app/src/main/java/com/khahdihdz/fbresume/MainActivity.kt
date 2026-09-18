package com.khahdihdz.fbresume

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var store: ResumeStore
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ResumeStore(this)
        render()
    }
    override fun onResume() { super.onResume(); if (::store.isInitialized) render() }

    private fun render() {
        val title = TextView(this).apply {
            text = "FBResume"
            textSize = 28f
            gravity = Gravity.CENTER
            setPadding(24, 40, 24, 12)
        }
        val info = TextView(this).apply {
            text = "Tự lưu vị trí video Facebook và thử tiếp tục khi mở lại."
            textSize = 16f
            setPadding(24, 12, 24, 24)
        }
        val openSettings = Button(this).apply {
            text = "Mở Accessibility"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        val list = TextView(this).apply {
            val items = store.all()
            text = if (items.isEmpty()) {
                "Chưa có video được lưu.\n\nSau khi bật Accessibility, mở video Facebook và để video chạy vài giây."
            } else {
                items.take(10).joinToString("\n\n") {
                    "• " + it.title.take(70) + "\n  " + formatTime(it.positionMs) + " / " + formatTime(it.durationMs)
                }
            }
            textSize = 15f
            setPadding(24, 20, 24, 24)
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
            addView(title); addView(info); addView(openSettings); addView(list)
        })
    }

    private fun formatTime(ms: Long): String {
        val total = ms / 1000L
        val h = total / 3600L
        val m = (total % 3600L) / 60L
        val s = total % 60L
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
