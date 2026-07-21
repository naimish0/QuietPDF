package com.rameshta.quietpdf.pdf

object ExtractPageSelectionParser {
    fun parse(value: String, pageCount: Int): IntArray? {
        if (pageCount <= 0 || value.isBlank()) return null
        val tokens = value.split(',').map(String::trim)
        if (tokens.any(String::isEmpty)) return null
        val selectedPages = ArrayList<Int>()
        var previousPageNumber = 0
        tokens.forEach { token ->
            val singlePage = SinglePagePattern.matchEntire(token)?.groupValues?.get(1)?.toIntOrNull()
            val range = PageRangePattern.matchEntire(token)
            val first = singlePage ?: range?.groupValues?.get(1)?.toIntOrNull() ?: return null
            val last = singlePage ?: range?.groupValues?.get(2)?.toIntOrNull() ?: return null
            if (first <= previousPageNumber || first < 1 || last < first || last > pageCount) {
                return null
            }
            for (pageNumber in first..last) selectedPages += pageNumber - 1
            previousPageNumber = last
        }
        return selectedPages.toIntArray().takeIf(IntArray::isNotEmpty)
    }

    private val SinglePagePattern = Regex("(\\d+)")
    private val PageRangePattern = Regex("(\\d+)\\s*-\\s*(\\d+)")
}
