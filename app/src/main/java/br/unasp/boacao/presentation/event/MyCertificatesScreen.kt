package br.unasp.boacao.presentation.event

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import br.unasp.boacao.BoaAcaoApplication
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MyCertificatesScreen(navController: NavController) {
    val context = LocalContext.current
    val app = context.applicationContext as BoaAcaoApplication
    val viewModel: MyCertificatesViewModel = viewModel(
        factory = MyCertificatesViewModelFactory(
            app.attendanceRepository, app.eventRepository, app.authRepository
        )
    )
    val state by viewModel.uiState.collectAsState()
    val df = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Meus Certificados", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.rows.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Você ainda não possui certificados emitidos.")
                    }
                }
                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.rows) { row ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(row.event?.title ?: "Evento", fontWeight = FontWeight.Bold)
                                    row.event?.let {
                                        Text("Por: ${it.ngoName}", style = MaterialTheme.typography.bodySmall)
                                        Text("Carga: ${it.workloadHours}h", style = MaterialTheme.typography.bodySmall)
                                        Text("Data: ${df.format(Date(it.startAt))}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text(
                                        "Hash: ${row.attendance.certificateHash.take(16)}…",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Button(onClick = {
                                        viewModel.openCertificate(
                                            context = context,
                                            row = row,
                                            onReady = { uri ->
                                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(uri, "application/pdf")
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                runCatching {
                                                    context.startActivity(Intent.createChooser(intent, "Abrir certificado"))
                                                }.onFailure {
                                                    scope.launch { snack.showSnackbar("Nenhum app de PDF instalado") }
                                                }
                                            },
                                            onError = { msg -> scope.launch { snack.showSnackbar(msg) } }
                                        )
                                    }) { Text("Abrir certificado (PDF)") }
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
