package br.unasp.boacao.presentation.giftcard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import br.unasp.boacao.BoaAcaoApplication
import br.unasp.boacao.domain.GiftCard
import br.unasp.boacao.domain.RedeemedGiftCard

@Composable
fun GiftCardScreen(navController: NavController) {
    val context = LocalContext.current
    val application = context.applicationContext as BoaAcaoApplication
    val viewModel: GiftCardViewModel = viewModel(
        factory = GiftCardViewModelFactory(application.giftCardRepository, application.pointsRepository)
    )
    val state by viewModel.uiState.collectAsState()
    val warmColor = Color(0xFFF06A38)
    var selectedTab by remember { mutableIntStateOf(0) }
    var cardToRedeem by remember { mutableStateOf<GiftCard?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Points header
        Box(
            modifier = Modifier.fillMaxWidth().background(warmColor).padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("${state.currentPoints} pontos disponíveis", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }

        TabRow(selectedTabIndex = selectedTab, containerColor = Color.White, contentColor = warmColor) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Catálogo", fontWeight = FontWeight.Bold) })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Meus Resgates", fontWeight = FontWeight.Bold) })
        }

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = warmColor) }
            return@Column
        }

        if (selectedTab == 0) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.availableCards) { card ->
                    GiftCardItem(card = card, currentPoints = state.currentPoints, onRedeem = { cardToRedeem = card })
                }
            }
        } else {
            if (state.redeemedCards.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("Você ainda não resgatou nenhum gift card.", color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.redeemedCards) { redeemed -> RedeemedCard(redeemed) }
                }
            }
        }
    }

    // Confirm redeem dialog
    cardToRedeem?.let { card ->
        AlertDialog(
            onDismissRequest = { cardToRedeem = null },
            icon = { Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = warmColor, modifier = Modifier.size(36.dp)) },
            title = { Text("Resgatar ${card.title}?") },
            text = { Text("Você usará ${card.requiredPoints} pontos. Saldo atual: ${state.currentPoints} pontos. Um código de voucher será gerado para você.") },
            confirmButton = {
                Button(onClick = { viewModel.redeemGiftCard(card); cardToRedeem = null }, colors = ButtonDefaults.buttonColors(containerColor = warmColor)) {
                    Text("Confirmar Resgate")
                }
            },
            dismissButton = { TextButton(onClick = { cardToRedeem = null }) { Text("Cancelar") } }
        )
    }

    // Voucher success dialog
    state.redeemedCard?.let { redeemed ->
        Dialog(onDismissRequest = { viewModel.clearRedeemedCard() }) {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Gift Card Resgatado!", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text(redeemed.giftCardTitle, color = warmColor, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Seu código:", color = Color.Gray, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().background(Color(0xFFFFF3E0), RoundedCornerShape(12.dp)).padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(redeemed.voucherCode, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = warmColor, letterSpacing = 2.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Anote este código — use na plataforma da marca!", color = Color.Gray, fontSize = 12.sp, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(onClick = { viewModel.clearRedeemedCard() }, colors = ButtonDefaults.buttonColors(containerColor = warmColor), modifier = Modifier.fillMaxWidth()) {
                        Text("Entendido!")
                    }
                }
            }
        }
    }

    state.error?.let { err ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Atenção") },
            text = { Text(err) },
            confirmButton = { TextButton(onClick = { viewModel.clearError() }) { Text("OK") } }
        )
    }
}

@Composable
private fun GiftCardItem(card: GiftCard, currentPoints: Int, onRedeem: () -> Unit) {
    val unlocked = currentPoints >= card.requiredPoints
    val warmColor = Color(0xFFF06A38)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (unlocked) Color.White else Color(0xFFF5F5F5)),
        elevation = CardDefaults.cardElevation(if (unlocked) 4.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = if (unlocked) Icons.Default.CardGiftcard else Icons.Default.Lock,
                contentDescription = null,
                tint = if (unlocked) warmColor else Color.Gray,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(card.brand, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center)
            Text(card.title, fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(8.dp))
            Text("${card.requiredPoints} pts", fontWeight = FontWeight.ExtraBold, color = if (unlocked) warmColor else Color.Gray, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            if (unlocked) {
                Button(onClick = onRedeem, colors = ButtonDefaults.buttonColors(containerColor = warmColor), modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("Resgatar", fontSize = 12.sp)
                }
            } else {
                val missing = card.requiredPoints - currentPoints
                Text("Faltam $missing pts", fontSize = 11.sp, color = Color.Gray, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun RedeemedCard(redeemed: RedeemedGiftCard) {
    val warmColor = Color(0xFFF06A38)
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = warmColor, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(redeemed.giftCardTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Código: ${redeemed.voucherCode}", fontSize = 13.sp, color = warmColor, fontWeight = FontWeight.Medium)
                Text(redeemed.redeemedAt, fontSize = 11.sp, color = Color.Gray)
            }
            Text("-${redeemed.pointsSpent} pts", color = Color(0xFFE53935), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}
