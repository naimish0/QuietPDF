package com.rameshta.quietpdf.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfFileOrganizerTest {
    private data class FileItem(
        val name: String?,
        val pages: Int,
        val timestamp: Long,
    )

    private val files = listOf(
        FileItem("Zulu.pdf", 8, 30L),
        FileItem("annual Report.pdf", 2, 10L),
        FileItem("Notes.pdf", 5, 20L),
        FileItem(null, 1, 40L),
    )

    @Test
    fun searchTrimsQueryAndMatchesFilenameCaseInsensitively() {
        val result = organize(query = "  REPORT  ")
        assertEquals(listOf("annual Report.pdf"), result.map { it.name })
        assertEquals(emptyList<FileItem>(), organize(query = "pdf document"))
        assertEquals(emptyList<FileItem>(), organize(query = "missing"))
    }

    @Test
    fun newestAndOldestUseCollectionTimestamp() {
        assertEquals(
            listOf(null, "Zulu.pdf", "Notes.pdf", "annual Report.pdf"),
            organize(sort = PdfFileSortOrder.Newest).map { it.name },
        )
        assertEquals(
            listOf("annual Report.pdf", "Notes.pdf", "Zulu.pdf", null),
            organize(sort = PdfFileSortOrder.Oldest).map { it.name },
        )
    }

    @Test
    fun nameSortIsCaseInsensitiveAndDeterministic() {
        assertEquals(
            listOf(null, "annual Report.pdf", "Notes.pdf", "Zulu.pdf"),
            organize(sort = PdfFileSortOrder.NameAscending).map { it.name },
        )
        assertEquals(
            listOf("Zulu.pdf", "Notes.pdf", "annual Report.pdf", null),
            organize(sort = PdfFileSortOrder.NameDescending).map { it.name },
        )
    }

    @Test
    fun pageCountSortSupportsBothDirectionsWithoutMutatingInput() {
        assertEquals(listOf(1, 2, 5, 8), organize(sort = PdfFileSortOrder.PageCountAscending).map { it.pages })
        assertEquals(listOf(8, 5, 2, 1), organize(sort = PdfFileSortOrder.PageCountDescending).map { it.pages })
        assertEquals("Zulu.pdf", files.first().name)
    }

    private fun organize(
        query: String = "",
        sort: PdfFileSortOrder = PdfFileSortOrder.Newest,
    ): List<FileItem> = PdfFileOrganizer.organize(
        files = files,
        query = query,
        sortOrder = sort,
        displayName = FileItem::name,
        pageCount = FileItem::pages,
        timestamp = FileItem::timestamp,
    )
}
