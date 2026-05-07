package br.unasp.boacao.presentation.event

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import br.unasp.boacao.BoaAcaoApplication
import br.unasp.boacao.presentation.navigation.InternalRoutes
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun EventCreateScreen(navController: NavController) {
    val context = LocalContext.current
    val app = context.applicationContext as BoaAcaoApplication
    val viewModel: EventCreateViewModel = viewModel(
        factory = EventCreateViewModelFactory(app.eventRepository, app.authRepository)
    )
    val state by viewModel.uiState.collectAsState()

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var startAt by remember { mutableLongStateOf(0L) }
    var endAt by remember { mutableLongStateOf(0L) }
    var hours by remember { mutableStateOf("4") }
    var maxParticipants by remember { mutableStateOf("0") }

    LaunchedEffect(state.createdEventId) {
        state.createdEventId?.let { id ->
            viewModel.consumeCreated()
            navController.navigate("${InternalRoutes.BENEFICIARY_EVENT_DETAIL}/$id") {
                popUpTo(InternalRoutes.BENEFICIARY_EVENT_CREATE) { inclusive = true }
            }
        }
    }

    val df = remember { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")) }

    fun pickDate(initial: Long, onPick: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply {
            if (initial > 0) timeInMillis = initial
        }
        DatePickerDialog(
            context,
            { _, y, m, d ->
                cal.set(y, m, d, 8, 0, 0)
                cal.set(Calendar.MILLISECOND, 0)
                onPick(cal.timeInMillis)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Novo Evento", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = title, onValueChange = { title = it },
            label = { Text("Título") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = description, onValueChange = { description = it },
            label = { Text("Descrição") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = address, onValueChange = { address = it },
            label = { Text("Endereço") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = { pickDate(startAt) { startAt = it } },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (startAt == 0L) "Selecionar data de início" else "Início: ${df.format(Date(startAt))}")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { pickDate(endAt) { endAt = it } },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (endAt == 0L) "Selecionar data de término" else "Fim: ${df.format(Date(endAt))}")
        }
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = hours, onValueChange = { hours = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
            label = { Text("Carga horária (horas)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = maxParticipants, onValueChange = { maxParticipants = it.filter { c -> c.isDigit() } },
            label = { Text("Máx. participantes (0 = ilimitado)") },
            modifier = Modifier.fillMaxWidth()
        )

        state.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(20.dp))

        if (state.saving) {
            CircularProgressIndicator()
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        viewModel.submit(
                            title = title,
                            description = description,
                            address = address,
                            startAt = startAt,
                            endAt = endAt,
                            workloadHours = hours.replace(",", ".").toDoubleOrNull() ?: 0.0,
                            maxParticipants = maxParticipants.toIntOrNull() ?: 0
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Criar Evento") }
                OutlinedButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Cancelar") }
            }
        }
    }
}
