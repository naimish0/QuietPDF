package com.rameshta.quietpdf.pdf

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri

object PdfShareIntentFactory {
    fun create(context: Context, uri: Uri): Intent? {
        if (uri.scheme != CONTENT_SCHEME) return null
        return Intent(Intent.ACTION_SEND).apply {
            type = PDF_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, GENERIC_CLIP_LABEL, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    const val PDF_MIME_TYPE = "application/pdf"
    private const val CONTENT_SCHEME = "content"
    private const val GENERIC_CLIP_LABEL = "PDF document"
}
