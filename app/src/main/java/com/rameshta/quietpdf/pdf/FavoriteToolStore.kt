package com.rameshta.quietpdf.pdf

import android.content.Context

enum class SmartTool {
    ScanDocument,
    ImagesToPdf,
    MergePdf,
    SplitPdf,
    RearrangePages,
    ExtractPages,
    DeletePages,
    RotatePages,
    DuplicatePages,
    CompressPdf,
    TargetFileSize,
    ProtectPdf,
    RemovePassword,
    ChangePassword,
    FillForms,
    SignPdf,
    AnnotatePdf,
    TextWatermark,
    ImageWatermark,
    ExtractImages,
}

class FavoriteToolStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun load(): List<SmartTool> {
        if (!preferences.getBoolean(INITIALIZED_KEY, false)) return DEFAULT_TOOLS
        val stored = preferences.getString(TOOLS_KEY, null) ?: return emptyList()
        return stored.split(SEPARATOR).mapNotNull { encoded ->
            runCatching { SmartTool.valueOf(encoded) }.getOrNull()
        }.distinct().take(MAX_FAVORITES)
    }

    @Synchronized
    fun toggle(tool: SmartTool): List<SmartTool> {
        val current = load()
        val updated = if (tool in current) current - tool else {
            if (current.size >= MAX_FAVORITES) current else current + tool
        }
        preferences.edit()
            .putBoolean(INITIALIZED_KEY, true)
            .putString(TOOLS_KEY, updated.joinToString(SEPARATOR, transform = SmartTool::name))
            .apply()
        return updated
    }

    companion object {
        const val PREFERENCES_NAME = "favorite_tools"
        const val MAX_FAVORITES = 4
        val DEFAULT_TOOLS = listOf(
            SmartTool.ImagesToPdf,
            SmartTool.MergePdf,
            SmartTool.CompressPdf,
            SmartTool.SplitPdf,
        )
        private const val INITIALIZED_KEY = "initialized"
        private const val TOOLS_KEY = "tools"
        private const val SEPARATOR = ","
    }
}

data class ContinueReadingPdf(
    val uri: android.net.Uri,
    val displayName: String?,
    val currentPageIndex: Int,
    val pageCount: Int,
)
