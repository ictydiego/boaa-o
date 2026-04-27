package br.unasp.boacao.presentation.ranking

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
            "Nenhum doador no ranking ainda.", "doações", Icons.Default.VolunteerActivism
        )
        UserRole.VOLUNTEER -> RankingConfig(
            "Ranking de Voluntários", "Voluntários com mais pontos",
            "Nenhum voluntário no ranking ainda.", "pontos", Icons.Default.DirectionsBike
        )
        UserRole.BENEFICIARY -> RankingConfig(
            "Ranking de ONGs", "ONGs que mais receberam doações",
            "Nenhuma ONG no ranking ainda.", "recebimentos", Icons.Default.Business
        )
    }

    val warmColor = Color(0xFFF06A38)
    val goldColor = Color(0xFFFFD700)
    val silverColor = Color(0xFFB0BEC5)
    val bronzeColor = Color(0xFFCD7F32)

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = warmColor) }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize().background(Color(0xFFF8F8F8)), contentPadding = PaddingValues(bottom = 24.dp)) {
        // Header with gradient
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(warmColor, Color(0xFFFF8A50))))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(headerTitle, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text(headerSubtitle, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                }
            }
        }

        if (state.topUsers.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Leaderboard, contentDescription = null, modifier = Modifier.size(72.dp), tint = Color(0xFFE0E0E0))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(emptyText, color = Color.Gray, textAlign = TextAlign.Center, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Faça sua parte e apareça aqui!", color = warmColor, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                }
            }
            return@LazyColumn
        }

        // Podium for top 3
        if (state.topUsers.size >= 3) {
            item {
                Podium(
                    first = state.topUsers[0],
                    second = state.topUsers[1],
                    third = state.topUsers[2],
                    icon = icon,
                    metricFn = { profile -> metricValue(profile, rankingFor) },
                    metricLabel = metricLabel,
                    goldColor = goldColor,
                    silverColor = silverColor,
                    bronzeColor = bronzeColor
                )
            }
        } else {
            // Less than 3 users - show them in a simple list
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    state.topUsers.forEachIndexed { index, profile ->
                        RankingRow(
                            position = index + 1,
                            profile = profile,
                            icon = icon,
                            metricLabel = metricLabel,
                            rankingFor = rankingFor,
                            medalColor = when (index) {
                                0 -> goldColor
                                1 -> silverColor
                                else -> bronzeColor
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        // Rest of ranking (4-10)
        val rest = if (state.topUsers.size > 3) state.topUsers.drop(3) else emptyList()
        if (rest.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FormatListNumbered, contentDescription = null, tint = warmColor, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Classificação Geral", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF424242))
                }
            }
            itemsIndexed(rest) { index, profile ->
                RankingRow(
                    position = index + 4,
                    profile = profile,
                    icon = icon,
                    metricLabel = metricLabel,
                    rankingFor = rankingFor,
                    medalColor = null
                )
            }
        }

        // Refresh hint
        item {
            TextButton(
                onClick = { viewModel.loadRanking() },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Atualizar ranking", fontSize = 13.sp)
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
    icon: ImageVector, metricFn: (UserProfile) -> Int, metricLabel: String,
    goldColor: Color, silverColor: Color, bronzeColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            PodiumItem(second, 2, 80.dp, silverColor, icon, metricFn(second), metricLabel)
            PodiumItem(first, 1, 110.dp, goldColor, icon, metricFn(first), metricLabel)
            PodiumItem(third, 3, 60.dp, bronzeColor, icon, metricFn(third), metricLabel)
        }
    }
}

@Composable
private fun PodiumItem(
    profile: UserProfile, position: Int, height: Dp, medalColor: Color,
    icon: ImageVector, metricValue: Int, metricLabel: String
) {
    val animatedValue by animateIntAsState(targetValue = metricValue, label = "metric")

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(100.dp)) {
        // Crown for 1st place
        if (position == 1) {
            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Avatar
        val photoBitmap = remember(profile.photoBase64) {
            if (profile.photoBase64.isNotBlank()) {
                try {
                    val bytes = Base64.decode(profile.photoBase64, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                } catch (_: Exception) { null }
            } else null
        }

        Box(
            modifier = Modifier
                .size(if (position == 1) 64.dp else 52.dp)
                .clip(CircleShape)
                .background(medalColor),
            contentAlignment = Alignment.Center
        ) {
            if (photoBitmap != null) {
                Image(
                    bitmap = photoBitmap,
                    contentDescription = profile.name,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(if (position == 1) 32.dp else 26.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            profile.name.take(14),
            fontWeight = FontWeight.Bold,
            fontSize = if (position == 1) 13.sp else 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp
        )
        Text("$animatedValue $metricLabel", fontSize = 11.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(height)
                .background(
                    Brush.verticalGradient(listOf(medalColor, medalColor.copy(alpha = 0.4f))),
                    RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("$position°", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = Color.White)
        }
    }
}

@Composable
private fun RankingRow(
    position: Int, profile: UserProfile, icon: ImageVector,
    metricLabel: String, rankingFor: UserRole, medalColor: Color? = null
) {
    val warmColor = Color(0xFFF06A38)

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Position number with medal color
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(
                    (medalColor ?: Color(0xFFE0E0E0)).copy(alpha = if (medalColor != null) 1f else 0.3f)
                ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$position",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (medalColor != null) Color.White else Color.Gray
                )
            }
            Spacer(modifier = Modifier.width(12.dp))

            // Avatar
            val photoBitmap = remember(profile.photoBase64) {
                if (profile.photoBase64.isNotBlank()) {
                    try {
                        val bytes = Base64.decode(profile.photoBase64, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    } catch (_: Exception) { null }
                } else null
            }

            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(warmColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (photoBitmap != null) {
                    Image(
                        bitmap = photoBitmap,
                        contentDescription = profile.name,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(icon, contentDescription = null, tint = warmColor, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(profile.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF212121))
                Text("${metricValue(profile, rankingFor)} $metricLabel", fontSize = 12.sp, color = Color.Gray)
            }
            if (position <= 3 && medalColor != null) {
                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = medalColor, modifier = Modifier.size(22.dp))
            }
        }
    }
}
