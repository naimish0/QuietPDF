package com.rameshta.quietpdf.pdf

import java.util.Locale

enum class PdfFileSortOrder {
    Newest,
    Oldest,
    NameAscending,
    NameDescending,
    PageCountAscending,
    PageCountDescending,
}

object PdfFileOrganizer {
    fun <T> organize(
        files: List<T>,
        query: String,
        sortOrder: PdfFileSortOrder,
        displayName: (T) -> String?,
        pageCount: (T) -> Int,
        timestamp: (T) -> Long,
    ): List<T> {
        val normalizedQuery = query.trim()
        val filtered = if (normalizedQuery.isEmpty()) files else files.filter { file ->
            displayName(file)?.contains(normalizedQuery, ignoreCase = true) == true
        }
        val nameComparator = compareBy<T> { displayName(it).orEmpty().lowercase(Locale.ROOT) }
            .thenBy { displayName(it).orEmpty() }
            .thenByDescending(timestamp)
        return when (sortOrder) {
            PdfFileSortOrder.Newest -> filtered.sortedByDescending(timestamp)
            PdfFileSortOrder.Oldest -> filtered.sortedBy(timestamp)
            PdfFileSortOrder.NameAscending -> filtered.sortedWith(nameComparator)
            PdfFileSortOrder.NameDescending -> filtered.sortedWith(
                compareByDescending<T> { displayName(it).orEmpty().lowercase(Locale.ROOT) }
                    .thenByDescending { displayName(it).orEmpty() }
                    .thenByDescending(timestamp),
            )
            PdfFileSortOrder.PageCountAscending -> filtered.sortedWith(
                compareBy<T>(pageCount).thenByDescending(timestamp),
            )
            PdfFileSortOrder.PageCountDescending -> filtered.sortedWith(
                compareByDescending<T>(pageCount).thenByDescending(timestamp),
            )
        }
    }
}
