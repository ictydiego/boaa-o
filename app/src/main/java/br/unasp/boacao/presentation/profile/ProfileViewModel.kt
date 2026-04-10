package br.unasp.boacao.presentation.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.unasp.boacao.data.repository.AuthRepository
import br.unasp.boacao.domain.model.UserProfile
import br.unasp.boacao.domain.model.UserRole
import br.unasp.boacao.util.ImageUtils
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val profile: UserProfile = UserProfile(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val successMessage: String? = null,
    val error: String? = null
)

class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState = _uiState.asStateFlow()

    init { loadProfile() }

    private fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            authRepository.getUserProfile().onSuccess { profile ->
                _uiState.value = _uiState.value.copy(isLoading = false, profile = profile)
            }.onFailure {
                _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
            }
        }
    }

    fun uploadPhoto(context: Context, uri: Uri) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            val base64 = ImageUtils.uriToBase64(context, uri, 400, 400, 50)
            if (base64 == null) {
                _uiState.value = _uiState.value.copy(isSaving = false, error = "Erro ao processar imagem.")
                return@launch
            }
            authRepository.updateProfilePhoto(userId, base64).onSuccess {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    successMessage = "Foto atualizada com sucesso!",
                    profile = _uiState.value.profile.copy(photoBase64 = base64)
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(isSaving = false, error = it.message)
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(successMessage = null, error = null)
    }
}

class ProfileViewModelFactory(private val authRepository: AuthRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ProfileViewModel(authRepository) as T
    }
}
