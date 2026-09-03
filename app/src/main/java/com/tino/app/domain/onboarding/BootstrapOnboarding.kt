package com.tino.app.domain.onboarding

import android.app.Activity
import com.tino.app.core.auth.OidcAuthCoordinator
import com.tino.app.core.common.IdentityProvider
import com.tino.app.core.network.BackendApiException
import com.tino.app.core.network.BackendAuthenticationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BootstrapOnboarding @Inject constructor(
    private val auth: OidcAuthCoordinator,
    private val api: BootstrapApi,
    private val dataSourceApi: BusinessDataSourceApi,
    private val identity: IdentityProvider,
) {
    /**
     * Re-authenticates an existing local business without creating a new one.
     * The local business id is only a selection hint; the final tenant and
     * installation always come from the authenticated READY bootstrap.
     */
    suspend fun reauthenticateExistingBusiness(
        activity: Activity,
        phone: String,
        otpCodeProvider: suspend (OtpChallenge) -> OtpCodeAttempt,
        onStage: (OnboardingState) -> Unit = {},
    ): OnboardingResult {
        onStage(OnboardingState.Authenticating)
        auth.login(
            activity,
            phone = phone,
            otpCodeProvider = otpCodeProvider,
            onWhatsAppConfirmed = { onStage(OnboardingState.WhatsAppConfirmed) },
        ).getOrThrow()

        val installationId = identity.current().installationId
        var context = api.bootstrap(
            requestedBusinessId = identity.current().businessId,
            installationExternalId = installationId,
        )
        if (context.state == "BUSINESS_REQUIRED" && context.selectedBusiness == null && context.businesses.size == 1) {
            context = api.bootstrap(
                requestedBusinessId = context.businesses.single().id,
                installationExternalId = installationId,
            )
        }
        check(context.state == "READY") {
            "O comércio existente não está pronto para este aparelho (${context.state})."
        }
        val business = context.selectedBusiness
            ?: error("O bootstrap READY não retornou o comércio existente.")
        var installation = context.installation
            ?.takeIf { it.businessId == business.id && it.installationId == installationId }
        if (installation == null) {
            onStage(OnboardingState.RegisteringInstallation)
            installation = api.registerInstallation(business.id, installationId)
        }
        context = api.bootstrap(
            requestedBusinessId = business.id,
            installationExternalId = installationId,
        )
        check(context.state == "READY") { "O backend não liberou o comércio para uso (${context.state})." }
        val selected = context.selectedBusiness
        check(selected?.id == business.id) { "O backend selecionou um comércio diferente do solicitado." }
        val readyInstallation = context.installation
            ?: error("O bootstrap READY não retornou uma instalação ativa.")
        check(readyInstallation.businessId == business.id) {
            "A instalação retornada não pertence ao comércio selecionado."
        }
        val dataSource = dataSourceApi.get(business.id)
        identity.setBusinessId(business.id)
        onStage(OnboardingState.Ready)
        return OnboardingResult(selected, readyInstallation, dataSource)
    }

    suspend fun complete(
        activity: Activity,
        tradeName: String,
        vertical: String,
        phone: String,
        dataSource: OnboardingDataSourceChoice = OnboardingDataSourceChoice.Native,
        otpCodeProvider: suspend (OtpChallenge) -> OtpCodeAttempt,
        onStage: (OnboardingState) -> Unit = {},
    ): OnboardingResult {
        onStage(OnboardingState.Authenticating)
        auth.login(activity, phone = phone, otpCodeProvider = { challenge ->
            onStage(OnboardingState.AwaitingOtp(challenge))
            otpCodeProvider(challenge)
        }, onWhatsAppConfirmed = { onStage(OnboardingState.WhatsAppConfirmed) }).getOrThrow()

        val installationId = identity.current().installationId
        var context = api.bootstrap()
        var createdBusiness = false
        val business = when (context.state) {
            "READY" -> context.selectedBusiness
                ?: error("O backend informou READY sem uma empresa selecionada.")

            "BUSINESS_REQUIRED" -> {
                val pendingId = identity.pendingBusinessId() ?: identity.current().businessId
                val pending = pendingId?.let { id -> context.businesses.firstOrNull { it.id == id } }
                pending ?: run {
                    onStage(OnboardingState.LoadingBusiness)
                    api.createBusiness(tradeName, vertical).also {
                        createdBusiness = true
                        identity.setPendingBusinessId(it.id)
                    }
                }
            }

            "LOCAL_BUSINESS_LINK_REQUIRED" -> {
                val requestedId = identity.current().businessId ?: identity.pendingBusinessId()
                val requested = requestedId?.let { id -> context.businesses.firstOrNull { it.id == id } }
                requested ?: context.businesses.singleOrNull()
                ?: error("Selecione no backend qual comércio deve ser vinculado a este aparelho.")
            }

            else -> error("O backend retornou um estado de bootstrap não reconhecido.")
        }

        val authoritativeDataSource = if (createdBusiness) {
            onStage(OnboardingState.LoadingBusiness)
            dataSourceApi.select(business.id, dataSource.sourceType, dataSource.provider)
        } else {
            // A source belongs to the Business. A second device must inherit it,
            // never reapply the choice shown by its local onboarding screen.
            dataSourceApi.get(business.id)
        }

        var installation = context.installation
            ?.takeIf { it.businessId == business.id && it.installationId == installationId }
        if (installation == null) {
            onStage(OnboardingState.RegisteringInstallation)
            installation = api.registerInstallation(business.id, installationId)
        }

        context = api.bootstrap(
            requestedBusinessId = business.id,
            installationExternalId = installationId,
        )
        check(context.state == "READY") { "O backend não liberou o comércio para uso (${context.state})." }
        val selected = context.selectedBusiness
        check(selected?.id == business.id) { "O backend selecionou uma empresa diferente da solicitada." }
        val readyInstallation = context.installation
            ?: error("O backend informou READY sem uma instalação ativa.")
        check(readyInstallation.businessId == business.id) { "A instalação retornada não pertence à empresa selecionada." }

        identity.setBusinessId(business.id)
        onStage(OnboardingState.Ready)
        return OnboardingResult(selected, readyInstallation, authoritativeDataSource)
    }

    fun userMessage(error: Throwable): String = when (error) {
        is BackendAuthenticationException -> "Sua sessão expirou. Entre novamente para continuar."
        is BackendApiException -> when (error.code) {
            com.tino.app.core.network.BackendWireErrorCode.INVALID_OTP_REQUEST ->
                "Informe um celular brasileiro válido com DDD e 9 dígitos."
            com.tino.app.core.network.BackendWireErrorCode.OTP_RATE_LIMITED ->
                "Aguarde um pouco antes de pedir outro código."
            com.tino.app.core.network.BackendWireErrorCode.OTP_DELIVERY_UNAVAILABLE ->
                "Não foi possível enviar o código agora. Tente novamente em instantes."
            com.tino.app.core.network.BackendWireErrorCode.OTP_INVALID ->
                "O código informado não confere."
            com.tino.app.core.network.BackendWireErrorCode.OTP_EXPIRED ->
                "Esse código expirou. Solicite um novo código."
            com.tino.app.core.network.BackendWireErrorCode.OTP_LOCKED ->
                "Muitas tentativas. Solicite um novo código."
            com.tino.app.core.network.BackendWireErrorCode.OTP_ALREADY_USED ->
                "Esse código já foi utilizado. Solicite um novo código."
            com.tino.app.core.network.BackendWireErrorCode.OTP_OPERATION_FAILED ->
                "Não foi possível concluir a autenticação agora."
            else -> error.message
        }
        else -> error.message?.takeIf { it.isNotBlank() } ?: "Não foi possível preparar o comércio agora."
    }
}
