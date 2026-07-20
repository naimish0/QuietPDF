package com.rameshta.quietpdf.pdf

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface PdfOpenState {
    data object Idle : PdfOpenState
    data object Opening : PdfOpenState
    data class Opened(
        val uri: Uri,
        val displayName: String?,
        val pageCount: Int,
    ) : PdfOpenState
    data class Failed(val failure: PdfOpenFailure) : PdfOpenState
}

class PdfOpenViewModel(application: Application) : AndroidViewModel(application) {
    private val opener = PdfDocumentOpener(application.contentResolver)
    private var openJob: Job? = null

    var state: PdfOpenState by mutableStateOf(PdfOpenState.Idle)
        private set

    fun open(uri: Uri) {
        openJob?.cancel()
        state = PdfOpenState.Opening
        openJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { opener.open(uri) }
            state = when (result) {
                is PdfOpenResult.Success -> PdfOpenState.Opened(
                    uri = result.document.uri,
                    displayName = result.document.displayName,
                    pageCount = result.document.pageCount,
                )
                is PdfOpenResult.Failure -> PdfOpenState.Failed(result.reason)
            }
        }
    }

    fun rejectUnsupportedUri() {
        openJob?.cancel()
        state = PdfOpenState.Failed(PdfOpenFailure.Unsupported)
    }
}
