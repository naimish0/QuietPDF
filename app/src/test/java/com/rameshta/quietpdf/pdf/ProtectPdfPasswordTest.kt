package com.rameshta.quietpdf.pdf

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectPdfPasswordTest {
    @Test
    fun validation_requiresSixNonWhitespaceCharacters() {
        assertFalse(ProtectPdfPassword.isValid("short"))
        assertFalse(ProtectPdfPassword.isValid("      "))
        assertTrue(ProtectPdfPassword.isValid("quiet!"))
    }

    @Test
    fun validation_rejectsPasswordsOverMaximumLength() {
        assertTrue(ProtectPdfPassword.isValid("a".repeat(ProtectPdfPassword.MAX_LENGTH)))
        assertFalse(ProtectPdfPassword.isValid("a".repeat(ProtectPdfPassword.MAX_LENGTH + 1)))
    }
}
