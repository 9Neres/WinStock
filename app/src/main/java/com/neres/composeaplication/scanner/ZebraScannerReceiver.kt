package com.neres.composeaplication.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.neres.composeaplication.data.ScannedCodesRepository

class ZebraScannerReceiver(
    private val onCodigoRecebido: (String) -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val codigo = intent?.getStringExtra(DATAWEDGE_DATA_KEY)
            ?: intent?.getStringExtra(LEGACY_DATA_KEY)

        if (!codigo.isNullOrBlank()) {
            onCodigoRecebido(codigo)
        }
    }

    companion object {
        const val DATAWEDGE_ACTION = "com.neres.composeaplication.SCAN"
        private const val DATAWEDGE_DATA_KEY = "com.symbol.datawedge.data_string"
        private const val LEGACY_DATA_KEY = "com.motorolasolutions.emdk.datawedge.data_string"
    }
}

