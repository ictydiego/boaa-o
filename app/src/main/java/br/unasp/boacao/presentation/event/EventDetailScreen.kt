package br.unasp.boacao.presentation.event

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import br.unasp.boacao.BoaAcaoApplication
import br.unasp.boacao.domain.model.AttendanceStatus
import br.unasp.boacao.domain.model.EventStatus
import br.unasp.boacao.presentation.navigation.InternalRoutes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val warmPrimary = Color(0xFFF06A38)

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val event = state.event
    if (event == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(state.error ?: "Evento não encontrado")
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(event.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Status: ${labelFor(event.status)}", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text("Data: ${df.format(Date(event.startAt))}", style = MaterialTheme.typography.bodyMedium)
            if (event.endAt > 0) {
                Text("Término: ${df.format(Date(event.endAt))}", style = MaterialTheme.typography.bodyMedium)
            }
            Text("Carga horária: ${event.workloadHours}h", style = MaterialTheme.typography.bodyMedium)
            if (event.address.isNotBlank()) {
                Text("Local: ${event.address}", style = MaterialTheme.typography.bodyMedium)
            }
            if (event.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(event.description, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (event.status == EventStatus.PUBLISHED) {
                    Button(onClick = { viewModel.setStatus(EventStatus.IN_PROGRESS) }) { Text("Iniciar") }
                }
                if (event.status == EventStatus.IN_PROGRESS) {
                    Button(onClick = { viewModel.setStatus(EventStatus.FINISHED) }) { Text("Finalizar") }
                }
                if (event.status != EventStatus.FINISHED && event.status != EventStatus.CANCELLED) {
                    OutlinedButton(onClick = { viewModel.setStatus(EventStatus.CANCELLED) }) { Text("Cancelar") }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Inscritos (${state.attendances.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))

            if (state.attendances.isEmpty()) {
                Text("Nenhum voluntário inscrito.", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(state.attendances) { a ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp)) {
                                Text(a.volunteerName, fontWeight = FontWeight.Bold)
                                Text("CPF: ${a.volunteerDocument}", style = MaterialTheme.typography.bodySmall)
                                Text("Status: ${labelFor(a.status)}", style = MaterialTheme.typography.bodySmall)
                                if (a.performanceNote.isNotBlank()) {
                                    Text("Obs: ${a.performanceNote}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (event.status == EventStatus.IN_PROGRESS || event.status == EventStatus.PUBLISHED) {
            ExtendedFloatingActionButton(
                onClick = {
                    navController.navigate("${InternalRoutes.BENEFICIARY_EVENT_SCANNER}/$eventId")
                },
                text = { Text("Escanear QR") },
                icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                containerColor = warmPrimary,
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            )
        }
    }
}

private fun labelFor(status: EventStatus): String = when (status) {
    EventStatus.PUBLISHED -> "Publicado"
    EventStatus.IN_PROGRESS -> "Em andamento"
    EventStatus.FINISHED -> "Finalizado"
    EventStatus.CANCELLED -> "Cancelado"
}

private fun labelFor(status: AttendanceStatus): String = when (status) {
    AttendanceStatus.SUBSCRIBED -> "Inscrito"
    AttendanceStatus.CHECKED_IN -> "Check-in"
    AttendanceStatus.CHECKED_OUT -> "Concluído"
    AttendanceStatus.NO_SHOW -> "Não compareceu"
}
