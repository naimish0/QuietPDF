package com.rameshta.quietpdf

import android.net.Uri
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rameshta.quietpdf.pdf.FavoritePdf
import com.rameshta.quietpdf.pdf.PageRenderFailure
import com.rameshta.quietpdf.pdf.PageRenderResult
import com.rameshta.quietpdf.pdf.PdfOpenState
import com.rameshta.quietpdf.pdf.RecentPdf
import com.rameshta.quietpdf.ui.theme.QuietPDFTheme
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoriteFilesUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun favoritesShowMetadataAndOpenOrRemoveSelectedDocument() {
        val opened = AtomicReference<Uri>()
        val removed = AtomicReference<Uri>()
        val favorites = listOf(
            FavoritePdf(Uri.parse("content://documents/a"), "Annual report.pdf", 1, 20L),
            FavoritePdf(Uri.parse("content://documents/b"), null, 8, 10L),
        )
        setHome(favorites = favorites, onOpenFavorite = opened::set, onRemoveFavorite = removed::set)
        composeRule.onNodeWithTag("favorite_files_title").assertIsDisplayed()
        composeRule.onNodeWithText("Annual report.pdf").assertIsDisplayed()
        composeRule.onNodeWithText("1 page").assertIsDisplayed()
        composeRule.onNodeWithText("PDF document").assertIsDisplayed()
        composeRule.onNodeWithTag("favorite_file_0").performClick()
        assertEquals(favorites[0].uri, opened.get())
        composeRule.onNodeWithTag("favorite_file_remove_1").performClick()
        assertEquals(favorites[1].uri, removed.get())
    }

    @Test
    fun recentItemCanBeFavoritedAndUnfavorited() {
        val uri = Uri.parse("content://documents/a")
        val toggled = AtomicReference<Uri>()
        setHome(
            recents = listOf(RecentPdf(uri, "A.pdf", 2, 1L)),
            onToggleFavorite = toggled::set,
        )
        composeRule.onNodeWithText("Favorite").assertIsDisplayed()
        composeRule.onNodeWithTag("recent_file_favorite_0").performClick()
        assertEquals(uri, toggled.get())
    }

    @Test
    fun favoriteRecentItemUsesUnfavoriteAction() {
        val uri = Uri.parse("content://documents/a")
        setHome(
            favorites = listOf(FavoritePdf(uri, "A.pdf", 2, 1L)),
            recents = listOf(RecentPdf(uri, "A.pdf", 2, 1L)),
        )
        composeRule.onNodeWithTag("recent_file_favorite_0").assertTextContains("Unfavorite")
    }

    @Test
    fun emptyFavoriteListDoesNotAddAnEmptySection() {
        setHome()
        composeRule.onAllNodesWithTag("favorite_files_title").assertCountEquals(0)
    }

    @Test
    fun readerMenuReflectsFavoriteStateAndTogglesCurrentDocument() {
        val toggles = AtomicInteger()
        composeRule.setContent {
            QuietPDFTheme(dynamicColor = false) {
                QuietPdfApp(
                    state = PdfOpenState.Opened(
                        uri = Uri.parse("content://documents/a"),
                        displayName = "A.pdf",
                        pageCount = 1,
                        isFavorite = true,
                    ),
                    onToggleFavoritePdf = { toggles.incrementAndGet() },
                    onOpenPdf = {},
                    renderPage = { _, _ -> PageRenderResult.Failed(PageRenderFailure.UnableToRender) },
                )
            }
        }
        composeRule.onNodeWithTag("reader_mode_button").performClick()
        composeRule.onNodeWithText("Remove from favorites").assertIsDisplayed()
        composeRule.onNodeWithTag("toggle_favorite_file").performClick()
        assertEquals(1, toggles.get())
    }

    private fun setHome(
        favorites: List<FavoritePdf> = emptyList(),
        recents: List<RecentPdf> = emptyList(),
        onOpenFavorite: (Uri) -> Unit = {},
        onRemoveFavorite: (Uri) -> Unit = {},
        onToggleFavorite: (Uri) -> Unit = {},
    ) {
        composeRule.setContent {
            QuietPDFTheme(dynamicColor = false) {
                QuietPdfApp(
                    state = PdfOpenState.Idle,
                    favoritePdfs = favorites,
                    recentPdfs = recents,
                    onOpenFavoritePdf = onOpenFavorite,
                    onRemoveFavoritePdf = onRemoveFavorite,
                    onToggleFavoritePdf = onToggleFavorite,
                    onOpenPdf = {},
                    renderPage = { _, _ -> PageRenderResult.Failed(PageRenderFailure.UnableToRender) },
                )
            }
        }
    }
}
