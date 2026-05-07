package br.unasp.boacao.presentation.beneficiary

import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import br.unasp.boacao.BoaAcaoApplication
import br.unasp.boacao.domain.model.Donation
import br.unasp.boacao.domain.model.DonationItem
import br.unasp.boacao.presentation.components.QrScannerDialog
import br.unasp.boacao.util.ImageUtils

@Composable
fun BeneficiaryDashboardScreen(navController: NavController) {
    val context = LocalContext.current
    val application = context.applicationContext as BoaAcaoApplication
    val viewModel: BeneficiaryViewModel = viewModel(
        factory = BeneficiaryViewModelFactory(application.beneficiaryRepository, application.pointsRepository)
    )
    val state by viewModel.uiState.collectAsState()
    val warmPrimaryColor = Color(0xFFF06A38)
    var showReceiveDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showReceiveDialog = true },
                containerColor = Color(0xFF4CAF50),
                contentColor = Color.White
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("RECEBER", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Surface(modifier = Modifier.fillMaxWidth(), color = Color.White, shadowElevation = 2.dp) {
                Text(
                    text = "Doações a Caminho",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = warmPrimaryColor,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (state.isLoading && state.incomingDonations.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = warmPrimaryColor)
                } else if (state.incomingDonations.isEmpty()) {
                    Column(modifier = Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Nenhuma doação a caminho.", color = Color.Gray, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Quando um voluntário selecionar esta ONG como destino, a doação aparecerá aqui.\n\nSe o voluntário já está presente, use o botão RECEBER.",
                            color = Color.LightGray, textAlign = TextAlign.Center, fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(state.incomingDonations) { donation ->
                            IncomingDonationCard(donation = donation) { showReceiveDialog = true }
                        }
                    }
                }
            }
        }
    }

    if (showReceiveDialog) {
        ReceiveByPinDialog(
            viewModel = viewModel,
            onDismiss = { showReceiveDialog = false },
            onConfirmed = {
                showReceiveDialog = false
                Toast.makeText(context, "Doação Recebida! Ciclo Completo 🎉 Pontos concedidos!", Toast.LENGTH_LONG).show()
            }
        )
    }
}

@Composable
fun IncomingDonationCard(donation: Donation, onConfirmClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(4.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalShipping, contentDescription = null, tint = Color(0xFF1976D2))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Voluntário: ${donation.volunteerName ?: "A Caminho"}", fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(donation.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Vindo de: ${donation.donorName}", color = Color.Gray, fontSize = 14.sp)

            if (donation.items.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                donation.items.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(8.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("${item.name} — ${item.quantity}", fontSize = 13.sp, color = Color.DarkGray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onConfirmClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Icon(Icons.Default.VerifiedUser, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Confirmar Recebimento (PIN)")
            }
        }
    }
}

@Composable
fun ReceiveByPinDialog(
    viewModel: BeneficiaryViewModel,
    onDismiss: () -> Unit,
    onConfirmed: () -> Unit
) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    var code by remember { mutableStateOf("") }
    var foundDonation by remember { mutableStateOf<Donation?>(null) }
    var proofBase64 by remember { mutableStateOf("") }
    var isLooking by remember { mutableStateOf(false) }
    var lookupError by remember { mutableStateOf<String?>(null) }
    var showQrScanner by remember { mutableStateOf(false) }
    val greenColor = Color(0xFF4CAF50)

    if (showQrScanner) {
        QrScannerDialog(
            title = "Escanear Código do Voluntário",
            hint = "Aponte para o QR Code exibido na tela do voluntário",
            onScanned = { value ->
                val digits = value.filter { it.isDigit() }.take(4)
                if (digits.length == 4) {
                    code = digits
                    isLooking = true
                    lookupError = null
                    viewModel.lookupDonationByDeliveryCode(digits) { donation, error ->
                        isLooking = false
                        if (donation != null) {
                            foundDonation = donation
                            step = 1
                        } else {
                            lookupError = error ?: "Código não encontrado."
                        }
                    }
                }
            },
            onDismiss = { showQrScanner = false }
        )
    }

    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            cameraUri?.let { uri ->
                val base64 = ImageUtils.uriToBase64(context, uri, 800, 600, 60)
                if (base64 != null) proofBase64 = base64
            }
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val base64 = ImageUtils.uriToBase64(context, it, 800, 600, 60)
            if (base64 != null) proofBase64 = base64
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {

                when (step) {
                    // ---------- STEP 0: Enter delivery code ----------
                    0 -> {
                        Icon(Icons.Default.VpnKey, contentDescription = null, tint = greenColor, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Receber Doação", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("Escaneie ou digite o código que o voluntário está mostrando.", textAlign = TextAlign.Center, color = Color.Gray, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(16.dp))

                        // QR scan button
                        Button(
                            onClick = { showQrScanner = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Escanear QR Code")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("ou digite manualmente:", fontSize = 12.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = code,
                            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) { code = it; lookupError = null } },
                            placeholder = { Text("0000") },
                            label = { Text("Código do Voluntário") },
                            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontSize = 28.sp, letterSpacing = 8.sp),
                            singleLine = true, modifier = Modifier.fillMaxWidth(0.65f)
                        )
                        if (lookupError != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(lookupError!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, textAlign = TextAlign.Center)
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = onDismiss) { Text("Cancelar") }
                            Button(
                                onClick = {
                                    isLooking = true
                                    lookupError = null
                                    viewModel.lookupDonationByDeliveryCode(code) { donation, error ->
                                        isLooking = false
                                        if (donation != null) {
                                            foundDonation = donation
                                            step = 1
                                        } else {
                                            lookupError = error ?: "Código não encontrado."
                                        }
                                    }
                                },
                                enabled = code.length == 4 && !isLooking,
                                colors = ButtonDefaults.buttonColors(containerColor = greenColor)
                            ) {
                                if (isLooking) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                                else Text("Verificar →")
                            }
                        }
                    }

                    // ---------- STEP 1: Preview + Confirm ----------
                    1 -> {
                        val donation = foundDonation!!
                        Icon(Icons.Default.Inventory, contentDescription = null, tint = greenColor, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Você está recebendo:", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = greenColor)
                        Spacer(modifier = Modifier.height(10.dp))

                        // Donation info card
                        Surface(color = Color(0xFFF5F5F5), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(donation.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Doador: ${donation.donorName}", fontSize = 13.sp, color = Color.Gray)
                                Text("Voluntário: ${donation.volunteerName ?: "—"}", fontSize = 13.sp, color = Color.Gray)
                                if (donation.items.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Itens:", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                                    donation.items.forEach { item ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = greenColor, modifier = Modifier.size(7.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("${item.name} — ${item.quantity}", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Proof photo
                        if (proofBase64.isNotBlank()) {
                            val bytes = Base64.decode(proofBase64, Base64.DEFAULT)
                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Comprovante",
                                    modifier = Modifier.fillMaxWidth().height(120.dp).background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                val uri = ImageUtils.createTempImageUri(context)
                                cameraUri = uri
                                cameraLauncher.launch(uri)
                            }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (proofBase64.isBlank()) "Câmera" else "Retomar", fontSize = 12.sp)
                            }
                            OutlinedButton(onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Photo, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Galeria", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { step = 0; code = "" }) { Text("← Voltar") }
                            Button(
                                onClick = {
                                    viewModel.confirmDelivery(context, donation, code, proofBase64) { success, errorMsg ->
                                        if (success) onConfirmed()
                                        else Toast.makeText(context, errorMsg ?: "Erro", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = greenColor)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Confirmar Recebimento")
                            }
                        }
                    }
                }
            }
        }
    }
}
