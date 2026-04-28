package br.unasp.boacao.presentation.volunteer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.unasp.boacao.data.repository.PointsRepository
import br.unasp.boacao.data.repository.VolunteerRepository
import br.unasp.boacao.domain.model.Donation
import br.unasp.boacao.domain.model.UserRole
import br.unasp.boacao.util.NetworkUtils
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
        startRealtimeListeners()
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

    private fun startRealtimeListeners() {
        val userId = auth.currentUser?.uid ?: return
        _uiState.value = _uiState.value.copy(isLoading = true)

        viewModelScope.launch {
            launch {
                repository.observeAvailableDonations().collect { donations ->
                    _uiState.value = _uiState.value.copy(
                        availableDonations = donations,
                        isLoading = false
                    )
                }
            }
            launch {
                repository.observeMyDeliveries(userId).collect { deliveries ->
                    _uiState.value = _uiState.value.copy(myDeliveries = deliveries)
                }
            }
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

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun claimDonation(context: Context, donation: Donation, onResult: (Boolean, String?) -> Unit) {
        if (!NetworkUtils.isOnline(context)) {
            onResult(false, "Sem conexão com a internet.")
            return
        }
        val userId = auth.currentUser?.uid ?: return
        val userName = _uiState.value.volunteerName
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.claimDonation(donation.id, userId, userName)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    onResult(true, null)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                    onResult(false, e.message)
                }
        }
    }

    fun confirmPickup(context: Context, donationId: String, pin: String, onComplete: (Boolean, String?) -> Unit) {
        if (!NetworkUtils.isOnline(context)) {
            onComplete(false, "Sem conexão com a internet.")
            return
        }
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = repository.confirmPickup(donationId, pin)
            if (result.isSuccess) {
                pointsRepository.addPoints(userId, 10, "Coleta confirmada! +10 pontos")
                _uiState.value = _uiState.value.copy(isLoading = false)
                onComplete(true, null)
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false, error = result.exceptionOrNull()?.message)
                onComplete(false, result.exceptionOrNull()?.message)
            }
        }
    }

    fun assignNgo(donationId: String, ngo: NgoInfo, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.assignNgo(donationId, ngo.id, ngo.name)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    onComplete(true)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    onComplete(false)
                }
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
