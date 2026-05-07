package br.unasp.boacao.presentation.event

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Draw
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
import br.unasp.boacao.domain.model.EventStatus
import br.unasp.boacao.presentation.navigation.InternalRoutes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NgoEventsScreen(navController: NavController) {
    val context = LocalContext.current
    val app = context.applicationContext as BoaAcaoApplication
    val viewModel: NgoEventsViewModel = viewModel(
        factory = NgoEventsViewModelFactory(app.eventRepository, app.authRepository)
    )
    val state by viewModel.uiState.collectAsState()
    val warmPrimary = Color(0xFFF06A38)
    val df = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Meus Eventos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = {
                    navController.navigate(InternalRoutes.BENEFICIARY_SIGNATURE)
                }) {
                    Icon(Icons.Default.Draw, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Assinatura")
                }
            }
            Spacer(Modifier.height(12.dp))

            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.events.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Nenhum evento criado ainda.\nUse o botão + para criar.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.events) { event ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        navController.navigate("${InternalRoutes.BENEFICIARY_EVENT_DETAIL}/${event.id}")
                                    }
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(event.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(4.dp))
                                    Text("Data: ${df.format(Date(event.startAt))}", style = MaterialTheme.typography.bodySmall)
                                    Text("Carga: ${event.workloadHours}h", style = MaterialTheme.typography.bodySmall)
                                    Spacer(Modifier.height(4.dp))
                                    StatusBadge(event.status)
                                }
                            }
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { navController.navigate(InternalRoutes.BENEFICIARY_EVENT_CREATE) },
            text = { Text("Criar Evento") },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            containerColor = warmPrimary,
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
    }
}

@Composable
private fun StatusBadge(status: EventStatus) {
    val (label, bg) = when (status) {
        EventStatus.PUBLISHED -> "Publicado" to Color(0xFF1976D2)
        EventStatus.IN_PROGRESS -> "Em andamento" to Color(0xFF2E7D32)
        EventStatus.FINISHED -> "Finalizado" to Color(0xFF616161)
        EventStatus.CANCELLED -> "Cancelado" to Color(0xFFC62828)
    }
    Box(
        modifier = Modifier
            .background(bg, shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}
