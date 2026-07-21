package com.rameshta.quietpdf.pdf

enum class SplitPdfMode {
    EachPage,
    CustomRanges,
    EveryPages,
}

data class SplitPageRange(
    val firstPageIndex: Int,
    val lastPageIndex: Int,
) {
    val pageCount: Int get() = lastPageIndex - firstPageIndex + 1
}

object SplitPdfPlanner {
    fun createPlan(
        mode: SplitPdfMode,
        pageCount: Int,
        customRanges: String = "",
        everyPages: String = "",
    ): List<SplitPageRange>? {
        if (pageCount < 2) return null
        return when (mode) {
            SplitPdfMode.EachPage -> List(pageCount) { SplitPageRange(it, it) }
            SplitPdfMode.CustomRanges -> parseCompleteRanges(customRanges, pageCount)
            SplitPdfMode.EveryPages -> everyPages.toIntOrNull()
                ?.takeIf { it in 1 until pageCount }
                ?.let { chunkSize ->
                    (0 until pageCount step chunkSize).map { first ->
                        SplitPageRange(first, minOf(first + chunkSize - 1, pageCount - 1))
                    }
                }
        }
    }

    private fun parseCompleteRanges(value: String, pageCount: Int): List<SplitPageRange>? {
        val tokens = value.split(',').map(String::trim)
        if (tokens.size < 2 || tokens.any(String::isEmpty)) return null
        val ranges = tokens.map { token ->
            val match = RangePattern.matchEntire(token) ?: return null
            val first = match.groupValues[1].toIntOrNull() ?: return null
            val last = match.groupValues[2].toIntOrNull() ?: return null
            if (first < 1 || last < first || last > pageCount) return null
            SplitPageRange(first - 1, last - 1)
        }
        var expectedFirst = 0
        ranges.forEach { range ->
            if (range.firstPageIndex != expectedFirst) return null
            expectedFirst = range.lastPageIndex + 1
        }
        return ranges.takeIf { expectedFirst == pageCount }
    }

    private val RangePattern = Regex("(\\d+)\\s*-\\s*(\\d+)")
}
