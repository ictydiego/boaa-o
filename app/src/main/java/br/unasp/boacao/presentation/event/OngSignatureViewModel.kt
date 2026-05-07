package br.unasp.boacao.presentation.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.unasp.boacao.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OngSignatureUiState(
    val isLoading: Boolean = true,
    val saving: Boolean = false,
    val currentSignatureBase64: String = "",
    val message: String? = null,
    val error: String? = null
)

class OngSignatureViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OngSignatureUiState())
    val uiState = _uiState.asStateFlow()

    private var userId: String = ""

    init { load() }

    private fun load() {
        viewModelScope.launch {
            authRepository.getUserProfile().onSuccess { p ->
                userId = p.id
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    currentSignatureBase64 = p.signatureBase64
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
            }
        }
    }

    fun save(signatureBase64: String) {
        if (userId.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(saving = true, error = null, message = null)
            authRepository.updateSignature(userId, signatureBase64).fold(
                {
                    _uiState.value = _uiState.value.copy(
                        saving = false,
                        currentSignatureBase64 = signatureBase64,
                        message = "Assinatura salva."
                    )
                },
                {
                    _uiState.value = _uiState.value.copy(saving = false, error = it.message ?: "Erro ao salvar")
                }
            )
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(message = null, error = null)
    }
}

class OngSignatureViewModelFactory(
    private val authRepository: AuthRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OngSignatureViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return OngSignatureViewModel(authRepository) as T
        }
        throw IllegalArgumentException("ViewModel desconhecido")
    }
}
