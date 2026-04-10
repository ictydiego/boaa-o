package br.unasp.boacao.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.unasp.boacao.data.repository.AuthRepository
import br.unasp.boacao.domain.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RegisterUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val document: String = "",
    val address: String = "",
    val photoBase64: String = "",
    val isCnpj: Boolean = false,
    val selectedRole: UserRole = UserRole.VOLUNTEER,
    val isLoading: Boolean = false,
    val error: String? = null
)

class RegisterViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState = _uiState.asStateFlow()

    fun onNameChange(v: String) { _uiState.value = _uiState.value.copy(name = v) }
    fun onEmailChange(v: String) { _uiState.value = _uiState.value.copy(email = v) }
    fun onPasswordChange(v: String) { _uiState.value = _uiState.value.copy(password = v) }
    fun onAddressChange(v: String) { _uiState.value = _uiState.value.copy(address = v) }
    fun onPhotoChange(v: String) { _uiState.value = _uiState.value.copy(photoBase64 = v) }

    fun onDocumentChange(v: String) {
        val numbersOnly = v.filter { it.isDigit() }
        val maxLength = if (_uiState.value.isCnpj) 14 else 11
        if (numbersOnly.length <= maxLength) {
            _uiState.value = _uiState.value.copy(document = numbersOnly)
        }
    }

    fun onDocumentTypeChange(isCnpj: Boolean) {
        _uiState.value = _uiState.value.copy(isCnpj = isCnpj, document = "")
    }

    fun onRoleChange(v: UserRole) {
        val isCnpjDefault = v == UserRole.BENEFICIARY || v == UserRole.DONOR
        _uiState.value = _uiState.value.copy(selectedRole = v, isCnpj = isCnpjDefault, document = "")
    }

    fun register(onNavigate: (UserRole) -> Unit) {
        val state = _uiState.value
        if (state.photoBase64.isBlank()) {
            _uiState.value = state.copy(error = "Adicione uma foto de perfil para continuar")
            return
        }
        if (state.name.isBlank() || state.email.isBlank() || state.password.isBlank() || state.document.isBlank()) {
            _uiState.value = state.copy(error = "Preencha todos os campos obrigatórios")
            return
        }

        if ((state.selectedRole == UserRole.DONOR || state.selectedRole == UserRole.BENEFICIARY) && state.address.isBlank()) {
            val label = if (state.selectedRole == UserRole.DONOR) "Doadores" else "ONGs/Abrigos"
            _uiState.value = state.copy(error = "$label precisam informar o endereço")
            return
        }

        val requiredDocLength = if (state.isCnpj) 14 else 11
        if (state.document.length < requiredDocLength) {
            _uiState.value = state.copy(error = if (state.isCnpj) "CNPJ inválido" else "CPF inválido")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            val result = repository.register(state.email, state.password, state.name, state.document, state.address, state.selectedRole, state.photoBase64)
            result.onSuccess { profile ->
                _uiState.value = _uiState.value.copy(isLoading = false)
                onNavigate(profile.role)
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
            }
        }
    }
}

class RegisterViewModelFactory(private val repository: AuthRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RegisterViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") return RegisterViewModel(repository) as T
        }
        throw IllegalArgumentException("ViewModel desconhecido")
    }
}