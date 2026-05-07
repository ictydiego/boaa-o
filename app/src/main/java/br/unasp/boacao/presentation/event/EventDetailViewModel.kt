package br.unasp.boacao.presentation.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.unasp.boacao.data.repository.AttendanceRepository
import br.unasp.boacao.data.repository.EventRepository
import br.unasp.boacao.domain.model.Attendance
import br.unasp.boacao.domain.model.Event
import br.unasp.boacao.domain.model.EventStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EventDetailUiState(
    val isLoading: Boolean = true,
    val event: Event? = null,
    val attendances: List<Attendance> = emptyList(),
    val error: String? = null
)

class EventDetailViewModel(
    private val eventRepository: EventRepository,
    private val attendanceRepository: AttendanceRepository,
    val eventId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(EventDetailUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadEvent()
        observeAttendances()
    }

    private fun loadEvent() {
        viewModelScope.launch {
            eventRepository.getEvent(eventId).fold(
                { _uiState.value = _uiState.value.copy(isLoading = false, event = it) },
                { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
            )
        }
    }

    private fun observeAttendances() {
        viewModelScope.launch {
            attendanceRepository.observeEventAttendances(eventId).collect { list ->
                _uiState.value = _uiState.value.copy(attendances = list)
            }
        }
    }

    fun setStatus(status: EventStatus) {
        viewModelScope.launch {
            eventRepository.updateStatus(eventId, status).onSuccess {
                eventRepository.getEvent(eventId).onSuccess {
                    _uiState.value = _uiState.value.copy(event = it)
                }
            }.onFailure {
                _uiState.value = _uiState.value.copy(error = it.message)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

class EventDetailViewModelFactory(
    private val eventRepository: EventRepository,
    private val attendanceRepository: AttendanceRepository,
    private val eventId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EventDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EventDetailViewModel(eventRepository, attendanceRepository, eventId) as T
        }
        throw IllegalArgumentException("ViewModel desconhecido")
    }
}
