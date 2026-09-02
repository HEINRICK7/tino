package com.tino.app.feature.receiving

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tino.app.core.network.BackendApiException
import com.tino.app.core.network.BackendAuthenticationException
import com.tino.app.core.network.BackendTransportException
import com.tino.app.domain.receiving.GoodsReceiptConfirmation
import com.tino.app.domain.receiving.GoodsReceiptPreview
import com.tino.app.domain.receiving.GoodsReceiptRemoteState
import com.tino.app.domain.receiving.GoodsReceiptRepository
import com.tino.app.domain.receiving.GoodsReceiptPreviewStatus
import com.tino.app.domain.receiving.NfeRetrievalStatus
import com.tino.app.domain.receiving.ProductSearchItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

@HiltViewModel
class GoodsReceiptViewModel @Inject constructor(
    private val repository: GoodsReceiptRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<GoodsReceiptRemoteState>(GoodsReceiptRemoteState.Idle)
    val state: StateFlow<GoodsReceiptRemoteState> = _state.asStateFlow()

    private val _searchResults = MutableStateFlow<List<ProductSearchItem>>(emptyList())
    val searchResults: StateFlow<List<ProductSearchItem>> = _searchResults.asStateFlow()

    private var lastAccessKey: String? = null
    private var pendingConfirmation: Pair<GoodsReceiptPreview, GoodsReceiptConfirmation>? = null

    fun submitAccessKey(accessKey: String) {
        lastAccessKey = accessKey
        viewModelScope.launch {
            _state.value = GoodsReceiptRemoteState.ReadingKey
            _state.value = GoodsReceiptRemoteState.Retrieving
            runCatching {
                val document = repository.retrieve(accessKey)
                when (document.retrievalStatus) {
                    NfeRetrievalStatus.SUCCESS -> repository.getPreview(document.documentId)
                        .also { preview ->
                            _state.value = if (preview.status == GoodsReceiptPreviewStatus.REVIEW_REQUIRED || preview.items.any { it.requiresUserAction }) {
                                GoodsReceiptRemoteState.ReviewRequired(preview)
                            } else {
                                GoodsReceiptRemoteState.PreviewReady(preview)
                            }
                        }
                    NfeRetrievalStatus.PENDING,
                    NfeRetrievalStatus.IN_PROGRESS,
                    -> _state.value = GoodsReceiptRemoteState.Waiting
                    NfeRetrievalStatus.NOT_FOUND -> terminal("NFE_NOT_FOUND")
                    NfeRetrievalStatus.OUTCOME_UNKNOWN -> terminal("OUTCOME_UNKNOWN")
                    NfeRetrievalStatus.FAILED -> if (document.retryable) {
                        _state.value = GoodsReceiptRemoteState.RetryableError(messageFor(document.errorCode?.name ?: "RETRIEVAL_UNAVAILABLE"))
                    } else {
                        terminal(document.errorCode?.name ?: "RETRIEVAL_UNAVAILABLE")
                    }
                }
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                _state.value = when {
                    error is BackendApiException && error.retryable -> GoodsReceiptRemoteState.RetryableError(messageFor(error.code.name))
                    error is BackendTransportException && error.retryable -> GoodsReceiptRemoteState.RetryableError(error.message ?: "Verifique sua conexão.")
                    else -> GoodsReceiptRemoteState.TerminalError(messageFor(errorCode(error)))
                }
            }
        }
    }

    fun retry() {
        pendingConfirmation?.let { (preview, confirmation) ->
            confirm(preview, confirmation)
            return
        }
        viewModelScope.launch {
            runCatching { repository.retryPendingConfirmationIfPresent() }
                .onSuccess { result ->
                    if (result != null) {
                        _state.value = GoodsReceiptRemoteState.Confirmed(result)
                    } else {
                        lastAccessKey?.let(::submitAccessKey)
                    }
                }
                .onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    _state.value = when {
                        error is BackendApiException && error.retryable -> GoodsReceiptRemoteState.RetryableError(messageFor(error.code.name))
                        error is BackendTransportException && error.retryable -> GoodsReceiptRemoteState.RetryableError(error.message ?: "Verifique sua conexão.")
                        else -> GoodsReceiptRemoteState.TerminalError(messageFor(errorCode(error)))
                    }
                }
        }
    }

    fun searchProducts(query: String, gtin: String? = null) {
        viewModelScope.launch {
            val digits = query.filter(Char::isDigit)
            val isGtin = digits == query && digits.length in 8..14
            runCatching {
                repository.searchProducts(
                    query = query.takeIf { it.isNotBlank() && !isGtin },
                    gtin = gtin ?: digits.takeIf { isGtin },
                )
            }
                .onSuccess { _searchResults.value = it }
                .onFailure { _searchResults.value = emptyList() }
        }
    }

    fun confirm(preview: GoodsReceiptPreview, confirmation: GoodsReceiptConfirmation) {
        pendingConfirmation = preview to confirmation
        viewModelScope.launch {
            _state.value = GoodsReceiptRemoteState.Confirming(preview)
            runCatching { repository.confirm(preview, confirmation) }
                .onSuccess {
                    pendingConfirmation = null
                    _state.value = GoodsReceiptRemoteState.Confirmed(it)
                }
                .onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    _state.value = when {
                        error is BackendApiException && error.retryable -> GoodsReceiptRemoteState.RetryableError(messageFor(error.code.name))
                        error is BackendTransportException && error.retryable -> GoodsReceiptRemoteState.RetryableError(error.message ?: "Verifique sua conexão.")
                        else -> GoodsReceiptRemoteState.TerminalError(messageFor(errorCode(error)))
                    }
                }
        }
    }

    private fun terminal(code: String) {
        _state.value = GoodsReceiptRemoteState.TerminalError(messageFor(code))
    }

    private fun errorCode(error: Throwable): String = when (error) {
        is BackendApiException -> error.code.name
        is BackendAuthenticationException -> "BUSINESS_ACCESS_DENIED"
        else -> "RETRIEVAL_UNAVAILABLE"
    }

    private fun messageFor(code: String): String = when (code) {
        "INVALID_ACCESS_KEY" -> "Confira os 44 dígitos da chave de acesso."
        "NFE_NOT_FOUND" -> "Não encontramos essa NF-e para este negócio."
        "OUTCOME_UNKNOWN" -> "A consulta não teve um resultado confirmado. Tente consultar novamente mais tarde."
        "FISCAL_CANCELLED" -> "Esta NF-e está cancelada e não pode entrar no estoque."
        "FISCAL_DENIED" -> "Esta NF-e está denegada e não pode entrar no estoque."
        "PRODUCT_REVIEW_REQUIRED" -> "Escolha um produto para cada item pendente."
        "PACKAGING_CONVERSION_REQUIRED" -> "Informe como a unidade de compra vira unidade de estoque."
        "STALE_PREVIEW" -> "A prévia mudou. Consulte a NF-e novamente antes de confirmar."
        "INVALID_PRODUCT_SELECTION" -> "A escolha de produto não é válida para esta entrada."
        "BUSINESS_ACCESS_DENIED" -> "Entre novamente ou selecione um negócio autorizado."
        "IDEMPOTENCY_CONFLICT" -> "Esta operação já está vinculada a outra solicitação."
        "RETRIEVAL_UNAVAILABLE" -> "O TINO Backend está temporariamente indisponível."
        else -> "Não foi possível concluir a entrada NF-e."
    }
}
