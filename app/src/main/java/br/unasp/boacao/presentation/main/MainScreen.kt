package br.unasp.boacao.presentation.main

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import br.unasp.boacao.BoaAcaoApplication
import br.unasp.boacao.domain.model.UserRole
import br.unasp.boacao.presentation.beneficiary.BeneficiaryDashboardScreen
import br.unasp.boacao.presentation.components.FilterIconCoordinator
import br.unasp.boacao.presentation.components.LocalFilterIconCoordinator
import br.unasp.boacao.presentation.donor.DonorDashboardScreen
import br.unasp.boacao.presentation.giftcard.GiftCardScreen
import br.unasp.boacao.presentation.navigation.InternalRoutes
import br.unasp.boacao.presentation.points.PointsScreen
import br.unasp.boacao.presentation.profile.ProfileScreen
import br.unasp.boacao.presentation.ranking.RankingScreen
import br.unasp.boacao.presentation.volunteer.VolunteerDashboardScreen
import br.unasp.boacao.presentation.beneficiary.BeneficiaryHistoryScreen
import br.unasp.boacao.presentation.event.EventCreateScreen
import br.unasp.boacao.presentation.event.EventDetailScreen
import br.unasp.boacao.presentation.event.EventListScreen
import br.unasp.boacao.presentation.event.EventScannerScreen
import br.unasp.boacao.presentation.event.MyCertificatesScreen
import br.unasp.boacao.presentation.event.MyTicketsScreen
import br.unasp.boacao.presentation.event.NgoEventsScreen
import br.unasp.boacao.presentation.event.OngSignatureScreen
import br.unasp.boacao.presentation.event.TicketQrScreen
import br.unasp.boacao.presentation.volunteer.VolunteerHistoryScreen
import br.unasp.boacao.presentation.volunteer.VolunteerMapScreen
import br.unasp.boacao.util.NotificationHelper
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

data class MenuItem(val title: String, val icon: ImageVector, val route: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onLogoutSuccess: () -> Unit) {
    val context = LocalContext.current
    val application = context.applicationContext as BoaAcaoApplication
    val viewModel: MainViewModel = viewModel(factory = MainViewModelFactory(application.authRepository))
    val state by viewModel.uiState.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val nestedNavController = rememberNavController()
    val navBackStackEntry by nestedNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val warmPrimaryColor = Color(0xFFF06A38)
    val filterCoordinator = remember { FilterIconCoordinator() }

