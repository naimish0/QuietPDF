package com.rameshta.quietpdf.pdf

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDComboBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDListBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDVariableText
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

enum class FormFieldKind { Text, CheckBox, Radio, ComboBox, ListBox }

data class FormFieldDescriptor(
    val id: String,
    val label: String,
    val pageNumber: Int?,
    val kind: FormFieldKind,
    val values: List<String>,
    val options: List<String> = emptyList(),
    val optionValues: List<String> = options,
    val required: Boolean = false,
    val readOnly: Boolean = false,
    val multiline: Boolean = false,
    val password: Boolean = false,
    val editableChoice: Boolean = false,
    val multiSelect: Boolean = false,
    val maxLength: Int = 0,
)

data class FillFormsAnalysis(
    val pageCount: Int,
    val fields: List<FormFieldDescriptor>,
    val unsupportedFieldCount: Int,
)

data class FormFieldUpdate(val id: String, val values: List<String>)

sealed interface FillFormsAnalysisResult {
    data class Ready(val analysis: FillFormsAnalysis) : FillFormsAnalysisResult
    data object NoForm : FillFormsAnalysisResult
    data object UnsupportedForm : FillFormsAnalysisResult
    data object PasswordProtected : FillFormsAnalysisResult
    data object InvalidDocument : FillFormsAnalysisResult
    data object PermissionDenied : FillFormsAnalysisResult
    data object InsufficientMemory : FillFormsAnalysisResult
}

sealed interface FillFormsResult {
    data class Success(val pageCount: Int, val updatedFieldCount: Int) : FillFormsResult
    data object UnsupportedForm : FillFormsResult
    data object InvalidValues : FillFormsResult
    data object PasswordProtected : FillFormsResult
    data object InvalidDocument : FillFormsResult
    data object PermissionDenied : FillFormsResult
    data object InsufficientMemory : FillFormsResult
    data object Failed : FillFormsResult
}

class FillFormsEngine(context: Context) {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver

    init { PDFBoxResourceLoader.init(appContext) }

    suspend fun analyze(sourceUri: Uri): FillFormsAnalysisResult = withContext(Dispatchers.IO) {
        try {
            resolver.openInputStream(sourceUri)?.use { input ->
                PDDocument.load(input).use { document -> analyzeDocument(document) }
            } ?: FillFormsAnalysisResult.InvalidDocument
        } catch (_: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            FillFormsAnalysisResult.PasswordProtected
        } catch (_: SecurityException) {
            FillFormsAnalysisResult.PermissionDenied
        } catch (_: OutOfMemoryError) {
            FillFormsAnalysisResult.InsufficientMemory
        } catch (_: Exception) {
            FillFormsAnalysisResult.InvalidDocument
        }
    }

    suspend fun fill(
        sourceUri: Uri,
        outputUri: Uri,
        updates: List<FormFieldUpdate>,
        expectedPageCount: Int,
    ): FillFormsResult = withContext(Dispatchers.IO) {
        if (sourceUri == outputUri || updates.isEmpty() || updates.map { it.id }.toSet().size != updates.size) {
            if (sourceUri != outputUri) cleanupNewPdfOutput(appContext, resolver, outputUri)
            return@withContext FillFormsResult.InvalidValues
        }
        val temporary = try {
            File.createTempFile("filled-form-", ".pdf", appContext.cacheDir)
        } catch (_: Exception) {
            cleanupNewPdfOutput(appContext, resolver, outputUri)
            return@withContext FillFormsResult.Failed
        }
        var outputCommitted = false
        try {
            resolver.openInputStream(sourceUri)?.use { input ->
                PDDocument.load(input).use { document ->
                    if (document.isEncrypted) return@withContext FillFormsResult.PasswordProtected
                    if (document.numberOfPages != expectedPageCount) {
                        return@withContext FillFormsResult.InvalidDocument
                    }
                    val acroForm = document.documentCatalog.acroForm
                        ?: return@withContext FillFormsResult.UnsupportedForm
                    if (acroForm.hasXFA()) return@withContext FillFormsResult.UnsupportedForm
                    val supported = supportedFields(document)
                    val descriptors = supported.mapNotNull { describeField(document, it) }
                    val descriptorById = descriptors.associateBy(FormFieldDescriptor::id)
                    val fieldById = supported.associateBy { it.fullyQualifiedName }
                    if (updates.any { it.id !in descriptorById } ||
                        !updates.all { validUpdate(descriptorById.getValue(it.id), it.values) }
                    ) return@withContext FillFormsResult.InvalidValues

                    val variableFields = updates.mapNotNull { fieldById[it.id] as? PDVariableText }
                    if (variableFields.isNotEmpty()) configureAppearanceFont(document, acroForm, variableFields)
                    val updatedFields = mutableListOf<PDField>()
                    updates.forEach { update ->
                        coroutineContext.ensureActive()
                        val field = fieldById.getValue(update.id)
                        when (field) {
                            is PDTextField -> field.value = update.values.single()
                            is PDCheckBox -> if (update.values.single() == TRUE_VALUE) field.check() else field.unCheck()
                            is PDRadioButton -> field.value = update.values.single()
                            is PDComboBox -> field.setValue(update.values.single())
                            is PDListBox -> field.value = update.values
                        }
                        updatedFields += field
                    }
                    acroForm.setNeedAppearances(false)
                    acroForm.refreshAppearances(updatedFields)
                    document.save(temporary)
                }
            } ?: return@withContext FillFormsResult.InvalidDocument
            coroutineContext.ensureActive()
            if (!validatesOutput(temporary, updates, expectedPageCount)) return@withContext FillFormsResult.Failed
            resolver.openOutputStream(outputUri, "wt")?.use { output ->
                temporary.inputStream().use { it.copyTo(output) }
            } ?: return@withContext FillFormsResult.Failed
            if (!validatesOutput(outputUri, updates, expectedPageCount)) return@withContext FillFormsResult.Failed
            outputCommitted = true
            FillFormsResult.Success(expectedPageCount, updates.size)
        } catch (_: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            FillFormsResult.PasswordProtected
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IllegalArgumentException) {
            FillFormsResult.InvalidValues
        } catch (_: SecurityException) {
            FillFormsResult.PermissionDenied
        } catch (_: OutOfMemoryError) {
            FillFormsResult.InsufficientMemory
        } catch (_: Exception) {
            FillFormsResult.Failed
        } finally {
            temporary.delete()
            if (!outputCommitted) cleanupNewPdfOutput(appContext, resolver, outputUri)
        }
    }

