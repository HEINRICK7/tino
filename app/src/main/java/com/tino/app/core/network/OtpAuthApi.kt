package com.tino.app.core.network

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tino.app.domain.onboarding.OtpAuthApi
import com.tino.app.domain.onboarding.OtpChallenge
import com.tino.app.domain.onboarding.OtpChallengeStatus
import com.tino.app.domain.onboarding.OtpVerification

class RestOtpAuthApi(
    baseUrl: String,
    private val transport: BackendHttpTransport,
) : OtpAuthApi {
    private val normalizedBaseUrl = baseUrl.trimEnd('/')

    init {
        require(normalizedBaseUrl.startsWith("https://")) { "A API do TINO exige HTTPS." }
    }

    override suspend fun requestChallenge(phone: String): OtpChallenge {
        require(phone.isNotBlank()) { "Celular é obrigatório." }
        val response = transport.execute(
            BackendHttpRequest(
                method = "POST",
                path = "/api/v1/auth/otp/challenges",
                body = JsonObject().apply { addProperty("phone", phone.trim()) }.toString(),
            ),
        )
        if (response.status !in 200..299) throw response.toBackendException()
        return JsonParser.parseString(response.body).asJsonObject.toOtpChallenge()
    }

    override suspend fun verifyCode(challengeId: String, code: String): OtpVerification {
        require(challengeId.isNotBlank()) { "Desafio OTP inválido." }
        require(code.matches(Regex("[0-9]{6}"))) { "O código deve ter seis dígitos." }
        val response = transport.execute(
            BackendHttpRequest(
                method = "POST",
                path = "/api/v1/auth/otp/challenges/$challengeId/verify",
                body = JsonObject().apply { addProperty("code", code) }.toString(),
            ),
        )
        if (response.status !in 200..299) throw response.toBackendException()
        return JsonParser.parseString(response.body).asJsonObject.toOtpVerification()
    }

    override suspend fun getChallengeStatus(challengeId: String): OtpChallengeStatus {
        require(challengeId.isNotBlank()) { "Desafio OTP inválido." }
        val response = transport.execute(
            BackendHttpRequest(
                method = "GET",
                path = "/api/v1/auth/otp/challenges/$challengeId",
            ),
        )
        if (response.status !in 200..299) throw response.toBackendException()
        return JsonParser.parseString(response.body).asJsonObject.toOtpChallengeStatus()
    }

    override suspend fun claimVerification(challengeId: String): OtpVerification {
        require(challengeId.isNotBlank()) { "Desafio OTP inválido." }
        val response = transport.execute(
            BackendHttpRequest(
                method = "POST",
                path = "/api/v1/auth/otp/challenges/$challengeId/claim",
            ),
        )
        if (response.status !in 200..299) throw response.toBackendException()
        return JsonParser.parseString(response.body).asJsonObject.toOtpVerification()
    }
}

class UnavailableOtpAuthApi : OtpAuthApi {
    private fun unavailable(): Nothing = throw BackendTransportException(
        "A autenticação por celular ainda não está disponível.",
        retryable = false,
    )

    override suspend fun requestChallenge(phone: String): OtpChallenge = unavailable()

    override suspend fun verifyCode(challengeId: String, code: String): OtpVerification = unavailable()

    override suspend fun getChallengeStatus(challengeId: String): OtpChallengeStatus = unavailable()

    override suspend fun claimVerification(challengeId: String): OtpVerification = unavailable()
}

private fun JsonObject.toOtpChallenge(): OtpChallenge = OtpChallenge(
    challengeId = requiredString("challenge_id"),
    expiresInSeconds = requiredLong("expires_in_seconds"),
    resendAvailableInSeconds = requiredLong("resend_available_in_seconds"),
    deliveryChannel = requiredString("delivery_channel"),
)

private fun JsonObject.toOtpVerification(): OtpVerification = OtpVerification(
    challengeId = requiredString("challenge_id"),
    verificationStatus = requiredString("verification_status"),
    verificationTicket = requiredString("verification_ticket"),
    ticketExpiresInSeconds = requiredLong("ticket_expires_in_seconds"),
)

private fun JsonObject.toOtpChallengeStatus(): OtpChallengeStatus = OtpChallengeStatus(
    challengeId = requiredString("challenge_id"),
    status = requiredString("status"),
    expiresInSeconds = requiredNonNegativeLong("expires_in_seconds"),
    verificationAvailable = get("verification_available")?.asBoolean ?: false,
)

private fun JsonObject.requiredString(key: String): String =
    get(key)?.takeUnless { it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
        ?: error("Resposta OTP sem $key.")

private fun JsonObject.requiredLong(key: String): Long =
    get(key)?.takeUnless { it.isJsonNull }?.asLong?.takeIf { it > 0 }
        ?: error("Resposta OTP sem $key.")

private fun JsonObject.requiredNonNegativeLong(key: String): Long =
    get(key)?.takeUnless { it.isJsonNull }?.asLong?.takeIf { it >= 0 }
        ?: error("Resposta OTP sem $key.")
