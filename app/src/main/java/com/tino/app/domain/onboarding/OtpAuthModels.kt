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

data class OtpChallengeStatus(
    val challengeId: String,
    val status: String,
    val expiresInSeconds: Long,
    val verificationAvailable: Boolean,
)

sealed interface OtpCodeAttempt {
    data class Submit(val code: String) : OtpCodeAttempt
    data object Resend : OtpCodeAttempt
    data object WhatsAppConfirmed : OtpCodeAttempt
}

interface OtpAuthApi {
    suspend fun requestChallenge(phone: String): OtpChallenge

    suspend fun verifyCode(challengeId: String, code: String): OtpVerification

    suspend fun getChallengeStatus(challengeId: String): OtpChallengeStatus

    suspend fun claimVerification(challengeId: String): OtpVerification
}
