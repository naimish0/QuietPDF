package com.rameshta.quietpdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rameshta.quietpdf.pdf.ExtractImagesAnalysisResult
import com.rameshta.quietpdf.pdf.ExtractImagesEngine
import com.rameshta.quietpdf.pdf.ExtractImagesResult
import com.rameshta.quietpdf.pdf.ExtractImagesShareResult
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDFormContentStream
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExtractImagesEngineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun analyze_findsActualUniqueAndNestedImageObjects_withoutPageScreenshots() {
        val source = File(context.cacheDir, "extract-images-objects.pdf")
        try {
            writeImageObjectPdf(source)
            val result = runBlocking { ExtractImagesEngine(context).analyze(Uri.fromFile(source)) }
            assertTrue(result is ExtractImagesAnalysisResult.Ready)
            val analysis = (result as ExtractImagesAnalysisResult.Ready).analysis
            assertEquals(2, analysis.pageCount)
            assertEquals(2, analysis.images.size)
            assertEquals(listOf(1, 2), analysis.images.map { it.pageNumber })
            assertEquals(listOf(120 to 80, 64 to 96), analysis.images.map { it.width to it.height })
            assertTrue(analysis.images.all { it.extractable && it.bitmap != null })
            analysis.images.forEach { it.bitmap?.recycle() }
        } finally { source.delete() }
    }

    @Test
    fun zipAndShare_extractSelectedImagePixelsAndValidateOutputs() {
        val source = File(context.cacheDir, "extract-images-export.pdf")
        val output = File(context.cacheDir, "extract-images-export.zip")
        val engine = ExtractImagesEngine(context)
        try {
            writeImageObjectPdf(source)
            assertEquals(
                ExtractImagesResult.Success(1),
                runBlocking { engine.exportToZip(Uri.fromFile(source), Uri.fromFile(output), setOf(1)) },
            )
            ZipFile(output).use { zip ->
                val entries = zip.entries().toList()
                assertEquals(1, entries.size)
                val bitmap = zip.getInputStream(entries.single()).use(BitmapFactory::decodeStream)
                assertEquals(64, bitmap.width)
                assertEquals(96, bitmap.height)
                assertEquals(Color.BLUE, bitmap.getPixel(32, 48))
                bitmap.recycle()
            }
            val share = runBlocking { engine.prepareShare(Uri.fromFile(source), setOf(0, 1)) }
            assertTrue(share is ExtractImagesShareResult.Ready)
            val files = (share as ExtractImagesShareResult.Ready).files
            assertEquals(2, files.size)
            assertTrue(files.all { it.isFile && it.length() > 0L })
            context.getExternalFilesDir(null)?.let { artifacts ->
                files[0].copyTo(File(artifacts, "extracted-image-red.png"), overwrite = true)
                files[1].copyTo(File(artifacts, "extracted-image-blue.png"), overwrite = true)
            }
        } finally {
            engine.clearShareFiles(); source.delete(); output.delete()
        }
    }

    @Test
    fun analyze_reportsNoEmbeddedImages_andInvalidSelectionsLeaveNoZip() {
        val source = File(context.cacheDir, "extract-images-empty.pdf")
        val output = File(context.cacheDir, "extract-images-invalid.zip")
        try {
            PDDocument().use { document -> document.addPage(PDPage()); document.save(source) }
            val engine = ExtractImagesEngine(context)
            assertEquals(
                ExtractImagesAnalysisResult.NoImages,
                runBlocking { engine.analyze(Uri.fromFile(source)) },
            )
            output.writeText("placeholder")
            assertEquals(
                ExtractImagesResult.InvalidSelection,
                runBlocking { engine.exportToZip(Uri.fromFile(source), Uri.fromFile(output), emptySet()) },
            )
            assertFalse(output.exists())
        } finally { source.delete(); output.delete() }
    }

    @Test
    fun analyze_blocksOversizedImageMetadataBeforeDecode() {
        val source = File(context.cacheDir, "extract-images-oversized.pdf")
        try {
            PDFBoxResourceLoader.init(context)
            PDDocument().use { document ->
                val page = PDPage()
                document.addPage(page)
                val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
                val image = LosslessFactory.createFromImage(document, bitmap)
                bitmap.recycle()
                image.cosObject.setInt(COSName.WIDTH, 100_001)
                image.cosObject.setInt(COSName.HEIGHT, 100_001)
                PDPageContentStream(document, page).use { it.drawImage(image, 0f, 0f, 10f, 10f) }
                document.save(source)
            }
            val result = runBlocking { ExtractImagesEngine(context).analyze(Uri.fromFile(source)) }
            assertTrue(result is ExtractImagesAnalysisResult.Ready)
            val preview = (result as ExtractImagesAnalysisResult.Ready).analysis.images.single()
            assertFalse(preview.extractable)
            assertEquals(null, preview.bitmap)
        } finally { source.delete() }
    }

    private fun writeImageObjectPdf(file: File) {
        PDFBoxResourceLoader.init(context)
        PDDocument().use { document ->
            val pageOne = PDPage(PDRectangle(400f, 500f))
            val pageTwo = PDPage(PDRectangle(400f, 500f))
            document.addPage(pageOne)
            document.addPage(pageTwo)

            val firstBitmap = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.RED)
            }
            val secondBitmap = Bitmap.createBitmap(64, 96, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.BLUE)
            }
            val firstImage = LosslessFactory.createFromImage(document, firstBitmap)
            val secondImage = LosslessFactory.createFromImage(document, secondBitmap)
            firstBitmap.recycle(); secondBitmap.recycle()

            PDPageContentStream(document, pageOne).use { it.drawImage(firstImage, 20f, 20f, 120f, 80f) }
            PDPageContentStream(document, pageTwo).use { stream ->
                stream.drawImage(firstImage, 30f, 30f, 120f, 80f)
                val form = PDFormXObject(document).apply {
                    resources = PDResources()
                    setBBox(PDRectangle(100f, 120f))
                }
                PDFormContentStream(form).use { it.drawImage(secondImage, 10f, 10f, 64f, 96f) }
                stream.drawForm(form)
            }
            document.save(file)
        }
    }
}
