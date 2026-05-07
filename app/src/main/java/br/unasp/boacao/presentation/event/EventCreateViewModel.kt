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

data class EventCreateUiState(
    val saving: Boolean = false,
    val error: String? = null,
    val createdEventId: String? = null
)

class EventCreateViewModel(
    private val eventRepository: EventRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EventCreateUiState())
    val uiState = _uiState.asStateFlow()

    fun submit(
        title: String,
        description: String,
        address: String,
        startAt: Long,
        endAt: Long,
        workloadHours: Double,
        maxParticipants: Int
    ) {
        if (title.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Informe o título do evento")
            return
        }
        if (startAt == 0L) {
            _uiState.value = _uiState.value.copy(error = "Selecione a data de início")
            return
        }
        viewModelScope.launch {
            _uiState.value = EventCreateUiState(saving = true)
            val profile = authRepository.getUserProfile().getOrNull()
            if (profile == null) {
                _uiState.value = EventCreateUiState(error = "Usuário não autenticado")
                return@launch
            }
            val event = Event(
                ngoId = profile.id,
                ngoName = profile.name,
                title = title.trim(),
                description = description.trim(),
                address = address.trim(),
                startAt = startAt,
                endAt = endAt,
                workloadHours = workloadHours,
                maxParticipants = maxParticipants
            )
            eventRepository.createEvent(event).fold(
                { id -> _uiState.value = EventCreateUiState(createdEventId = id) },
                { _uiState.value = EventCreateUiState(error = it.message ?: "Erro ao criar evento") }
            )
        }
    }

    fun consumeCreated() {
        _uiState.value = _uiState.value.copy(createdEventId = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

class EventCreateViewModelFactory(
    private val eventRepository: EventRepository,
    private val authRepository: AuthRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EventCreateViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EventCreateViewModel(eventRepository, authRepository) as T
        }
        throw IllegalArgumentException("ViewModel desconhecido")
    }
}
