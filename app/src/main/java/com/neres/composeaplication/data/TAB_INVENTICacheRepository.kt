package com.neres.composeaplication.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Repositório de cache offline das tabelas TAB_INVENTI, TAB_INVENTIC e PCPRODUT
 * Armazena todos os dados das tabelas localmente para uso offline completo
 */
object TAB_INVENTICacheRepository {

    private const val PREFS_NAME = "tab_inventi_cache"
    private const val KEY_CACHE = "inventi_cache_data"
    private const val KEY_LAST_SYNC = "last_sync_timestamp"
    
    // Chaves para TAB_INVENTIC
    private const val KEY_INVENTIC_CACHE = "inventic_cache_data"
    private const val KEY_INVENTIC_LAST_SYNC = "inventic_last_sync_timestamp"
    
    // Chaves para PCPRODUT
    private const val KEY_PCPRODUT_CACHE = "pcprodut_cache_data"
    private const val KEY_PCPRODUT_LAST_SYNC = "pcprodut_last_sync_timestamp"

    private var context: Context? = null
    private val cache = mutableMapOf<Long, ProdutoRecord>() // CODBARID (Long) -> ProdutoRecord (TAB_INVENTI)
    private val cacheTAB_INVENTIC = mutableMapOf<Long, ProdutoRecord>() // CODBARID (Long) -> ProdutoRecord (TAB_INVENTIC)
    private val cachePCPRODUT = mutableMapOf<Int, PCPRODUTRecord>() // CODPROD (Int) -> PCPRODUTRecord
    private val _cacheSizeFlow = MutableStateFlow<Int>(0)
    val cacheSizeFlow: StateFlow<Int> = _cacheSizeFlow.asStateFlow()

