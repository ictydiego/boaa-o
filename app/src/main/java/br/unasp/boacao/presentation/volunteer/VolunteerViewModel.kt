package br.unasp.boacao.presentation.volunteer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.unasp.boacao.data.repository.PointsRepository
import br.unasp.boacao.data.repository.VolunteerRepository
import br.unasp.boacao.domain.model.Donation
import br.unasp.boacao.domain.model.UserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class NgoInfo(val id: String = "", val name: String = "", val address: String = "")

data class VolunteerUiState(
    val availableDonations: List<Donation> = emptyList(),
    val myDeliveries: List<Donation> = emptyList(),
    val volunteerName: String = "Voluntário",
    val ngos: List<NgoInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class VolunteerViewModel(
    private val repository: VolunteerRepository,
    private val pointsRepository: PointsRepository,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(VolunteerUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadVolunteerName()
        loadData()
        loadNgos()
    }

    private fun loadVolunteerName() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val doc = FirebaseFirestore.getInstance().collection("users").document(userId).get().await()
                _uiState.value = _uiState.value.copy(volunteerName = doc.getString("name") ?: "Voluntário")
            } catch (_: Exception) {}
        }
    }

    fun loadNgos() {
        viewModelScope.launch {
            try {
                val snapshot = FirebaseFirestore.getInstance()
                    .collection("users")
                    .whereEqualTo("role", UserRole.BENEFICIARY.name)
                    .get().await()
                val list = snapshot.documents.map { doc ->
                    NgoInfo(
                        id = doc.id,
                        name = doc.getString("name") ?: "ONG",
                        address = doc.getString("address") ?: ""
                    )
                }
                _uiState.value = _uiState.value.copy(ngos = list)
            } catch (_: Exception) {}
        }
    }

    fun loadData() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val availableResult = repository.getAvailableDonations()
            val deliveriesResult = repository.getMyDeliveries(userId)
            if (availableResult.isSuccess && deliveriesResult.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    availableDonations = availableResult.getOrDefault(emptyList()),
                    myDeliveries = deliveriesResult.getOrDefault(emptyList())
                )
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Erro ao carregar dados")
            }
        }
    }

    fun claimDonation(donation: Donation) {
        val userId = auth.currentUser?.uid ?: return
        val userName = _uiState.value.volunteerName
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.claimDonation(donation.id, userId, userName)
                .onSuccess { loadData() }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.message) }
        }
    }

    fun confirmPickup(donationId: String, pin: String, onComplete: (Boolean, String?) -> Unit) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = repository.confirmPickup(donationId, pin)
            if (result.isSuccess) {
                pointsRepository.addPoints(userId, 10, "Coleta confirmada! +10 pontos")
                loadData()
                onComplete(true, null)
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false)
                onComplete(false, result.exceptionOrNull()?.message)
            }
        }
    }

    fun assignNgo(donationId: String, ngo: NgoInfo, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            repository.assignNgo(donationId, ngo.id, ngo.name)
                .onSuccess { onComplete(true) }
                .onFailure { onComplete(false) }
        }
    }
}

class VolunteerViewModelFactory(
    private val repository: VolunteerRepository,
    private val pointsRepository: PointsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return VolunteerViewModel(repository, pointsRepository) as T
    }
}
