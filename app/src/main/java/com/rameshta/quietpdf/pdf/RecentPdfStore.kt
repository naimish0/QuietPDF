package com.rameshta.quietpdf.pdf

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

data class RecentPdf(
    val uri: Uri,
    val displayName: String?,
    val pageCount: Int,
    val lastOpenedEpochMillis: Long,
)

class RecentPdfStore(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun load(): List<RecentPdf> {
        val encoded = preferences.getString(ENTRIES_KEY, null) ?: return emptyList()
        return try {
            val decoded = JSONArray(encoded)
            buildList {
                val seenUris = mutableSetOf<String>()
                for (index in 0 until decoded.length()) {
                    val item = decoded.optJSONObject(index) ?: continue
                    val uriText = item.optString(URI_KEY).trim()
                    if (!seenUris.add(uriText)) continue
                    val uri = runCatching { Uri.parse(uriText) }.getOrNull() ?: continue
                    val pageCount = item.optInt(PAGE_COUNT_KEY, 0)
                    val timestamp = item.optLong(LAST_OPENED_KEY, -1L)
                    if (uri.scheme != CONTENT_SCHEME || pageCount <= 0 || timestamp < 0L) continue
                    val name = item.optString(DISPLAY_NAME_KEY).trim().takeIf(String::isNotEmpty)
                    add(RecentPdf(uri, name, pageCount, timestamp))
                    if (size == MAX_ENTRIES) break
                }
            }.sortedByDescending(RecentPdf::lastOpenedEpochMillis)
        } catch (_: Exception) {
            preferences.edit().remove(ENTRIES_KEY).apply()
            emptyList()
        }
    }

    @Synchronized
    fun record(uri: Uri, displayName: String?, pageCount: Int): List<RecentPdf> {
        if (uri.scheme != CONTENT_SCHEME || pageCount <= 0) return load()
        val normalizedName = displayName?.trim()?.take(MAX_DISPLAY_NAME_LENGTH)
            ?.takeIf(String::isNotEmpty)
        val updated = buildList {
            add(RecentPdf(uri, normalizedName, pageCount, clock().coerceAtLeast(0L)))
            addAll(load().filterNot { it.uri == uri }.take(MAX_ENTRIES - 1))
        }
        persist(updated)
        return updated
    }

    @Synchronized
    fun remove(uri: Uri): List<RecentPdf> {
        val updated = load().filterNot { it.uri == uri }
        persist(updated)
        return updated
    }

    @Synchronized
    fun clear() {
        preferences.edit().remove(ENTRIES_KEY).apply()
    }

    private fun persist(entries: List<RecentPdf>) {
        val encoded = JSONArray().apply {
            entries.take(MAX_ENTRIES).forEach { entry ->
                put(JSONObject().apply {
                    put(URI_KEY, entry.uri.toString())
                    put(DISPLAY_NAME_KEY, entry.displayName.orEmpty())
                    put(PAGE_COUNT_KEY, entry.pageCount)
                    put(LAST_OPENED_KEY, entry.lastOpenedEpochMillis)
                })
            }
        }
        preferences.edit().putString(ENTRIES_KEY, encoded.toString()).apply()
    }

    companion object {
        const val PREFERENCES_NAME = "recent_pdfs"
        const val MAX_ENTRIES = 10
        private const val ENTRIES_KEY = "entries"
        private const val URI_KEY = "uri"
        private const val DISPLAY_NAME_KEY = "display_name"
        private const val PAGE_COUNT_KEY = "page_count"
        private const val LAST_OPENED_KEY = "last_opened"
        private const val CONTENT_SCHEME = "content"
        private const val MAX_DISPLAY_NAME_LENGTH = 256
    }
}
