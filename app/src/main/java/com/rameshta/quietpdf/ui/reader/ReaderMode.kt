package com.rameshta.quietpdf.ui.reader

import androidx.annotation.StringRes
import com.rameshta.quietpdf.R

enum class ReaderMode(@get:StringRes val labelResource: Int) {
    VerticalContinuous(R.string.reader_mode_vertical),
    HorizontalContinuous(R.string.reader_mode_horizontal),
    SinglePage(R.string.reader_mode_single_page),
}
