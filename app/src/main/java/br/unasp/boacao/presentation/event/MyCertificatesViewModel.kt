package br.unasp.boacao.presentation.event

import android.content.Context
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.unasp.boacao.data.repository.AttendanceRepository
import br.unasp.boacao.data.repository.AuthRepository
import br.unasp.boacao.data.repository.EventRepository
import br.unasp.boacao.domain.model.Attendance
import br.unasp.boacao.domain.model.AttendanceStatus
import br.unasp.boacao.domain.model.Event
import br.unasp.boacao.domain.model.UserProfile
import br.unasp.boacao.util.CertificatePdfUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CertificateRow(val attendance: Attendance, val event: Event?)

data class MyCertificatesUiState(
    val isLoading: Boolean = true,
    val rows: List<CertificateRow> = emptyList(),
    val error: String? = null
)

class MyCertificatesViewModel(
    private val attendanceRepository: AttendanceRepository,
    private val eventRepository: EventRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyCertificatesUiState())
    val uiState = _uiState.asStateFlow()

    private val eventCache = mutableMapOf<String, Event>()
    private var volunteer: UserProfile? = null

    init {
        viewModelScope.launch {
            authRepository.getUserProfile().onSuccess { profile ->
                volunteer = profile
                attendanceRepository.observeMyAttendances(profile.id).collect { list ->
                    val checkedOut = list.filter { it.status == AttendanceStatus.CHECKED_OUT }
                    val rows = checkedOut.map { att ->
                        val cached = eventCache[att.eventId]
                        if (cached != null) CertificateRow(att, cached)
                        else {
                            val fetched = eventRepository.getEvent(att.eventId).getOrNull()
                            if (fetched != null) eventCache[att.eventId] = fetched
                            CertificateRow(att, fetched)
                        }
                    }.sortedByDescending { it.attendance.checkOutAt ?: 0L }
                    _uiState.value = MyCertificatesUiState(isLoading = false, rows = rows)
                }
            }.onFailure {
                _uiState.value = MyCertificatesUiState(isLoading = false, error = it.message)
            }
        }
    }

    fun openCertificate(
        context: Context,
        row: CertificateRow,
        onReady: (android.net.Uri) -> Unit,
        onError: (String) -> Unit
    ) {
        val event = row.event
        val volunteerProfile = volunteer
        if (event == null || volunteerProfile == null) {
            onError("Dados do evento indisponíveis")
            return
        }
        viewModelScope.launch {
            try {
                val ngo = authRepository.getUserById(event.ngoId).getOrNull()
                    ?: throw Exception("ONG não encontrada")
                if (ngo.signatureBase64.isBlank()) {
                    throw Exception("A ONG ainda não cadastrou a assinatura digital.")
                }
                val file = withContext(Dispatchers.IO) {
                    CertificatePdfUtil.generate(context, event, ngo, volunteerProfile, row.attendance)
                }
                val uri = FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", file
                )
                onReady(uri)
            } catch (e: Exception) {
                onError(e.message ?: "Erro ao gerar PDF")
            }
        }
    }
}

class MyCertificatesViewModelFactory(
    private val attendanceRepository: AttendanceRepository,
    private val eventRepository: EventRepository,
    private val authRepository: AuthRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MyCertificatesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MyCertificatesViewModel(attendanceRepository, eventRepository, authRepository) as T
        }
        throw IllegalArgumentException("ViewModel desconhecido")
    }
}
