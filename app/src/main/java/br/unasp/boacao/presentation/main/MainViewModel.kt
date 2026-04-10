package br.unasp.boacao.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.unasp.boacao.data.repository.AuthRepository
import br.unasp.boacao.domain.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MainUiState(
    val userName: String = "Carregando...",
    val userRole: UserRole = UserRole.VOLUNTEER,
    val userPoints: Int = 0,
    val userPhotoBase64: String = "",
    val isLoading: Boolean = true
)

class MainViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()

    init { loadUserProfile() }

    private fun loadUserProfile() {
        viewModelScope.launch {
            val result = authRepository.getUserProfile()
            result.onSuccess { profile ->
                _uiState.value = MainUiState(
                    userName = profile.name,
                    userRole = profile.role,
                    userPoints = profile.points,
                    userPhotoBase64 = profile.photoBase64,
                    isLoading = false
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(userName = "Usuário", isLoading = false)
            }
        }
    }

    fun logout() { authRepository.logout() }
}

class MainViewModelFactory(private val repository: AuthRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("ViewModel desconhecido")
    }
}
