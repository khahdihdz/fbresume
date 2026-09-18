package com.khahdihdz.fbresume

import android.content.Context
import org.json.JSONObject

data class ResumeItem(
    val key: String,
    val title: String,
    val url: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long
)

class ResumeStore(context: Context) {
    private val prefs = context.getSharedPreferences("fbresume", Context.MODE_PRIVATE)

    fun save(item: ResumeItem) {
        val obj = JSONObject()
            .put("key", item.key)
            .put("title", item.title)
            .put("url", item.url)
            .put("positionMs", item.positionMs)
            .put("durationMs", item.durationMs)
            .put("updatedAt", item.updatedAt)
        prefs.edit().putString("item:${item.key}", obj.toString()).apply()
    }

    fun get(key: String): ResumeItem? {
        val raw = prefs.getString("item:$key", null) ?: return null
        val o = JSONObject(raw)
        return ResumeItem(
            o.optString("key"), o.optString("title"), o.optString("url"),
            o.optLong("positionMs"), o.optLong("durationMs"), o.optLong("updatedAt")
        )
    }

    fun keys(): List<String> =
        prefs.all.keys.filter { it.startsWith("item:") }.map { it.removePrefix("item:") }
}