    private fun analyzeDocument(document: PDDocument): FillFormsAnalysisResult {
        if (document.isEncrypted) return FillFormsAnalysisResult.PasswordProtected
        val acroForm = document.documentCatalog.acroForm ?: return FillFormsAnalysisResult.NoForm
        if (acroForm.hasXFA()) return FillFormsAnalysisResult.UnsupportedForm
        val allFields = acroForm.fieldTree.toList()
        if (allFields.isEmpty()) return FillFormsAnalysisResult.NoForm
        val descriptors = allFields.mapNotNull { describeField(document, it) }
        if (descriptors.isEmpty()) return FillFormsAnalysisResult.UnsupportedForm
        if (descriptors.none { !it.readOnly }) return FillFormsAnalysisResult.UnsupportedForm
        if (descriptors.map { it.id }.toSet().size != descriptors.size) {
            return FillFormsAnalysisResult.UnsupportedForm
        }
        return FillFormsAnalysisResult.Ready(
            FillFormsAnalysis(document.numberOfPages, descriptors, allFields.size - descriptors.size),
        )
    }

    private fun supportedFields(document: PDDocument): List<PDField> =
        document.documentCatalog.acroForm?.fieldTree?.filter { describeField(document, it) != null }
            ?.toList().orEmpty()

    private fun describeField(document: PDDocument, field: PDField): FormFieldDescriptor? {
        val id = field.fullyQualifiedName?.takeIf { it.isNotBlank() } ?: return null
        val label = field.alternateFieldName?.takeIf { it.isNotBlank() }
            ?: field.partialName?.takeIf { it.isNotBlank() }
            ?: id
        val pageNumber = field.widgets.firstNotNullOfOrNull { widget ->
            val page = widget.page ?: return@firstNotNullOfOrNull null
            document.pages.indexOf(page).takeIf { it >= 0 }?.plus(1)
        }
        return when (field) {
            is PDTextField -> if (field.isFileSelect || field.isRichText) null else FormFieldDescriptor(
                id, label, pageNumber, FormFieldKind.Text,
                values = listOf(if (field.isPassword) "" else field.value.orEmpty()),
                required = field.isRequired,
                readOnly = field.isReadOnly,
                multiline = field.isMultiline,
                password = field.isPassword,
                maxLength = field.maxLen,
            )
            is PDCheckBox -> FormFieldDescriptor(
                id, label, pageNumber, FormFieldKind.CheckBox,
                values = listOf(if (field.isChecked) TRUE_VALUE else FALSE_VALUE),
                required = field.isRequired,
                readOnly = field.isReadOnly,
            )
            is PDRadioButton -> {
                val optionValues = field.exportValues.ifEmpty { field.onValues.sorted() }
                if (optionValues.isEmpty()) null else FormFieldDescriptor(
                    id, label, pageNumber, FormFieldKind.Radio,
                    values = listOf(field.value.orEmpty().takeIf { it in optionValues }.orEmpty()),
                    options = optionValues,
                    optionValues = optionValues,
                    required = field.isRequired,
                    readOnly = field.isReadOnly,
                )
            }
            is PDComboBox -> choiceDescriptor(field, id, label, pageNumber, FormFieldKind.ComboBox)
            is PDListBox -> choiceDescriptor(field, id, label, pageNumber, FormFieldKind.ListBox)
            else -> null
        }
    }

