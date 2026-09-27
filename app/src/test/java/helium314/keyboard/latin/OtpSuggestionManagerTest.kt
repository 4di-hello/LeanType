// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OtpSuggestionManagerTest {

    @Test
    fun testExplicitOtpPatterns() {
        assertEquals("123456", OtpSuggestionManager.extractOtp("Your OTP is 123456."))
        assertEquals("482910", OtpSuggestionManager.extractOtp("Your verification code: 482910"))
        assertEquals("847291", OtpSuggestionManager.extractOtp("Login passcode: 847291"))
        assertEquals("314159", OtpSuggestionManager.extractOtp("Use OTP 314159 to confirm transaction."))
        assertEquals("654321", OtpSuggestionManager.extractOtp("Your one-time password is 654321."))
        assertEquals("9988", OtpSuggestionManager.extractOtp("Your 2FA pin is: 9988"))
    }

    @Test
    fun testReverseOtpPatterns() {
        assertEquals("482910", OtpSuggestionManager.extractOtp("482910 is your Google verification code."))
        assertEquals("789101", OtpSuggestionManager.extractOtp("789101 is your OTP."))
        assertEquals("123456", OtpSuggestionManager.extractOtp("123456 is your security code."))
    }

    @Test
    fun testMessagesWithYearsDatesAndAmounts() {
        assertEquals("492015", OtpSuggestionManager.extractOtp("Account accessed on 2026-09-28. Verification code: 492015."))
        assertEquals("314159", OtpSuggestionManager.extractOtp("Order #982345: Use OTP 314159 to confirm delivery."))
        assertEquals("789012", OtpSuggestionManager.extractOtp("Dear user, OTP for INR 2500.00 at Store is 789012."))
    }

    @Test
    fun testSingleNumberWithoutKeyword() {
        assertEquals("582914", OtpSuggestionManager.extractOtp("582914"))
        assertEquals("1234", OtpSuggestionManager.extractOtp("Your number is 1234"))
    }

    @Test
    fun testIrrelevantMessages() {
        assertNull(OtpSuggestionManager.extractOtp(""))
        assertNull(OtpSuggestionManager.extractOtp("   "))
        assertNull(OtpSuggestionManager.extractOtp("Hello! How are you doing today?"))
        assertNull(OtpSuggestionManager.extractOtp("See you tomorrow at 5pm."))
    }
}
