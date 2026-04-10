package br.unasp.boacao.presentation.volunteer

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import br.unasp.boacao.BoaAcaoApplication
import br.unasp.boacao.domain.model.Donation
import br.unasp.boacao.util.LocationUtils
import br.unasp.boacao.util.MarkerUtils
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

/**
 * Tela do mapa. Pode ser usada em modo standalone (navController.popBackStack) ou
 * embutida num Dashboard (onSwitchToList callback para trocar de aba).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun VolunteerMapScreen(
    navController: NavController,
    onSwitchToList: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val application = context.applicationContext as BoaAcaoApplication
    val viewModel: VolunteerMapViewModel = viewModel(
        factory = VolunteerMapViewModelFactory(application.volunteerRepository)
    )
    val state by viewModel.uiState.collectAsState()
    val warmColor = Color(0xFFF06A38)

    val locationPermission = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    var selectedDonation by remember { mutableStateOf<MapDonation?>(null) }
    var selectedNgo by remember { mutableStateOf<NgoLocation?>(null) }

    // Markers must be created AFTER Maps SDK is initialized (inside GoogleMap scope via MapEffect)
    var donorMarkerIcon by remember { mutableStateOf<com.google.android.gms.maps.model.BitmapDescriptor?>(null) }
    var ngoMarkerIcon by remember { mutableStateOf<com.google.android.gms.maps.model.BitmapDescriptor?>(null) }

    LaunchedEffect(locationPermission.status.isGranted) {
        if (locationPermission.status.isGranted) {
            viewModel.loadMapData(context)
        } else {
            locationPermission.launchPermissionRequest()
        }
    }

    val defaultLocation = LatLng(-15.793889, -47.882778)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(state.userLocation ?: defaultLocation, 13f)
    }

    LaunchedEffect(state.userLocation) {
        state.userLocation?.let { loc ->
            cameraPositionState.position = CameraPosition.fromLatLngZoom(loc, 13f)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        if (!locationPermission.status.isGranted) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.LocationOff, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(56.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("Permissão de localização necessária.", color = Color.Gray, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { locationPermission.launchPermissionRequest() }, colors = ButtonDefaults.buttonColors(containerColor = warmColor)) {
                    Text("Conceder Permissão")
                }
            }
            return@Box
        }

        if (state.isLoading) {
            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = warmColor)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Carregando doações e ONGs...", color = Color.Gray, fontSize = 13.sp)
            }
            return@Box
        }

        val visibleDonors = viewModel.visibleDonorPins()
        val visibleNgos = viewModel.visibleNgoPins()

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = true),
            uiSettings = MapUiSettings(myLocationButtonEnabled = true)
        ) {
            // Initialize custom markers after Maps SDK is ready
            MapEffect(Unit) { _ ->
                if (donorMarkerIcon == null) donorMarkerIcon = MarkerUtils.createDonorMarker()
                if (ngoMarkerIcon == null) ngoMarkerIcon = MarkerUtils.createNgoMarker()
            }

            // Pins de Doadores — ícone de casinha
            visibleDonors.forEach { mapDonation ->
                Marker(
                    state = MarkerState(position = mapDonation.latLng),
                    title = mapDonation.donation.title,
                    snippet = "Doador: ${mapDonation.donation.donorName}",
                    icon = donorMarkerIcon,
                    onClick = {
                        selectedDonation = mapDonation
                        selectedNgo = null
                        false
                    }
                )
            }

            // Pins de ONGs — ícone de prédio
            visibleNgos.forEach { ngo ->
                Marker(
                    state = MarkerState(position = ngo.latLng),
                    title = ngo.name,
                    snippet = "ONG / Beneficiário",
                    icon = ngoMarkerIcon,
                    onClick = {
                        selectedNgo = ngo
                        selectedDonation = null
                        false
                    }
                )
            }
        }

        // Top control panel
        Card(
            modifier = Modifier.align(Alignment.TopCenter).padding(10.dp).fillMaxWidth(0.95f),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.97f)),
            elevation = CardDefaults.cardElevation(6.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Filter chips
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MapFilterChip(
                        label = "Exibir Todos",
                        selected = state.mapFilter == MapFilter.ALL,
                        color = warmColor,
                        onClick = { viewModel.setMapFilter(MapFilter.ALL) }
                    )
                    MapFilterChip(
                        label = "🏠 Doadores (${visibleDonors.size})",
                        selected = state.mapFilter == MapFilter.DONORS_ONLY,
                        color = Color(0xFF4CAF50),
                        onClick = { viewModel.setMapFilter(MapFilter.DONORS_ONLY) }
                    )
                    MapFilterChip(
                        label = "🏢 ONGs (${visibleNgos.size})",
                        selected = state.mapFilter == MapFilter.NGOS_ONLY,
                        color = Color(0xFF1976D2),
                        onClick = { viewModel.setMapFilter(MapFilter.NGOS_ONLY) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Radius slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Raio: ${state.filterRadiusKm.toInt()} km", fontWeight = FontWeight.Bold, color = warmColor, fontSize = 13.sp)
                    if (onSwitchToList != null) {
                        TextButton(onClick = onSwitchToList) {
                            Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ver Lista", fontSize = 12.sp)
                        }
                    }
                }
                Slider(
                    value = state.filterRadiusKm,
                    onValueChange = { viewModel.setFilterRadius(it) },
                    valueRange = 1f..50f,
                    steps = 48,
                    colors = SliderDefaults.colors(thumbColor = warmColor, activeTrackColor = warmColor),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Bottom sheet: Donation detail
        selectedDonation?.let { mapDonation ->
            DonationDetailSheet(
                donation = mapDonation.donation,
                distanceKm = state.userLocation?.let { loc ->
                    LocationUtils.distanceBetweenKm(loc.latitude, loc.longitude, mapDonation.latLng.latitude, mapDonation.latLng.longitude)
                },
                onDismiss = { selectedDonation = null },
                onNavigateToList = {
                    selectedDonation = null
                    if (onSwitchToList != null) onSwitchToList()
                    else navController.popBackStack()
                }
            )
        }

        // Bottom sheet: NGO detail
        selectedNgo?.let { ngo ->
            NgoDetailSheet(
                ngo = ngo,
                distanceKm = state.userLocation?.let { loc ->
                    LocationUtils.distanceBetweenKm(loc.latitude, loc.longitude, ngo.latLng.latitude, ngo.latLng.longitude)
                },
                onDismiss = { selectedNgo = null }
            )
        }
    }
}

@Composable
private fun MapFilterChip(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color.copy(alpha = 0.15f),
            selectedLabelColor = color
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DonationDetailSheet(
    donation: Donation,
    distanceKm: Double?,
    onDismiss: () -> Unit,
    onNavigateToList: () -> Unit
) {
    val warmColor = Color(0xFFF06A38)
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Home, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ponto de Doação", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(donation.title, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(donation.donorName, color = Color.Gray, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(12.dp))
            distanceKm?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NearMe, contentDescription = null, tint = warmColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${String.format("%.1f", it)} km de você", color = warmColor, fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(donation.pickupAddress, color = Color.DarkGray, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text("Validade: ${donation.expiryDate}", fontSize = 13.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onNavigateToList,
                colors = ButtonDefaults.buttonColors(containerColor = warmColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.DirectionsCar, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ir à lista para reservar esta doação")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NgoDetailSheet(
    ngo: NgoLocation,
    distanceKm: Double?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Business, contentDescription = null, tint = Color(0xFF1976D2), modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ponto de Entrega — ONG", color = Color(0xFF1976D2), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(ngo.name, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(12.dp))
            distanceKm?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NearMe, contentDescription = null, tint = Color(0xFF1976D2), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${String.format("%.1f", it)} km de você", color = Color(0xFF1976D2), fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(ngo.address, color = Color.DarkGray, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(20.dp))

            // Navigation button
            Button(
                onClick = {
                    val lat = ngo.latLng.latitude
                    val lng = ngo.latLng.longitude
                    val geoUri = if (lat != 0.0 && lng != 0.0) {
                        Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(ngo.name)})")
                    } else {
                        Uri.parse("geo:0,0?q=${Uri.encode(ngo.address)}")
                    }
                    val intent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                        setPackage("com.google.android.apps.maps")
                    }
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                    } else {
                        context.startActivity(Intent(Intent.ACTION_VIEW, geoUri))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
            ) {
                Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Navegar até a ONG", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF1976D2), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Entregue doações neste local usando o código fornecido pela ONG.", fontSize = 13.sp, color = Color(0xFF1976D2))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
