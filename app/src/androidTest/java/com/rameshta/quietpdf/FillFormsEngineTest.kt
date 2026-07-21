package com.rameshta.quietpdf

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rameshta.quietpdf.pdf.FillFormsAnalysisResult
import com.rameshta.quietpdf.pdf.FillFormsEngine
import com.rameshta.quietpdf.pdf.FillFormsResult
import com.rameshta.quietpdf.pdf.FormFieldKind
import com.rameshta.quietpdf.pdf.FormFieldUpdate
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDAppearanceContentStream
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDComboBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDListBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import com.tom_roush.pdfbox.rendering.PDFRenderer
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FillFormsEngineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun analyzeAndFill_writesSupportedFieldDictionariesAndAppearances_withoutChangingSource() {
        val source = File(context.cacheDir, "fill-form-source.pdf")
        val output = File(context.cacheDir, "fill-form-output.pdf")
        try {
            writeSupportedForm(source)
            val sourceBytes = source.readBytes()
            val engine = FillFormsEngine(context)
            val analysisResult = runBlocking { engine.analyze(Uri.fromFile(source)) }
            assertTrue(analysisResult is FillFormsAnalysisResult.Ready)
            val analysis = (analysisResult as FillFormsAnalysisResult.Ready).analysis
            assertEquals(1, analysis.pageCount)
            assertEquals(
                setOf(FormFieldKind.Text, FormFieldKind.CheckBox, FormFieldKind.Radio,
                    FormFieldKind.ComboBox, FormFieldKind.ListBox),
                analysis.fields.mapTo(mutableSetOf()) { it.kind },
            )
            val updates = listOf(
                FormFieldUpdate("name", listOf("Ada Lovelace")),
                FormFieldUpdate("accept", listOf(FillFormsEngine.TRUE_VALUE)),
                FormFieldUpdate("contact", listOf("Email")),
                FormFieldUpdate("country", listOf("IN")),
                FormFieldUpdate("interests", listOf("PDF", "Privacy")),
            )
            assertEquals(
                FillFormsResult.Success(1, 5),
                runBlocking { engine.fill(Uri.fromFile(source), Uri.fromFile(output), updates, 1) },
            )
            assertEquals(sourceBytes.asList(), source.readBytes().asList())
            PDDocument.load(output).use { document ->
                val form = document.documentCatalog.acroForm
                assertEquals("Ada Lovelace", form.getField("name").valueAsString)
                assertTrue((form.getField("accept") as PDCheckBox).isChecked)
                assertEquals("Email", form.getField("contact").valueAsString)
                assertEquals(listOf("IN"), (form.getField("country") as PDComboBox).value)
                assertEquals(listOf("PDF", "Privacy"), (form.getField("interests") as PDListBox).value)
                assertTrue(form.getField("name").widgets.single().normalAppearanceStream != null)
            }
            val render = PDDocument.load(output).use { PDFRenderer(it).renderImage(0, 1.5f) }
            context.getExternalFilesDir(null)?.let { artifacts ->
                File(artifacts, "filled-form-render.png").outputStream().use {
                    assertTrue(render.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                output.copyTo(File(artifacts, "filled-form-output.pdf"), overwrite = true)
            }
            render.recycle()
        } finally { source.delete(); output.delete() }
    }

    @Test
    fun fill_rejectsInvalidRequiredValueAndRemovesPlaceholder() {
        val source = File(context.cacheDir, "fill-form-invalid-source.pdf")
        val output = File(context.cacheDir, "fill-form-invalid-output.pdf")
        try {
            writeSupportedForm(source)
            output.writeText("placeholder")
            assertEquals(
                FillFormsResult.InvalidValues,
                runBlocking {
                    FillFormsEngine(context).fill(
                        Uri.fromFile(source), Uri.fromFile(output),
                        listOf(FormFieldUpdate("name", listOf(""))), 1,
                    )
                },
            )
            assertFalse(output.exists())
        } finally { source.delete(); output.delete() }
    }

    @Test
    fun analyze_distinguishesNoFormAndUnsupportedOnlyForm() {
        val noForm = File(context.cacheDir, "fill-form-none.pdf")
        val unsupported = File(context.cacheDir, "fill-form-unsupported.pdf")
        try {
            PDFBoxResourceLoader.init(context)
            PDDocument().use { it.addPage(PDPage()); it.save(noForm) }
            PDDocument().use { document ->
                document.addPage(PDPage())
                document.documentCatalog.acroForm = PDAcroForm(document).apply {
                    fields = listOf(com.tom_roush.pdfbox.pdmodel.interactive.form.PDSignatureField(this))
                }
                document.save(unsupported)
            }
            val engine = FillFormsEngine(context)
            assertEquals(FillFormsAnalysisResult.NoForm,
                runBlocking { engine.analyze(Uri.fromFile(noForm)) })
            assertEquals(FillFormsAnalysisResult.UnsupportedForm,
                runBlocking { engine.analyze(Uri.fromFile(unsupported)) })
        } finally { noForm.delete(); unsupported.delete() }
    }

    private fun writeSupportedForm(file: File) {
        PDFBoxResourceLoader.init(context)
        PDDocument().use { document ->
            val page = PDPage(PDRectangle.LETTER)
            document.addPage(page)
            val form = PDAcroForm(document)
            document.documentCatalog.acroForm = form
            form.defaultResources = PDResources().apply { put(COSName.HELV, PDType1Font.HELVETICA) }
            form.defaultAppearance = "/Helv 12 Tf 0 g"
            val fields = mutableListOf<PDField>()

            val name = PDTextField(form).apply {
                partialName = "name"
                alternateFieldName = "Full name"
                isRequired = true
            }
            attach(name.widgets.single(), page, PDRectangle(140f, 650f, 220f, 30f))
            fields += name

            val check = PDCheckBox(form).apply { partialName = "accept" }
            configureButtonWidget(document, check.widgets.single(), page,
                PDRectangle(140f, 600f, 25f, 25f), "Yes")
            fields += check

            val radio = PDRadioButton(form).apply {
                partialName = "contact"
                exportValues = listOf("Email", "Phone")
            }
            val email = PDAnnotationWidget()
            val phone = PDAnnotationWidget()
            radio.widgets = listOf(email, phone)
            configureButtonWidget(document, email, page, PDRectangle(140f, 550f, 25f, 25f), "Email")
            configureButtonWidget(document, phone, page, PDRectangle(220f, 550f, 25f, 25f), "Phone")
            fields += radio

            val combo = PDComboBox(form).apply {
                partialName = "country"
                setOptions(listOf("US", "IN"), listOf("United States", "India"))
            }
            attach(combo.widgets.single(), page, PDRectangle(140f, 500f, 160f, 30f))
            fields += combo

            val list = PDListBox(form).apply {
                partialName = "interests"
                isMultiSelect = true
                setOptions(listOf("PDF", "Privacy", "Offline"))
            }
            attach(list.widgets.single(), page, PDRectangle(140f, 400f, 160f, 80f))
            fields += list
            form.fields = fields

            PDPageContentStream(document, page).use { stream ->
                stream.beginText(); stream.setFont(PDType1Font.HELVETICA, 12f)
                stream.newLineAtOffset(50f, 665f); stream.showText("Name")
                stream.newLineAtOffset(0f, -55f); stream.showText("Accept")
                stream.newLineAtOffset(0f, -50f); stream.showText("Contact")
                stream.newLineAtOffset(0f, -50f); stream.showText("Country")
                stream.newLineAtOffset(0f, -70f); stream.showText("Interests")
                stream.endText()
            }
            form.refreshAppearances()
            document.save(file)
        }
    }

    private fun attach(widget: PDAnnotationWidget, page: PDPage, rectangle: PDRectangle) {
        widget.rectangle = rectangle
        widget.page = page
        widget.isPrinted = true
        page.annotations.add(widget)
    }

    private fun configureButtonWidget(
        document: PDDocument,
        widget: PDAnnotationWidget,
        page: PDPage,
        rectangle: PDRectangle,
        onValue: String,
    ) {
        attach(widget, page, rectangle)
        val off = appearance(document, false)
        val on = appearance(document, true)
        val normal = COSDictionary().apply {
            setItem(COSName.Off, off.cosObject)
            setItem(COSName.getPDFName(onValue), on.cosObject)
        }
        widget.cosObject.setItem(COSName.AP, COSDictionary().apply { setItem(COSName.N, normal) })
        widget.setAppearanceState("Off")
    }

    private fun appearance(document: PDDocument, marked: Boolean): PDAppearanceStream =
        PDAppearanceStream(document).apply {
            setBBox(PDRectangle(25f, 25f))
            resources = PDResources()
            PDAppearanceContentStream(this).use { stream ->
                stream.setLineWidth(1f); stream.addRect(1f, 1f, 23f, 23f); stream.stroke()
                if (marked) {
                    stream.moveTo(5f, 13f); stream.lineTo(11f, 6f); stream.lineTo(21f, 20f)
                    stream.setLineWidth(2f); stream.stroke()
                }
            }
        }
}
