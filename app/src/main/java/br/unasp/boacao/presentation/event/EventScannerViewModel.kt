package br.unasp.boacao.presentation.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.unasp.boacao.data.repository.AttendanceRepository
import br.unasp.boacao.domain.model.Attendance
import br.unasp.boacao.domain.model.AttendanceStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ScannerStep {
    data object Scanning : ScannerStep()
    data object Loading : ScannerStep()
    data class CheckedIn(val attendance: Attendance) : ScannerStep()
    data class AwaitingCheckout(val ticketCode: String, val volunteerName: String) : ScannerStep()
    data class Done(val message: String) : ScannerStep()
    data class Error(val message: String) : ScannerStep()
}

class EventScannerViewModel(
    private val attendanceRepository: AttendanceRepository,
    val eventId: String
) : ViewModel() {

    private val _step = MutableStateFlow<ScannerStep>(ScannerStep.Scanning)
    val step = _step.asStateFlow()

    fun onScanned(ticket: String) {
        if (_step.value !is ScannerStep.Scanning) return
        _step.value = ScannerStep.Loading
        viewModelScope.launch {
            attendanceRepository.checkIn(eventId, ticket).fold(
                { att ->
                    _step.value = ScannerStep.CheckedIn(att)
                },
                { err ->
                    val msg = err.message ?: "Erro"
                    if (msg.contains("Status inválido")) {
                        // already checked in or beyond — try to surface checkout option
                        viewModelScope.launch {
                            attendanceRepository.checkIn(eventId, ticket) // no-op; we read state
                            // fetch attendance via the ticket to know its current state
                        }
                        // simpler: branch on message and let user request checkout
                        _step.value = ScannerStep.AwaitingCheckout(ticket, "Voluntário")
                    } else {
                        _step.value = ScannerStep.Error(msg)
                    }
                }
            )
        }
    }

    fun confirmCheckout(ticket: String, note: String) {
        _step.value = ScannerStep.Loading
        viewModelScope.launch {
            attendanceRepository.checkOut(eventId, ticket, note).fold(
                { _step.value = ScannerStep.Done("Check-out registrado. Certificado liberado.") },
                { _step.value = ScannerStep.Error(it.message ?: "Erro no check-out") }
            )
        }
    }

    fun reset() {
        _step.value = ScannerStep.Scanning
    }
}

class EventScannerViewModelFactory(
    private val attendanceRepository: AttendanceRepository,
    private val eventId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EventScannerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EventScannerViewModel(attendanceRepository, eventId) as T
        }
        throw IllegalArgumentException("ViewModel desconhecido")
    }
}
