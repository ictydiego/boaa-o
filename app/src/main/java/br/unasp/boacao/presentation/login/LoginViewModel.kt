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

    fun login(onNavigate: (UserRole) -> Unit) {
        val email = _uiState.value.email
        val pass = _uiState.value.password

        if (email.isBlank() || pass.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Preencha todos os campos")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val result = repository.login(email, pass)

            result.onSuccess { profile ->
                _uiState.value = _uiState.value.copy(isLoading = false)
                onNavigate(profile.role)
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