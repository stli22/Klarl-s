package com.klarl.accessibility.extraction

/**
 * Decides whether a node's content must never leave the device, and what to put in its place
 * when it doesn't. Two independent signals are used because `isPassword` alone misses payment /
 * ID fields that Android doesn't mark as password inputs:
 *
 *  1. [AccessibilityNode.isPassword] - reliable, framework-reported, catches every password field.
 *  2. Keyword heuristics against the field's id/hint/label - catches card numbers, CVV, PIN,
 *     personnummer, etc. that are technically plain-text inputs.
 *
 * This runs *before* anything is added to a [com.klarl.accessibility.model.ScreenNode], so a
 * masked field's real value never gets built into the tree we send to Claude in the first place.
 */
object SensitiveFieldMasker {

    private val sensitiveKeywords = listOf(
        // Swedish
        "lösenord", "lösen", "pin", "kortnummer", "kort_nummer", "cvv", "cvc",
        "säkerhetskod", "kontonummer", "personnummer", "bankid",
        // English
        "password", "passcode", "creditcard", "credit_card", "card_number",
        "cardnumber", "cvv", "cvc", "security_code", "securitycode",
        "account_number", "accountnumber", "ssn", "social_security"
    )

    fun isSensitive(node: AccessibilityNode): Boolean {
        if (node.isPassword) return true
        return matchesSensitiveKeyword(node.viewIdResourceName) ||
            matchesSensitiveKeyword(node.hintText) ||
            matchesSensitiveKeyword(node.contentDescription)
    }

    /** True only for the payment/ID heuristic (used to pick a more specific mask label). */
    fun isLikelyPaymentField(node: AccessibilityNode): Boolean {
        if (node.isPassword) return false
        val haystack = listOfNotNull(node.viewIdResourceName, node.hintText, node.contentDescription)
            .joinToString(" ") { it.lowercase() }
        return listOf("kort", "card", "cvv", "cvc", "kontonummer", "account_number", "accountnumber")
            .any { haystack.contains(it) }
    }

    private fun matchesSensitiveKeyword(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        val lower = value.lowercase()
        return sensitiveKeywords.any { lower.contains(it) }
    }
}
