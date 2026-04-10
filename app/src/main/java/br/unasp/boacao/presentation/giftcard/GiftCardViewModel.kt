package br.unasp.boacao.presentation.giftcard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.unasp.boacao.data.repository.GiftCardRepository
import br.unasp.boacao.data.repository.PointsRepository
import br.unasp.boacao.domain.GiftCard
import br.unasp.boacao.domain.RedeemedGiftCard
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GiftCardUiState(
    val availableCards: List<GiftCard> = emptyList(),
    val redeemedCards: List<RedeemedGiftCard> = emptyList(),
    val currentPoints: Int = 0,
    val isLoading: Boolean = false,
    val redeemedCard: RedeemedGiftCard? = null,
    val error: String? = null
)

class GiftCardViewModel(
    private val giftCardRepository: GiftCardRepository,
    private val pointsRepository: PointsRepository,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(GiftCardUiState())
    val uiState = _uiState.asStateFlow()

    init { loadData() }

    fun loadData() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val cards = giftCardRepository.getAvailableGiftCards()
            val points = pointsRepository.getPoints(userId).getOrDefault(0)
            val redeemed = giftCardRepository.getRedeemedCards(userId).getOrDefault(emptyList())
            _uiState.value = GiftCardUiState(
                availableCards = cards,
                redeemedCards = redeemed,
                currentPoints = points,
                isLoading = false
            )
        }
    }

    fun redeemGiftCard(giftCard: GiftCard) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            giftCardRepository.redeemGiftCard(userId, giftCard, _uiState.value.currentPoints)
                .onSuccess { redeemed ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        redeemedCard = redeemed,
                        currentPoints = _uiState.value.currentPoints - giftCard.requiredPoints
                    )
                    loadData()
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
        }
    }

    fun clearRedeemedCard() {
        _uiState.value = _uiState.value.copy(redeemedCard = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

class GiftCardViewModelFactory(
    private val giftCardRepository: GiftCardRepository,
    private val pointsRepository: PointsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return GiftCardViewModel(giftCardRepository, pointsRepository) as T
    }
}
