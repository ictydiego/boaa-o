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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
            ExtendedFloatingActionButton(
                onClick = { showReceiveDialog = true },
                containerColor = Color(0xFF4CAF50),
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("RECEBER", fontWeight = FontWeight.Bold)
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (state.isLoading && state.incomingDonations.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = warmPrimaryColor)
                } else if (state.incomingDonations.isEmpty()) {
                    Column(modifier = Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(72.dp), tint = Color(0xFFE0E0E0))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Nenhuma doação a caminho", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Quando um voluntário selecionar esta ONG como destino, a doação aparecerá aqui.",
                            color = Color.LightGray, textAlign = TextAlign.Center, fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.incomingDonations, key = { it.id }) { donation ->
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
            isLoading = state.isLoading,
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
    val infoColor = Color(0xFF1976D2)
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(infoColor.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.LocalShipping, contentDescription = null, tint = infoColor, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Voluntário: ${donation.volunteerName ?: "A Caminho"}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = infoColor)
                    Text(donation.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF212121))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Vindo de: ${donation.donorName}", color = Color.Gray, fontSize = 12.sp)

            if (donation.items.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFFF0F0F0))
                Spacer(modifier = Modifier.height(12.dp))
                donation.items.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(7.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${item.name} — ${item.quantity}", fontSize = 13.sp, color = Color.DarkGray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onConfirmClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Confirmar Recebimento (PIN)")
            }
        }
    }
}

@Composable
fun ReceiveByPinDialog(
    viewModel: BeneficiaryViewModel,
    isLoading: Boolean,
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

    Dialog(onDismissRequest = { if (!isLoading) onDismiss() }) {
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {

                when (step) {
                    0 -> {
                        Box(
                            modifier = Modifier.size(64.dp).clip(CircleShape).background(greenColor.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.VpnKey, contentDescription = null, tint = greenColor, modifier = Modifier.size(32.dp))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Receber Doação", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("Escaneie ou digite o código que o voluntário está mostrando.", textAlign = TextAlign.Center, color = Color.Gray, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = { showQrScanner = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isLooking
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Escanear QR Code")
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            HorizontalDivider(modifier = Modifier.weight(1f))
                            Text("  ou  ", fontSize = 12.sp, color = Color.Gray)
                            HorizontalDivider(modifier = Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = code,
                            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) { code = it; lookupError = null } },
                            placeholder = { Text("0000") },
                            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontSize = 28.sp, letterSpacing = 8.sp),
                            singleLine = true, modifier = Modifier.fillMaxWidth(0.65f),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isLooking
                        )
                        if (lookupError != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(lookupError!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, textAlign = TextAlign.Center)
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = onDismiss, enabled = !isLooking) { Text("Cancelar") }
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
                                colors = ButtonDefaults.buttonColors(containerColor = greenColor),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isLooking) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Text("Verificar")
                            }
                        }
                    }
                    1 -> {
                        foundDonation?.let { donation ->
                            Icon(Icons.Default.Info, contentDescription = null, tint = greenColor, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Confirmar Itens", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Verifique se os itens estão corretos.", color = Color.Gray, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(16.dp))

                            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(donation.title, fontWeight = FontWeight.Bold)
                                    donation.items.forEach { item ->
                                        Text("• ${item.name} (${item.quantity})", fontSize = 13.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))
                            Text("Foto do Recebimento (Opcional)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(8.dp))

                            if (proofBase64.isNotBlank()) {
                                val bitmap = remember(proofBase64) {
                                    val decoded = Base64.decode(proofBase64, Base64.DEFAULT)
                                    BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
                                }
                                Box(modifier = Modifier.size(120.dp).clip(RoundedCornerShape(8.dp))) {
                                    Image(bitmap!!.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                    IconButton(onClick = { proofBase64 = "" }, modifier = Modifier.align(Alignment.TopEnd).background(Color.Black.copy(0.4f), CircleShape).size(24.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedButton(onClick = {
                                        val photoFile = java.io.File(context.cacheDir, "proof_${System.currentTimeMillis()}.jpg")
                                        cameraUri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", photoFile)
                                        cameraLauncher.launch(cameraUri!!)
                                    }, shape = RoundedCornerShape(12.dp)) {
                                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Câmera")
                                    }
                                    OutlinedButton(onClick = { galleryLauncher.launch("image/*") }, shape = RoundedCornerShape(12.dp)) {
                                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Galeria")
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                TextButton(onClick = { step = 0 }, enabled = !isLoading) { Text("Voltar") }
                                Button(
                                    onClick = {
                                        viewModel.confirmDelivery(context, donation, code, proofBase64) { success, err ->
                                            if (success) onConfirmed()
                                            else Toast.makeText(context, err ?: "Erro", Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = greenColor),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = !isLoading
                                ) {
                                    if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    else Text("Confirmar Tudo")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
