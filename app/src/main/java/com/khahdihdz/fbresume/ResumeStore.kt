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
        prefs.edit().putString("item:" + item.key, obj.toString()).apply()
    }

    fun get(key: String): ResumeItem? {
        val raw = prefs.getString("item:" + key, null) ?: return null
        val o = JSONObject(raw)
        return ResumeItem(o.optString("key"), o.optString("title"), o.optString("url"), o.optLong("positionMs"), o.optLong("durationMs"), o.optLong("updatedAt"))
    }

    fun keys(): List<String> = prefs.all.keys.filter { it.startsWith("item:") }.map { it.removePrefix("item:") }

    fun all(): List<ResumeItem> = keys().mapNotNull(::get).sortedByDescending { it.updatedAt }

    fun remove(key: String) {
        prefs.edit().remove("item:" + key).apply()
        if (getPendingKey() == key) clearPendingKey()
    }


    /** Finds an old saved video that was created before URL capture was added. */
    fun findLegacyMatch(title: String, durationMs: Long): ResumeItem? {
        if (title.isBlank() || durationMs <= 0L) return null
        val normalizedTitle = normalizeTitle(title)
        return all()
            .asSequence()
            .filter { it.url.isBlank() }
            .filter { kotlin.math.abs(it.durationMs - durationMs) <= maxOf(5_000L, durationMs / 20L) }
            .map { it to titleSimilarity(normalizedTitle, normalizeTitle(it.title)) }
            .filter { it.second >= 0.62 }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun normalizeTitle(value: String): String =
        value.lowercase()
            .replace(Regex("""https?://\S+"""), " ")
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun titleSimilarity(a: String, b: String): Double {
        if (a.isBlank() || b.isBlank()) return 0.0
        if (a == b) return 1.0
        val aa = a.split(" ").filter { it.length >= 2 }.toSet()
        val bb = b.split(" ").filter { it.length >= 2 }.toSet()
        if (aa.isEmpty() || bb.isEmpty()) return 0.0
        val intersection = aa.intersect(bb).size.toDouble()
        return intersection / aa.union(bb).size.toDouble()
    }

    fun setPendingKey(key: String) {
        prefs.edit().putString("pending_resume_key", key).apply()
    }

    fun getPendingKey(): String? =
        prefs.getString("pending_resume_key", null)

    fun clearPendingKey() {
        prefs.edit().remove("pending_resume_key").apply()
    }
}
