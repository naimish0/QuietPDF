package com.rameshta.quietpdf.pdf

import android.content.Context
import android.net.Uri
import java.security.MessageDigest

class ReadingPositionStore(context: Context) {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    fun restore(uri: Uri, pageCount: Int): Int {
        if (pageCount <= 0) return 0
        return preferences.getInt(keyFor(uri), 0).coerceIn(0, pageCount - 1)
    }

    fun remember(uri: Uri, pageIndex: Int, pageCount: Int) {
        if (pageCount <= 0 || pageIndex !in 0 until pageCount) return
        val key = keyFor(uri)
        if (preferences.getInt(key, -1) == pageIndex) return
        preferences.edit().putInt(key, pageIndex).apply()
    }

    private fun keyFor(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(uri.toString().toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
        return "$PositionKeyPrefix$digest"
    }

    companion object {
        const val PreferencesName = "reading_positions"
        private const val PositionKeyPrefix = "page_"
    }
}
