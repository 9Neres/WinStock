package com.neres.composeaplication.componets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.neres.composeaplication.data.ScannedCodesRepository

@androidx.camera.core.ExperimentalGetImage
@Composable
fun TelaScanner(
    navController: NavController,
    onProdutoAtualizado: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface {
            LeitorQrCode(
                onQrCodeScanned = { codigo ->
                    ScannedCodesRepository.addCode(codigo)
                    onProdutoAtualizado()
                }
            )
        }
    }
}
