package br.unasp.boacao.presentation.ranking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import br.unasp.boacao.BoaAcaoApplication
import br.unasp.boacao.domain.model.UserProfile
import br.unasp.boacao.domain.model.UserRole

@Composable
fun RankingScreen(navController: NavController, rankingFor: UserRole = UserRole.DONOR) {
    val context = LocalContext.current
    val application = context.applicationContext as BoaAcaoApplication
    val viewModel: RankingViewModel = viewModel(
        key = rankingFor.name,
        factory = RankingViewModelFactory(application.rankingRepository, rankingFor)
    )
    val state by viewModel.uiState.collectAsState()

    val (headerTitle, headerSubtitle, emptyText, metricLabel, icon) = when (rankingFor) {
        UserRole.DONOR -> RankingConfig(
            "Ranking de Doadores", "Empresas e pessoas que mais doam",
            "Nenhum doador no ranking ainda.", "doações", Icons.Default.Business
        )
        UserRole.VOLUNTEER -> RankingConfig(
            "Ranking de Voluntários", "Voluntários com mais pontos e entregas",
            "Nenhum voluntário no ranking ainda.", "pontos", Icons.Default.DirectionsBike
        )
        UserRole.BENEFICIARY -> RankingConfig(
            "Ranking de ONGs", "ONGs que mais receberam doações",
            "Nenhuma ONG no ranking ainda.", "recebimentos", Icons.Default.Business
        )
    }

    val warmColor = Color(0xFFF06A38)

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = warmColor) }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Box(
                modifier = Modifier.fillMaxWidth().background(warmColor).padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                    Text(headerTitle, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text(headerSubtitle, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                }
            }
        }

        if (state.topUsers.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) {
                    Text(emptyText, color = Color.Gray, textAlign = TextAlign.Center)
                }
            }
            return@LazyColumn
        }

        if (state.topUsers.size >= 3) {
            item {
                Podium(
                    first = state.topUsers[0],
                    second = state.topUsers[1],
                    third = state.topUsers[2],
                    icon = icon,
                    metricFn = { profile -> metricValue(profile, rankingFor) },
                    metricLabel = metricLabel
                )
            }
        }

        val rest = if (state.topUsers.size > 3) state.topUsers.drop(3) else emptyList()
        if (rest.isNotEmpty()) {
            item { Text("Top 10", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) }
            itemsIndexed(rest) { index, profile ->
                RankingRow(position = index + 4, profile = profile, icon = icon, metricLabel = metricLabel, rankingFor = rankingFor)
            }
        }
    }
}

private fun metricValue(profile: UserProfile, role: UserRole): Int = when (role) {
    UserRole.DONOR -> profile.donationCount
    UserRole.VOLUNTEER -> profile.points
    UserRole.BENEFICIARY -> profile.receivedCount
}

private data class RankingConfig(
    val title: String, val subtitle: String, val emptyText: String,
    val metricLabel: String, val icon: ImageVector
)

private operator fun RankingConfig.component5() = icon

@Composable
private fun Podium(
    first: UserProfile, second: UserProfile, third: UserProfile,
    icon: ImageVector, metricFn: (UserProfile) -> Int, metricLabel: String
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        PodiumItem(second, 2, 80.dp, Color(0xFFB0BEC5), icon, metricFn(second), metricLabel)
        PodiumItem(first, 1, 110.dp, Color(0xFFFFD700), icon, metricFn(first), metricLabel)
        PodiumItem(third, 3, 60.dp, Color(0xFFCD7F32), icon, metricFn(third), metricLabel)
    }
}

@Composable
private fun PodiumItem(
    profile: UserProfile, position: Int, height: Dp, medalColor: Color,
    icon: ImageVector, metricValue: Int, metricLabel: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(52.dp).clip(CircleShape).background(medalColor), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(profile.name.take(12), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 2)
        Text("$metricValue $metricLabel", fontSize = 11.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier.width(80.dp).height(height)
                .background(medalColor.copy(alpha = 0.3f), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
            contentAlignment = Alignment.Center
        ) { Text("$position°", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = medalColor) }
    }
}

@Composable
private fun RankingRow(position: Int, profile: UserProfile, icon: ImageVector, metricLabel: String, rankingFor: UserRole) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("$position°", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Gray, modifier = Modifier.width(32.dp))
            Icon(icon, contentDescription = null, tint = Color(0xFFF06A38), modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(profile.name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text("${metricValue(profile, rankingFor)} $metricLabel", fontSize = 12.sp, color = Color.Gray)
            }
            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(20.dp))
        }
    }
}
