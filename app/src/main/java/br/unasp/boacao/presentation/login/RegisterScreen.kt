package br.unasp.boacao.presentation.login

import android.Manifest
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import br.unasp.boacao.BoaAcaoApplication
import br.unasp.boacao.domain.model.UserRole
import br.unasp.boacao.util.GeocodeUtils
import br.unasp.boacao.util.ImageUtils
import br.unasp.boacao.util.LocationUtils
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun RegisterScreen(
    onNavigateToDashboard: (UserRole) -> Unit,
    onBackToLogin: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as BoaAcaoApplication
    val repository = application.authRepository
    val viewModel: RegisterViewModel = viewModel(factory = RegisterViewModelFactory(repository))
    val state by viewModel.uiState.collectAsState()
    val warmPrimaryColor = Color(0xFFF06A38)
    var expandedDropdown by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            cameraUri?.let { uri ->
                val base64 = ImageUtils.uriToBase64(context, uri, maxWidth = 400, maxHeight = 400, quality = 70)
                if (base64 != null) viewModel.onPhotoChange(base64)
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val base64 = ImageUtils.uriToBase64(context, it, maxWidth = 400, maxHeight = 400, quality = 70)
            if (base64 != null) viewModel.onPhotoChange(base64)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text("Criar Conta", style = MaterialTheme.typography.headlineLarge, color = warmPrimaryColor, fontWeight = FontWeight.Bold)
        Text("Junte-se a nós e faça a diferença", color = Color.Gray, modifier = Modifier.padding(bottom = 24.dp))

        // ---- PHOTO SECTION (obrigatório) ----
        Text("Foto de Perfil *", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.align(Alignment.Start))
        Spacer(modifier = Modifier.height(8.dp))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
            if (state.photoBase64.isNotBlank()) {
                val bytes = Base64.decode(state.photoBase64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Foto de perfil",
                        modifier = Modifier.size(100.dp).clip(CircleShape).border(2.dp, warmPrimaryColor, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
            } else {
                Box(
                    modifier = Modifier.size(100.dp).clip(CircleShape)
                        .background(Color(0xFFF5F5F5)).border(2.dp, Color.LightGray, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(48.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                if (cameraPermission.status.isGranted) {
                    val uri = ImageUtils.createTempImageUri(context)
                    cameraUri = uri
                    cameraLauncher.launch(uri)
                } else {
                    cameraPermission.launchPermissionRequest()
                }
            }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Câmera", fontSize = 12.sp)
            }
            OutlinedButton(onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Photo, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Galeria", fontSize = 12.sp)
            }
        }
        if (state.photoBase64.isBlank()) {
            Text("Obrigatório — tire ou escolha uma foto", fontSize = 11.sp, color = Color(0xFFD32F2F), modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(modifier = Modifier.height(20.dp))

        // ---- ROLE SELECTOR ----
        ExposedDropdownMenuBox(
            expanded = expandedDropdown, onExpandedChange = { expandedDropdown = !expandedDropdown }, modifier = Modifier.padding(bottom = 16.dp)
        ) {
            OutlinedTextField(
                value = when (state.selectedRole) {
                    UserRole.DONOR -> "Doador (Empresa/Pessoa)"
                    UserRole.VOLUNTEER -> "Voluntário (Coletor)"
                    UserRole.BENEFICIARY -> "Beneficiário (ONG/Abrigo)"
                },
                onValueChange = {}, readOnly = true, label = { Text("Qual o seu perfil?") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDropdown) },
                modifier = Modifier.menuAnchor().fillMaxWidth(), shape = RoundedCornerShape(12.dp)
            )
            ExposedDropdownMenu(expanded = expandedDropdown, onDismissRequest = { expandedDropdown = false }) {
                DropdownMenuItem(text = { Text("Doador (Empresa/Pessoa)") }, onClick = { viewModel.onRoleChange(UserRole.DONOR); expandedDropdown = false })
                DropdownMenuItem(text = { Text("Voluntário (Coletor)") }, onClick = { viewModel.onRoleChange(UserRole.VOLUNTEER); expandedDropdown = false })
                DropdownMenuItem(text = { Text("Beneficiário (ONG/Abrigo)") }, onClick = { viewModel.onRoleChange(UserRole.BENEFICIARY); expandedDropdown = false })
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = !state.isCnpj, onClick = { viewModel.onDocumentTypeChange(false) }, colors = RadioButtonDefaults.colors(selectedColor = warmPrimaryColor))
                Text("CPF")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = state.isCnpj, onClick = { viewModel.onDocumentTypeChange(true) }, colors = RadioButtonDefaults.colors(selectedColor = warmPrimaryColor))
                Text("CNPJ")
            }
        }

        OutlinedTextField(
            value = state.document, onValueChange = viewModel::onDocumentChange, label = { Text(if (state.isCnpj) "CNPJ" else "CPF") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), visualTransformation = CpfCnpjVisualTransformation(state.isCnpj)
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = state.name, onValueChange = viewModel::onNameChange, label = { Text(if (state.isCnpj) "Razão Social / Nome da ONG" else "Nome Completo") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))

        AnimatedVisibility(visible = state.selectedRole == UserRole.DONOR || state.selectedRole == UserRole.BENEFICIARY) {
            val locationPermission = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
            var isLocating by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            Column {
                OutlinedTextField(
                    value = state.address, onValueChange = viewModel::onAddressChange,
                    label = { Text(if (state.selectedRole == UserRole.DONOR) "Endereço Completo de Retirada" else "Endereço da ONG/Abrigo") },
                    placeholder = { Text("Rua, Número, Bairro, Cidade") },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        if (isLocating) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = warmPrimaryColor)
                        } else {
                            IconButton(onClick = {
                                if (locationPermission.status.isGranted) {
                                    scope.launch {
                                        isLocating = true
                                        val latLng = LocationUtils.getCurrentLocation(context)
                                        if (latLng != null) {
                                            val address = GeocodeUtils.reverseGeocode(context, latLng.latitude, latLng.longitude)
                                            if (address != null) viewModel.onAddressChange(address)
                                        }
                                        isLocating = false
                                    }
                                } else {
                                    locationPermission.launchPermissionRequest()
                                }
                            }) {
                                Icon(Icons.Default.MyLocation, contentDescription = "Usar localização atual", tint = warmPrimaryColor)
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        OutlinedTextField(
            value = state.email, onValueChange = viewModel::onEmailChange, label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = state.password, onValueChange = viewModel::onPasswordChange, label = { Text("Senha") },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { IconButton(onClick = { passwordVisible = !passwordVisible }) { Icon(if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, null) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
        )

        if (state.error != null) { Text(state.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = { viewModel.register(onNavigateToDashboard) }, modifier = Modifier.fillMaxWidth().height(55.dp),
            enabled = !state.isLoading && state.photoBase64.isNotBlank(),
            shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = warmPrimaryColor)
        ) {
            if (state.isLoading) CircularProgressIndicator(color = Color.White) else Text("Cadastrar", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onBackToLogin) { Text("Já tem conta? Entrar", color = warmPrimaryColor, fontWeight = FontWeight.Medium) }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

class CpfCnpjVisualTransformation(private val isCnpj: Boolean) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val trimmed = if (text.text.length >= (if (isCnpj) 14 else 11)) text.text.substring(0, if (isCnpj) 14 else 11) else text.text
        var out = ""
        for (i in trimmed.indices) {
            out += trimmed[i]
            if (isCnpj) { if (i == 1 || i == 4) out += "."; if (i == 7) out += "/"; if (i == 11) out += "-" }
            else { if (i == 2 || i == 5) out += "."; if (i == 8) out += "-" }
        }
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (isCnpj) { return if (offset <= 1) offset else if (offset <= 4) offset + 1 else if (offset <= 7) offset + 2 else if (offset <= 11) offset + 3 else if (offset <= 14) offset + 4 else 18 }
                else { return if (offset <= 2) offset else if (offset <= 5) offset + 1 else if (offset <= 8) offset + 2 else if (offset <= 11) offset + 3 else 14 }
            }
            override fun transformedToOriginal(offset: Int): Int {
                if (isCnpj) { return if (offset <= 2) offset else if (offset <= 6) offset - 1 else if (offset <= 10) offset - 2 else if (offset <= 15) offset - 3 else if (offset <= 18) offset - 4 else 14 }
                else { return if (offset <= 3) offset else if (offset <= 7) offset - 1 else if (offset <= 11) offset - 2 else if (offset <= 14) offset - 3 else 11 }
            }
        }
        return TransformedText(AnnotatedString(out), offsetMapping)
    }
}
