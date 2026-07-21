package com.rameshta.quietpdf.pdf

import android.content.Context
import androidx.annotation.StringRes
import com.rameshta.quietpdf.R
import org.json.JSONArray
import org.json.JSONObject

enum class PdfHistoryOperation(@get:StringRes val labelResource: Int) {
    ScanDocument(R.string.history_scan_document),
    ImagesToPdf(R.string.history_images_to_pdf),
    MergePdf(R.string.history_merge_pdf),
    SplitPdf(R.string.history_split_pdf),
    ExtractPages(R.string.history_extract_pages),
    DeletePages(R.string.history_delete_pages),
    RearrangePages(R.string.history_rearrange_pages),
    RotatePages(R.string.history_rotate_pages),
    DuplicatePages(R.string.history_duplicate_pages),
    CompressPdf(R.string.history_compress_pdf),
    ProtectPdf(R.string.history_protect_pdf),
    RemovePassword(R.string.history_remove_password),
    ChangePassword(R.string.history_change_password),
    TextWatermark(R.string.history_text_watermark),
    ImageWatermark(R.string.history_image_watermark),
    ExtractImages(R.string.history_extract_images),
    FillForms(R.string.history_fill_forms),
    SignPdf(R.string.history_sign_pdf),
    AnnotatePdf(R.string.history_annotate_pdf),
}

data class PdfHistoryEntry(
    val operation: PdfHistoryOperation,
    val completedEpochMillis: Long,
)

class PdfHistoryStore(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun load(): List<PdfHistoryEntry> {
        val encoded = preferences.getString(ENTRIES_KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(encoded)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val operation = runCatching {
                        PdfHistoryOperation.valueOf(item.optString(OPERATION_KEY))
                    }.getOrNull() ?: continue
                    val completed = item.optLong(COMPLETED_KEY, -1L)
                    if (completed < 0L) continue
                    add(PdfHistoryEntry(operation, completed))
                }
            }.sortedByDescending(PdfHistoryEntry::completedEpochMillis).take(MAX_ENTRIES)
        } catch (_: Exception) {
            preferences.edit().remove(ENTRIES_KEY).apply()
            emptyList()
        }
    }

    @Synchronized
    fun record(operation: PdfHistoryOperation): List<PdfHistoryEntry> {
        val updated = (listOf(PdfHistoryEntry(operation, clock().coerceAtLeast(0L))) + load())
            .sortedByDescending(PdfHistoryEntry::completedEpochMillis)
            .take(MAX_ENTRIES)
        persist(updated)
        return updated
    }

    @Synchronized
    fun clear() {
        preferences.edit().remove(ENTRIES_KEY).apply()
    }

    @Synchronized
    fun remove(entry: PdfHistoryEntry): List<PdfHistoryEntry> {
        val updated = load().toMutableList().also { entries ->
            val index = entries.indexOf(entry)
            if (index >= 0) entries.removeAt(index)
        }
        persist(updated)
        return updated
    }

    private fun persist(entries: List<PdfHistoryEntry>) {
        val array = JSONArray().apply {
            entries.take(MAX_ENTRIES).forEach { entry ->
                put(JSONObject().apply {
                    put(OPERATION_KEY, entry.operation.name)
                    put(COMPLETED_KEY, entry.completedEpochMillis)
                })
            }
        }
        preferences.edit().putString(ENTRIES_KEY, array.toString()).apply()
    }

    companion object {
        const val PREFERENCES_NAME = "pdf_history"
        const val MAX_ENTRIES = 50
        private const val ENTRIES_KEY = "entries"
        private const val OPERATION_KEY = "operation"
        private const val COMPLETED_KEY = "completed"
    }
}
