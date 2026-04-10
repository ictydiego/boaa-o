package br.unasp.boacao.presentation.beneficiary

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeneficiaryHistoryScreen(navController: NavController) {
    val context = LocalContext.current
    val application = context.applicationContext as BoaAcaoApplication
    val viewModel: BeneficiaryHistoryViewModel = viewModel(
        factory = BeneficiaryHistoryViewModelFactory(application.beneficiaryRepository)
    )
    val state by viewModel.uiState.collectAsState()
    val filtered = viewModel.filteredDeliveries()
    val warmColor = Color(0xFFF06A38)
    val blueColor = Color(0xFF1976D2)

    var showPeriodPicker by remember { mutableStateOf(false) }
    val startPickerState = rememberDatePickerState()
    val endPickerState = rememberDatePickerState()
    var periodStep by remember { mutableIntStateOf(0) } // 0=start, 1=end

    if (showPeriodPicker) {
        DatePickerDialog(
            onDismissRequest = { showPeriodPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    if (periodStep == 0) {
                        periodStep = 1
                    } else {
                        val fmt = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                        val start = startPickerState.selectedDateMillis?.let { fmt.format(java.util.Date(it)) } ?: ""
                        val end = endPickerState.selectedDateMillis?.let { fmt.format(java.util.Date(it)) } ?: ""
                        viewModel.setPeriod(start, end)
                        showPeriodPicker = false
                        periodStep = 0
                    }
                }) { Text(if (periodStep == 0) "Próximo (data fim)" else "Confirmar") }
            },
            dismissButton = { TextButton(onClick = { showPeriodPicker = false; periodStep = 0 }) { Text("Cancelar") } }
        ) { DatePicker(state = if (periodStep == 0) startPickerState else endPickerState) }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5))) {
        // Summary header
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = blueColor),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${state.deliveries.size}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp)
                    Text("Total\nRecebidos", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, textAlign = TextAlign.Center)
                }
                Box(modifier = Modifier.height(60.dp).width(1.dp).background(Color.White.copy(alpha = 0.4f)))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${filtered.size}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp)
                    Text("No filtro\nAtual", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            }
        }

        // Filter chips
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReceivedFilter.values().filter { it != ReceivedFilter.PERIOD }.forEach { f ->
                FilterChip(
                    selected = state.filter == f,
                    onClick = { viewModel.setFilter(f) },
                    label = {
                        Text(when (f) {
                            ReceivedFilter.ALL -> "Todos"
                            ReceivedFilter.TODAY -> "Hoje"
                            ReceivedFilter.WEEK -> "Semana"
                            ReceivedFilter.MONTH -> "Mês"
                            ReceivedFilter.PERIOD -> ""
                        }, fontSize = 12.sp)
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = blueColor.copy(alpha = 0.15f),
                        selectedLabelColor = blueColor
                    )
                )
            }
            FilterChip(
                selected = state.filter == ReceivedFilter.PERIOD,
                onClick = { showPeriodPicker = true },
                label = {
                    Text(
                        if (state.filter == ReceivedFilter.PERIOD && state.periodStart.isNotBlank())
                            "${state.periodStart} → ${state.periodEnd}"
                        else "Período",
                        fontSize = 12.sp
                    )
                },
                leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = blueColor.copy(alpha = 0.15f),
                    selectedLabelColor = blueColor
                )
            )
        }

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = blueColor) }
            return@Column
        }

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Icon(Icons.Default.Inbox, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Nenhum recebimento neste período.", color = Color.Gray, textAlign = TextAlign.Center)
                }
            }
            return@Column
        }

        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(filtered) { delivery ->
                ReceivedDonationCard(delivery)
            }
        }
    }
}

@Composable
private fun ReceivedDonationCard(donation: Donation) {
    val blueColor = Color(0xFF1976D2)
    val greenColor = Color(0xFF4CAF50)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(3.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(donation.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Surface(color = greenColor.copy(alpha = 0.12f), shape = RoundedCornerShape(20.dp)) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = greenColor, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Recebido", color = greenColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (donation.items.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                donation.items.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = blueColor, modifier = Modifier.size(7.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("${item.name} — ${item.quantity}", fontSize = 13.sp, color = Color.DarkGray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFFF0F0F0))
            Spacer(modifier = Modifier.height(10.dp))

            // Chain
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(24.dp).background(Color(0xFFF06A38).copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Home, contentDescription = null, tint = Color(0xFFF06A38), modifier = Modifier.size(14.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Doador", fontSize = 10.sp, color = Color.Gray)
                    Text(donation.donorName.ifBlank { "—" }, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(24.dp).background(Color(0xFF9C27B0).copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.DirectionsBike, contentDescription = null, tint = Color(0xFF9C27B0), modifier = Modifier.size(14.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Voluntário", fontSize = 10.sp, color = Color.Gray)
                    Text(donation.volunteerName ?: "—", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            if (donation.deliveredAt.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(donation.deliveredAt, fontSize = 12.sp, color = Color.LightGray)
                }
            }
        }
    }
}
