package com.neres.composeaplication

import android.content.Context.RECEIVER_EXPORTED
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.neres.composeaplication.data.ScannedCodesRepository
import com.neres.composeaplication.data.TAB_INVENTICacheRepository
import com.neres.composeaplication.data.UsuariosCacheRepository
import com.neres.composeaplication.data.LogsRepository
import com.neres.composeaplication.navigation.AppNavigation
import com.neres.composeaplication.scanner.ScannerController
import com.neres.composeaplication.scanner.ZebraScannerReceiver
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.neres.composeaplication.ui.theme.ComposeApplicationTheme

class MainActivity : ComponentActivity() {

    private var zebraReceiver: ZebraScannerReceiver? = null
    private val zebraIntentFilter = IntentFilter(ZebraScannerReceiver.DATAWEDGE_ACTION).apply {
        addCategory(Intent.CATEGORY_DEFAULT)
    }
    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inicializa os repositórios com persistência
        ScannedCodesRepository.initialize(this)
        TAB_INVENTICacheRepository.initialize(this)
        UsuariosCacheRepository.initialize(this)
        LogsRepository.initialize(this)
        
        // Log de inicialização
        LogsRepository.info("Sistema", "App iniciado", "MainActivity onCreate")
        
        @androidx.camera.core.ExperimentalGetImage
        setContent {
            ComposeApplicationTheme {
                AppNavigation()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (isZebraDevice()) {
            if (zebraReceiver == null) {
                zebraReceiver = ZebraScannerReceiver { codigo ->
                    scope.launch {
                        ScannerController.onCodigoRecebido(codigo)
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(
                    zebraReceiver,
                    zebraIntentFilter,
                    RECEIVER_EXPORTED
                )
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(zebraReceiver, zebraIntentFilter)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (isZebraDevice()) {
            zebraReceiver?.let {
                try {
                    unregisterReceiver(it)
                } catch (_: IllegalArgumentException) {
                    // receiver já removido
                }
            }
            zebraReceiver = null
        }
    }

    private fun isZebraDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.orEmpty()
        return manufacturer.contains("zebra", ignoreCase = true)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
