package br.unasp.boacao.presentation.donor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import br.unasp.boacao.BoaAcaoApplication
import br.unasp.boacao.domain.model.Donation
import br.unasp.boacao.domain.model.DonationItem
import br.unasp.boacao.domain.model.DonationStatus
import br.unasp.boacao.util.QrCodeUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonorDashboardScreen(navController: NavController) {
    val context = LocalContext.current
    val application = context.applicationContext as BoaAcaoApplication
    val viewModel: DonorViewModel = viewModel(factory = DonorViewModelFactory(application.donorRepository))
    val state by viewModel.uiState.collectAsState()
    val filtered = viewModel.filteredDonations()
    var showAddDialog by remember { mutableStateOf(false) }
    val warmPrimaryColor = Color(0xFFF06A38)

    var showPeriodPicker by remember { mutableStateOf(false) }
    val startPickerState = rememberDatePickerState()
    val endPickerState = rememberDatePickerState()
    var periodStep by remember { mutableIntStateOf(0) }

    if (showPeriodPicker) {
        DatePickerDialog(
            onDismissRequest = { showPeriodPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    if (periodStep == 0) {
                        periodStep = 1
                    } else {
                        val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                        val start = startPickerState.selectedDateMillis?.let { fmt.format(Date(it)) } ?: ""
                        val end = endPickerState.selectedDateMillis?.let { fmt.format(Date(it)) } ?: ""
                        viewModel.setPeriod(start, end)
                        showPeriodPicker = false
                        periodStep = 0
                    }
                }) { Text(if (periodStep == 0) "Próximo (data fim)" else "Confirmar") }
            },
            dismissButton = { TextButton(onClick = { showPeriodPicker = false; periodStep = 0 }) { Text("Cancelar") } }
        ) { DatePicker(state = if (periodStep == 0) startPickerState else endPickerState) }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = warmPrimaryColor,
                contentColor = Color.White
            ) { Icon(Icons.Default.Add, contentDescription = "Nova Doação") }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // Points & stats header
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = warmPrimaryColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        Text("${state.donorPoints}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                        Text("pontos", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                    }
                    Divider(modifier = Modifier.height(48.dp).width(1.dp), color = Color.White.copy(alpha = 0.3f))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.VolunteerActivism, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        Text("${state.donorDonationCount}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                        Text("doações", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                    }
                }
            }

            // Status filter chips
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DonorStatusFilter.values().forEach { f ->
                    FilterChip(
                        selected = state.statusFilter == f,
                        onClick = { viewModel.setStatusFilter(f) },
                        label = {
                            Text(when (f) {
                                DonorStatusFilter.ALL -> "Todos"
                                DonorStatusFilter.AVAILABLE -> "Aguardando"
                                DonorStatusFilter.IN_PROGRESS -> "Em trânsito"
                                DonorStatusFilter.DELIVERED -> "Entregue"
                            }, fontSize = 12.sp)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = warmPrimaryColor.copy(alpha = 0.15f),
                            selectedLabelColor = warmPrimaryColor
                        )
                    )
                }
            }

            // Time filter chips
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DonorTimeFilter.values().filter { it != DonorTimeFilter.PERIOD }.forEach { f ->
                    FilterChip(
                        selected = state.timeFilter == f,
                        onClick = { viewModel.setTimeFilter(f) },
                        label = {
                            Text(when (f) {
                                DonorTimeFilter.ALL -> "Todo período"
                                DonorTimeFilter.TODAY -> "Hoje"
                                DonorTimeFilter.WEEK -> "Semana"
                                DonorTimeFilter.MONTH -> "Mês"
                                DonorTimeFilter.PERIOD -> ""
                            }, fontSize = 12.sp)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = warmPrimaryColor.copy(alpha = 0.15f),
                            selectedLabelColor = warmPrimaryColor
                        )
                    )
                }
                FilterChip(
                    selected = state.timeFilter == DonorTimeFilter.PERIOD,
                    onClick = { showPeriodPicker = true },
                    label = {
                        Text(
                            if (state.timeFilter == DonorTimeFilter.PERIOD && state.periodStart.isNotBlank())
                                "${state.periodStart} → ${state.periodEnd}"
                            else "Período",
                            fontSize = 12.sp
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = warmPrimaryColor.copy(alpha = 0.15f),
                        selectedLabelColor = warmPrimaryColor
                    )
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                if (state.isLoading && state.donations.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = warmPrimaryColor)
                } else if (filtered.isEmpty()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Inventory, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                        Spacer(modifier = Modifier.height(16.dp))
                        if (state.donations.isEmpty()) {
                            Text("Nenhuma doação cadastrada ainda.", color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Text("Clique no + para fazer o bem!", color = warmPrimaryColor, fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        } else {
                            Text("Nenhuma doação neste filtro.", color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(filtered) { donation ->
                            DonationCard(
                                donation = donation,
                                onCancel = if (donation.status == DonationStatus.AVAILABLE) {
                                    { viewModel.cancelDonation(donation.id) }
                                } else null
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        CreateDonationDialog(
            donorAddress = state.donorAddress,
            onDismiss = { showAddDialog = false },
            onSave = { items, title, desc, expiry ->
                viewModel.createDonation(context, items, title, desc, expiry) { showAddDialog = false }
            },
            isSaving = state.isLoading
        )
    }
}

@Composable
fun DonationCard(donation: Donation, onCancel: (() -> Unit)? = null) {
    val warmColor = Color(0xFFF06A38)
    var expanded by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }

    val statusColor = when (donation.status) {
        DonationStatus.AVAILABLE -> Color(0xFF4CAF50)
        DonationStatus.CLAIMED, DonationStatus.IN_TRANSIT -> Color(0xFFFFC107)
        DonationStatus.DELIVERED -> Color(0xFF9E9E9E)
    }
    val statusText = when (donation.status) {
        DonationStatus.AVAILABLE -> "Aguardando Voluntário"
        DonationStatus.CLAIMED -> "Voluntário a caminho"
        DonationStatus.IN_TRANSIT -> "A caminho da ONG"
        DonationStatus.DELIVERED -> "Entregue à ONG!"
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
            title = { Text("Cancelar doação?") },
            text = { Text("Esta ação é irreversível. A doação será removida.") },
            confirmButton = {
                Button(
                    onClick = { showCancelDialog = false; onCancel?.invoke() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("Cancelar doação") }
            },
            dismissButton = { TextButton(onClick = { showCancelDialog = false }) { Text("Manter") } }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = donation.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge(containerColor = statusColor) {
                        Text(statusText, color = if (donation.status == DonationStatus.AVAILABLE || donation.status == DonationStatus.DELIVERED) Color.White else Color.Black, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                if (donation.items.isNotEmpty()) {
                    donation.items.forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = warmColor, modifier = Modifier.size(8.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("${item.name} — ${item.quantity}", style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
                        }
                    }
                } else if (donation.description.isNotBlank()) {
                    Text(text = donation.description, style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = donation.pickupAddress, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }

                if (donation.status == DonationStatus.AVAILABLE || donation.status == DonationStatus.CLAIMED) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth().background(Color(0xFFFFF3E0), RoundedCornerShape(8.dp)).padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.QrCode, contentDescription = null, tint = warmColor, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Código de Retirada — mostre ao voluntário", fontSize = 12.sp, color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        val qrBitmap = remember(donation.pickupCode) { QrCodeUtils.generateQrBitmap(donation.pickupCode, 300) }
                        if (qrBitmap != null) {
                            Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "QR Code", modifier = Modifier.size(140.dp), contentScale = ContentScale.Fit)
                        }
                        Text(text = donation.pickupCode, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = warmColor, letterSpacing = 4.sp)
                    }
                }

                if (onCancel != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showCancelDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cancelar Doação")
                    }
                }
            } else {
                // Collapsed — show brief info
                Spacer(modifier = Modifier.height(4.dp))
                if (donation.items.isNotEmpty()) {
                    Text(
                        text = donation.items.take(2).joinToString(" • ") { it.name } + if (donation.items.size > 2) " +${donation.items.size - 2}" else "",
                        fontSize = 12.sp, color = Color.Gray
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateDonationDialog(
    donorAddress: String,
    onDismiss: () -> Unit,
    onSave: (List<DonationItem>, String, String, String) -> Unit,
    isSaving: Boolean
) {
    var title by remember { mutableStateOf("") }
    var expiryDate by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    val warmPrimaryColor = Color(0xFFF06A38)
    var items by remember { mutableStateOf(listOf(DonationItem())) }
    val scrollState = rememberScrollState()

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    datePickerState.selectedDateMillis?.let { millis ->
                        expiryDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(millis))
                    }
                }) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") } }
        ) { DatePicker(state = datePickerState) }
    }

    val validItems = items.filter { it.name.isNotBlank() }
    val canSave = !isSaving && validItems.isNotEmpty() && expiryDate.isNotBlank()

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(24.dp).verticalScroll(scrollState)) {
                Text("Nova Doação", style = MaterialTheme.typography.headlineSmall, color = warmPrimaryColor, fontWeight = FontWeight.Bold)
                if (donorAddress.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(donorAddress, fontSize = 12.sp, color = Color.Gray)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                Text("Itens da Doação", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.DarkGray)
                Spacer(modifier = Modifier.height(8.dp))
                items.forEachIndexed { index, item ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = item.name,
                            onValueChange = { v -> items = items.toMutableList().also { it[index] = it[index].copy(name = v) } },
                            label = { Text("Item") },
                            placeholder = { Text("Ex: Pão Francês") },
                            modifier = Modifier.weight(1.5f),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        OutlinedTextField(
                            value = item.quantity,
                            onValueChange = { v -> items = items.toMutableList().also { it[index] = it[index].copy(quantity = v) } },
                            label = { Text("Qtd") },
                            placeholder = { Text("10 un") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        if (items.size > 1) {
                            IconButton(onClick = { items = items.toMutableList().also { it.removeAt(index) } }) {
                                Icon(Icons.Default.Remove, contentDescription = "Remover", tint = Color.Red)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                TextButton(onClick = { items = items + DonationItem() }, modifier = Modifier.align(Alignment.Start)) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Adicionar Item", fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Título (opcional)") },
                    placeholder = { Text("Ex: Doação de Padaria") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = expiryDate, onValueChange = {}, readOnly = true,
                    label = { Text("Data de Validade") },
                    placeholder = { Text("Selecione a data") },
                    trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    interactionSource = remember { MutableInteractionSource() }.also { src ->
                        LaunchedEffect(src) { src.interactions.collect { if (it is PressInteraction.Release) showDatePicker = true } }
                    }
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancelar", color = Color.Gray) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(validItems, title, "", expiryDate) },
                        enabled = canSave,
                        colors = ButtonDefaults.buttonColors(containerColor = warmPrimaryColor)
                    ) {
                        if (isSaving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        else Text("Publicar")
                    }
                }
            }
        }
    }
}
