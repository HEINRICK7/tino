package com.tino.app.core.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OtpAuthApiContractTest {
    @Test
    fun requestsChallengeUsingTheBackendPhoneOtpContract() = runBlocking {
        var captured: BackendHttpRequest? = null
        val api = RestOtpAuthApi("https://api.tino.test", BackendHttpTransport { request ->
            captured = request
            BackendHttpResponse(
                201,
                """{"challenge_id":"challenge-1","expires_in_seconds":300,"resend_available_in_seconds":30,"delivery_channel":"WHATSAPP"}""",
            )
        })

        val challenge = api.requestChallenge(" (86) 9 1234-5678 ")

        assertEquals("POST", captured?.method)
        assertEquals("/api/v1/auth/otp/challenges", captured?.path)
        assertEquals("{\"phone\":\"(86) 9 1234-5678\"}", captured?.body)
        assertEquals("challenge-1", challenge.challengeId)
        assertEquals(300L, challenge.expiresInSeconds)
        assertEquals(30L, challenge.resendAvailableInSeconds)
        assertEquals("WHATSAPP", challenge.deliveryChannel)
    }

    @Test
    fun verifiesCodeAndReturnsTheAuthorizationTicket() = runBlocking {
        var captured: BackendHttpRequest? = null
        val api = RestOtpAuthApi("https://api.tino.test", BackendHttpTransport { request ->
            captured = request
            BackendHttpResponse(
                200,
                """{"challenge_id":"challenge-1","verification_status":"VERIFIED","verification_ticket":"ticket-1","ticket_expires_in_seconds":60}""",
            )
        })

        val verification = api.verifyCode("challenge-1", "123456")

        assertEquals("POST", captured?.method)
        assertEquals("/api/v1/auth/otp/challenges/challenge-1/verify", captured?.path)
        assertEquals("{\"code\":\"123456\"}", captured?.body)
        assertEquals("challenge-1", verification.challengeId)
        assertEquals("VERIFIED", verification.verificationStatus)
        assertEquals("ticket-1", verification.verificationTicket)
        assertEquals(60L, verification.ticketExpiresInSeconds)
    }

    @Test
    fun pollsChallengeStatusWithoutSendingThePhoneOrCode() = runBlocking {
        var captured: BackendHttpRequest? = null
        val api = RestOtpAuthApi("https://api.tino.test", BackendHttpTransport { request ->
            captured = request
            BackendHttpResponse(
                200,
                """{"challenge_id":"challenge-1","status":"VERIFIED","expires_in_seconds":120,"verification_available":true}""",
            )
        })

        val status = api.getChallengeStatus("challenge-1")

        assertEquals("GET", captured?.method)
        assertEquals("/api/v1/auth/otp/challenges/challenge-1", captured?.path)
        assertEquals(null, captured?.body)
        assertEquals("VERIFIED", status.status)
        assertEquals(120L, status.expiresInSeconds)
        assertTrue(status.verificationAvailable)
    }

    @Test
    fun claimsTicketAfterBackendConfirmsWhatsApp() = runBlocking {
        var captured: BackendHttpRequest? = null
        val api = RestOtpAuthApi("https://api.tino.test", BackendHttpTransport { request ->
            captured = request
            BackendHttpResponse(
                200,
                """{"challenge_id":"challenge-1","verification_status":"VERIFIED","verification_ticket":"ticket-2","ticket_expires_in_seconds":60}""",
            )
        })

        val verification = api.claimVerification("challenge-1")

        assertEquals("POST", captured?.method)
        assertEquals("/api/v1/auth/otp/challenges/challenge-1/claim", captured?.path)
        assertEquals(null, captured?.body)
        assertEquals("ticket-2", verification.verificationTicket)
    }

    @Test
    fun rejectsCodesThatCouldNotBeSentToTheBackend() {
        val api = RestOtpAuthApi("https://api.tino.test", BackendHttpTransport {
            error("transport must not be called")
        })

        val error = runCatching {
            runBlocking { api.verifyCode("challenge-1", "12a456") }
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("O código deve ter seis dígitos.", error?.message)
    }

    @Test
    fun preservesInvalidOtpRequestCodeFromBackend() = runBlocking {
        val api = RestOtpAuthApi("https://api.tino.test", BackendHttpTransport {
            BackendHttpResponse(
                400,
                """{"code":"INVALID_OTP_REQUEST","message":"invalid OTP request","correlation_id":"corr-1"}""",
            )
        })

        val error = runCatching { api.requestChallenge("8694209350") }.exceptionOrNull()

        assertTrue(error is BackendApiException)
        assertEquals(BackendWireErrorCode.INVALID_OTP_REQUEST, (error as BackendApiException).code)
        assertEquals("corr-1", error.correlationId)
    }
}
