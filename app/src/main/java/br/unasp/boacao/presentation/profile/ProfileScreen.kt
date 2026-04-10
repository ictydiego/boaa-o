package br.unasp.boacao.presentation.profile

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import br.unasp.boacao.BoaAcaoApplication
import br.unasp.boacao.domain.model.UserRole

@Composable
fun ProfileScreen(navController: NavController) {
    val context = LocalContext.current
    val application = context.applicationContext as BoaAcaoApplication
    val viewModel: ProfileViewModel = viewModel(factory = ProfileViewModelFactory(application.authRepository))
    val state by viewModel.uiState.collectAsState()
    val warmColor = Color(0xFFF06A38)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.uploadPhoto(context, it) }
    }

    LaunchedEffect(state.successMessage, state.error) {
        if (state.successMessage != null || state.error != null) {
            kotlinx.coroutines.delay(2500)
            viewModel.clearMessages()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (state.isLoading) {
            CircularProgressIndicator(color = warmColor, modifier = Modifier.padding(top = 80.dp))
            return@Column
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Avatar
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Color(0xFFF0F0F0))
                .clickable { launcher.launch("image/*") },
            contentAlignment = Alignment.Center
        ) {
            val photoBase64 = state.profile.photoBase64
            if (photoBase64.isNotBlank()) {
                val bytes = Base64.decode(photoBase64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Foto de perfil",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            } else {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.Gray)
            }
            if (state.isSaving) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = { launcher.launch("image/*") }) {
            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = warmColor, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Alterar foto", color = warmColor, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Points badge
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = warmColor)
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("${state.profile.points} pontos", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        ProfileInfoRow(Icons.Default.Person, "Nome", state.profile.name)
        ProfileInfoRow(Icons.Default.Email, "E-mail", state.profile.email)
        ProfileInfoRow(Icons.Default.Badge, "Documento", state.profile.document)
        if (state.profile.role == UserRole.DONOR) {
            ProfileInfoRow(Icons.Default.LocationOn, "Endereço", state.profile.address)
            ProfileInfoRow(Icons.Default.Inventory, "Doações realizadas", "${state.profile.donationCount}")
        }
        ProfileInfoRow(
            Icons.Default.VolunteerActivism, "Perfil",
            when (state.profile.role) {
                UserRole.DONOR -> "Empresa Doadora"
                UserRole.VOLUNTEER -> "Voluntário"
                UserRole.BENEFICIARY -> "ONG / Beneficiário"
            }
        )

        state.successMessage?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50))) {
                Text(it, color = Color.White, modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Medium)
            }
        }
        state.error?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE53935))) {
                Text(it, color = Color.White, modifier = Modifier.padding(12.dp))
            }
        }
    }
}

@Composable
private fun ProfileInfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFFF06A38), modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(label, fontSize = 11.sp, color = Color.Gray)
            Text(value.ifBlank { "—" }, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
    HorizontalDivider(color = Color(0xFFF0F0F0))
}
