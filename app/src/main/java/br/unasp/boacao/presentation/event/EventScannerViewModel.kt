package br.unasp.boacao.presentation.event

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.unasp.boacao.data.repository.AttendanceRepository
import br.unasp.boacao.domain.model.Attendance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ScannerMode { CHECKIN, CHECKOUT }

sealed class ScannerStep {
    data object Scanning : ScannerStep()
    data object Loading : ScannerStep()
    data class CheckedInSuccess(val attendance: Attendance) : ScannerStep() // dialog "Próximo / Fechar"
    data class CollectFeedback(val ticket: String, val volunteerName: String) : ScannerStep()
    data class Done(val message: String) : ScannerStep()
    data class Error(val message: String) : ScannerStep()
}

class EventScannerViewModel(
    private val attendanceRepository: AttendanceRepository,
    val eventId: String,
    val mode: ScannerMode
) : ViewModel() {

    private val _step = MutableStateFlow<ScannerStep>(ScannerStep.Scanning)
    val step = _step.asStateFlow()

    fun onScanned(ticket: String) {
        if (_step.value !is ScannerStep.Scanning) return
        _step.value = ScannerStep.Loading
        viewModelScope.launch {
            when (mode) {
                ScannerMode.CHECKIN -> doCheckIn(ticket)
                ScannerMode.CHECKOUT -> doPrepareCheckout(ticket)
            }
        }
    }

    private suspend fun doCheckIn(ticket: String) {
        attendanceRepository.checkIn(eventId, ticket).fold(
            { att -> _step.value = ScannerStep.CheckedInSuccess(att) },
            { err -> _step.value = ScannerStep.Error(err.message ?: "Erro no check-in") }
        )
    }

    private suspend fun doPrepareCheckout(ticket: String) {
        // Look up attendance to fetch volunteer name + validate it is CHECKED_IN
        // We use the ticket to find via observe? Simpler: just go to feedback collection.
        // Repo will validate state on confirm.
        _step.value = ScannerStep.CollectFeedback(ticket, "Voluntário")
    }

    fun confirmCheckOut(ticket: String, note: String, rating: Int) {
        _step.value = ScannerStep.Loading
        viewModelScope.launch {
            attendanceRepository.checkOut(eventId, ticket, note, rating).fold(
                { att ->
                    val pts = (rating.coerceIn(1, 5)) * 10
                    _step.value = ScannerStep.Done("Check-out registrado. ${att.volunteerName} recebeu $pts pontos.")
                },
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
    private val eventId: String,
    private val mode: ScannerMode
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EventScannerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EventScannerViewModel(attendanceRepository, eventId, mode) as T
        }
        throw IllegalArgumentException("ViewModel desconhecido")
    }
}