    // Request notification permission (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission handled silently */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Real-time Firestore listener for nearby donations
    DisposableEffect(Unit) {
        var lastKnownIds = mutableSetOf<String>()
        var registration: ListenerRegistration? = null
        var isFirstLoad = true

        registration = FirebaseFirestore.getInstance()
            .collection("donations")
            .whereEqualTo("status", "AVAILABLE")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null) return@addSnapshotListener
                if (isFirstLoad) {
                    lastKnownIds = snapshot.documents.map { it.id }.toMutableSet()
                    isFirstLoad = false
                    return@addSnapshotListener
                }
                snapshot.documentChanges.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val doc = change.document
                        if (doc.id !in lastKnownIds) {
                            lastKnownIds.add(doc.id)
                            val donorName = doc.getString("donorName") ?: "Empresa parceira"
                            NotificationHelper.showNewDonationNotification(context, donorName)
                        }
                    }
                }
            }

        onDispose { registration?.remove() }
    }

    val menuItems = when (state.userRole) {
        UserRole.DONOR -> listOf(
            MenuItem("Painel de Doações", Icons.Default.Inventory, InternalRoutes.DONOR_HOME),
            MenuItem("Ranking de Doadores", Icons.Default.EmojiEvents, InternalRoutes.DONOR_RANKING),
            MenuItem("Meu Perfil", Icons.Default.Person, InternalRoutes.DONOR_PROFILE)
        )
        UserRole.VOLUNTEER -> listOf(
            MenuItem("Disponíveis", Icons.Default.List, InternalRoutes.VOLUNTEER_HOME),
            MenuItem("Histórico de Entregas", Icons.Default.History, InternalRoutes.VOLUNTEER_HISTORY),
            MenuItem("Meus Pontos", Icons.Default.Star, InternalRoutes.VOLUNTEER_POINTS),
            MenuItem("Gift Cards", Icons.Default.CardGiftcard, InternalRoutes.VOLUNTEER_GIFTCARDS),
            MenuItem("Eventos", Icons.Default.Event, InternalRoutes.VOLUNTEER_EVENTS),
            MenuItem("Meus Ingressos", Icons.Default.ConfirmationNumber, InternalRoutes.VOLUNTEER_TICKETS),
            MenuItem("Meus Certificados", Icons.Default.WorkspacePremium, InternalRoutes.VOLUNTEER_CERTIFICATES),
            MenuItem("Ranking", Icons.Default.EmojiEvents, InternalRoutes.VOLUNTEER_RANKING),
            MenuItem("Meu Perfil", Icons.Default.Person, InternalRoutes.VOLUNTEER_PROFILE)
        )
        UserRole.BENEFICIARY -> listOf(
            MenuItem("Recebimentos", Icons.Default.CheckCircle, InternalRoutes.BENEFICIARY_HOME),
            MenuItem("Histórico de Recebimentos", Icons.Default.History, InternalRoutes.BENEFICIARY_HISTORY),
            MenuItem("Meus Eventos", Icons.Default.Event, InternalRoutes.BENEFICIARY_EVENTS),
            MenuItem("Assinatura Digital", Icons.Default.Draw, InternalRoutes.BENEFICIARY_SIGNATURE),
            MenuItem("Ranking de ONGs", Icons.Default.EmojiEvents, InternalRoutes.BENEFICIARY_RANKING),
            MenuItem("Meu Perfil", Icons.Default.Person, InternalRoutes.BENEFICIARY_PROFILE)
        )
    }

    val startDestination = when (state.userRole) {
        UserRole.DONOR -> InternalRoutes.DONOR_HOME
        UserRole.VOLUNTEER -> InternalRoutes.VOLUNTEER_HOME
        UserRole.BENEFICIARY -> InternalRoutes.BENEFICIARY_HOME
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(180.dp).background(warmPrimaryColor).padding(16.dp),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Column {
                        val photoBytes = if (state.userPhotoBase64.isNotBlank())
                            runCatching { Base64.decode(state.userPhotoBase64, Base64.DEFAULT) }.getOrNull()
                        else null
                        val photoBitmap = photoBytes?.let {
                            runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull()
                        }
                        if (photoBitmap != null) {
                            Image(
                                bitmap = photoBitmap.asImageBitmap(),
                                contentDescription = "Foto de perfil",
                                modifier = Modifier.size(64.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = warmPrimaryColor,
                                modifier = Modifier.size(64.dp).clip(CircleShape).background(Color.White).padding(8.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        if (state.isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        } else {
                            Text(text = state.userName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = when (state.userRole) {
                                        UserRole.DONOR -> "Empresa Parceira"
                                        UserRole.VOLUNTEER -> "Voluntário Bom Samaritano"
                                        UserRole.BENEFICIARY -> "ONG Beneficiada"
                                    },
                                    color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp
                                )
                                if (state.userPoints > 0) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(Icons.Default.Star, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(14.dp))
                                    Text("${state.userPoints} pts", color = Color.Yellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                menuItems.forEach { item ->
                    NavigationDrawerItem(
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.title) },
                        selected = currentRoute == item.route,
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = warmPrimaryColor.copy(alpha = 0.1f),
                            selectedTextColor = warmPrimaryColor,
                            selectedIconColor = warmPrimaryColor
                        ),
                        onClick = {
                            scope.launch { drawerState.close() }
                            nestedNavController.navigate(item.route) {
                                popUpTo(startDestination) {
                                    saveState = false
                                    inclusive = false
                                }
                                launchSingleTop = true
                                restoreState = false
                            }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
                HorizontalDivider()
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Logout, contentDescription = null, tint = Color.Red) },
                    label = { Text("Sair da Conta", color = Color.Red, fontWeight = FontWeight.Bold) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        viewModel.logout()
                        onLogoutSuccess()
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding).padding(bottom = 16.dp)
                )
            }
        }
    ) {
        CompositionLocalProvider(LocalFilterIconCoordinator provides filterCoordinator) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Boa Ação", color = Color.White, fontWeight = FontWeight.Bold) },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = warmPrimaryColor),
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
                            }
                        },
                        actions = {
                            if (filterCoordinator.isVisible) {
                                IconButton(onClick = { filterCoordinator.trigger() }) {
                                    BadgedBox(
                                        badge = {
                                            if (filterCoordinator.hasActiveFilters) {
                                                Badge(containerColor = Color.Yellow)
                                            }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.FilterList,
                                            contentDescription = "Filtros",
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            ) { paddingValues ->
                if (!state.isLoading) {
                    NavHost(
                        navController = nestedNavController,
                        startDestination = startDestination,
                        modifier = Modifier.padding(paddingValues)
                    ) {
                        composable(InternalRoutes.DONOR_HOME) { DonorDashboardScreen(nestedNavController) }
                        composable(InternalRoutes.DONOR_PROFILE) { ProfileScreen(nestedNavController) }
                        composable(InternalRoutes.DONOR_RANKING) { RankingScreen(nestedNavController, UserRole.DONOR) }

                        composable(InternalRoutes.VOLUNTEER_HOME) { VolunteerDashboardScreen(nestedNavController) }
                        composable(InternalRoutes.VOLUNTEER_MAP) { VolunteerMapScreen(nestedNavController) }
                        composable(InternalRoutes.VOLUNTEER_HISTORY) { VolunteerHistoryScreen(nestedNavController) }
                        composable(InternalRoutes.VOLUNTEER_POINTS) { PointsScreen(nestedNavController) }
                        composable(InternalRoutes.VOLUNTEER_GIFTCARDS) { GiftCardScreen(nestedNavController) }
                        composable(InternalRoutes.VOLUNTEER_RANKING) { RankingScreen(nestedNavController, UserRole.VOLUNTEER) }
                        composable(InternalRoutes.VOLUNTEER_PROFILE) { ProfileScreen(nestedNavController) }

                        composable(InternalRoutes.BENEFICIARY_HOME) { BeneficiaryDashboardScreen(nestedNavController) }
                        composable(InternalRoutes.BENEFICIARY_HISTORY) { BeneficiaryHistoryScreen(nestedNavController) }
                        composable(InternalRoutes.BENEFICIARY_RANKING) { RankingScreen(nestedNavController, UserRole.BENEFICIARY) }
                        composable(InternalRoutes.BENEFICIARY_PROFILE) { ProfileScreen(nestedNavController) }

                        // Eventos & Certificação
                        composable(InternalRoutes.VOLUNTEER_EVENTS) { EventListScreen(nestedNavController) }
                        composable(InternalRoutes.VOLUNTEER_TICKETS) { MyTicketsScreen(nestedNavController) }
                        composable(InternalRoutes.VOLUNTEER_CERTIFICATES) { MyCertificatesScreen(nestedNavController) }
                        composable("${InternalRoutes.VOLUNTEER_TICKET_QR}/{ticketCode}") { backStack ->
                            val code = backStack.arguments?.getString("ticketCode").orEmpty()
                            TicketQrScreen(nestedNavController, code)
                        }
                        composable(InternalRoutes.BENEFICIARY_EVENTS) { NgoEventsScreen(nestedNavController) }
                        composable(InternalRoutes.BENEFICIARY_EVENT_CREATE) { EventCreateScreen(nestedNavController) }
                        composable("${InternalRoutes.BENEFICIARY_EVENT_DETAIL}/{eventId}") { backStack ->
                            val id = backStack.arguments?.getString("eventId").orEmpty()
                            EventDetailScreen(nestedNavController, id)
                        }
                        composable("${InternalRoutes.BENEFICIARY_EVENT_SCANNER}/{eventId}/{mode}") { backStack ->
                            val id = backStack.arguments?.getString("eventId").orEmpty()
                            val modeStr = backStack.arguments?.getString("mode").orEmpty()
                            val mode = runCatching { br.unasp.boacao.presentation.event.ScannerMode.valueOf(modeStr) }
                                .getOrDefault(br.unasp.boacao.presentation.event.ScannerMode.CHECKIN)
                            EventScannerScreen(nestedNavController, id, mode)
                        }
                        composable(InternalRoutes.BENEFICIARY_SIGNATURE) { OngSignatureScreen(nestedNavController) }
                    }
                }
            }
        }
    }
}
