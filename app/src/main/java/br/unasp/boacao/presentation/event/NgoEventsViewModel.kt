package br.unasp.boacao.presentation.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.unasp.boacao.data.repository.AuthRepository
import br.unasp.boacao.data.repository.EventRepository
import br.unasp.boacao.domain.model.Event
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NgoEventsUiState(
    val isLoading: Boolean = true,
    val events: List<Event> = emptyList(),
    val error: String? = null
)

class NgoEventsViewModel(
    private val eventRepository: EventRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NgoEventsUiState())
    val uiState = _uiState.asStateFlow()

    init { observe() }

    private fun observe() {
        viewModelScope.launch {
            authRepository.getUserProfile().onSuccess { profile ->
                eventRepository.observeNgoEvents(profile.id).collect { list ->
                    _uiState.value = NgoEventsUiState(
                        isLoading = false,
                        events = list.sortedByDescending { it.createdAt }
                    )
                }
            }.onFailure {
                _uiState.value = NgoEventsUiState(isLoading = false, error = it.message)
            }
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}

class NgoEventsViewModelFactory(
    private val eventRepository: EventRepository,
    private val authRepository: AuthRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NgoEventsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NgoEventsViewModel(eventRepository, authRepository) as T
        }
        throw IllegalArgumentException("ViewModel desconhecido")
    }
}
