package br.unasp.boacao.presentation.volunteer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.unasp.boacao.data.repository.VolunteerRepository
import br.unasp.boacao.domain.model.Donation
import br.unasp.boacao.domain.model.UserRole
import br.unasp.boacao.util.GeocodeUtils
import br.unasp.boacao.util.LocationUtils
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

enum class MapFilter { ALL, DONORS_ONLY, NGOS_ONLY }

data class MapDonation(val donation: Donation, val latLng: LatLng)

data class NgoLocation(val userId: String, val name: String, val address: String, val latLng: LatLng)

data class VolunteerMapUiState(
    val donorPins: List<MapDonation> = emptyList(),
    val ngoPins: List<NgoLocation> = emptyList(),
    val userLocation: LatLng? = null,
    val filterRadiusKm: Float = 20f,
    val mapFilter: MapFilter = MapFilter.ALL,
    val isLoading: Boolean = false,
    val error: String? = null
)

class VolunteerMapViewModel(
    private val volunteerRepository: VolunteerRepository,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(VolunteerMapUiState())
    val uiState = _uiState.asStateFlow()

    fun loadMapData(context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                coroutineScope {
                    val userLocationDeferred = async { LocationUtils.getCurrentLocation(context) }
                    val donationsDeferred = async { volunteerRepository.getAvailableDonations() }
                    val ngosDeferred = async { loadNgosFromFirestore(context) }

                    val userLocation = userLocationDeferred.await()
                    val donations = donationsDeferred.await().getOrDefault(emptyList())
                    val ngos = ngosDeferred.await()

                    val donorPins = mutableListOf<MapDonation>()
                    for (donation in donations) {
                        val latLng = if (donation.latitude != 0.0 && donation.longitude != 0.0) {
                            LatLng(donation.latitude, donation.longitude)
                        } else {
                            GeocodeUtils.geocodeAddress(context, donation.pickupAddress)
                        }
                        if (latLng != null) donorPins.add(MapDonation(donation, latLng))
                    }

                    _uiState.value = _uiState.value.copy(
                        donorPins = donorPins,
                        ngoPins = ngos,
                        userLocation = userLocation,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Erro ao carregar mapa: ${e.message}")
            }
        }
    }

    private suspend fun loadNgosFromFirestore(context: Context): List<NgoLocation> {
        return try {
            val snapshot = FirebaseFirestore.getInstance()
                .collection("users")
                .whereEqualTo("role", UserRole.BENEFICIARY.name)
                .get().await()
            val result = mutableListOf<NgoLocation>()
            for (doc in snapshot.documents) {
                val address = doc.getString("address") ?: continue
                if (address.isBlank()) continue
                val name = doc.getString("name") ?: "ONG"
                // Try stored coordinates first
                val storedLat = doc.getDouble("latitude") ?: 0.0
                val storedLon = doc.getDouble("longitude") ?: 0.0
                val latLng = if (storedLat != 0.0 && storedLon != 0.0) {
                    LatLng(storedLat, storedLon)
                } else {
                    GeocodeUtils.geocodeAddress(context, address)
                }
                if (latLng != null) result.add(NgoLocation(doc.id, name, address, latLng))
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setFilterRadius(km: Float) {
        _uiState.value = _uiState.value.copy(filterRadiusKm = km)
    }

    fun setMapFilter(filter: MapFilter) {
        _uiState.value = _uiState.value.copy(mapFilter = filter)
    }

    fun visibleDonorPins(): List<MapDonation> {
        val state = _uiState.value
        if (state.mapFilter == MapFilter.NGOS_ONLY) return emptyList()
        val userLoc = state.userLocation ?: return state.donorPins
        return state.donorPins.filter { pin ->
            LocationUtils.distanceBetweenKm(userLoc.latitude, userLoc.longitude, pin.latLng.latitude, pin.latLng.longitude) <= state.filterRadiusKm
        }
    }

    fun visibleNgoPins(): List<NgoLocation> {
        val state = _uiState.value
        if (state.mapFilter == MapFilter.DONORS_ONLY) return emptyList()
        val userLoc = state.userLocation ?: return state.ngoPins
        return state.ngoPins.filter { ngo ->
            LocationUtils.distanceBetweenKm(userLoc.latitude, userLoc.longitude, ngo.latLng.latitude, ngo.latLng.longitude) <= state.filterRadiusKm
        }
    }
}

class VolunteerMapViewModelFactory(
    private val volunteerRepository: VolunteerRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return VolunteerMapViewModel(volunteerRepository) as T
    }
}