    private fun choiceDescriptor(
        field: PDChoice,
        id: String,
        label: String,
        pageNumber: Int?,
        kind: FormFieldKind,
    ): FormFieldDescriptor? {
        val exportValues = field.optionsExportValues
        val displayValues = field.optionsDisplayValues
        if (exportValues.size != displayValues.size ||
            exportValues.toSet().size != exportValues.size ||
            (field is PDListBox && field.isMultiSelect && exportValues.isEmpty())
        ) return null
        return FormFieldDescriptor(
            id, label, pageNumber, kind,
            values = field.value,
            options = displayValues,
            optionValues = exportValues,
            required = field.isRequired,
            readOnly = field.isReadOnly,
            editableChoice = field is PDComboBox && field.isEdit,
            multiSelect = field is PDListBox && field.isMultiSelect,
        )
    }

    private fun validUpdate(descriptor: FormFieldDescriptor, values: List<String>): Boolean {
        if (descriptor.readOnly) return false
        return when (descriptor.kind) {
            FormFieldKind.Text -> values.size == 1 &&
                (descriptor.maxLength <= 0 || values.single().length <= descriptor.maxLength) &&
                (!descriptor.required || values.single().isNotBlank()) && values.single().none(Char::isISOControl)
            FormFieldKind.CheckBox -> values.size == 1 && values.single() in setOf(TRUE_VALUE, FALSE_VALUE) &&
                (!descriptor.required || values.single() == TRUE_VALUE)
            FormFieldKind.Radio -> values.size == 1 &&
                (values.single().isEmpty() && !descriptor.required || values.single() in descriptor.optionValues)
            FormFieldKind.ComboBox -> values.size == 1 &&
                (values.single().isEmpty() && !descriptor.required ||
                    descriptor.editableChoice || values.single() in descriptor.optionValues) &&
                (!descriptor.required || values.single().isNotBlank()) && values.single().none(Char::isISOControl)
            FormFieldKind.ListBox -> values.distinct().size == values.size &&
                (descriptor.multiSelect || values.size <= 1) && values.all { it in descriptor.optionValues } &&
                (!descriptor.required || values.isNotEmpty())
        }
    }

    private fun configureAppearanceFont(
        document: PDDocument,
        acroForm: com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm,
        fields: List<PDVariableText>,
    ) {
        val font = appContext.assets.open(FONT_ASSET).use { PDType0Font.load(document, it, true) }
        val resources = acroForm.defaultResources ?: PDResources().also(acroForm::setDefaultResources)
        resources.put(FORM_FONT_NAME, font)
        fields.forEach { it.defaultAppearance = FORM_DEFAULT_APPEARANCE }
    }

    private fun validatesOutput(file: File, updates: List<FormFieldUpdate>, pageCount: Int): Boolean =
        runCatching { PDDocument.load(file).use { validateDocument(it, updates, pageCount) } }.getOrDefault(false)

    private fun validatesOutput(uri: Uri, updates: List<FormFieldUpdate>, pageCount: Int): Boolean = runCatching {
        resolver.openInputStream(uri)?.use { PDDocument.load(it).use { doc -> validateDocument(doc, updates, pageCount) } }
            ?: false
    }.getOrDefault(false)

    private fun validateDocument(
        document: PDDocument,
        updates: List<FormFieldUpdate>,
        pageCount: Int,
    ): Boolean {
        if (document.isEncrypted || document.numberOfPages != pageCount) return false
        val form = document.documentCatalog.acroForm ?: return false
        return updates.all { update ->
            val field = form.getField(update.id) ?: return@all false
            when (field) {
                is PDCheckBox -> field.isChecked == (update.values.single() == TRUE_VALUE)
                is PDChoice -> field.value == update.values
                else -> field.valueAsString == update.values.single()
            }
        }
    }

    companion object {
        const val TRUE_VALUE = "true"
        const val FALSE_VALUE = "false"
        private val FORM_FONT_NAME = COSName.getPDFName("QuietPdfForm")
        private const val FORM_DEFAULT_APPEARANCE = "/QuietPdfForm 10 Tf 0 g"
        private const val FONT_ASSET =
            "com/tom_roush/pdfbox/resources/ttf/LiberationSans-Regular.ttf"
    }
}
