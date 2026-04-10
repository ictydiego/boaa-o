package br.unasp.boacao.presentation.points

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.unasp.boacao.data.repository.PointsRepository
import br.unasp.boacao.domain.PointsTransaction
import br.unasp.boacao.domain.GIFT_CARD_CATALOG
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PointsUiState(
    val totalPoints: Int = 0,
    val history: List<PointsTransaction> = emptyList(),
    val nextGiftCardName: String = "",
    val nextGiftCardPoints: Int = 0,
    val isLoading: Boolean = false
)

class PointsViewModel(
    private val pointsRepository: PointsRepository,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(PointsUiState())
    val uiState = _uiState.asStateFlow()

    init { loadData() }

    fun loadData() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val pointsResult = pointsRepository.getPoints(userId)
            val historyResult = pointsRepository.getPointsHistory(userId)
            val currentPoints = pointsResult.getOrDefault(0)
            val nextCard = GIFT_CARD_CATALOG
                .filter { it.requiredPoints > currentPoints }
                .minByOrNull { it.requiredPoints }
            _uiState.value = PointsUiState(
                totalPoints = currentPoints,
                history = historyResult.getOrDefault(emptyList()),
                nextGiftCardName = nextCard?.title ?: "Todos os gift cards desbloqueados!",
                nextGiftCardPoints = nextCard?.requiredPoints ?: currentPoints,
                isLoading = false
            )
        }
    }
}

class PointsViewModelFactory(private val pointsRepository: PointsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return PointsViewModel(pointsRepository) as T
    }
}
