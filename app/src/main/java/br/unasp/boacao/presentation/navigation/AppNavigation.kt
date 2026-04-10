package br.unasp.boacao.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import br.unasp.boacao.presentation.login.LoginScreen
import br.unasp.boacao.presentation.login.RegisterScreen
import br.unasp.boacao.presentation.main.MainScreen

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object Main : Screen("main")
}

// Rotas internas do NavHost dentro do MainScreen
object InternalRoutes {
    const val DONOR_HOME = "donor_home"
    const val DONOR_PROFILE = "donor_profile"
    const val DONOR_RANKING = "donor_ranking"

    const val VOLUNTEER_HOME = "volunteer_home"
    const val VOLUNTEER_MAP = "volunteer_map"
    const val VOLUNTEER_POINTS = "volunteer_points"
    const val VOLUNTEER_GIFTCARDS = "volunteer_giftcards"
    const val VOLUNTEER_RANKING = "volunteer_ranking"
    const val VOLUNTEER_PROFILE = "volunteer_profile"
    const val VOLUNTEER_HISTORY = "volunteer_history"

    const val BENEFICIARY_HOME = "beneficiary_home"
    const val BENEFICIARY_HISTORY = "beneficiary_history"
    const val BENEFICIARY_RANKING = "beneficiary_ranking"
    const val BENEFICIARY_PROFILE = "beneficiary_profile"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Login.route) {

        composable(Screen.Login.route) {
            LoginScreen(
                onNavigateToDashboard = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToRegister = { navController.navigate(Screen.Register.route) }
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                onNavigateToDashboard = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onBackToLogin = { navController.popBackStack() }
            )
        }

        composable(Screen.Main.route) {
            MainScreen(
                onLogoutSuccess = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
