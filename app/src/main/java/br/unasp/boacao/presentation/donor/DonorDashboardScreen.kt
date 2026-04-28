package br.unasp.boacao.presentation.donor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import br.unasp.boacao.presentation.components.FilterBottomSheet
import br.unasp.boacao.presentation.components.FilterChipOption
import br.unasp.boacao.presentation.components.FilterChipsRow
import br.unasp.boacao.presentation.components.FilterSection
import br.unasp.boacao.presentation.components.LocalFilterIconCoordinator
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
    var showFilterSheet by remember { mutableStateOf(false) }
    val warmPrimaryColor = Color(0xFFF06A38)

    val filterCoordinator = LocalFilterIconCoordinator.current
    val hasActiveFilters = state.statusFilter != DonorStatusFilter.ALL || state.timeFilter != DonorTimeFilter.ALL

    LaunchedEffect(hasActiveFilters) {
        filterCoordinator?.register(hasActiveFilters) {
            showFilterSheet = true
        }
    }

    DisposableEffect(Unit) {
        onDispose { filterCoordinator?.unregister() }
    }

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

    if (showFilterSheet) {
        FilterBottomSheet(
            onDismiss = { showFilterSheet = false },
            onClear = {
                viewModel.setStatusFilter(DonorStatusFilter.ALL)
                viewModel.setTimeFilter(DonorTimeFilter.ALL)
            }
        ) {
            FilterSection(title = "Status da Doação", icon = Icons.Default.Info) {
                FilterChipsRow(
                    options = listOf(
                        FilterChipOption(DonorStatusFilter.ALL.name, "Todos"),
                        FilterChipOption(DonorStatusFilter.AVAILABLE.name, "Aguardando"),
                        FilterChipOption(DonorStatusFilter.IN_PROGRESS.name, "Em trânsito"),
                        FilterChipOption(DonorStatusFilter.DELIVERED.name, "Entregue")
                    ),
                    selectedKey = state.statusFilter.name,
                    onSelect = { viewModel.setStatusFilter(DonorStatusFilter.valueOf(it)) }
                )
            }
            FilterSection(title = "Período de Criação", icon = Icons.Default.DateRange) {
                FilterChipsRow(
                    options = listOf(
                        FilterChipOption(DonorTimeFilter.ALL.name, "Tudo"),
                        FilterChipOption(DonorTimeFilter.TODAY.name, "Hoje"),
                        FilterChipOption(DonorTimeFilter.WEEK.name, "Semana"),
                        FilterChipOption(DonorTimeFilter.MONTH.name, "Mês"),
                        FilterChipOption(DonorTimeFilter.PERIOD.name, if (state.timeFilter == DonorTimeFilter.PERIOD && state.periodStart.isNotBlank()) "${state.periodStart} - ${state.periodEnd}" else "Personalizado")
                    ),
                    selectedKey = state.timeFilter.name,
                    onSelect = {
                        if (it == DonorTimeFilter.PERIOD.name) showPeriodPicker = true
                        else viewModel.setTimeFilter(DonorTimeFilter.valueOf(it))
                    }
                )
            }
        }
    }

    val animatedPoints by animateIntAsState(targetValue = state.donorPoints, label = "points")
    val animatedDonations by animateIntAsState(targetValue = state.donorDonationCount, label = "donations")

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = warmPrimaryColor,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nova Doação")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Nova Doação", fontWeight = FontWeight.SemiBold)
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // Stats header with gradient
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(4.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.horizontalGradient(listOf(warmPrimaryColor, Color(0xFFFF8A50))))
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("$animatedPoints", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                            Text("pontos", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                        }
                        Box(modifier = Modifier.width(1.dp).height(60.dp).background(Color.White.copy(alpha = 0.3f)))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.VolunteerActivism, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("$animatedDonations", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                            Text("doações", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                if (state.isLoading && state.donations.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = warmPrimaryColor)
                } else if (filtered.isEmpty()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Inventory, contentDescription = null, modifier = Modifier.size(72.dp), tint = Color(0xFFE0E0E0))
                        Spacer(modifier = Modifier.height(16.dp))
                        if (state.donations.isEmpty()) {
                            Text("Nenhuma doação cadastrada ainda.", color = Color.Gray, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Clique em Nova Doação para fazer o bem!", color = warmPrimaryColor, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, fontSize = 13.sp)
                        } else {
                            Text("Nenhuma doação neste filtro.", color = Color.Gray, textAlign = TextAlign.Center)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filtered, key = { it.id }) { donation ->
                            DonationCard(
                                donation = donation,
                                isLoading = state.isLoading,
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
fun DonationCard(donation: Donation, isLoading: Boolean, onCancel: (() -> Unit)? = null) {
    val warmColor = Color(0xFFF06A38)
    var expanded by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }

    val statusColor = when (donation.status) {
        DonationStatus.AVAILABLE -> Color(0xFF4CAF50)
        DonationStatus.CLAIMED -> Color(0xFFFFA726)
        DonationStatus.IN_TRANSIT -> Color(0xFF1976D2)
        DonationStatus.DELIVERED -> Color(0xFF9E9E9E)
    }
    val statusText = when (donation.status) {
        DonationStatus.AVAILABLE -> "Aguardando"
        DonationStatus.CLAIMED -> "Voluntário a caminho"
        DonationStatus.IN_TRANSIT -> "A caminho da ONG"
        DonationStatus.DELIVERED -> "Entregue"
    }
    val statusIcon = when (donation.status) {
        DonationStatus.AVAILABLE -> Icons.Default.HourglassEmpty
        DonationStatus.CLAIMED -> Icons.Default.DirectionsCar
        DonationStatus.IN_TRANSIT -> Icons.Default.LocalShipping
        DonationStatus.DELIVERED -> Icons.Default.CheckCircle
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { if (!isLoading) showCancelDialog = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) },
            title = { Text("Cancelar doação?") },
            text = { Text("Esta ação é irreversível. A doação será removida.") },
            confirmButton = {
                Button(
                    onClick = { onCancel?.invoke(); showCancelDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Cancelar doação")
                }
            },
            dismissButton = { TextButton(onClick = { showCancelDialog = false }, enabled = !isLoading) { Text("Manter") } },
            shape = RoundedCornerShape(20.dp)
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(if (expanded) 4.dp else 1.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = donation.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF212121))
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(statusText, fontSize = 12.sp, color = statusColor, fontWeight = FontWeight.Medium)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge(containerColor = statusColor.copy(alpha = 0.15f)) {
                        Text(
                            when (donation.status) {
                                DonationStatus.AVAILABLE -> "Ativo"
                                DonationStatus.CLAIMED, DonationStatus.IN_TRANSIT -> "Trânsito"
                                DonationStatus.DELIVERED -> "Concluído"
                            },
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 4.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color(0xFFF0F0F0))
                    Spacer(modifier = Modifier.height(12.dp))
                    if (donation.items.isNotEmpty()) {
                        donation.items.forEach { item ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                                Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = warmColor, modifier = Modifier.size(8.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("${item.name} — ${item.quantity}", style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
                            }
                        }
                    } else if (donation.description.isNotBlank()) {
                        Text(text = donation.description, style = MaterialTheme.typography.bodyMedium, color = Color.DarkGray)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = donation.pickupAddress, fontSize = 12.sp, color = Color.Gray)
                    }

                    if (donation.volunteerName != null && donation.volunteerName.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Voluntário: ${donation.volunteerName}", fontSize = 12.sp, color = Color.Gray)
                        }
                    }

                    if (donation.status == DonationStatus.AVAILABLE || donation.status == DonationStatus.CLAIMED) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.QrCode, contentDescription = null, tint = warmColor, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Código de Retirada", fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                                }
                                Text("Mostre ao voluntário quando ele chegar", fontSize = 11.sp, color = Color.LightGray)
                                Spacer(modifier = Modifier.height(12.dp))
                                val qrBitmap = remember(donation.pickupCode) { QrCodeUtils.generateQrBitmap(donation.pickupCode, 300) }
                                if (qrBitmap != null) {
                                    Card(
                                        shape = RoundedCornerShape(8.dp),
                                        elevation = CardDefaults.cardElevation(2.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White)
                                    ) {
                                        Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "QR Code", modifier = Modifier.size(140.dp).padding(8.dp), contentScale = ContentScale.Fit)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = donation.pickupCode, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, color = warmColor, letterSpacing = 6.sp)
                            }
                        }
                    }

                    if (donation.status == DonationStatus.DELIVERED && donation.deliveredAt.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Entregue em ${donation.deliveredAt}", fontSize = 13.sp, color = Color(0xFF388E3C), fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    if (onCancel != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { showCancelDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isLoading
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cancelar Doação")
                        }
                    }
                }
            }

            if (!expanded) {
                Spacer(modifier = Modifier.height(4.dp))
                if (donation.items.isNotEmpty()) {
                    Text(
                        text = donation.items.take(2).joinToString(" · ") { it.name } + if (donation.items.size > 2) " +${donation.items.size - 2}" else "",
                        fontSize = 12.sp, color = Color.LightGray
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

    Dialog(onDismissRequest = { if (!isSaving) onDismiss() }) {
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(24.dp).verticalScroll(scrollState)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(warmPrimaryColor.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.VolunteerActivism, contentDescription = null, tint = warmPrimaryColor, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Nova Doação", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF212121))
                        if (donorAddress.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(donorAddress, fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))

                Text("Itens da Doação", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF424242))
                Spacer(modifier = Modifier.height(8.dp))
                items.forEachIndexed { index, item ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = item.name,
                            onValueChange = { v -> items = items.toMutableList().also { it[index] = it[index].copy(name = v) } },
                            label = { Text("Item") },
                            placeholder = { Text("Ex: Pão Francês") },
                            modifier = Modifier.weight(1.5f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isSaving
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        OutlinedTextField(
                            value = item.quantity,
                            onValueChange = { v -> items = items.toMutableList().also { it[index] = it[index].copy(quantity = v) } },
                            label = { Text("Qtd") },
                            placeholder = { Text("10 un") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isSaving
                        )
                        if (items.size > 1) {
                            IconButton(onClick = { items = items.toMutableList().also { it.removeAt(index) } }, enabled = !isSaving) {
                                Icon(Icons.Default.Remove, contentDescription = "Remover", tint = Color.Red)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                TextButton(onClick = { items = items + DonationItem() }, modifier = Modifier.align(Alignment.Start), enabled = !isSaving) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Adicionar Item", fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Título (opcional)") },
                    placeholder = { Text("Ex: Doação de Padaria") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isSaving
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = expiryDate, onValueChange = {}, readOnly = true,
                    label = { Text("Data de Validade") },
                    placeholder = { Text("Selecione a data") },
                    trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isSaving,
                    interactionSource = remember { MutableInteractionSource() }.also { src ->
                        LaunchedEffect(src) { src.interactions.collect { if (it is PressInteraction.Release && !isSaving) showDatePicker = true } }
                    }
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancelar", color = Color.Gray) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(validItems, title, "", expiryDate) },
                        enabled = canSave,
                        colors = ButtonDefaults.buttonColors(containerColor = warmPrimaryColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSaving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else {
                            Icon(Icons.Default.Publish, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Publicar")
                        }
                    }
                }
            }
        }
    }
}
