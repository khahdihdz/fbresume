package com.khahdihdz.fbresume

import android.view.accessibility.AccessibilityNodeInfo

object FacebookDetector {
    const val FACEBOOK_PACKAGE = "com.facebook.katana"

    fun isFacebook(packageName: CharSequence?): Boolean =
        packageName?.toString() == FACEBOOK_PACKAGE

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

    fun findTimeStrings(root: AccessibilityNodeInfo?): List<String> =
        collectTexts(root).filter { Regex("""^\d{1,3}:\d{2}(:\d{2})?$""").matches(it) }
}
