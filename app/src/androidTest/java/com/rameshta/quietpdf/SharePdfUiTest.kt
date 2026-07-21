package com.rameshta.quietpdf

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rameshta.quietpdf.pdf.ChangePasswordState
import com.rameshta.quietpdf.pdf.FavoritePdf
import com.rameshta.quietpdf.pdf.PageRenderFailure
import com.rameshta.quietpdf.pdf.PageRenderResult
import com.rameshta.quietpdf.pdf.PdfOpenState
import com.rameshta.quietpdf.pdf.RecentPdf
import com.rameshta.quietpdf.ui.theme.QuietPDFTheme
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharePdfUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun recentAndFavoriteRowsShareTheSelectedContentUri() {
        val recentUri = Uri.parse("content://documents/recent.pdf")
        val favoriteUri = Uri.parse("content://documents/favorite.pdf")
        val shared = AtomicReference<Uri>()
        setContent(
            recentPdfs = listOf(RecentPdf(recentUri, "Recent.pdf", 2, 20L)),
            favoritePdfs = listOf(FavoritePdf(favoriteUri, "Favorite.pdf", 3, 10L)),
            onShare = shared::set,
        )
        composeRule.onNodeWithTag("favorite_file_share_0").performClick()
        assertEquals(favoriteUri, shared.get())
        composeRule.onNodeWithTag("recent_file_share_0").performScrollTo().performClick()
        assertEquals(recentUri, shared.get())
    }

    @Test
    fun readerMenuSharesTheOpenDocument() {
        val uri = Uri.parse("content://documents/open.pdf")
        val shared = AtomicReference<Uri>()
        setContent(
            state = PdfOpenState.Opened(uri, "Open.pdf", 1),
            onShare = shared::set,
        )
        composeRule.onNodeWithTag("reader_mode_button").performClick()
        composeRule.onNodeWithTag("share_pdf_button").performClick()
        assertEquals(uri, shared.get())
    }

    @Test
    fun successfulSinglePdfResultSharesItsOutputUri() {
        val uri = Uri.parse("content://documents/password-changed.pdf")
        val shared = AtomicReference<Uri>()
        setContent(
            changePasswordState = ChangePasswordState.Completed(uri, 4),
            onShare = shared::set,
        )
        composeRule.onNodeWithTag("share_password_changed_pdf")
            .performScrollTo().assertIsDisplayed().performClick()
        assertEquals(uri, shared.get())
    }

    private fun setContent(
        state: PdfOpenState = PdfOpenState.Idle,
        recentPdfs: List<RecentPdf> = emptyList(),
        favoritePdfs: List<FavoritePdf> = emptyList(),
        changePasswordState: ChangePasswordState = ChangePasswordState.Idle,
        onShare: (Uri) -> Unit,
    ) {
        composeRule.setContent {
            QuietPDFTheme(dynamicColor = false) {
                QuietPdfApp(
                    state = state,
                    recentPdfs = recentPdfs,
                    favoritePdfs = favoritePdfs,
                    changePasswordState = changePasswordState,
                    onSharePdf = onShare,
                    onOpenPdf = {},
                    renderPage = { _, _ -> PageRenderResult.Failed(PageRenderFailure.UnableToRender) },
                )
            }
        }
    }
}
