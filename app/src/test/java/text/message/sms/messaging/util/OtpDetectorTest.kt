package text.message.sms.messaging.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OtpDetectorTest {

    @Test
    fun `extracts a bank OTP that precedes the keyword`() {
        val body = "1234 is your OTP for txn of Rs.500 at Amazon. Valid for 10 mins. " +
            "Do not share this OTP with anyone."
        assertEquals("1234", OtpDetector.extractCode(body))
    }

    @Test
    fun `extracts a bank OTP that follows the keyword`() {
        val body = "Your OTP for login is 482910. It is valid for 5 minutes. " +
            "Do not share this code with anyone."
        assertEquals("482910", OtpDetector.extractCode(body))
    }

    @Test
    fun `extracts a delivery OTP`() {
        val body = "Use OTP 5566 to confirm your Swiggy delivery. " +
            "Do not share this OTP with the delivery person."
        assertEquals("5566", OtpDetector.extractCode(body))
    }

    @Test
    fun `extracts a one-time password phrased without the literal word OTP`() {
        val body = "778899 is your one-time password for login to MyBank NetBanking."
        assertEquals("778899", OtpDetector.extractCode(body))
    }

    @Test
    fun `picks the code nearest the keyword over an unrelated far-away number`() {
        val body = "Welcome to Acme Bank. Your OTP is 4521. " +
            "FYI our support number is 18002001234 for any queries."
        assertEquals("4521", OtpDetector.extractCode(body))
    }

    @Test
    fun `ignores an account number sitting next to the real OTP`() {
        val body = "Rs 2500 debited from A/c XX1234 on 19-09-26. Your OTP for this txn is 9081. " +
            "Do not share OTP or CVV with anyone. -Bank"
        assertEquals("9081", OtpDetector.extractCode(body))
    }

    @Test
    fun `returns null for a balance alert with no OTP keyword`() {
        val body = "Your account balance is Rs.45000 as of 15-09-2026. " +
            "Avl bal after due EMI of Rs.12500 will be Rs.32500."
        assertNull(OtpDetector.extractCode(body))
    }

    @Test
    fun `returns null for a plain phone number with no OTP keyword`() {
        val body = "Call us at +919876543210 for support regarding your query."
        assertNull(OtpDetector.extractCode(body))
    }

    @Test
    fun `returns null when the only candidate number is part of a date`() {
        val body = "Your OTP request expires on 15-09-2026."
        assertNull(OtpDetector.extractCode(body))
    }

    @Test
    fun `returns null when the only nearby number is an order reference, not a code`() {
        val body = "Your OTP for order ref 12345678 has been generated. Please check your app."
        assertNull(OtpDetector.extractCode(body))
    }

    @Test
    fun `returns null for a promo code message with no standalone digit run`() {
        val body = "Use code SAVE2026 to get 20% off your order this week."
        assertNull(OtpDetector.extractCode(body))
    }
}
