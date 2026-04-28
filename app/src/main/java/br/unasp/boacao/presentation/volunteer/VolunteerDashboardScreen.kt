package br.unasp.boacao.presentation.volunteer

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import br.unasp.boacao.BoaAcaoApplication
import br.unasp.boacao.domain.model.Donation
import br.unasp.boacao.domain.model.DonationStatus
import br.unasp.boacao.presentation.components.LocalFilterIconCoordinator
import br.unasp.boacao.presentation.components.QrScannerDialog
import br.unasp.boacao.util.QrCodeUtils

@Composable
fun VolunteerDashboardScreen(navController: NavController) {
    val context = LocalContext.current
    val application = context.applicationContext as BoaAcaoApplication
    val viewModel: VolunteerViewModel = viewModel(
        factory = VolunteerViewModelFactory(application.volunteerRepository, application.pointsRepository)
    )
    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val warmPrimaryColor = Color(0xFFF06A38)
    var donationToClaim by remember { mutableStateOf<Donation?>(null) }
    var donationToConfirm by remember { mutableStateOf<Donation?>(null) }
    var donationPickedUp by remember { mutableStateOf<Donation?>(null) }

    // Show error snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.White,
                contentColor = warmPrimaryColor,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = warmPrimaryColor
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Mapa", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
                    icon = { Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Disponíveis", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal)
                            if (state.availableDonations.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Badge(containerColor = warmPrimaryColor) {
                                    Text("${state.availableDonations.size}", color = Color.White, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Em Coleta", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal)
                            if (state.myDeliveries.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Badge(containerColor = Color(0xFF4CAF50)) {
                                    Text("${state.myDeliveries.size}", color = Color.White, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                )
            }

            when (selectedTab) {
                0 -> {
                    VolunteerMapScreen(
                        navController = navController,
                        onSwitchToList = { selectedTab = 1 }
                    )
                }
                1 -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (state.isLoading && state.availableDonations.isEmpty()) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = warmPrimaryColor)
                        } else if (state.availableDonations.isEmpty()) {
                            Column(
                                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.SearchOff, contentDescription = null, modifier = Modifier.size(72.dp), tint = Color(0xFFE0E0E0))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Nenhuma doação disponível", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color.Gray)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Novas doações aparecerão aqui automaticamente.",
                                    color = Color.LightGray,
                                    textAlign = TextAlign.Center,
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(state.availableDonations, key = { it.id }) { donation ->
                                    AvailableDonationCard(
                                        donation = donation,
                                        onClaimClick = { donationToClaim = donation }
                                    )
                                }
                            }
                        }
                    }
                }
                2 -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (state.isLoading && state.myDeliveries.isEmpty()) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = warmPrimaryColor)
                        } else if (state.myDeliveries.isEmpty()) {
                            Column(
                                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(72.dp), tint = Color(0xFFE0E0E0))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Nenhuma entrega em andamento", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color.Gray)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Reserve uma doação na aba Disponíveis.",
                                    color = Color.LightGray,
                                    textAlign = TextAlign.Center,
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(state.myDeliveries, key = { it.id }) { donation ->
                                    VolunteerDonationCard(
                                        donation = donation,
                                        isMyDelivery = true,
                                        onClaimClick = {},
                                        onConfirmPickupClick = { donationToConfirm = donation }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    donationToClaim?.let { donation ->
        ClaimDonationDialog(
            donation = donation,
            isLoading = state.isLoading,
            onDismiss = { donationToClaim = null },
            onConfirm = {
                viewModel.claimDonation(context, donation) { success, errorMsg ->
                    if (success) {
                        donationToClaim = null
                        selectedTab = 2
                        Toast.makeText(context, "Doação reservada com sucesso!", Toast.LENGTH_SHORT).show()
                    } else {
                        donationToClaim = null
                        Toast.makeText(context, errorMsg ?: "Erro ao reservar", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    donationToConfirm?.let { donation ->
        ConfirmPickupDialog(
            isLoading = state.isLoading,
            onDismiss = { donationToConfirm = null },
            onConfirm = { pin ->
                viewModel.confirmPickup(context, donation.id, pin) { success, errorMsg ->
                    if (success) {
                        donationToConfirm = null
                        donationPickedUp = donation
                        Toast.makeText(context, "Retirada confirmada! +10 pontos ganhos!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, errorMsg ?: "Erro", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    donationPickedUp?.let { donation ->
        SelectNgoSheet(
            ngos = state.ngos,
            isLoading = state.isLoading,
            onSelect = { ngo ->
                viewModel.assignNgo(donation.id, ngo) { success ->
                    if (success) Toast.makeText(context, "ONG ${ngo.name} notificada!", Toast.LENGTH_SHORT).show()
                    donationPickedUp = null
                }
            },
            onDismiss = { donationPickedUp = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectNgoSheet(
    ngos: List<NgoInfo>,
    isLoading: Boolean,
    onSelect: (NgoInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF1976D2).copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Business, contentDescription = null, tint = Color(0xFF1976D2), modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Selecionar ONG de Destino", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("A ONG escolhida verá esta doação como 'a caminho'.", fontSize = 12.sp, color = Color.Gray)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF1976D2))
                }
            } else if (ngos.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFF06A38))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Nenhuma ONG cadastrada ainda.", color = Color(0xFFF06A38), fontSize = 14.sp)
                    }
                }
            } else {
                ngos.forEach { ngo ->
                    Card(
                        onClick = { if (!isLoading) onSelect(ngo) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF1976D2).copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Business, contentDescription = null, tint = Color(0xFF1976D2), modifier = Modifier.size(22.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ngo.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                if (ngo.address.isNotBlank()) Text(ngo.address, fontSize = 12.sp, color = Color.Gray)
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.LightGray)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                enabled = !isLoading
            ) {
                Text("Ir sem selecionar ONG", color = Color.Gray, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun VolunteerDonationCard(
    donation: Donation,
    isMyDelivery: Boolean,
    onClaimClick: () -> Unit,
    onConfirmPickupClick: () -> Unit
) {
    val warmColor = Color(0xFFF06A38)
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(warmColor.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Storefront, contentDescription = null, tint = warmColor, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = donation.donorName.ifBlank { "Doador Local" }, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.Gray)
                    Text(text = donation.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF212121))
                }
                // Status badge
                val statusColor = when (donation.status) {
                    DonationStatus.CLAIMED -> Color(0xFFFFA726)
                    DonationStatus.IN_TRANSIT -> Color(0xFF1976D2)
                    else -> warmColor
                }
                Badge(containerColor = statusColor.copy(alpha = 0.15f)) {
                    Text(
                        when (donation.status) {
                            DonationStatus.CLAIMED -> "Reservado"
                            DonationStatus.IN_TRANSIT -> "Em trânsito"
                            else -> ""
                        },
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.LightGray)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Validade: ${donation.expiryDate}", fontSize = 12.sp, color = Color.Gray)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.LightGray)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = donation.pickupAddress, fontSize = 12.sp, color = Color.Gray)
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (!isMyDelivery) {
                Button(onClick = onClaimClick, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = warmColor), shape = RoundedCornerShape(12.dp)) {
                    Text("Ver e Ir Coletar")
                }
            } else {
                when (donation.status) {
                    DonationStatus.CLAIMED -> {
                        Button(
                            onClick = onConfirmPickupClick,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Cheguei no Doador (Validar PIN)")
                        }
                    }
                    DonationStatus.IN_TRANSIT -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LocalShipping, contentDescription = null, tint = Color(0xFF1976D2), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Você está com o alimento!", fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Mostre o QR Code ou código à ONG:", fontSize = 12.sp, color = Color.Gray)
                                Spacer(modifier = Modifier.height(12.dp))
                                val qrBitmap = remember(donation.deliveryCode) {
                                    QrCodeUtils.generateQrBitmap(donation.deliveryCode, 300)
                                }
                                if (qrBitmap != null) {
                                    Card(
                                        shape = RoundedCornerShape(8.dp),
                                        elevation = CardDefaults.cardElevation(2.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White)
                                    ) {
                                        Image(
                                            bitmap = qrBitmap.asImageBitmap(),
                                            contentDescription = "QR Code de entrega",
                                            modifier = Modifier.size(140.dp).padding(8.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    donation.deliveryCode,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 28.sp,
                                    color = Color(0xFF1976D2),
                                    letterSpacing = 6.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFA726), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("+10 pontos ao completar!", fontSize = 11.sp, color = Color(0xFFFFA726), fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}

@Composable
fun ClaimDonationDialog(donation: Donation, isLoading: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val context = LocalContext.current
    val warmColor = Color(0xFFF06A38)

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(warmColor.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = warmColor, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Assumir Coleta?", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Confirme para reservar esta doação", fontSize = 12.sp, color = Color.Gray)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(donation.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF212121))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Doador: ${donation.donorName}", fontSize = 13.sp, color = Color.Gray)
                        if (donation.pickupAddress.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(donation.pickupAddress, fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                        if (donation.items.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = Color(0xFFE0E0E0))
                            Spacer(modifier = Modifier.height(8.dp))
                            donation.items.forEach { item ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                    Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = warmColor, modifier = Modifier.size(7.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("${item.name} — ${item.quantity}", fontSize = 13.sp, color = Color.DarkGray)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                if (donation.pickupAddress.isNotBlank()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val uri = android.net.Uri.parse("geo:0,0?q=${android.net.Uri.encode(donation.pickupAddress)}")
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                                context.startActivity(android.content.Intent.createChooser(intent, "Navegar com..."))
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Navegar", fontSize = 13.sp)
                        }
                        Button(
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = warmColor),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Reservar e Ir")
                            }
                        }
                    }
                } else {
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = warmColor),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Reservar e Ir")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Cancelar", color = Color.Gray) }
            }
        }
    }
}

@Composable
fun ConfirmPickupDialog(isLoading: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var showQrScanner by remember { mutableStateOf(false) }
    val greenColor = Color(0xFF4CAF50)

    if (showQrScanner) {
        QrScannerDialog(
            title = "Escanear PIN do Doador",
            hint = "Aponte para o QR Code exibido na tela do doador",
            onScanned = { value ->
                val digits = value.filter { it.isDigit() }.take(4)
                if (digits.length == 4) {
                    pin = digits
                    onConfirm(digits)
                }
            },
            onDismiss = { showQrScanner = false }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.size(64.dp).clip(CircleShape).background(greenColor.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = greenColor, modifier = Modifier.size(32.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("PIN de Retirada", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("Peça o PIN de 4 dígitos ao responsável.", textAlign = TextAlign.Center, color = Color.Gray, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFA726), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("+10 pontos ao confirmar!", fontSize = 12.sp, color = Color(0xFFFFA726), fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { showQrScanner = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Escanear QR Code")
                }
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text("  ou  ", fontSize = 12.sp, color = Color.Gray)
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = pin, onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pin = it },
                    placeholder = { Text("0000") },
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontSize = 28.sp, letterSpacing = 8.sp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(0.6f),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onDismiss, enabled = !isLoading) { Text("Cancelar") }
                    Button(
                        onClick = { onConfirm(pin) },
                        enabled = pin.length == 4 && !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = greenColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("Validar")
                    }
                }
            }
        }
    }
}


@Composable
fun AvailableDonationCard(donation: Donation, onClaimClick: () -> Unit) {
    val warmColor = Color(0xFFF06A38)
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(if (expanded) 4.dp else 1.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(warmColor.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Storefront, contentDescription = null, tint = warmColor, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(donation.donorName.ifBlank { "Doador Local" }, fontSize = 12.sp, color = Color.Gray)
                    Text(donation.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF212121))
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = Color.LightGray)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color.LightGray)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Validade: ${donation.expiryDate}", fontSize = 11.sp, color = Color.Gray)
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color(0xFFF0F0F0))
                    Spacer(modifier = Modifier.height(12.dp))
                    if (donation.items.isNotEmpty()) {
                        donation.items.forEach { item ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = warmColor, modifier = Modifier.size(7.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("${item.name} — ${item.quantity}", fontSize = 13.sp, color = Color.DarkGray)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(donation.pickupAddress, fontSize = 12.sp, color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onClaimClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = warmColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ver e Ir Coletar")
                    }
                }
            }

            if (!expanded && donation.items.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    donation.items.take(2).joinToString(" · ") { it.name } + if (donation.items.size > 2) " +${donation.items.size - 2}" else "",
                    fontSize = 12.sp, color = Color.LightGray
                )
            }
        }
    }
}
