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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import br.unasp.boacao.BoaAcaoApplication
import br.unasp.boacao.domain.model.AttendanceStatus
import br.unasp.boacao.presentation.navigation.InternalRoutes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MyTicketsScreen(navController: NavController) {
    val context = LocalContext.current
    val app = context.applicationContext as BoaAcaoApplication
    val viewModel: MyTicketsViewModel = viewModel(
        factory = MyTicketsViewModelFactory(
            app.attendanceRepository, app.eventRepository, app.authRepository
        )
    )
    val state by viewModel.uiState.collectAsState()
    val df = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Meus Ingressos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.rows.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Você ainda não está inscrito em nenhum evento.")
                }
            }
            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.rows) { row ->
                        val a = row.attendance
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(row.event?.title ?: "Evento", fontWeight = FontWeight.Bold)
                                row.event?.let {
                                    Text("Por: ${it.ngoName}", style = MaterialTheme.typography.bodySmall)
                                    Text("Data: ${df.format(Date(it.startAt))}", style = MaterialTheme.typography.bodySmall)
                                }
                                Text("Status: ${labelFor(a.status)}", style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    when (a.status) {
                                        AttendanceStatus.SUBSCRIBED, AttendanceStatus.CHECKED_IN -> {
                                            Button(onClick = {
                                                navController.navigate("${InternalRoutes.VOLUNTEER_TICKET_QR}/${a.ticketCode}")
                                            }) { Text("Ver QR") }
                                        }
                                        AttendanceStatus.CHECKED_OUT -> {
                                            OutlinedButton(onClick = {
                                                navController.navigate(InternalRoutes.VOLUNTEER_CERTIFICATES)
                                            }) { Text("Certificado") }
                                        }
                                        AttendanceStatus.NO_SHOW -> {
                                            Text("Não compareceu", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun labelFor(status: AttendanceStatus): String = when (status) {
    AttendanceStatus.SUBSCRIBED -> "Inscrito"
    AttendanceStatus.CHECKED_IN -> "Check-in feito"
    AttendanceStatus.CHECKED_OUT -> "Concluído"
    AttendanceStatus.NO_SHOW -> "Não compareceu"
}
