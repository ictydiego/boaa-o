package br.unasp.boacao.presentation.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.unasp.boacao.data.repository.AttendanceRepository
import br.unasp.boacao.data.repository.AuthRepository
import br.unasp.boacao.data.repository.EventRepository
import br.unasp.boacao.domain.model.Attendance
import br.unasp.boacao.domain.model.Event
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TicketRow(val attendance: Attendance, val event: Event?)

data class MyTicketsUiState(
    val isLoading: Boolean = true,
    val rows: List<TicketRow> = emptyList(),
    val error: String? = null
)

class MyTicketsViewModel(
    private val attendanceRepository: AttendanceRepository,
    private val eventRepository: EventRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyTicketsUiState())
    val uiState = _uiState.asStateFlow()

    private val eventCache = mutableMapOf<String, Event>()

    init {
        viewModelScope.launch {
            authRepository.getUserProfile().onSuccess { profile ->
                attendanceRepository.observeMyAttendances(profile.id).collect { list ->
                    val rows = list.map { att ->
                        val cached = eventCache[att.eventId]
                        if (cached != null) {
                            TicketRow(att, cached)
                        } else {
                            val fetched = eventRepository.getEvent(att.eventId).getOrNull()
                            if (fetched != null) eventCache[att.eventId] = fetched
                            TicketRow(att, fetched)
                        }
                    }.sortedByDescending { it.event?.startAt ?: 0L }
                    _uiState.value = MyTicketsUiState(isLoading = false, rows = rows)
                }
            }.onFailure {
                _uiState.value = MyTicketsUiState(isLoading = false, error = it.message)
            }
        }
    }
}

class MyTicketsViewModelFactory(
    private val attendanceRepository: AttendanceRepository,
    private val eventRepository: EventRepository,
    private val authRepository: AuthRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MyTicketsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MyTicketsViewModel(attendanceRepository, eventRepository, authRepository) as T
        }
        throw IllegalArgumentException("ViewModel desconhecido")
    }
}
