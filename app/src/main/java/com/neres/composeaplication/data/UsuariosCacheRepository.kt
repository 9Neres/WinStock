package com.neres.composeaplication.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Repositório de cache offline de usuários
 * Armazena lista de usuários localmente para uso offline
 */
object UsuariosCacheRepository {

    private const val PREFS_NAME = "usuarios_cache"
    private const val KEY_CACHE = "usuarios_cache_data"
    private const val KEY_LAST_SYNC = "last_sync_timestamp"

    private var context: Context? = null
    private val cache = mutableListOf<UsuarioRecord>()
    private val _cacheSizeFlow = MutableStateFlow<Int>(0)
    val cacheSizeFlow: StateFlow<Int> = _cacheSizeFlow.asStateFlow()

    /**
     * Inicializa o repositório de cache com o contexto
     */
    fun initialize(context: Context) {
        this.context = context.applicationContext
        loadCacheFromStorage()
    }

    /**
     * Carrega o cache do SharedPreferences
     */
    private fun loadCacheFromStorage() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_CACHE, null) ?: return

        try {
            val array = JSONArray(stored)
            synchronized(cache) {
                cache.clear()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val usuario = usuarioRecordFromJson(obj)
                    usuario?.let { cache.add(it) }
                }
                _cacheSizeFlow.value = cache.size
            }
        } catch (e: Exception) {
            clearCache()
        }
    }

    /**
     * Salva o cache no SharedPreferences
     */
    private fun saveCacheToStorage() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        try {
            val array = JSONArray()
            synchronized(cache) {
                cache.forEach { usuario ->
                    array.put(usuarioRecordToJson(usuario))
                }
            }
            prefs.edit()
                .putString(KEY_CACHE, array.toString())
                .putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            // Log error if needed
        }
    }

    /**
     * Limpa o cache
     */
    fun clearCache() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        synchronized(cache) {
            cache.clear()
            _cacheSizeFlow.value = 0
        }
        prefs.edit().remove(KEY_CACHE).remove(KEY_LAST_SYNC).apply()
    }

    /**
     * Atualiza o cache com lista de usuários
     */
    fun atualizarCache(usuarios: List<UsuarioRecord>) {
        synchronized(cache) {
            cache.clear()
            cache.addAll(usuarios)
            _cacheSizeFlow.value = cache.size
        }
        saveCacheToStorage()
    }

    /**
     * Obtém todos os usuários do cache
     */
    fun getAllCachedUsuarios(): List<UsuarioRecord> {
        return synchronized(cache) {
            cache.toList()
        }
    }
    
    /**
     * Autentica usuário usando o cache offline
     * Retorna o UsuarioRecord se credenciais válidas
     */
    fun autenticarOffline(user: String, password: String): UsuarioRecord? {
        return synchronized(cache) {
            cache.find { 
                it.user?.equals(user, ignoreCase = false) == true && 
                it.password?.equals(password, ignoreCase = false) == true 
            }
        }
    }

    /**
     * Obtém timestamp da última sincronização
     */
    fun getLastSyncTimestamp(): Long {
        val ctx = context ?: return 0L
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_SYNC, 0L)
    }

    /**
     * Verifica se o cache está vazio
     */
    fun isCacheEmpty(): Boolean = synchronized(cache) { cache.isEmpty() }

    // Helper functions para serialização
    private fun usuarioRecordToJson(usuario: UsuarioRecord): JSONObject {
        return JSONObject().apply {
            usuario.Id?.let { put("Id", it) }
            usuario.user?.let { put("user", it) }
            usuario.password?.let { put("password", it) }
            usuario.status?.let { put("status", it) }
            usuario.codFunc?.let { put("codFunc", it) }
        }
    }

    private fun usuarioRecordFromJson(json: JSONObject): UsuarioRecord? {
        return try {
            UsuarioRecord(
                Id = if (json.has("Id") && !json.isNull("Id")) json.getInt("Id") else null,
                user = if (json.has("user") && !json.isNull("user")) json.getString("user") else null,
                password = if (json.has("password") && !json.isNull("password")) json.getString("password") else null,
                status = if (json.has("status") && !json.isNull("status")) json.getString("status") else null,
                codFunc = if (json.has("codFunc") && !json.isNull("codFunc")) json.getInt("codFunc") else null
            )
        } catch (e: Exception) {
            null
        }
    }
}

