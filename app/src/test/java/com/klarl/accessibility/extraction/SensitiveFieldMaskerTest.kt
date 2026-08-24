package com.klarl.accessibility.extraction

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveFieldMaskerTest {

    @Test
    fun `isPassword flag alone marks node sensitive`() {
        val node = FakeAccessibilityNode(isPassword = true)
        assertTrue(SensitiveFieldMasker.isSensitive(node))
    }

    @Test
    fun `view id resource name matching password keyword marks node sensitive`() {
        val node = FakeAccessibilityNode(viewIdResourceName = "com.example.bank:id/login_password_field")
        assertTrue(SensitiveFieldMasker.isSensitive(node))
    }

    @Test
    fun `hint text matching swedish password keyword marks node sensitive`() {
        val node = FakeAccessibilityNode(hintText = "Ange ditt lösenord")
        assertTrue(SensitiveFieldMasker.isSensitive(node))
    }

    @Test
    fun `card number field is sensitive and classified as payment`() {
        val node = FakeAccessibilityNode(viewIdResourceName = "com.example.shop:id/card_number_input")
        assertTrue(SensitiveFieldMasker.isSensitive(node))
        assertTrue(SensitiveFieldMasker.isLikelyPaymentField(node))
    }

    @Test
    fun `ordinary text field is not sensitive`() {
        val node = FakeAccessibilityNode(
            viewIdResourceName = "com.example.app:id/username_field",
            hintText = "Användarnamn"
        )
        assertFalse(SensitiveFieldMasker.isSensitive(node))
    }
}
