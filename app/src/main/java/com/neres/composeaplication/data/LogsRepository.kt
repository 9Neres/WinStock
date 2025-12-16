package com.neres.composeaplication.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tipos de log
 */
enum class TipoLog {
    INFO,
    AVISO,
    ERRO,
    SUCESSO
}

/**
 * Item de log do sistema
 */
data class LogItem(
    val timestamp: Long,
    val tipo: TipoLog,
    val categoria: String,
    val mensagem: String,
    val detalhes: String? = null
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("timestamp", timestamp)
            put("tipo", tipo.name)
            put("categoria", categoria)
            put("mensagem", mensagem)
            detalhes?.let { put("detalhes", it) }
        }
    }
    
    companion object {
        fun fromJson(json: JSONObject): LogItem {
            return LogItem(
                timestamp = json.getLong("timestamp"),
                tipo = TipoLog.valueOf(json.getString("tipo")),
                categoria = json.getString("categoria"),
                mensagem = json.getString("mensagem"),
                detalhes = if (json.has("detalhes") && !json.isNull("detalhes")) 
                    json.getString("detalhes") else null
            )
        }
    }
}

/**
 * Repositório de logs do sistema
 * Armazena logs de erros e eventos importantes para debug
 */
object LogsRepository {
    
    private const val PREFS_NAME = "system_logs"
    private const val KEY_LOGS = "logs_data"
    private const val MAX_LOGS = 500 // Máximo de logs armazenados
    
    private var context: Context? = null
    private val logs = mutableListOf<LogItem>()
    private val _logsFlow = MutableStateFlow<List<LogItem>>(emptyList())
    val logsFlow: StateFlow<List<LogItem>> = _logsFlow.asStateFlow()
    
    /**
     * Inicializa o repositório de logs com o contexto
     */
    fun initialize(context: Context) {
        this.context = context.applicationContext
        loadLogsFromStorage()
    }
    
    /**
     * Carrega logs do SharedPreferences
     */
    private fun loadLogsFromStorage() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_LOGS, null) ?: return
        
        try {
            val array = JSONArray(stored)
            synchronized(logs) {
                logs.clear()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    logs.add(LogItem.fromJson(obj))
                }
                _logsFlow.value = logs.toList()
            }
        } catch (e: Exception) {
            clearLogs()
        }
    }
    
    /**
     * Salva logs no SharedPreferences
     */
    private fun saveLogsToStorage() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        try {
            val array = JSONArray()
            synchronized(logs) {
                logs.forEach { log ->
                    array.put(log.toJson())
                }
            }
            prefs.edit().putString(KEY_LOGS, array.toString()).apply()
        } catch (e: Exception) {
            // Ignora erro ao salvar
        }
    }
    
    /**
     * Adiciona um log
     */
    fun log(tipo: TipoLog, categoria: String, mensagem: String, detalhes: String? = null) {
        val item = LogItem(
            timestamp = System.currentTimeMillis(),
            tipo = tipo,
            categoria = categoria,
            mensagem = mensagem,
            detalhes = detalhes
        )
        
        synchronized(logs) {
            logs.add(0, item) // Adiciona no início (mais recente primeiro)
            
            // Limita quantidade de logs
            if (logs.size > MAX_LOGS) {
                logs.removeAt(logs.size - 1)
            }
            
            _logsFlow.value = logs.toList()
        }
        
        saveLogsToStorage()
        
        // Log no Logcat também
        val tag = "SystemLog[$categoria]"
        when (tipo) {
            TipoLog.INFO -> android.util.Log.i(tag, mensagem)
            TipoLog.AVISO -> android.util.Log.w(tag, mensagem + (detalhes?.let { " | $it" } ?: ""))
            TipoLog.ERRO -> android.util.Log.e(tag, mensagem + (detalhes?.let { " | $it" } ?: ""))
            TipoLog.SUCESSO -> android.util.Log.d(tag, mensagem)
        }
    }
    
    /**
     * Atalhos para tipos específicos
     */
    fun erro(categoria: String, mensagem: String, detalhes: String? = null) {
        log(TipoLog.ERRO, categoria, mensagem, detalhes)
    }
    
    fun aviso(categoria: String, mensagem: String, detalhes: String? = null) {
        log(TipoLog.AVISO, categoria, mensagem, detalhes)
    }
    
    fun info(categoria: String, mensagem: String, detalhes: String? = null) {
        log(TipoLog.INFO, categoria, mensagem, detalhes)
    }
    
    fun sucesso(categoria: String, mensagem: String, detalhes: String? = null) {
        log(TipoLog.SUCESSO, categoria, mensagem, detalhes)
    }
    
    /**
     * Obtém todos os logs
     */
    fun getAllLogs(): List<LogItem> {
        return synchronized(logs) {
            logs.toList()
        }
    }
    
    /**
     * Filtra logs por tipo
     */
    fun getLogsByTipo(tipo: TipoLog): List<LogItem> {
        return synchronized(logs) {
            logs.filter { it.tipo == tipo }
        }
    }
    
    /**
     * Filtra logs por categoria
     */
    fun getLogsByCategoria(categoria: String): List<LogItem> {
        return synchronized(logs) {
            logs.filter { it.categoria == categoria }
        }
    }
    
    /**
     * Limpa todos os logs
     */
    fun clearLogs() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        synchronized(logs) {
            logs.clear()
            _logsFlow.value = emptyList()
        }
        prefs.edit().remove(KEY_LOGS).apply()
    }
    
    /**
     * Formata timestamp para exibição
     */
    fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

