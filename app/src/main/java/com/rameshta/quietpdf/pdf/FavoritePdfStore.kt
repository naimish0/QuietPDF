package com.rameshta.quietpdf.pdf

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

data class FavoritePdf(
    val uri: Uri,
    val displayName: String?,
    val pageCount: Int,
    val addedEpochMillis: Long,
)

class FavoritePdfStore(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun load(): List<FavoritePdf> {
        val encoded = preferences.getString(ENTRIES_KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(encoded)
            buildList {
                val seen = mutableSetOf<String>()
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val uriText = item.optString(URI_KEY).trim()
                    if (!seen.add(uriText)) continue
                    val uri = runCatching { Uri.parse(uriText) }.getOrNull() ?: continue
                    val pageCount = item.optInt(PAGE_COUNT_KEY, 0)
                    val added = item.optLong(ADDED_KEY, -1L)
                    if (uri.scheme != CONTENT_SCHEME || pageCount <= 0 || added < 0L) continue
                    val name = item.optString(DISPLAY_NAME_KEY).trim().takeIf(String::isNotEmpty)
                    add(FavoritePdf(uri, name, pageCount, added))
                }
            }.sortedByDescending(FavoritePdf::addedEpochMillis).take(MAX_ENTRIES)
        } catch (_: Exception) {
            preferences.edit().remove(ENTRIES_KEY).apply()
            emptyList()
        }
    }

    @Synchronized
    fun add(uri: Uri, displayName: String?, pageCount: Int): List<FavoritePdf> {
        if (uri.scheme != CONTENT_SCHEME || pageCount <= 0) return load()
        val current = load()
        val existing = current.firstOrNull { it.uri == uri }
        val favorite = FavoritePdf(
            uri = uri,
            displayName = normalizedName(displayName),
            pageCount = pageCount,
            addedEpochMillis = existing?.addedEpochMillis ?: clock().coerceAtLeast(0L),
        )
        val updated = (listOf(favorite) + current.filterNot { it.uri == uri })
            .sortedByDescending(FavoritePdf::addedEpochMillis)
            .take(MAX_ENTRIES)
        persist(updated)
        return updated
    }

    @Synchronized
    fun remove(uri: Uri): List<FavoritePdf> {
        val updated = load().filterNot { it.uri == uri }
        persist(updated)
        return updated
    }

    private fun normalizedName(displayName: String?): String? =
        displayName?.trim()?.take(MAX_DISPLAY_NAME_LENGTH)?.takeIf(String::isNotEmpty)

    private fun persist(entries: List<FavoritePdf>) {
        val array = JSONArray().apply {
            entries.take(MAX_ENTRIES).forEach { entry ->
                put(JSONObject().apply {
                    put(URI_KEY, entry.uri.toString())
                    put(DISPLAY_NAME_KEY, entry.displayName.orEmpty())
                    put(PAGE_COUNT_KEY, entry.pageCount)
                    put(ADDED_KEY, entry.addedEpochMillis)
                })
            }
        }
        preferences.edit().putString(ENTRIES_KEY, array.toString()).apply()
    }

    companion object {
        const val PREFERENCES_NAME = "favorite_pdfs"
        const val MAX_ENTRIES = 20
        private const val ENTRIES_KEY = "entries"
        private const val URI_KEY = "uri"
        private const val DISPLAY_NAME_KEY = "display_name"
        private const val PAGE_COUNT_KEY = "page_count"
        private const val ADDED_KEY = "added"
        private const val CONTENT_SCHEME = "content"
        private const val MAX_DISPLAY_NAME_LENGTH = 256
    }
}
