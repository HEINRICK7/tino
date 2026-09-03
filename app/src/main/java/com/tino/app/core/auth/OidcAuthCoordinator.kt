package com.tino.app.core.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.tino.app.BuildConfig
import com.tino.app.core.network.BackendTokenRefresher
import com.tino.app.domain.onboarding.OtpAuthApi
import com.tino.app.domain.onboarding.OtpCodeAttempt
import com.tino.app.domain.onboarding.OtpChallenge
import com.tino.app.core.security.SecureTokenStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.selects.select
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.CodeVerifierUtil
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import kotlin.coroutines.resume

@Singleton
class OidcAuthCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenStore: SecureTokenStore,
    private val otpAuthApi: OtpAuthApi,
) : BackendTokenRefresher {
    private val authMutex = Mutex()
    private val pendingLogin = AtomicReference<PendingLogin?>(null)
    private var configuration: AuthorizationServiceConfiguration? = null
    private var authorizationService: AuthorizationService? = null

    suspend fun login(
        activity: Activity,
        phone: String,
        otpCodeProvider: suspend (OtpChallenge) -> OtpCodeAttempt,
        onWhatsAppConfirmed: () -> Unit = {},
    ): Result<Unit> = authMutex.withLock {
        val current = tokenStore.readSession()
        if (current?.accessToken?.isNotBlank() == true &&
            (current.expiresAtEpochMs == null || current.expiresAtEpochMs > System.currentTimeMillis() + EXPIRY_SAFETY_WINDOW_MS)
        ) {
            return@withLock Result.success(Unit)
        }
        if (current?.refreshToken?.isNotBlank() == true && refreshAccessTokenLocked()) {
            return@withLock Result.success(Unit)
        }

        var challenge = otpAuthApi.requestChallenge(phone)
        while (true) {
            when (val attempt = awaitOtpAttempt(challenge, otpCodeProvider)) {
                OtpCodeAttempt.Resend -> challenge = otpAuthApi.requestChallenge(phone)
                is OtpCodeAttempt.Submit -> {
                    val verification = otpAuthApi.verifyCode(challenge.challengeId, attempt.code)
                    check(verification.verificationStatus.isOtpVerifiedStatus()) {
                        "O TINO não confirmou o código."
                    }
                    return@withLock authorize(activity, verification.verificationTicket)
                }
                OtpCodeAttempt.WhatsAppConfirmed -> {
                    val verification = otpAuthApi.claimVerification(challenge.challengeId)
                    check(verification.verificationStatus.isOtpVerifiedStatus()) {
                        "O TINO não confirmou a posse do WhatsApp."
                    }
                    onWhatsAppConfirmed()
                    return@withLock authorize(activity, verification.verificationTicket)
                }
            }
        }
        error("O fluxo OTP terminou sem autenticar.")
    }

    private suspend fun awaitOtpAttempt(
        challenge: OtpChallenge,
        otpCodeProvider: suspend (OtpChallenge) -> OtpCodeAttempt,
    ): OtpCodeAttempt = coroutineScope {
        val manual = async { otpCodeProvider(challenge) }
        val automatic = async { awaitWhatsAppConfirmation(challenge) }
        val winner = select<Any> {
            manual.onAwait { it }
            automatic.onAwait { it }
        }
        when (winner) {
            is OtpCodeAttempt -> {
                automatic.cancel()
                winner
            }
            AutomaticOtpResult.Confirmed -> {
                manual.cancel()
                OtpCodeAttempt.WhatsAppConfirmed
            }
            AutomaticOtpResult.Unavailable -> manual.await()
            else -> error("Resultado OTP desconhecido.")
        }
    }

    private suspend fun awaitWhatsAppConfirmation(challenge: OtpChallenge): AutomaticOtpResult {
        while (currentCoroutineContext().isActive) {
            val status = runCatching { otpAuthApi.getChallengeStatus(challenge.challengeId) }.getOrNull()
            if (status != null) {
                if (status.verificationAvailable && status.status.isOtpVerifiedStatus()) {
                    return AutomaticOtpResult.Confirmed
                }
                if (status.expiresInSeconds <= 0L || status.status in setOf(
                        "EXPIRED", "LOCKED", "CONSUMED",
                        "OTP_EXPIRED", "OTP_RATE_LIMITED", "OTP_CANCELLED", "OTP_FAILED",
                    )) {
                    return AutomaticOtpResult.Unavailable
                }
            }
            delay(1_500)
        }
        return AutomaticOtpResult.Unavailable
    }

    private enum class AutomaticOtpResult {
        Confirmed,
        Unavailable,
    }

    private suspend fun authorize(activity: Activity, verificationTicket: String): Result<Unit> {
        val config = runCatching { loadConfiguration() }.getOrElse { return Result.failure(it) }
        val request = AuthorizationRequest.Builder(
            config,
            BuildConfig.TINO_OIDC_CLIENT_ID,
            ResponseTypeValues.CODE,
            Uri.parse(BuildConfig.TINO_OIDC_REDIRECT_URI),
        ).setCodeVerifier(CodeVerifierUtil.generateRandomCodeVerifier())
            .setAdditionalParameters(mapOf("tino_otp_ticket" to verificationTicket))
            .build()
        val result = CompletableDeferred<Result<Unit>>()
        pendingLogin.set(PendingLogin(request, result))
        activity.startActivity(authorizationService().getAuthorizationRequestIntent(request))
        return result.await()
    }

    fun handleRedirect(intent: Intent) {
        val data = intent.data
        val hasAppAuthPayload = intent.hasExtra(AuthorizationResponse.EXTRA_RESPONSE) ||
            intent.hasExtra(AuthorizationException.EXTRA_EXCEPTION)
        val configuredRedirect = Uri.parse(BuildConfig.TINO_OIDC_REDIRECT_URI)
        val isConfiguredRedirect = data?.scheme == configuredRedirect.scheme &&
            data?.host == configuredRedirect.host &&
            data?.path == configuredRedirect.path
        if (!hasAppAuthPayload && !isConfiguredRedirect) return

        val pending = pendingLogin.getAndSet(null) ?: return
        val response = AuthorizationResponse.fromIntent(intent)
            ?: data?.let { uri ->
                runCatching { AuthorizationResponse.Builder(pending.request).fromUri(uri).build() }
                    .getOrNull()
            }
        val exception = AuthorizationException.fromIntent(intent)
            ?: data?.takeIf { it.getQueryParameter(AuthorizationException.PARAM_ERROR) != null }
                ?.let(AuthorizationException::fromOAuthRedirect)
        if (response == null) {
            pending.result.complete(
                Result.failure(exception ?: IllegalStateException("A autenticação do TINO foi cancelada.")),
            )
            return
        }
        authorizationService().performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, tokenException ->
            if (tokenResponse?.accessToken.isNullOrBlank()) {
                pending.result.complete(
                    Result.failure(tokenException ?: IllegalStateException("O TINO não retornou um access token.")),
                )
            } else {
                saveTokenResponse(tokenResponse!!)
                pending.result.complete(Result.success(Unit))
            }
        }
    }

    override suspend fun refreshAccessToken(): Boolean = authMutex.withLock {
        refreshAccessTokenLocked()
    }

    private suspend fun refreshAccessTokenLocked(): Boolean {
        val current = tokenStore.readSession() ?: return false
        val refreshToken = current.refreshToken?.takeIf { it.isNotBlank() } ?: run {
            tokenStore.clear()
            return false
        }
        val config = runCatching { loadConfiguration() }.getOrElse {
            tokenStore.clear()
            return false
        }
        val request = TokenRequest.Builder(config, BuildConfig.TINO_OIDC_CLIENT_ID)
            .setGrantType(net.openid.appauth.GrantTypeValues.REFRESH_TOKEN)
            .setRefreshToken(refreshToken)
            .build()
        val response = requestToken(request) ?: run {
            tokenStore.clear()
            return false
        }
        saveTokenResponse(response, refreshToken)
        return true
    }

    fun logout() {
        tokenStore.clear()
    }

    private suspend fun loadConfiguration(): AuthorizationServiceConfiguration = configuration
        ?: suspendCancellableCoroutine { continuation ->
            AuthorizationServiceConfiguration.fetchFromIssuer(Uri.parse(BuildConfig.TINO_OIDC_ISSUER)) { config, exception ->
                if (config != null) {
                    configuration = config
                    continuation.resume(config)
                } else {
                    continuation.resumeWith(Result.failure(exception ?: IllegalStateException("Não foi possível descobrir o Keycloak.")))
                }
            }
        }

    private fun authorizationService(): AuthorizationService = authorizationService
        ?: AuthorizationService(context).also { authorizationService = it }

    private suspend fun requestToken(request: TokenRequest): TokenResponse? =
        suspendCancellableCoroutine { continuation ->
            authorizationService().performTokenRequest(request) { response, exception ->
                if (response != null && exception == null) continuation.resume(response)
                else continuation.resume(null)
            }
        }

    private fun saveTokenResponse(response: TokenResponse, previousRefreshToken: String? = null) {
        val accessToken = response.accessToken ?: return
        tokenStore.saveSession(
            accessToken = accessToken,
            refreshToken = response.refreshToken ?: previousRefreshToken,
            expiresAtEpochMs = response.accessTokenExpirationTime,
        )
    }

    private companion object {
        const val EXPIRY_SAFETY_WINDOW_MS = 30_000L
    }

    private data class PendingLogin(
        val request: AuthorizationRequest,
        val result: CompletableDeferred<Result<Unit>>,
    )
}

private fun String.isOtpVerifiedStatus(): Boolean = this == "VERIFIED" || this == "OTP_VERIFIED"