    /**
     * Inicializa o repositório de cache com o contexto
     */
    fun initialize(context: Context) {
        this.context = context.applicationContext
        loadCacheFromStorage()
        loadTAB_INVENTICCacheFromStorage()
        loadPCPRODUTCacheFromStorage()
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
                    val produto = produtoRecordFromJson(obj)
                    produto?.codBarId?.toLongOrNull()?.let { codBarIdLong ->
                        cache[codBarIdLong] = produto
                    }
                }
                _cacheSizeFlow.value = cache.size
            }
        } catch (e: Exception) {
            // Se houver erro, limpa o cache corrompido
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
                cache.values.forEach { produto ->
                    array.put(produtoRecordToJson(produto))
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
     * Busca produto no cache pelo CODBARID
     */
    fun buscarNoCache(codBarId: String): ProdutoRecord? {
        val codBarIdLong = codBarId.trim().toLongOrNull() ?: return null
        return synchronized(cache) {
            cache[codBarIdLong]
        }
    }

    /**
     * Atualiza o cache com lista de produtos
     */
    fun atualizarCache(produtos: List<ProdutoRecord>) {
        synchronized(cache) {
            cache.clear()
            produtos.forEach { produto ->
                produto.codBarId?.toLongOrNull()?.let { codBarIdLong ->
                    cache[codBarIdLong] = produto
                }
            }
            _cacheSizeFlow.value = cache.size
        }
        saveCacheToStorage()
    }

    /**
     * Obtém o tamanho do cache
     */
    fun getCacheSize(): Int = synchronized(cache) { cache.size }

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
    
    /**
     * Busca NUMINVENT únicos do cache offline
     */
    fun getNUMINVENTUnicosDoCache(): List<Int> {
        return synchronized(cache) {
            cache.values
                .mapNotNull { it.numInvent }
                .distinct()
                .sorted()
        }
    }
    
    /**
     * Obtém todos os produtos do cache
     */
    fun getAllCachedProducts(): List<ProdutoRecord> {
        return synchronized(cache) {
            cache.values.toList()
        }
    }
    
    /**
     * Salva lista de pendentes no cache
     */
    fun salvarPendentes(pendentes: List<ProdutoRecord>) {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        try {
            val array = JSONArray()
            pendentes.forEach { produto ->
                array.put(produtoRecordToJson(produto))
            }
            prefs.edit()
                .putString("pendentes_cache", array.toString())
                .putLong("pendentes_last_sync", System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            // Log error if needed
        }
    }
    
    /**
     * Carrega lista de pendentes do cache
     */
    fun carregarPendentes(): List<ProdutoRecord> {
        val ctx = context ?: return emptyList()
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString("pendentes_cache", null) ?: return emptyList()
        
        return try {
            val array = JSONArray(stored)
            buildList(array.length()) {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val produto = produtoRecordFromJson(obj)
                    produto?.let { add(it) }
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Salva estatísticas de linhas
     */
    fun salvarEstatisticasLinhas(totalLinhas: Int, completas: Int, pendentes: Int) {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("total_linhas", totalLinhas)
            .putInt("completas_linhas", completas)
            .putInt("pendentes_linhas", pendentes)
            .putLong("estatisticas_last_update", System.currentTimeMillis())
            .apply()
    }
    
    /**
     * Carrega estatísticas de linhas
     */
    fun carregarEstatisticasLinhas(): Triple<Int, Int, Int> {
        val ctx = context ?: return Triple(0, 0, 0)
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return Triple(
            prefs.getInt("total_linhas", 0),
            prefs.getInt("completas_linhas", 0),
            prefs.getInt("pendentes_linhas", 0)
        )
    }
    
    // ===== CACHE TAB_INVENTIC =====
    
    /**
     * Carrega cache da TAB_INVENTIC do SharedPreferences
     */
    private fun loadTAB_INVENTICCacheFromStorage() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_INVENTIC_CACHE, null) ?: return

        try {
            val array = JSONArray(stored)
            synchronized(cacheTAB_INVENTIC) {
                cacheTAB_INVENTIC.clear()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val produto = produtoRecordFromJson(obj)
                    produto?.codBarId?.toLongOrNull()?.let { codBarIdLong ->
                        cacheTAB_INVENTIC[codBarIdLong] = produto
                    }
                }
            }
        } catch (e: Exception) {
            cacheTAB_INVENTIC.clear()
        }
    }
    
    /**
     * Atualiza cache da TAB_INVENTIC
     */
    fun atualizarCacheTAB_INVENTIC(produtos: List<ProdutoRecord>) {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        synchronized(cacheTAB_INVENTIC) {
            cacheTAB_INVENTIC.clear()
            produtos.forEach { produto ->
                produto.codBarId?.toLongOrNull()?.let { codBarIdLong ->
                    cacheTAB_INVENTIC[codBarIdLong] = produto
                }
            }
        }
        
        try {
            val array = JSONArray()
            produtos.forEach { produto ->
                array.put(produtoRecordToJson(produto))
            }
            prefs.edit()
                .putString(KEY_INVENTIC_CACHE, array.toString())
                .putLong(KEY_INVENTIC_LAST_SYNC, System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            // Log error if needed
        }
    }
    
    /**
     * Busca produto no cache da TAB_INVENTIC pelo CODBARID
     */
    fun buscarNoTAB_INVENTICCache(codBarId: String): ProdutoRecord? {
        val codBarIdLong = codBarId.trim().toLongOrNull() ?: return null
        return synchronized(cacheTAB_INVENTIC) {
            cacheTAB_INVENTIC[codBarIdLong]
        }
    }
    
    /**
     * Obtém todos os produtos do cache TAB_INVENTIC
     */
    fun getAllCachedTAB_INVENTIC(): List<ProdutoRecord> {
        return synchronized(cacheTAB_INVENTIC) {
            cacheTAB_INVENTIC.values.toList()
        }
    }
    
    /**
     * Obtém IDs de CODBARIDs completos do cache TAB_INVENTIC
     * TODOS os registros da TAB_INVENTIC são considerados completos
     */
    fun getCODBARIDsCompletosDoCache(): Set<Long> {
        return synchronized(cacheTAB_INVENTIC) {
            cacheTAB_INVENTIC.values
                .mapNotNull { it.codBarId?.toLongOrNull() }
                .toSet()
        }
    }
    
    /**
     * Obtém timestamp da última sincronização TAB_INVENTIC
     */
    fun getTAB_INVENTICLastSyncTimestamp(): Long {
        val ctx = context ?: return 0L
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_INVENTIC_LAST_SYNC, 0L)
    }
    
    // ===== CACHE PCPRODUT =====
    
    /**
     * Carrega cache da PCPRODUT do SharedPreferences
     */
    private fun loadPCPRODUTCacheFromStorage() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_PCPRODUT_CACHE, null) ?: return

        try {
            val array = JSONArray(stored)
            synchronized(cachePCPRODUT) {
                cachePCPRODUT.clear()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val produto = pcProdutRecordFromJson(obj)
                    produto?.codProd?.let { codProd ->
                        cachePCPRODUT[codProd] = produto
                    }
                }
            }
        } catch (e: Exception) {
            cachePCPRODUT.clear()
        }
    }
    
    /**
     * Atualiza cache da PCPRODUT
     */
    fun atualizarCachePCPRODUT(produtos: List<PCPRODUTRecord>) {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        synchronized(cachePCPRODUT) {
            cachePCPRODUT.clear()
            produtos.forEach { produto ->
                produto.codProd?.let { codProd ->
                    cachePCPRODUT[codProd] = produto
                }
            }
        }
        
        try {
            val array = JSONArray()
            produtos.forEach { produto ->
                array.put(pcProdutRecordToJson(produto))
            }
            prefs.edit()
                .putString(KEY_PCPRODUT_CACHE, array.toString())
                .putLong(KEY_PCPRODUT_LAST_SYNC, System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            // Log error if needed
        }
    }
    
    /**
     * Busca PRODUT no cache PCPRODUT pelo CODPROD
     */
    fun buscarPRODUTNoCache(codProd: Int): String? {
        return synchronized(cachePCPRODUT) {
            cachePCPRODUT[codProd]?.produt
        }
    }
    
    /**
     * Busca em lote no cache PCPRODUT
     */
    fun buscarPRODUTEmLoteNoCache(codProds: List<Int>): Map<Int, String> {
        return synchronized(cachePCPRODUT) {
            codProds.mapNotNull { codProd ->
                cachePCPRODUT[codProd]?.let { codProd to it.produt }
            }.filter { it.second != null }.associate { it.first to it.second!! }
        }
    }
    
    /**
     * Obtém todos os produtos do cache PCPRODUT
     */
    fun getAllCachedPCPRODUT(): List<PCPRODUTRecord> {
        return synchronized(cachePCPRODUT) {
            cachePCPRODUT.values.toList()
        }
    }
    
    /**
     * Obtém timestamp da última sincronização PCPRODUT
     */
    fun getPCPRODUTLastSyncTimestamp(): Long {
        val ctx = context ?: return 0L
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_PCPRODUT_LAST_SYNC, 0L)
    }

    // Helper functions para serialização
    private fun produtoRecordToJson(produto: ProdutoRecord): JSONObject {
        return JSONObject().apply {
            produto.id?.let { put("id", it) }
            produto.numInvent?.let { put("numInvent", it) }
            produto.codFilial?.let { put("codFilial", it) }
            produto.codProd?.let { put("codProd", it) }
            produto.codBarId?.let { put("codBarId", it) }
            produto.qtProd?.let { put("qtProd", it) }
            produto.qtCont?.let { put("qtCont", it) }
            produto.codFunc?.let { put("codFunc", it) }
            produto.status?.let { put("status", it) }
            produto.produt?.let { put("produt", it) } // Inclui PRODUT
        }
    }

    private fun produtoRecordFromJson(json: JSONObject): ProdutoRecord? {
        return try {
            ProdutoRecord(
                id = if (json.has("id") && !json.isNull("id")) json.getInt("id") else null,
                numInvent = if (json.has("numInvent") && !json.isNull("numInvent")) json.getInt("numInvent") else null,
                codFilial = if (json.has("codFilial") && !json.isNull("codFilial")) json.getInt("codFilial") else null,
                codProd = if (json.has("codProd") && !json.isNull("codProd")) json.getInt("codProd") else null,
                codBarId = if (json.has("codBarId") && !json.isNull("codBarId")) json.getString("codBarId") else null,
                qtProd = if (json.has("qtProd") && !json.isNull("qtProd")) json.getInt("qtProd") else null,
                qtCont = if (json.has("qtCont") && !json.isNull("qtCont")) json.getInt("qtCont") else null,
                codFunc = if (json.has("codFunc") && !json.isNull("codFunc")) json.getInt("codFunc") else null,
                status = if (json.has("status") && !json.isNull("status")) json.getString("status") else null,
                produt = if (json.has("produt") && !json.isNull("produt")) json.getString("produt") else null // Inclui PRODUT
            )
        } catch (e: Exception) {
            null
        }
    }
    
    private fun pcProdutRecordToJson(produto: PCPRODUTRecord): JSONObject {
        return JSONObject().apply {
            produto.id?.let { put("id", it) }
            produto.codProd?.let { put("codProd", it) }
            produto.produt?.let { put("produt", it) }
        }
    }
    
    private fun pcProdutRecordFromJson(json: JSONObject): PCPRODUTRecord? {
        return try {
            PCPRODUTRecord(
                id = if (json.has("id") && !json.isNull("id")) json.getInt("id") else null,
                codProd = if (json.has("codProd") && !json.isNull("codProd")) json.getInt("codProd") else null,
                produt = if (json.has("produt") && !json.isNull("produt")) json.getString("produt") else null
            )
        } catch (e: Exception) {
            null
        }
    }
}