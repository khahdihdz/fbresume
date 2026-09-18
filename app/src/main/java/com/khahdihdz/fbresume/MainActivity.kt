package com.khahdihdz.fbresume

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = TextView(this).apply {
            text = "FBResume\n\nLưu và khôi phục vị trí video Facebook"
            textSize = 22f
            setPadding(32, 48, 32, 32)
        }
        val status = TextView(this).apply {
            text = "Accessibility: bật dịch vụ FBResume trong Cài đặt trợ năng."
            textSize = 16f
            setPadding(32, 16, 32, 32)
        }
        val settings = Button(this).apply {
            text = "Mở Accessibility"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(title); addView(status); addView(settings)
        })
    }
}
