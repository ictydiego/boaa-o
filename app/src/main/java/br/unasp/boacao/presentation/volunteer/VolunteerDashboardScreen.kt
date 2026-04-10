package br.unasp.boacao.presentation.volunteer

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import br.unasp.boacao.domain.model.DonationItem
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
    // Tab 0 = Mapa (auto-selected on entry), Tab 1 = Lista de Disponíveis, Tab 2 = Minhas Entregas
    var selectedTab by remember { mutableIntStateOf(0) }
    val warmPrimaryColor = Color(0xFFF06A38)
    var donationToClaim by remember { mutableStateOf<Donation?>(null) }
    var donationToConfirm by remember { mutableStateOf<Donation?>(null) }
    var donationPickedUp by remember { mutableStateOf<Donation?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab, containerColor = Color.White, contentColor = warmPrimaryColor) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Mapa", fontWeight = FontWeight.Bold) })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Disponíveis", fontWeight = FontWeight.Bold) })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Em Coleta", fontWeight = FontWeight.Bold) })
        }

        when (selectedTab) {
            0 -> {
                // Map auto-opens — embedded VolunteerMapScreen with "Ver Lista" switching to tab 1
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
                        Text(
                            "Nenhuma doação disponível no momento.",
                            color = Color.Gray,
                            modifier = Modifier.align(Alignment.Center).padding(32.dp),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(state.availableDonations) { donation ->
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
                        Text(
                            "Você não tem entregas em andamento.",
                            color = Color.Gray,
                            modifier = Modifier.align(Alignment.Center).padding(32.dp),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(state.myDeliveries) { donation ->
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

    donationToClaim?.let { donation ->
        ClaimDonationDialog(
            donation = donation,
            onDismiss = { donationToClaim = null },
            onConfirm = {
                viewModel.claimDonation(donation)
                donationToClaim = null
                selectedTab = 2
            }
        )
    }

    donationToConfirm?.let { donation ->
        ConfirmPickupDialog(
            onDismiss = { donationToConfirm = null },
            onConfirm = { pin ->
                viewModel.confirmPickup(donation.id, pin) { success, errorMsg ->
                    if (success) {
                        donationToConfirm = null
                        donationPickedUp = donation
                        Toast.makeText(context, "Retirada confirmada! +10 pontos ganhos! 🌟", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, errorMsg ?: "Erro", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // NGO selection after pickup confirmed
    donationPickedUp?.let { donation ->
        SelectNgoSheet(
            ngos = state.ngos,
            onSelect = { ngo ->
                viewModel.assignNgo(donation.id, ngo) { success ->
                    if (success) Toast.makeText(context, "ONG ${ngo.name} notificada!", Toast.LENGTH_SHORT).show()
                }
                donationPickedUp = null
            },
            onDismiss = { donationPickedUp = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectNgoSheet(
    ngos: List<NgoInfo>,
    onSelect: (NgoInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Business, contentDescription = null, tint = Color(0xFF1976D2), modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Selecionar ONG de Destino", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Text("A ONG escolhida verá esta doação como 'a caminho'.", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))

            if (ngos.isEmpty()) {
                Text("Nenhuma ONG cadastrada ainda.", color = Color.Gray, modifier = Modifier.padding(vertical = 16.dp))
            } else {
                ngos.forEach { ngo ->
                    Card(
                        onClick = { onSelect(ngo) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Business, contentDescription = null, tint = Color(0xFF1976D2), modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(ngo.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                if (ngo.address.isNotBlank()) Text(ngo.address, fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
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
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(4.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storefront, contentDescription = null, tint = Color.Gray)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = donation.donorName.ifBlank { "Doador Local" }, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = donation.title, style = MaterialTheme.typography.titleLarge, color = Color(0xFFF06A38))
            Text(text = "Validade: ${donation.expiryDate}", fontSize = 12.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = donation.pickupAddress, style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (!isMyDelivery) {
                Button(onClick = onClaimClick, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF06A38))) {
                    Text("Ver e Ir Coletar")
                }
            } else {
                when (donation.status) {
                    DonationStatus.CLAIMED -> {
                        Button(onClick = onConfirmPickupClick, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) {
                            Text("Cheguei no Doador (Validar PIN)")
                        }
                    }
                    DonationStatus.IN_TRANSIT -> {
                        Column(modifier = Modifier.fillMaxWidth().background(Color(0xFFE3F2FD), RoundedCornerShape(8.dp)).padding(12.dp)) {
                            Text("Você está com o alimento!", fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
                            Text("Vá até uma ONG e mostre o QR Code ou o código:", fontSize = 12.sp, color = Color.DarkGray)
                            Spacer(modifier = Modifier.height(8.dp))
                            // QR Code display
                            val qrBitmap = remember(donation.deliveryCode) {
                                QrCodeUtils.generateQrBitmap(donation.deliveryCode, 300)
                            }
                            if (qrBitmap != null) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "QR Code de entrega",
                                    modifier = Modifier.size(140.dp).align(Alignment.CenterHorizontally),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            Text(donation.deliveryCode, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = Color(0xFF1976D2), letterSpacing = 4.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF06A38), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Você ganhará +10 pontos ao completar!", fontSize = 11.sp, color = Color(0xFFF06A38), fontWeight = FontWeight.Medium)
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
fun ClaimDonationDialog(donation: Donation, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val context = LocalContext.current
    val warmColor = Color(0xFFF06A38)

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = warmColor, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Assumir Coleta?", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("Doador: ${donation.donorName}", fontSize = 14.sp, color = Color.DarkGray, fontWeight = FontWeight.Medium)
                if (donation.pickupAddress.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(donation.pickupAddress, fontSize = 13.sp, color = Color.Gray)
                    }
                }
                if (donation.items.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Itens:", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                    donation.items.forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = warmColor, modifier = Modifier.size(7.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("${item.name} — ${item.quantity}", fontSize = 13.sp, color = Color.DarkGray)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                // Navigate buttons
                if (donation.pickupAddress.isNotBlank()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val uri = android.net.Uri.parse("geo:0,0?q=${android.net.Uri.encode(donation.pickupAddress)}")
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                                context.startActivity(android.content.Intent.createChooser(intent, "Navegar com..."))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Navegar", fontSize = 12.sp)
                        }
                        Button(
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = warmColor)
                        ) { Text("Reservar e Ir") }
                    }
                } else {
                    Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = warmColor)) {
                        Text("Reservar e Ir")
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Cancelar", color = Color.Gray) }
            }
        }
    }
}

@Composable
fun ConfirmPickupDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
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
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = greenColor, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("PIN de Retirada", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("Peça o PIN de 4 dígitos ao responsável.", textAlign = TextAlign.Center, color = Color.Gray, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Você ganhará +10 pontos ao confirmar!", fontSize = 12.sp, color = Color(0xFFF06A38), fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(16.dp))

                // QR scan button
                Button(
                    onClick = { showQrScanner = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Escanear QR Code")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("ou digite o PIN manualmente:", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = pin, onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pin = it },
                    placeholder = { Text("0000") },
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontSize = 24.sp, letterSpacing = 8.sp),
                    singleLine = true, modifier = Modifier.fillMaxWidth(0.6f)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Button(
                        onClick = { onConfirm(pin) },
                        enabled = pin.length == 4,
                        colors = ButtonDefaults.buttonColors(containerColor = greenColor)
                    ) { Text("Validar") }
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
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storefront, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(donation.donorName.ifBlank { "Doador Local" }, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = Color.Gray)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(donation.title, style = MaterialTheme.typography.titleMedium, color = warmColor)
            Text("Validade: ${donation.expiryDate}", fontSize = 12.sp, color = Color.Gray)

            if (expanded) {
                Spacer(modifier = Modifier.height(10.dp))
                if (donation.items.isNotEmpty()) {
                    Text("Itens:", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                    donation.items.forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = warmColor, modifier = Modifier.size(7.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("${item.name} — ${item.quantity}", fontSize = 13.sp, color = Color.DarkGray)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(donation.pickupAddress, fontSize = 12.sp, color = Color.Gray)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onClaimClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = warmColor)
                ) {
                    Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ver e Ir Coletar")
                }
            } else {
                if (donation.items.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        donation.items.take(2).joinToString(" • ") { it.name } + if (donation.items.size > 2) " +${donation.items.size - 2}" else "",
                        fontSize = 12.sp, color = Color.LightGray
                    )
                }
            }
        }
    }
}
