package br.unasp.boacao.presentation.event

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import br.unasp.boacao.presentation.components.EventWarmPrimary
import br.unasp.boacao.presentation.components.QrScannerView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

private val PRESET_PHRASES = listOf(
    "Excelente atuação",
    "Pontual e dedicado",
    "Trabalhou em equipe",
    "Proativo",
    "Comunicativo",
    "Boa comunicação com beneficiários",
    "Precisa de pontualidade"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun EventScannerScreen(navController: NavController, eventId: String, mode: ScannerMode) {
    val context = LocalContext.current
    val app = context.applicationContext as BoaAcaoApplication
    val viewModel: EventScannerViewModel = viewModel(
        key = "scanner-$eventId-$mode",
        factory = EventScannerViewModelFactory(app.attendanceRepository, eventId, mode)
    )
    val step by viewModel.step.collectAsState()
    val cameraPerm = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!cameraPerm.status.isGranted) cameraPerm.launchPermissionRequest()
    }

    val title = if (mode == ScannerMode.CHECKIN) "Check-in" else "Check-out"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, color = Color.White, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EventWarmPrimary),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val s = step) {
                ScannerStep.Scanning -> {
                    if (cameraPerm.status.isGranted) {
                        ScannerWithFrame(
                            mode = mode,
                            onScanned = { viewModel.onScanned(it) }
                        )
                    } else {
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Permissão de câmera necessária.", textAlign = TextAlign.Center)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { cameraPerm.launchPermissionRequest() }) { Text("Conceder permissão") }
                        }
                    }
                }
                ScannerStep.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = EventWarmPrimary)
                    }
                }
                is ScannerStep.CheckedInSuccess -> {
                    NextOrCloseDialog(
                        volunteerName = s.attendance.volunteerName,
                        onNext = { viewModel.reset() },
                        onClose = { navController.popBackStack() }
                    )
                }
                is ScannerStep.CollectFeedback -> {
                    CheckoutFeedbackForm(
                        onConfirm = { note, rating ->
                            viewModel.confirmCheckOut(s.ticket, note, rating)
                        },
                        onCancel = { viewModel.reset() }
                    )
                }
                is ScannerStep.Done -> {
                    DoneScreen(
                        message = s.message,
                        showNext = mode == ScannerMode.CHECKIN || mode == ScannerMode.CHECKOUT,
                        onNext = { viewModel.reset() },
                        onClose = { navController.popBackStack() }
                    )
                }
                is ScannerStep.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(s.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { navController.popBackStack() }) { Text("Sair") }
                            Button(
                                onClick = { viewModel.reset() },
                                colors = ButtonDefaults.buttonColors(containerColor = EventWarmPrimary)
                            ) { Text("Tentar novamente") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScannerWithFrame(mode: ScannerMode, onScanned: (String) -> Unit) {
    val instruction = if (mode == ScannerMode.CHECKIN) {
        "Aponte a câmera para o QR do voluntário para registrar a entrada."
    } else {
        "Aponte para o QR do voluntário para iniciar o check-out e avaliação."
    }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Live scanner behind everything
        QrScannerView(onScanned = onScanned)

        // Centered border frame (transparent middle so camera is visible)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(260.dp)
                .border(3.dp, EventWarmPrimary, RoundedCornerShape(16.dp))
        )

        // Top header pill
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Text(
                if (mode == ScannerMode.CHECKIN) "ENCAIXE O QR PARA CHECK-IN" else "ENCAIXE O QR PARA CHECK-OUT",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // Bottom hint pill
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp, start = 24.dp, end = 24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                instruction,
                color = Color.White,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun NextOrCloseDialog(
    volunteerName: String,
    onNext: () -> Unit,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* require choice */ },
        icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32)) },
        title = { Text("Check-in confirmado") },
        text = {
            Text("$volunteerName está presente. Escanear próximo voluntário?")
        },
        confirmButton = {
            Button(
                onClick = onNext,
                colors = ButtonDefaults.buttonColors(containerColor = EventWarmPrimary)
            ) { Text("Próximo") }
        },
        dismissButton = {
            OutlinedButton(onClick = onClose) { Text("Fechar") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CheckoutFeedbackForm(
    onConfirm: (note: String, rating: Int) -> Unit,
    onCancel: () -> Unit
) {
    var rating by remember { mutableIntStateOf(5) }
    val selectedPhrases = remember { mutableStateOf<Set<String>>(emptySet()) }
    var freeNote by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Avaliar voluntário", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = EventWarmPrimary.copy(alpha = 0.10f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Nota geral", fontWeight = FontWeight.SemiBold, color = EventWarmPrimary)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (i in 1..5) {
                            Icon(
                                imageVector = if (i <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = null,
                                tint = if (i <= rating) Color(0xFFFFC107) else Color.LightGray,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clickable { rating = i }
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "$rating estrela${if (rating > 1) "s" else ""} = ${rating * 10} pontos",
                        fontSize = 12.sp,
                        color = Color.DarkGray
                    )
                }
            }
        }
        item {
            Text("Selecione frases (opcional):", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
        item {
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PRESET_PHRASES.forEach { phrase ->
                    val isSelected = phrase in selectedPhrases.value
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedPhrases.value = if (isSelected) selectedPhrases.value - phrase
                            else selectedPhrases.value + phrase
                        },
                        label = { Text(phrase, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EventWarmPrimary.copy(alpha = 0.20f),
                            selectedLabelColor = EventWarmPrimary
                        )
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = freeNote,
                onValueChange = { freeNote = it },
                label = { Text("Observação adicional (opcional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) { Text("Cancelar") }
                Button(
                    onClick = {
                        val merged = buildString {
                            if (selectedPhrases.value.isNotEmpty()) {
                                append(selectedPhrases.value.joinToString(", "))
                            }
                            if (freeNote.isNotBlank()) {
                                if (isNotEmpty()) append(". ")
                                append(freeNote.trim())
                            }
                        }
                        onConfirm(merged, rating)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = EventWarmPrimary)
                ) { Text("Confirmar check-out") }
            }
        }
    }
}

@Composable
private fun DoneScreen(
    message: String,
    showNext: Boolean,
    onNext: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF2E7D32),
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(message, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showNext) {
                Button(
                    onClick = onNext,
                    colors = ButtonDefaults.buttonColors(containerColor = EventWarmPrimary)
                ) { Text("Próximo") }
            }
            OutlinedButton(onClick = onClose) { Text("Fechar") }
        }
    }
}
