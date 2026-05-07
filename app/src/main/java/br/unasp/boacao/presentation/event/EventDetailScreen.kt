package br.unasp.boacao.presentation.event

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import br.unasp.boacao.BoaAcaoApplication
import br.unasp.boacao.domain.model.EventStatus
import br.unasp.boacao.presentation.components.AttendanceStatusChip
import br.unasp.boacao.presentation.components.AttendeeRow
import br.unasp.boacao.presentation.components.EventStatusChip
import br.unasp.boacao.presentation.components.EventWarmPrimary
import br.unasp.boacao.presentation.components.HighlightCard
import br.unasp.boacao.presentation.navigation.InternalRoutes
import br.unasp.boacao.util.FormatUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(navController: NavController, eventId: String) {
    val context = LocalContext.current
    val app = context.applicationContext as BoaAcaoApplication
    val viewModel: EventDetailViewModel = viewModel(
        factory = EventDetailViewModelFactory(
            app.eventRepository, app.attendanceRepository, eventId
        )
    )
    val state by viewModel.uiState.collectAsState()
    val df = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalhes do Evento", color = Color.White, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EventWarmPrimary),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                    }
                }
            )
        },
        floatingActionButton = { /* moved into content for two distinct buttons */ }
    ) { padding ->
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = EventWarmPrimary)
                }
            }
            state.event == null -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(state.error ?: "Evento não encontrado")
                }
            }
            else -> {
                val event = state.event!!
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        event.title,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    EventStatusChip(event.status)
                                }
                                if (event.description.isNotBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(event.description, fontSize = 13.sp, color = Color.DarkGray)
                                }
                            }
                        }
                    }

                    // Info highlights
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HighlightCard(
                                title = "Data",
                                value = df.format(Date(event.startAt)),
                                icon = Icons.Default.CalendarToday,
                                modifier = Modifier.weight(1f)
                            )
                            HighlightCard(
                                title = "Carga horária",
                                value = "${FormatUtils.formatHours(event.workloadHours)}h",
                                icon = Icons.Default.AccessTime,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    if (event.address.isNotBlank()) {
                        item {
                            HighlightCard(
                                title = "Local",
                                value = event.address,
                                icon = Icons.Default.LocationOn,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Status controls
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (event.status == EventStatus.PUBLISHED) {
                                Button(
                                    onClick = { viewModel.setStatus(EventStatus.IN_PROGRESS) },
                                    colors = ButtonDefaults.buttonColors(containerColor = EventWarmPrimary)
                                ) { Text("Iniciar") }
                            }
                            if (event.status == EventStatus.IN_PROGRESS) {
                                Button(
                                    onClick = { viewModel.setStatus(EventStatus.FINISHED) },
                                    colors = ButtonDefaults.buttonColors(containerColor = EventWarmPrimary)
                                ) { Text("Finalizar") }
                            }
                            if (event.status != EventStatus.FINISHED && event.status != EventStatus.CANCELLED) {
                                OutlinedButton(onClick = { viewModel.setStatus(EventStatus.CANCELLED) }) {
                                    Text("Cancelar evento")
                                }
                            }
                        }
                    }

                    // Check-in / Check-out (only when IN_PROGRESS)
                    if (event.status == EventStatus.IN_PROGRESS) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = EventWarmPrimary.copy(alpha = 0.08f)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Registrar presença", fontWeight = FontWeight.Bold, color = EventWarmPrimary)
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { navController.navigate("${InternalRoutes.BENEFICIARY_EVENT_SCANNER}/$eventId/CHECKIN") },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = EventWarmPrimary)
                                        ) {
                                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Check-in")
                                        }
                                        OutlinedButton(
                                            onClick = { navController.navigate("${InternalRoutes.BENEFICIARY_EVENT_SCANNER}/$eventId/CHECKOUT") },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Check-out")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Attendees header
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Group, contentDescription = null, tint = EventWarmPrimary)
                            Spacer(Modifier.height(0.dp))
                            Text(
                                "  Inscritos (${state.attendances.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (state.attendances.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA))
                            ) {
                                Text(
                                    "Nenhum voluntário inscrito ainda.",
                                    modifier = Modifier.padding(16.dp),
                                    color = Color.Gray
                                )
                            }
                        }
                    } else {
                        items(state.attendances) { a ->
                            AttendeeRow(
                                name = a.volunteerName,
                                document = a.volunteerDocument,
                                statusChip = { AttendanceStatusChip(a.status) },
                                note = a.performanceNote
                            )
                        }
                    }

                    // bottom spacer for FAB
                    item { Spacer(Modifier.height(64.dp)) }
                }
            }
        }
    }
}
