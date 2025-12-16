package com.neres.composeaplication.scanner

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ScannerController {

    private val _ultimoCodigo = MutableStateFlow<String?>(null)
    val ultimoCodigo: StateFlow<String?> = _ultimoCodigo.asStateFlow()

    fun onCodigoRecebido(codigo: String) {
        _ultimoCodigo.value = codigo
    }

    fun clearUltimoCodigo() {
        _ultimoCodigo.value = null
    }
}

