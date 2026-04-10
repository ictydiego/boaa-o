package br.unasp.boacao.presentation.ranking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.unasp.boacao.data.repository.RankingRepository
import br.unasp.boacao.domain.model.UserProfile
import br.unasp.boacao.domain.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RankingUiState(
    val topUsers: List<UserProfile> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class RankingViewModel(
    private val rankingRepository: RankingRepository,
    private val rankingFor: UserRole
) : ViewModel() {

    private val _uiState = MutableStateFlow(RankingUiState())
    val uiState = _uiState.asStateFlow()

    init { loadRanking() }

    fun loadRanking() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            rankingRepository.getTopUsers(rankingFor, 10)
                .onSuccess { list -> _uiState.value = RankingUiState(topUsers = list, isLoading = false) }
                .onFailure { e -> _uiState.value = RankingUiState(isLoading = false, error = e.message) }
        }
    }
}

class RankingViewModelFactory(
    private val rankingRepository: RankingRepository,
    private val rankingFor: UserRole
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return RankingViewModel(rankingRepository, rankingFor) as T
    }
}
