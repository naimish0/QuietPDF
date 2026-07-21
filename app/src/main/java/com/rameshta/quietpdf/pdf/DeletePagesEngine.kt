package com.rameshta.quietpdf.pdf

import android.content.ContentResolver
import android.content.Context
import android.net.Uri

sealed interface DeletePagesResult {
    data class Success(val pageCount: Int) : DeletePagesResult
    data object InvalidDocument : DeletePagesResult
    data object InvalidSelection : DeletePagesResult
    data object PermissionDenied : DeletePagesResult
    data object InsufficientMemory : DeletePagesResult
    data object Failed : DeletePagesResult
}

class DeletePagesEngine(context: Context) {
    private val appContext = context.applicationContext
    private val contentResolver: ContentResolver = appContext.contentResolver
    private val subsetEngine = ExtractPagesEngine(appContext)

    suspend fun deletePages(
        sourceUri: Uri,
        outputUri: Uri,
        sourcePageCount: Int,
        deletedPageIndices: IntArray,
    ): DeletePagesResult {
        if (sourceUri == outputUri) return DeletePagesResult.InvalidSelection
        val keptPages = DeletePageSelectionPlanner.keptPageIndices(
            sourcePageCount,
            deletedPageIndices,
        )
        if (keptPages == null) {
            cleanupNewPdfOutput(appContext, contentResolver, outputUri)
            return DeletePagesResult.InvalidSelection
        }
        return when (
            val result = subsetEngine.extract(
                sourceUri = sourceUri,
                outputUri = outputUri,
                selectedPageIndices = keptPages,
                expectedSourcePageCount = sourcePageCount,
            )
        ) {
            is ExtractPagesResult.Success -> DeletePagesResult.Success(result.pageCount)
            ExtractPagesResult.InvalidDocument -> DeletePagesResult.InvalidDocument
            ExtractPagesResult.InvalidSelection -> DeletePagesResult.InvalidSelection
            ExtractPagesResult.PermissionDenied -> DeletePagesResult.PermissionDenied
            ExtractPagesResult.InsufficientMemory -> DeletePagesResult.InsufficientMemory
            ExtractPagesResult.Failed -> DeletePagesResult.Failed
        }
    }
}
