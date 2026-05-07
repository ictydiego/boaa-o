package br.unasp.boacao.presentation.event

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import br.unasp.boacao.BoaAcaoApplication
import br.unasp.boacao.presentation.components.SignaturePad

@Composable
fun OngSignatureScreen(navController: NavController) {
    val context = LocalContext.current
    val app = context.applicationContext as BoaAcaoApplication
    val viewModel: OngSignatureViewModel = viewModel(
        factory = OngSignatureViewModelFactory(app.authRepository)
    )
    val state by viewModel.uiState.collectAsState()
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(state.message, state.error) {
        state.message?.let { snack.showSnackbar(it); viewModel.clearMessages() }
        state.error?.let { snack.showSnackbar(it); viewModel.clearMessages() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    "Assinatura da ONG",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Esta assinatura aparecerá nos certificados emitidos para os voluntários ao final dos eventos.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))

                if (state.currentSignatureBase64.isNotBlank()) {
                    Text("Assinatura atual:", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    val bytes = remember(state.currentSignatureBase64) {
                        runCatching { Base64.decode(state.currentSignatureBase64, Base64.NO_WRAP) }.getOrNull()
                    }
                    val bmp = remember(bytes) {
                        bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                    }
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Assinatura atual",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .background(Color.White)
                                .border(1.dp, Color.LightGray)
                                .padding(8.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))
                }

                Text("Nova assinatura:", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                SignaturePad(onSave = { viewModel.save(it) })

                if (state.saving) {
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
        SnackbarHost(snack, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
