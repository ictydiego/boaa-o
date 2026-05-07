package br.unasp.boacao.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.unasp.boacao.domain.model.AttendanceStatus
import br.unasp.boacao.domain.model.EventStatus
import br.unasp.boacao.util.FormatUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val EventWarmPrimary = Color(0xFFF06A38)
val EventSoftBg = Color(0xFFFFF3EE)

private val brDateFmt = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))

@Composable
fun StatusChip(label: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun EventStatusChip(status: EventStatus, modifier: Modifier = Modifier) {
    val (label, color) = when (status) {
        EventStatus.PUBLISHED -> "Publicado" to Color(0xFF1976D2)
        EventStatus.IN_PROGRESS -> "Em andamento" to Color(0xFF2E7D32)
        EventStatus.FINISHED -> "Finalizado" to Color(0xFF616161)
        EventStatus.CANCELLED -> "Cancelado" to Color(0xFFC62828)
    }
    StatusChip(label, color, modifier)
}

@Composable
fun AttendanceStatusChip(status: AttendanceStatus, modifier: Modifier = Modifier) {
    val (label, color) = when (status) {
        AttendanceStatus.SUBSCRIBED -> "Inscrito" to Color(0xFF1976D2)
        AttendanceStatus.CHECKED_IN -> "Presente" to Color(0xFF2E7D32)
        AttendanceStatus.CHECKED_OUT -> "Concluído" to Color(0xFFF06A38)
        AttendanceStatus.NO_SHOW -> "Não compareceu" to Color(0xFFC62828)
    }
    StatusChip(label, color, modifier)
}

@Composable
fun InfoChip(icon: ImageVector, text: String, color: Color = Color.DarkGray) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, color = color, fontSize = 12.sp)
    }
}

@Composable
fun EventCard(
    title: String,
    subtitle: String,
    startAt: Long,
    workloadHours: Double,
    address: String,
    status: EventStatus? = null,
    statusChip: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val cardModifier = modifier.fillMaxWidth().let { m -> if (onClick != null) m.clickable { onClick() } else m }
    Card(
        modifier = cardModifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, EventWarmPrimary.copy(alpha = 0.20f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                when {
                    statusChip != null -> statusChip()
                    status != null -> EventStatusChip(status)
                }
            }
            if (subtitle.isNotBlank()) {
                Text(subtitle, fontSize = 12.sp, color = Color(0xFF555555))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoChip(Icons.Default.CalendarToday, brDateFmt.format(Date(startAt)), EventWarmPrimary)
                InfoChip(Icons.Default.AccessTime, "${FormatUtils.formatHours(workloadHours)}h", EventWarmPrimary)
            }
            if (address.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(address, fontSize = 12.sp, color = Color.DarkGray, maxLines = 2)
                }
            }
            if (trailing != null) {
                Spacer(Modifier.height(12.dp))
                trailing()
            }
        }
    }
}

@Composable
fun AttendeeRow(
    name: String,
    document: String,
    statusChip: @Composable () -> Unit,
    note: String = ""
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFEEEEEE))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Group, contentDescription = null, tint = EventWarmPrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row {
                        Text("Nome: ", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(name, fontSize = 13.sp)
                    }
                    Row {
                        Text("CPF: ", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.DarkGray)
                        Text(FormatUtils.formatDocument(document), fontSize = 12.sp, color = Color.DarkGray)
                    }
                }
                statusChip()
            }
            if (note.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = EventSoftBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.Top) {
                        Text("Obs: ", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = EventWarmPrimary)
                        Text(note, fontSize = 12.sp, color = Color(0xFF333333))
                    }
                }
            }
        }
    }
}

@Composable
fun HighlightCard(
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: Color = EventWarmPrimary,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.10f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, fontSize = 11.sp, color = Color.DarkGray)
                Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = accentColor)
            }
        }
    }
}
