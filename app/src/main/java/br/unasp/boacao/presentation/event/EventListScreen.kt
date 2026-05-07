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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import br.unasp.boacao.BoaAcaoApplication
import br.unasp.boacao.presentation.navigation.InternalRoutes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EventListScreen(navController: NavController) {
    val context = LocalContext.current
    val app = context.applicationContext as BoaAcaoApplication
    val viewModel: EventListViewModel = viewModel(
        factory = EventListViewModelFactory(
            app.eventRepository, app.attendanceRepository, app.authRepository
        )
    )
    val state by viewModel.uiState.collectAsState()
    val df = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(state.message, state.error) {
        state.message?.let { snack.showSnackbar(it); viewModel.clearMessages() }
        state.error?.let { snack.showSnackbar(it); viewModel.clearMessages() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Eventos disponíveis", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Button(onClick = { navController.navigate(InternalRoutes.VOLUNTEER_TICKETS) }) {
                    Text("Meus ingressos")
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
                        Text("Nenhum evento publicado no momento.")
                    }
                }
                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.events) { event ->
                            val subscribed = event.id in state.subscribedEventIds
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(event.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("Por: ${event.ngoName}", style = MaterialTheme.typography.bodySmall)
                                    Text("Data: ${df.format(Date(event.startAt))}", style = MaterialTheme.typography.bodySmall)
                                    Text("Carga: ${event.workloadHours}h", style = MaterialTheme.typography.bodySmall)
                                    if (event.description.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(event.description, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    if (subscribed) {
                                        Text("✓ Você está inscrito", color = MaterialTheme.colorScheme.primary)
                                    } else {
                                        Button(onClick = { viewModel.subscribe(event.id) }) {
                                            Text("Inscrever-se")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(snack, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
