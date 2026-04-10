package br.unasp.boacao.presentation.volunteer

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
fun VolunteerHistoryScreen(navController: NavController) {
    val context = LocalContext.current
    val application = context.applicationContext as BoaAcaoApplication
    val viewModel: VolunteerHistoryViewModel = viewModel(
        factory = VolunteerHistoryViewModelFactory(application.volunteerRepository)
    )
    val state by viewModel.uiState.collectAsState()
    val filtered = viewModel.filteredDeliveries()
    val warmColor = Color(0xFFF06A38)

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

        // Summary header card
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = warmColor),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(6.dp)
        ) {
            Row(
                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${state.deliveries.size}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp)
                    Text("Total\nEntregas", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, textAlign = TextAlign.Center)
                }
                Box(modifier = Modifier.height(60.dp).width(1.dp).background(Color.White.copy(alpha = 0.4f)))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${filtered.size}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp)
                    Text("No filtro\nAtual", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, textAlign = TextAlign.Center)
                }
                Box(modifier = Modifier.height(60.dp).width(1.dp).background(Color.White.copy(alpha = 0.4f)))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${viewModel.totalPointsEarned}", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
                    }
                    Text("Pontos\nGanhos", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            }
        }

        // Filter chips
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DeliveryFilter.values().filter { it != DeliveryFilter.PERIOD }.forEach { f ->
                FilterChip(
                    selected = state.filter == f,
                    onClick = { viewModel.setFilter(f) },
                    label = {
                        Text(when (f) {
                            DeliveryFilter.ALL -> "Todos"
                            DeliveryFilter.TODAY -> "Hoje"
                            DeliveryFilter.WEEK -> "Semana"
                            DeliveryFilter.MONTH -> "Mês"
                            DeliveryFilter.PERIOD -> ""
                        }, fontSize = 12.sp)
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = warmColor.copy(alpha = 0.15f),
                        selectedLabelColor = warmColor
                    )
                )
            }
            FilterChip(
                selected = state.filter == DeliveryFilter.PERIOD,
                onClick = { showPeriodPicker = true },
                label = {
                    Text(
                        if (state.filter == DeliveryFilter.PERIOD && state.periodStart.isNotBlank())
                            "${state.periodStart} → ${state.periodEnd}"
                        else "Período",
                        fontSize = 12.sp
                    )
                },
                leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = warmColor.copy(alpha = 0.15f),
                    selectedLabelColor = warmColor
                )
            )
        }

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = warmColor)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Carregando histórico...", color = Color.Gray, fontSize = 14.sp)
                }
            }
            return@Column
        }

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Icon(Icons.Default.History, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Nenhuma entrega neste período.", color = Color.Gray, textAlign = TextAlign.Center, fontSize = 15.sp)
                }
            }
            return@Column
        }

        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(filtered) { delivery ->
                DeliveryHistoryCard(delivery = delivery)
            }
        }
    }
}

@Composable
private fun DeliveryHistoryCard(delivery: Donation) {
    val greenColor = Color(0xFF4CAF50)
    val blueColor = Color(0xFF1976D2)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(3.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    delivery.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF212121),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = greenColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = greenColor, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Entregue", color = greenColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color(0xFFF0F0F0))
            Spacer(modifier = Modifier.height(12.dp))

            ChainStep(
                icon = Icons.Default.Home,
                iconTint = Color(0xFFF06A38),
                label = "Doador",
                value = delivery.donorName.ifBlank { "—" },
                subValue = delivery.pickupAddress.ifBlank { null }
            )
            ChainConnector()
            ChainStep(
                icon = Icons.Default.DirectionsBike,
                iconTint = Color(0xFF9C27B0),
                label = "Voluntário (você)",
                value = delivery.volunteerName ?: "—",
                subValue = null
            )
            ChainConnector()
            ChainStep(
                icon = Icons.Default.Business,
                iconTint = blueColor,
                label = "ONG Beneficiária",
                value = delivery.beneficiaryName?.ifBlank { null } ?: "—",
                subValue = null
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color(0xFFF0F0F0))
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (delivery.deliveredAt.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(delivery.deliveredAt, fontSize = 12.sp, color = Color.LightGray)
                    }
                } else {
                    Text("Validade: ${delivery.expiryDate}", fontSize = 12.sp, color = Color.LightGray)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF06A38), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+10 pontos", color = Color(0xFFF06A38), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ChainStep(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    label: String,
    value: String,
    subValue: String?
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(32.dp).background(iconTint.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(label, fontSize = 11.sp, color = Color.Gray)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF212121))
            if (subValue != null) {
                Text(subValue, fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
private fun ChainConnector() {
    Row {
        Spacer(modifier = Modifier.width(15.dp))
        Box(modifier = Modifier.width(2.dp).height(16.dp).background(Color(0xFFE0E0E0)))
    }
}
