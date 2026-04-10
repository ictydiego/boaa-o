package br.unasp.boacao.presentation.volunteer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.unasp.boacao.data.repository.VolunteerRepository
import br.unasp.boacao.domain.model.Donation
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class DeliveryFilter { ALL, TODAY, WEEK, MONTH, PERIOD }

data class VolunteerHistoryUiState(
    val deliveries: List<Donation> = emptyList(),
    val filter: DeliveryFilter = DeliveryFilter.ALL,
    val periodStart: String = "",
    val periodEnd: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

class VolunteerHistoryViewModel(
    private val volunteerRepository: VolunteerRepository,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(VolunteerHistoryUiState())
    val uiState = _uiState.asStateFlow()
    private val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    init {
        loadHistory()
    }

    fun loadHistory() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            volunteerRepository.getDeliveryHistory(userId)
                .onSuccess { list ->
                    _uiState.value = _uiState.value.copy(isLoading = false, deliveries = list.sortedByDescending { it.deliveredAt })
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
        }
    }

    fun setFilter(f: DeliveryFilter) { _uiState.value = _uiState.value.copy(filter = f) }
    fun setPeriod(start: String, end: String) {
        _uiState.value = _uiState.value.copy(filter = DeliveryFilter.PERIOD, periodStart = start, periodEnd = end)
    }

    fun filteredDeliveries(): List<Donation> {
        val s = _uiState.value
        val all = s.deliveries
        val cal = Calendar.getInstance()
        return when (s.filter) {
            DeliveryFilter.ALL -> all
            DeliveryFilter.TODAY -> {
                val today = fmt.format(Date())
                all.filter { it.deliveredAt == today }
            }
            DeliveryFilter.WEEK -> {
                cal.add(Calendar.DAY_OF_YEAR, -7)
                val weekAgo = cal.time
                all.filter { d -> fmt.parseOrNull(d.deliveredAt)?.after(weekAgo) == true }
            }
            DeliveryFilter.MONTH -> {
                cal.add(Calendar.MONTH, -1)
                val monthAgo = cal.time
                all.filter { d -> fmt.parseOrNull(d.deliveredAt)?.after(monthAgo) == true }
            }
            DeliveryFilter.PERIOD -> {
                val start = fmt.parseOrNull(s.periodStart)
                val end = fmt.parseOrNull(s.periodEnd)
                all.filter { d ->
                    val date = fmt.parseOrNull(d.deliveredAt) ?: return@filter false
                    (start == null || !date.before(start)) && (end == null || !date.after(end))
                }
            }
        }
    }

    val totalPointsEarned get() = _uiState.value.deliveries.size * 10

    private fun SimpleDateFormat.parseOrNull(s: String): Date? = try { if (s.isBlank()) null else parse(s) } catch (e: Exception) { null }
}

class VolunteerHistoryViewModelFactory(
    private val volunteerRepository: VolunteerRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return VolunteerHistoryViewModel(volunteerRepository) as T
    }
}
