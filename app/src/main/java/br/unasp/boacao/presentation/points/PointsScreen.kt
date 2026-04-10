package br.unasp.boacao.presentation.points

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import br.unasp.boacao.BoaAcaoApplication
import br.unasp.boacao.domain.PointsTransaction

@Composable
fun PointsScreen(navController: NavController) {
    val context = LocalContext.current
    val application = context.applicationContext as BoaAcaoApplication
    val viewModel: PointsViewModel = viewModel(factory = PointsViewModelFactory(application.pointsRepository))
    val state by viewModel.uiState.collectAsState()
    val warmColor = Color(0xFFF06A38)

    val animatedPoints by animateIntAsState(
        targetValue = state.totalPoints,
        animationSpec = tween(durationMillis = 800),
        label = "points_anim"
    )

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(color = warmColor)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // Big points card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = warmColor),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("$animatedPoints", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 56.sp)
                    Text("pontos acumulados", color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp)
                }
            }
        }

        if (state.nextGiftCardPoints > state.totalPoints) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Próxima recompensa", fontWeight = FontWeight.Bold, color = Color.DarkGray)
                        Text(state.nextGiftCardName, color = warmColor, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { (state.totalPoints.toFloat() / state.nextGiftCardPoints.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(10.dp),
                            color = warmColor,
                            trackColor = Color(0xFFE0E0E0)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "${state.totalPoints} / ${state.nextGiftCardPoints} pontos",
                            fontSize = 12.sp, color = Color.Gray,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
            }
        }

        item {
            Text("Histórico", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp))
        }

        if (state.history.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                    Text("Nenhuma atividade ainda. Faça sua primeira boa ação!", color = Color.Gray, fontSize = 14.sp)
                }
            }
        } else {
            items(state.history) { transaction ->
                PointsTransactionCard(transaction, warmColor)
            }
        }
    }
}

@Composable
private fun PointsTransactionCard(transaction: PointsTransaction, accentColor: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(accentColor.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Star, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(transaction.reason, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text(transaction.date, fontSize = 12.sp, color = Color.Gray)
            }
            Text(
                "+${transaction.points}",
                color = Color(0xFF4CAF50),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}
