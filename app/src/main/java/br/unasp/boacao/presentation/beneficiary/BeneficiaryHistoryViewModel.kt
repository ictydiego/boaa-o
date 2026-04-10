package br.unasp.boacao.presentation.beneficiary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.unasp.boacao.data.repository.BeneficiaryRepository
import br.unasp.boacao.domain.model.Donation
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class ReceivedFilter { ALL, TODAY, WEEK, MONTH, PERIOD }

data class BeneficiaryHistoryUiState(
    val deliveries: List<Donation> = emptyList(),
    val filter: ReceivedFilter = ReceivedFilter.ALL,
    val periodStart: String = "",
    val periodEnd: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

class BeneficiaryHistoryViewModel(
    private val repository: BeneficiaryRepository,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(BeneficiaryHistoryUiState())
    val uiState = _uiState.asStateFlow()
    private val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    init { loadHistory() }

    fun loadHistory() {
        val id = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.getDeliveryHistory(id)
                .onSuccess { list -> _uiState.value = _uiState.value.copy(isLoading = false, deliveries = list) }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false) }
        }
    }

    fun setFilter(f: ReceivedFilter) { _uiState.value = _uiState.value.copy(filter = f) }
    fun setPeriod(start: String, end: String) {
        _uiState.value = _uiState.value.copy(filter = ReceivedFilter.PERIOD, periodStart = start, periodEnd = end)
    }

    fun filteredDeliveries(): List<Donation> {
        val s = _uiState.value
        val all = s.deliveries
        val cal = Calendar.getInstance()
        return when (s.filter) {
            ReceivedFilter.ALL -> all
            ReceivedFilter.TODAY -> {
                val today = fmt.format(Date())
                all.filter { it.deliveredAt == today }
            }
            ReceivedFilter.WEEK -> {
                cal.add(Calendar.DAY_OF_YEAR, -7)
                val weekAgo = cal.time
                all.filter { d -> fmt.parseOrNull(d.deliveredAt)?.after(weekAgo) == true }
            }
            ReceivedFilter.MONTH -> {
                cal.add(Calendar.MONTH, -1)
                val monthAgo = cal.time
                all.filter { d -> fmt.parseOrNull(d.deliveredAt)?.after(monthAgo) == true }
            }
            ReceivedFilter.PERIOD -> {
                val start = fmt.parseOrNull(s.periodStart)
                val end = fmt.parseOrNull(s.periodEnd)
                all.filter { d ->
                    val date = fmt.parseOrNull(d.deliveredAt) ?: return@filter false
                    (start == null || !date.before(start)) && (end == null || !date.after(end))
                }
            }
        }
    }

    private fun SimpleDateFormat.parseOrNull(s: String): Date? = try { if (s.isBlank()) null else parse(s) } catch (e: Exception) { null }
}

class BeneficiaryHistoryViewModelFactory(
    private val repository: BeneficiaryRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return BeneficiaryHistoryViewModel(repository) as T
    }
}
