package br.unasp.boacao.presentation.event

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import br.unasp.boacao.BoaAcaoApplication
import br.unasp.boacao.presentation.components.QrScannerView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun EventScannerScreen(navController: NavController, eventId: String) {
    val context = LocalContext.current
    val app = context.applicationContext as BoaAcaoApplication
    val viewModel: EventScannerViewModel = viewModel(
        factory = EventScannerViewModelFactory(app.attendanceRepository, eventId)
    )
    val step by viewModel.step.collectAsState()
    val cameraPerm = rememberPermissionState(Manifest.permission.CAMERA)
    var note by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (!cameraPerm.status.isGranted) cameraPerm.launchPermissionRequest()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "Leitor de Tickets",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))

        when (val s = step) {
            ScannerStep.Scanning -> {
                if (cameraPerm.status.isGranted) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp)
                    ) {
                        QrScannerView(onScanned = { viewModel.onScanned(it) })
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Aponte para o QR do voluntário. O check-in é automático na primeira leitura. Leia novamente para registrar o check-out.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text("Permissão de câmera necessária.")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { cameraPerm.launchPermissionRequest() }) {
                        Text("Conceder permissão")
                    }
                }
            }
            ScannerStep.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is ScannerStep.CheckedIn -> {
                Text("Check-in registrado!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Voluntário: ${s.attendance.volunteerName}")
                Spacer(Modifier.height(16.dp))
                Text("Quando ele finalizar a atividade, escaneie novamente para o check-out.")
                Spacer(Modifier.height(16.dp))
                Button(onClick = { note = ""; viewModel.reset() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Próximo")
                }
            }
            is ScannerStep.AwaitingCheckout -> {
                Text("Pronto para check-out", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Observação (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.confirmCheckout(s.ticketCode, note) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Confirmar check-out") }
                    OutlinedButton(
                        onClick = { note = ""; viewModel.reset() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Cancelar") }
                }
            }
            is ScannerStep.Done -> {
                Text(s.message, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { note = ""; viewModel.reset() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Escanear próximo")
                }
            }
            is ScannerStep.Error -> {
                Text(s.message, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { viewModel.reset() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Tentar novamente")
                }
            }
        }
    }
}
