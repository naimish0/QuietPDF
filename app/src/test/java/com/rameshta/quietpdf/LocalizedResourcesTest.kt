package com.rameshta.quietpdf

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class LocalizedResourcesTest {
    private val locales = listOf(
        "de", "fr", "ja", "hi", "ru", "es", "pt-rPT", "pt-rBR", "it", "in", "ar", "ko", "ur-rPK",
    )
    private val formatArgument = Regex("%(?:\\d+\\$)?[a-zA-Z]|%%")

    @Test
    fun everyLocaleContainsEveryStringAndPluralWithMatchingFormatArguments() {
        val source = readResources(resourceFile("values"))

        locales.forEach { locale ->
            val translated = readResources(resourceFile("values-$locale"))
            val sourceStrings = source.filterKeys { "::" !in it }
            val translatedStrings = translated.filterKeys { "::" !in it }
            assertEquals("String resource keys differ for $locale", sourceStrings.keys, translatedStrings.keys)
            assertEquals(
                "Plural resource names differ for $locale",
                source.keys.filter { "::" in it }.map { it.substringBefore("::") }.toSet(),
                translated.keys.filter { "::" in it }.map { it.substringBefore("::") }.toSet(),
            )
            sourceStrings.forEach { (key, sourceValue) ->
                assertEquals(
                    "Format arguments differ for $locale:$key",
                    formatArgument.findAll(sourceValue).map { it.value }.groupingBy { it }.eachCount(),
                    formatArgument.findAll(translatedStrings.getValue(key)).map { it.value }
                        .groupingBy { it }.eachCount(),
                )
                assertTrue("Blank translation for $locale:$key", translatedStrings.getValue(key).isNotBlank())
            }
            translated.filterKeys { "::" in it }.forEach { (key, value) ->
                assertTrue("Blank plural translation for $locale:$key", value.isNotBlank())
            }
        }
    }

    @Test
    fun localeConfigDeclaresEveryPackagedLanguage() {
        val file = File("src/main/res/xml/locales_config.xml")
        assertTrue("Missing locale config", file.isFile)
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val declared = document.getElementsByTagName("locale").let { nodes ->
            (0 until nodes.length).map { index ->
                (nodes.item(index) as Element).getAttribute("android:name")
            }.toSet()
        }
        assertEquals(
            setOf("en", "de", "fr", "ja", "hi", "ru", "es", "pt-PT", "pt-BR", "it", "id", "ar", "ko", "ur"),
            declared,
        )
    }

    private fun resourceFile(folder: String) = File("src/main/res/$folder/strings.xml")

    private fun readResources(file: File): Map<String, String> {
        assertTrue("Missing resources: $file", file.isFile)
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val resources = linkedMapOf<String, String>()
        val root = document.documentElement
        for (index in 0 until root.childNodes.length) {
            val node = root.childNodes.item(index)
            if (node !is Element) continue
            when (node.tagName) {
                "string" -> resources[node.getAttribute("name")] = node.textContent
                "plurals" -> {
                    for (itemIndex in 0 until node.childNodes.length) {
                        val item = node.childNodes.item(itemIndex)
                        if (item is Element && item.tagName == "item") {
                            resources["${node.getAttribute("name")}::${item.getAttribute("quantity")}"] =
                                item.textContent
                        }
                    }
                }
            }
        }
        return resources
    }
}
