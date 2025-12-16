package com.neres.composeaplication.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class ScanneadoItem(
    val numInvent: Int? = null,
    val status: String? = null,
    val codFilial: Int? = null,
    val codProd: Int? = null,
    val codBarId: String,
    val qtProd: Int? = null,
    val qtCont: Int? = null,
    val codFunc: Int? = null,
    val data: String? = null, // Data/hora do scanneamento
    val produt: String? = null // Nome do produto da PCPRODUT
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("codBarId", codBarId)
            numInvent?.let { put("numInvent", it) }
            status?.let { put("status", it) }
            codFilial?.let { put("codFilial", it) }
            codProd?.let { put("codProd", it) }
            qtProd?.let { put("qtProd", it) }
            qtCont?.let { put("qtCont", it) }
            codFunc?.let { put("codFunc", it) }
            data?.let { put("data", it) }
            produt?.let { put("produt", it) }
        }
    }

    companion object {
        fun fromJson(json: JSONObject): ScanneadoItem {
            return ScanneadoItem(
                codBarId = json.getString("codBarId"),
                numInvent = if (json.has("numInvent") && !json.isNull("numInvent")) json.getInt("numInvent") else null,
                status = if (json.has("status") && !json.isNull("status")) json.getString("status") else null,
                codFilial = if (json.has("codFilial") && !json.isNull("codFilial")) json.getInt("codFilial") else null,
                codProd = if (json.has("codProd") && !json.isNull("codProd")) json.getInt("codProd") else null,
                qtProd = if (json.has("qtProd") && !json.isNull("qtProd")) json.getInt("qtProd") else null,
                qtCont = if (json.has("qtCont") && !json.isNull("qtCont")) json.getInt("qtCont") else null,
                codFunc = if (json.has("codFunc") && !json.isNull("codFunc")) json.getInt("codFunc") else null,
                data = if (json.has("data") && !json.isNull("data")) json.getString("data") else null,
                produt = if (json.has("produt") && !json.isNull("produt")) json.getString("produt") else null
            )
        }
    }
}

object ScannedCodesRepository {

    private const val PREFS_NAME = "scanned_codes_storage"
    private const val KEY_ITEMS = "scanned_items"

    private var context: Context? = null
    private val items = mutableListOf<ScanneadoItem>()
    private val _itemsFlow = MutableStateFlow<List<ScanneadoItem>>(emptyList())
    val itemsFlow: StateFlow<List<ScanneadoItem>> = _itemsFlow.asStateFlow()

    /**
     * Inicializa o repositório com o contexto da aplicação.
     * Deve ser chamado no onCreate da MainActivity ou Application.
     */
    fun initialize(context: Context) {
        this.context = context.applicationContext
        loadFromStorage()
    }

    /**
     * Carrega os dados salvos do SharedPreferences
     */
    private fun loadFromStorage() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_ITEMS, null) ?: return

        try {
            val array = JSONArray(stored)
            synchronized(items) {
                items.clear()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    items.add(ScanneadoItem.fromJson(obj))
                }
                _itemsFlow.value = items.toList()
            }
        } catch (e: Exception) {
            // Se houver erro ao carregar, limpa os dados corrompidos
            clearStorage()
        }
    }

    /**
     * Salva os dados no SharedPreferences
     */
    private fun saveToStorage() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        try {
            val array = JSONArray()
            synchronized(items) {
                items.forEach { item ->
                    array.put(item.toJson())
                }
            }
            prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
        } catch (e: Exception) {
            // Log error if needed
        }
    }

    /**
     * Limpa o armazenamento persistente
     */
    private fun clearStorage() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_ITEMS).apply()
    }

    fun addCode(code: String) {
        if (code.isBlank()) return
        val item = ScanneadoItem(codBarId = code.trim())
        addItem(item)
    }

    fun addItem(item: ScanneadoItem) {
        val codigo = item.codBarId.trim()
        if (codigo.isBlank()) return
        
        val novoItem = item.copy(codBarId = codigo)
        synchronized(items) {
            // Evita duplicatas baseado no codBarId
            if (items.none { it.codBarId == codigo }) {
                items.add(novoItem)
                _itemsFlow.value = items.toList()
                saveToStorage()
            }
        }
    }

    fun getItems(): List<ScanneadoItem> =
        synchronized(items) { items.toList() }

    fun getCodes(): List<String> =
        synchronized(items) { items.map { it.codBarId } }

    fun clear() {
        synchronized(items) {
            items.clear()
            _itemsFlow.value = emptyList()
            clearStorage()
        }
    }

    /**
     * Remove um item específico pelo codBarId
     */
    fun removeItem(codBarId: String) {
        synchronized(items) {
            if (items.removeAll { it.codBarId == codBarId }) {
                _itemsFlow.value = items.toList()
                saveToStorage()
            }
        }
    }
}
