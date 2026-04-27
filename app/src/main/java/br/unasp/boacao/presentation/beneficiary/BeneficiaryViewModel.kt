package br.unasp.boacao.presentation.beneficiary

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.unasp.boacao.data.repository.BeneficiaryRepository
import br.unasp.boacao.data.repository.PointsRepository
import br.unasp.boacao.domain.model.Donation
import br.unasp.boacao.util.NetworkUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class BeneficiaryUiState(
    val incomingDonations: List<Donation> = emptyList(),
    val beneficiaryName: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

class BeneficiaryViewModel(
    private val repository: BeneficiaryRepository,
    private val pointsRepository: PointsRepository,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(BeneficiaryUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadBeneficiaryName()
        startRealtimeListener()
    }

    private fun loadBeneficiaryName() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val doc = FirebaseFirestore.getInstance().collection("users").document(userId).get().await()
                _uiState.value = _uiState.value.copy(beneficiaryName = doc.getString("name") ?: "")
            } catch (_: Exception) {}
        }
    }

    /**
     * Real-time listener: sees new donations assigned to this NGO immediately.
     */
    private fun startRealtimeListener() {
        val beneficiaryId = auth.currentUser?.uid ?: return
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            repository.observeIncomingDonations(beneficiaryId).collect { donations ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    incomingDonations = donations
                )
            }
        }
    }

    fun loadData() {
        val beneficiaryId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getIncomingDonations(beneficiaryId)
                .onSuccess { list -> _uiState.value = _uiState.value.copy(isLoading = false, incomingDonations = list) }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false) }
        }
    }

    fun lookupDonationByDeliveryCode(code: String, onResult: (Donation?, String?) -> Unit) {
        viewModelScope.launch {
            repository.getDonationByDeliveryCode(code)
                .onSuccess { donation ->
                    if (donation != null) onResult(donation, null)
                    else onResult(null, "Nenhuma doação em trânsito com este código.")
                }
                .onFailure { e -> onResult(null, e.message) }
        }
    }

    /**
     * Confirms delivery with internet check + transaction-based validation.
     */
    fun confirmDelivery(
        context: Context,
        donation: Donation,
        pin: String,
        proofPhotoBase64: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        if (!NetworkUtils.isOnline(context)) {
            onComplete(false, "Sem conexão com a internet. Verifique sua rede e tente novamente.")
            return
        }
        val beneficiaryId = auth.currentUser?.uid ?: ""
        val beneficiaryName = _uiState.value.beneficiaryName
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = repository.confirmDelivery(donation.id, pin, proofPhotoBase64, beneficiaryId, beneficiaryName)
            if (result.isSuccess) {
                if (donation.donorId.isNotBlank()) {
                    pointsRepository.addPoints(donation.donorId, 5, "Sua doação foi entregue! +5 pontos")
                }
                if (!donation.volunteerId.isNullOrBlank()) {
                    pointsRepository.addPoints(donation.volunteerId, 10, "Entrega concluída! +10 pontos")
                }
                _uiState.value = _uiState.value.copy(isLoading = false)
                onComplete(true, null)
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false)
                onComplete(false, result.exceptionOrNull()?.message)
            }
        }
    }
}

class BeneficiaryViewModelFactory(
    private val repository: BeneficiaryRepository,
    private val pointsRepository: PointsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return BeneficiaryViewModel(repository, pointsRepository) as T
    }
}
