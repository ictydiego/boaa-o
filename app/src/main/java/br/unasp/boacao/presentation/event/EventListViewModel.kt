package br.unasp.boacao.presentation.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.unasp.boacao.data.repository.AttendanceRepository
import br.unasp.boacao.data.repository.AuthRepository
import br.unasp.boacao.data.repository.EventRepository
import br.unasp.boacao.domain.model.Event
import br.unasp.boacao.domain.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EventListUiState(
    val isLoading: Boolean = true,
    val events: List<Event> = emptyList(),
    val subscribedEventIds: Set<String> = emptySet(),
    val message: String? = null,
    val error: String? = null
)

class EventListViewModel(
    private val eventRepository: EventRepository,
    private val attendanceRepository: AttendanceRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EventListUiState())
    val uiState = _uiState.asStateFlow()

    private var profile: UserProfile? = null

    init {
        viewModelScope.launch { loadProfileThenObserve() }
    }

    private suspend fun loadProfileThenObserve() {
        authRepository.getUserProfile().onSuccess { p ->
            profile = p
            viewModelScope.launch {
                attendanceRepository.observeMyAttendances(p.id).collect { mine ->
                    _uiState.value = _uiState.value.copy(
                        subscribedEventIds = mine.map { it.eventId }.toSet()
                    )
                }
            }
            viewModelScope.launch {
                eventRepository.observePublishedEvents().collect { list ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        events = list.sortedBy { it.startAt }
                    )
                }
            }
        }.onFailure {
            _uiState.value = _uiState.value.copy(isLoading = false, error = it.message)
        }
    }

    fun subscribe(eventId: String) {
        val p = profile ?: return
        viewModelScope.launch {
            attendanceRepository.subscribe(eventId, p).fold(
                { _uiState.value = _uiState.value.copy(message = "Inscrição confirmada!") },
                { _uiState.value = _uiState.value.copy(error = it.message ?: "Erro ao inscrever") }
            )
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(message = null, error = null)
    }
}

class EventListViewModelFactory(
    private val eventRepository: EventRepository,
    private val attendanceRepository: AttendanceRepository,
    private val authRepository: AuthRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EventListViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EventListViewModel(eventRepository, attendanceRepository, authRepository) as T
        }
        throw IllegalArgumentException("ViewModel desconhecido")
    }
}
