package br.unasp.boacao.presentation.donor

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.unasp.boacao.data.repository.DonorRepository
import br.unasp.boacao.domain.model.Donation
import br.unasp.boacao.domain.model.DonationItem
import br.unasp.boacao.domain.model.DonationStatus
import br.unasp.boacao.util.GeocodeUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class DonorStatusFilter { ALL, AVAILABLE, IN_PROGRESS, DELIVERED }
enum class DonorTimeFilter { ALL, TODAY, WEEK, MONTH, PERIOD }

data class DonorUiState(
    val donations: List<Donation> = emptyList(),
    val donorAddress: String = "",
    val donorName: String = "",
    val donorPoints: Int = 0,
    val donorDonationCount: Int = 0,
    val statusFilter: DonorStatusFilter = DonorStatusFilter.ALL,
    val timeFilter: DonorTimeFilter = DonorTimeFilter.ALL,
    val periodStart: String = "",
    val periodEnd: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

class DonorViewModel(
    private val repository: DonorRepository,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(DonorUiState())
    val uiState = _uiState.asStateFlow()
    private var donationsListener: ListenerRegistration? = null

    init {
        loadDonorProfile()
        startDonationsListener()
    }

    private fun loadDonorProfile() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val doc = FirebaseFirestore.getInstance().collection("users").document(userId).get().await()
                _uiState.value = _uiState.value.copy(
                    donorAddress = doc.getString("address") ?: "",
                    donorName = doc.getString("name") ?: "Empresa Parceira",
                    donorPoints = (doc.getLong("points") ?: 0L).toInt(),
                    donorDonationCount = (doc.getLong("donationCount") ?: 0L).toInt()
                )
            } catch (_: Exception) {}
        }
    }

    private fun startDonationsListener() {
        val userId = auth.currentUser?.uid ?: return
        _uiState.value = _uiState.value.copy(isLoading = true)
        donationsListener = FirebaseFirestore.getInstance()
            .collection("donations")
            .whereEqualTo("donorId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { it.toObject(Donation::class.java) }
                    _uiState.value = _uiState.value.copy(isLoading = false, donations = list)
                }
            }
    }

    override fun onCleared() {
        donationsListener?.remove()
        super.onCleared()
    }

    fun createDonation(context: Context, items: List<DonationItem>, title: String, description: String, expiry: String, onComplete: () -> Unit) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val address = _uiState.value.donorAddress
            val latLng = GeocodeUtils.geocodeAddress(context, address)
            val resolvedTitle = title.ifBlank {
                if (items.isNotEmpty()) items.joinToString(", ") { "${it.name} (${it.quantity})" }
                else "Doação"
            }
            val newDonation = Donation(
                donorId = userId,
                donorName = _uiState.value.donorName,
                title = resolvedTitle,
                description = description,
                expiryDate = expiry,
                pickupAddress = address,
                items = items,
                latitude = latLng?.latitude ?: 0.0,
                longitude = latLng?.longitude ?: 0.0
            )
            repository.createDonation(newDonation)
                .onSuccess {
                    loadDonorProfile()
                    onComplete()
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
        }
    }

    fun cancelDonation(donationId: String) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            repository.cancelDonation(donationId, userId)
                .onSuccess { loadDonorProfile() }
        }
    }
    fun setStatusFilter(f: DonorStatusFilter) { _uiState.value = _uiState.value.copy(statusFilter = f) }
    fun setTimeFilter(f: DonorTimeFilter) { _uiState.value = _uiState.value.copy(timeFilter = f) }
    fun setPeriod(start: String, end: String) {
        _uiState.value = _uiState.value.copy(timeFilter = DonorTimeFilter.PERIOD, periodStart = start, periodEnd = end)
    }

    fun filteredDonations(): List<Donation> {
        val s = _uiState.value
        val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val cal = Calendar.getInstance()

        var list = s.donations

        list = when (s.statusFilter) {
            DonorStatusFilter.ALL -> list
            DonorStatusFilter.AVAILABLE -> list.filter { it.status == DonationStatus.AVAILABLE }
            DonorStatusFilter.IN_PROGRESS -> list.filter { it.status == DonationStatus.CLAIMED || it.status == DonationStatus.IN_TRANSIT }
            DonorStatusFilter.DELIVERED -> list.filter { it.status == DonationStatus.DELIVERED }
        }

        list = when (s.timeFilter) {
            DonorTimeFilter.ALL -> list
            DonorTimeFilter.TODAY -> {
                val today = fmt.format(Date())
                list.filter { it.createdAt == today }
            }
            DonorTimeFilter.WEEK -> {
                cal.add(Calendar.DAY_OF_YEAR, -7)
                val weekAgo = cal.time
                list.filter { d -> fmt.parseOrNull(d.createdAt)?.after(weekAgo) == true }
            }
            DonorTimeFilter.MONTH -> {
                cal.add(Calendar.MONTH, -1)
                val monthAgo = cal.time
                list.filter { d -> fmt.parseOrNull(d.createdAt)?.after(monthAgo) == true }
            }
            DonorTimeFilter.PERIOD -> {
                val start = fmt.parseOrNull(s.periodStart)
                val end = fmt.parseOrNull(s.periodEnd)
                list.filter { d ->
                    val date = fmt.parseOrNull(d.createdAt) ?: return@filter false
                    (start == null || !date.before(start)) && (end == null || !date.after(end))
                }
            }
        }
        return list
    }

    private fun SimpleDateFormat.parseOrNull(s: String): Date? = try { if (s.isBlank()) null else parse(s) } catch (e: Exception) { null }
}

class DonorViewModelFactory(private val repository: DonorRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return DonorViewModel(repository) as T
    }
}
