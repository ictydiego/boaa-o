package br.unasp.boacao.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val WarmPrimary = Color(0xFFF06A38)

/**
 * Coordinator that lets a child screen register its filter icon behavior so
 * the global TopAppBar (in MainScreen) can render a single, consistent
 * filter button in the top-right corner.
 */
class FilterIconCoordinator {
    var isVisible by mutableStateOf(false)
        private set
    var hasActiveFilters by mutableStateOf(false)
        private set
    private var onClick: (() -> Unit)? = null

    fun register(hasActive: Boolean, onClick: () -> Unit) {
        this.onClick = onClick
        this.hasActiveFilters = hasActive
        this.isVisible = true
    }

    fun unregister() {
        this.onClick = null
        this.hasActiveFilters = false
        this.isVisible = false
    }

    fun trigger() {
        onClick?.invoke()
    }
}

val LocalFilterIconCoordinator = compositionLocalOf<FilterIconCoordinator?> { null }

/**
 * Bonito, discreto, reutilizável.
 *
 * Uso:
 * ```
 * if (showDialog) {
 *     FilterBottomSheet(onDismiss = { showDialog = false }, onClear = { ... }) {
 *         FilterSection(title = "Status") { FilterChipsRow(...) }
 *     }
 * }
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    onDismiss: () -> Unit,
    title: String = "Filtros",
    accentColor: Color = WarmPrimary,
    onClear: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .background(Color(0xFFE0E0E0), RoundedCornerShape(2.dp))
            )
        }
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF212121),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.Gray)
                }
            }

            HorizontalDivider(color = Color(0xFFF0F0F0))
            Spacer(modifier = Modifier.height(12.dp))

            content()

            if (onClear != null) {
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { onClear(); onDismiss() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray)
                    ) {
                        Text("Limpar filtros", fontSize = 13.sp)
                    }
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Aplicar", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun FilterSection(
    title: String,
    icon: ImageVector? = null,
    accentColor: Color = WarmPrimary,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = Color(0xFF424242)
            )
        }
        content()
    }
}

/**
 * Linha de chips horizontalmente roláveis — visual elegante, padrão único do app.
 */
@Composable
fun FilterChipsRow(
    options: List<FilterChipOption>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    accentColor: Color = WarmPrimary
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { opt ->
            FilterChip(
                selected = selectedKey == opt.key,
                onClick = { onSelect(opt.key) },
                label = { Text(opt.label, fontSize = 12.sp) },
                leadingIcon = opt.icon?.let { ico ->
                    {
                        Icon(ico, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                },
                shape = RoundedCornerShape(20.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accentColor.copy(alpha = 0.15f),
                    selectedLabelColor = accentColor,
                    selectedLeadingIconColor = accentColor
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selectedKey == opt.key,
                    borderColor = Color(0xFFE0E0E0),
                    selectedBorderColor = accentColor.copy(alpha = 0.4f)
                )
            )
        }
    }
}

data class FilterChipOption(
    val key: String,
    val label: String,
    val icon: ImageVector? = null
)
