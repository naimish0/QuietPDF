package com.rameshta.quietpdf.pdf

object DeletePageSelectionPlanner {
    fun keptPageIndices(pageCount: Int, deletedPageIndices: IntArray): IntArray? {
        if (pageCount < 2 || deletedPageIndices.isEmpty() || deletedPageIndices.size >= pageCount) {
            return null
        }
        var previous = -1
        deletedPageIndices.forEach { index ->
            if (index <= previous || index !in 0 until pageCount) return null
            previous = index
        }
        val deleted = deletedPageIndices.toHashSet()
        return (0 until pageCount).filterNot(deleted::contains).toIntArray()
    }
}
