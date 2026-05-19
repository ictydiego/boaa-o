package br.unasp.boacao.presentation.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.unasp.boacao.data.repository.AuthRepository
import br.unasp.boacao.domain.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// 1. Estado da UI
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

// 2. ViewModel
class LoginViewModel(
    private val repository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState = _uiState.asStateFlow()

    fun onEmailChange(newValue: String) {
        _uiState.value = _uiState.value.copy(email = newValue)
    }

    fun onPasswordChange(newValue: String) {
        _uiState.value = _uiState.value.copy(password = newValue)
    }

    fun showError(message: String) {
        _uiState.value = _uiState.value.copy(error = message)
    }

    fun login(onSuccess: (UserRole, String, String) -> Unit) {
        val email = _uiState.value.email
        val pass = _uiState.value.password

        if (email.isBlank() || pass.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Preencha todos os campos")
            return
        }

        loginInternal(email, pass) { role -> onSuccess(role, email, pass) }
    }

    fun loginWithSavedCredentials(email: String, password: String, onSuccess: (UserRole) -> Unit) {
        loginInternal(email, password, onSuccess)
    }

    private fun loginInternal(email: String, password: String, onSuccess: (UserRole) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val result = repository.login(email, password)

            result.onSuccess { profile ->
                _uiState.value = _uiState.value.copy(isLoading = false)
                onSuccess(profile.role)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = error.message ?: "Erro desconhecido"
                )
            }
        }
    }
}

// 3. Factory para criar a ViewModel manualmente
class LoginViewModelFactory(private val repository: AuthRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LoginViewModel(repository) as T
        }
        throw IllegalArgumentException("ViewModel desconhecido")
    }
}
