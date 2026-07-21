package com.rameshta.quietpdf.pdf

import android.content.Context
import android.net.Uri
import java.security.MessageDigest

class PdfBookmarkStore(context: Context) {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    fun restore(uri: Uri, pageCount: Int): Set<Int> {
        if (pageCount <= 0) return emptySet()
        return preferences.getStringSet(keyFor(uri), emptySet()).orEmpty()
            .mapNotNull(String::toIntOrNull)
            .filterTo(sortedSetOf()) { it in 0 until pageCount }
    }

    fun toggle(uri: Uri, pageIndex: Int, pageCount: Int): Set<Int> {
        if (pageCount <= 0 || pageIndex !in 0 until pageCount) return restore(uri, pageCount)
        val updated = restore(uri, pageCount).toMutableSet().apply {
            if (!add(pageIndex)) remove(pageIndex)
        }.toSortedSet()
        preferences.edit().putStringSet(keyFor(uri), updated.map(Int::toString).toSet()).apply()
        return updated
    }

    private fun keyFor(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(uri.toString().toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
        return "$BookmarkKeyPrefix$digest"
    }

    companion object {
        const val PreferencesName = "pdf_bookmarks"
        private const val BookmarkKeyPrefix = "bookmarks_"
    }
}
