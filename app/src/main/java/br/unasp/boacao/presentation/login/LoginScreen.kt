package br.unasp.boacao.presentation.login

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import br.unasp.boacao.BoaAcaoApplication
import br.unasp.boacao.BuildConfig
import br.unasp.boacao.domain.model.UserRole
import br.unasp.boacao.R // Importante para puxar o R.drawable.logo
import br.unasp.boacao.security.BiometricCredentialStore
import br.unasp.boacao.security.StoredLoginCredentials

private data class PendingBiometricSetup(
    val role: UserRole,
    val credentials: StoredLoginCredentials
)

@Composable
fun LoginScreen(
    onNavigateToDashboard: (UserRole) -> Unit,
    onNavigateToRegister: () -> Unit
) {
    // --- INJEÇÃO MANUAL ---
    val context = LocalContext.current
    val application = context.applicationContext as BoaAcaoApplication
    val repository = application.authRepository
    val biometricCredentialStore = remember(context.applicationContext) {
        BiometricCredentialStore(context.applicationContext)
    }

    val viewModel: LoginViewModel = viewModel(
        factory = LoginViewModelFactory(repository)
    )
    // ----------------------

    val state by viewModel.uiState.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }
    var canUseBiometricLogin by remember { mutableStateOf(biometricCredentialStore.canUseBiometricLogin()) }
    var pendingBiometricSetup by remember { mutableStateOf<PendingBiometricSetup?>(null) }

    fun navigateAfterLogin(role: UserRole) {
        canUseBiometricLogin = biometricCredentialStore.canUseBiometricLogin()
        onNavigateToDashboard(role)
    }

    fun handlePasswordLoginSuccess(role: UserRole, email: String, password: String) {
        val credentials = StoredLoginCredentials(email = email, password = password)

        if (biometricCredentialStore.hasCredentials()) {
            biometricCredentialStore.save(credentials)
            navigateAfterLogin(role)
            return
        }

        if (biometricCredentialStore.canAuthenticateWithBiometrics()) {
            pendingBiometricSetup = PendingBiometricSetup(role, credentials)
        } else {
            navigateAfterLogin(role)
        }
    }

    fun authenticateWithBiometrics() {
        val activity = context.findFragmentActivity()
        if (activity == null) {
            viewModel.showError("Biometria indisponivel nesta tela.")
            return
        }

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    biometricCredentialStore.read().onSuccess { credentials ->
                        viewModel.loginWithSavedCredentials(
                            credentials.email,
                            credentials.password
                        ) { role -> navigateAfterLogin(role) }
                    }.onFailure {
                        canUseBiometricLogin = false
                        viewModel.showError("Login por digital expirou. Entre com email e senha.")
                    }
                }

                override fun onAuthenticationFailed() {
                    viewModel.showError("Digital nao reconhecida. Tente novamente.")
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_CANCELED
                    ) {
                        viewModel.showError(errString.toString())
                    }
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Entrar com digital")
            .setSubtitle("Use sua biometria para acessar o Boa Acao")
            .setNegativeButtonText("Usar senha")
            .build()

        prompt.authenticate(promptInfo)
    }

    // Cor acolhedora para o tema de doação (Laranja Coral)
    val warmPrimaryColor = Color(0xFFF06A38)

    pendingBiometricSetup?.let { pending ->
        AlertDialog(
            onDismissRequest = {
                pendingBiometricSetup = null
                navigateAfterLogin(pending.role)
            },
            title = { Text("Ativar login por digital?") },
            text = { Text("Na proxima vez, voce podera entrar neste aparelho usando biometria.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        biometricCredentialStore.save(pending.credentials)
                            .onFailure {
                                viewModel.showError("Nao foi possivel ativar a digital neste aparelho.")
                            }
                        pendingBiometricSetup = null
                        navigateAfterLogin(pending.role)
                    }
                ) {
                    Text("Ativar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingBiometricSetup = null
                        navigateAfterLogin(pending.role)
                    }
                ) {
                    Text("Agora nao")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp), // Margens um pouco maiores para ficar elegante
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        // --- LOGO ---
        Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "Logo Boa Ação",
            modifier = Modifier.size(120.dp) // Ajuste o tamanho conforme a proporção da sua imagem
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- TÍTULO ---
        Text(
            text = "Boa Ação",
            style = MaterialTheme.typography.headlineLarge,
            color = warmPrimaryColor,
            fontWeight = FontWeight.Bold
        )

        // --- SLOGAN / RÓTULO ---
        Text(
            text = "Conectando corações, alimentando esperanças.", // Escolha a sua frase favorita aqui
            style = MaterialTheme.typography.bodyLarge,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
        )

        // --- INPUT EMAIL ---
        OutlinedTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = warmPrimaryColor,
                focusedLabelColor = warmPrimaryColor
            ),
            shape = RoundedCornerShape(12.dp) // Bordas mais arredondadas passam um tom mais amigável
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- INPUT SENHA ---
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = { Text("Senha") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = "Mostrar senha",
                        tint = Color.Gray
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = warmPrimaryColor,
                focusedLabelColor = warmPrimaryColor
            ),
            shape = RoundedCornerShape(12.dp)
        )

        if (state.error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(state.error!!, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(32.dp))

        // --- BOTÃO ENTRAR ---
        Button(
            onClick = {
                viewModel.login { role, email, password ->
                    handlePasswordLoginSuccess(role, email, password)
                }
            },
            modifier = Modifier.fillMaxWidth().height(55.dp),
            enabled = !state.isLoading,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = warmPrimaryColor)
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text("Entrar", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (canUseBiometricLogin) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { authenticateWithBiometrics() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !state.isLoading,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = warmPrimaryColor)
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Entrar com digital", fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- BOTÃO CRIAR CONTA ---
        TextButton(onClick = onNavigateToRegister) {
            Text(
                "Ainda não tem conta? Faça parte!",
                color = warmPrimaryColor,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "v${BuildConfig.VERSION_NAME}",
            color = Color.LightGray,
            fontSize = 12.sp
        )
    }
}

private fun Context.findFragmentActivity(): FragmentActivity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is FragmentActivity) return currentContext
        currentContext = currentContext.baseContext
    }
    return null
}
