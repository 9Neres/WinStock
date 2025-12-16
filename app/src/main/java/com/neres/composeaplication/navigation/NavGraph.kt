package com.neres.composeaplication.navigation

import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.neres.composeaplication.componets.TelaBotaoScanner
import com.neres.composeaplication.ui.screens.ColetaInventarioScreen
import com.neres.composeaplication.ui.screens.InformacoesBancoScreen
import com.neres.composeaplication.ui.screens.LoginScreen
import com.neres.composeaplication.ui.screens.Resumo24hScreen
import com.neres.composeaplication.ui.screens.LogsScreen
import com.neres.composeaplication.componets.TelaScanner

@androidx.camera.core.ExperimentalGetImage
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    var reloadInventario by remember { mutableStateOf(false) }

    NavHost(
        navController = navController,
        startDestination = "login"
    ) {
        // 🔹 Login
        composable("login") {
            LoginScreen(onLogin = { navController.navigate("inventario") })
        }

        // 🔹 Inventário principal
        composable("inventario") {
            // Se reloadInventario == true, força o reload da tela
            ColetaInventarioScreen(navController = navController, forceReload = reloadInventario)
            // Resetar flag após recarregar
            LaunchedEffect(reloadInventario) {
                if (reloadInventario) reloadInventario = false
            }
        }

        composable("informacoes_banco") {
            InformacoesBancoScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("resumo_24h") {
            Resumo24hScreen(
                onBack = { navController.popBackStack() }
            )
        }
        
        composable("logs") {
            LogsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // 🔹 Tela inicial (não usada, mas mantida)
        composable("tela_inicial") {
            TelaBotaoScanner(navController)
        }

        // 🔹 Scanner
        composable("scanner") {
            TelaScanner(
                navController = navController,
                onProdutoAtualizado = {
                    // Quando um código for salvo localmente, marca para recarregar
                    reloadInventario = true
                    // Volta para o inventário
                    navController.popBackStack()
                }
            )
        }
    }
}
