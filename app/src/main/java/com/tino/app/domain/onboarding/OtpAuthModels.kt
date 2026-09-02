package com.tino.app.domain.onboarding

data class OtpChallenge(
    val challengeId: String,
    val expiresInSeconds: Long,
    val resendAvailableInSeconds: Long,
    val deliveryChannel: String,
)

data class OtpVerification(
    val challengeId: String,
    val verificationStatus: String,
    val verificationTicket: String,
    val ticketExpiresInSeconds: Long,
)

sealed interface OtpCodeAttempt {
    data class Submit(val code: String) : OtpCodeAttempt
    data object Resend : OtpCodeAttempt
}

interface OtpAuthApi {
    suspend fun requestChallenge(phone: String): OtpChallenge

    suspend fun verifyCode(challengeId: String, code: String): OtpVerification
}
