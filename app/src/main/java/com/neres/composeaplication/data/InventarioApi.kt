package com.neres.composeaplication.data

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Aceita número OU string e sempre devolve String.
 */
object StringOrNumberAsString : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("StringOrNumberAsString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val jd = decoder as? JsonDecoder
        val el = jd?.decodeJsonElement() ?: return ""
        return el.jsonPrimitive.content
    }
    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}

@Serializable
data class ProdutoRecord(
    @SerialName("id") val id: Int? = null,

    @SerialName("NUMINVENT") val numInvent: Int? = null,
    @SerialName("CODFILIAL") val codFilial: Int? = null,
    @SerialName("CODPROD")   val codProd: Int? = null,

    @SerialName("CODBARID")
    @Serializable(with = StringOrNumberAsString::class)
    val codBarId: String? = null,

    @SerialName("QTPROD")    val qtProd: Int? = null,
    @SerialName("QTCONT")    val qtCont: Int? = null,
    @SerialName("CODFUNC")   val codFunc: Int? = null,
    @SerialName("STATUS")    val status: String? = null,
    @SerialName("DATA")      val data: String? = null,
    @SerialName("PRODUT")    val produt: String? = null // Campo PRODUT da tabela PCPRODUT
)

@Serializable
data class ProdutosResponse(
    val list: List<ProdutoRecord> = emptyList()
)

data class ResultadoEnvio(
    val enviados: Int,
    val erros: List<String>
)

data class StatusResumo(
    val concluidos: Int,
    val pendentes: Int
)

data class NovoRegistroInventario(
    val numInvent: Int?,
    val codFilial: Int?,
    val codProd: Int?,
    val codBarId: String?,
    val qtProd: Int?,
    val qtCont: Int?,
    val codFunc: Int?,
    val status: String?
)

data class CadastroRegistroResultado(
    val sucesso: Boolean,
    val mensagem: String? = null
)

/**
 * Dados encontrados na TAB_INVENTI para preencher automaticamente
 */
data class DadosTAB_INVENTI(
    val qtProd: Int?,
    val codFilial: Int?,
    val numInvent: Int?,
    val codProd: Int? = null,
    val produt: String? = null // Nome do produto da PCPRODUT
)

/**
 * Record da tabela PCPRODUT
 */
@Serializable
data class PCPRODUTRecord(
    @SerialName("id") val id: Int? = null,
    @SerialName("CODPROD") val codProd: Int? = null,
    @SerialName("PRODUT") val produt: String? = null
)

@Serializable
data class PCPRODUTResponse(
    val list: List<PCPRODUTRecord> = emptyList()
)

data class ResultadoSincronizacao(
    val sucesso: Boolean,
    val totalRegistros: Int,
    val mensagem: String
)

data class ResultadoResumo24h(
    val registros: List<ProdutoRecord>,
    val erro: String? = null,
    val usandoOffline: Boolean = false
)

object InventarioApi {

    // TAB_INVENTI: Tabela de COMPARAÇÃO (somente leitura)
    // Usada apenas para buscar informações como QTPROD para comparação
    // NÃO deve ser modificada (sem PATCH/POST)
    private const val BASE_URL = ""
    
    // TAB_INVENTIC: Tabela de DADOS (escrita)
    // Tabela onde os dados scanneados são salvos
    private const val BASE_URL_INVENTIC = ""
    
    // PCPRODUT: Tabela de produtos (somente leitura)
    // Usada para buscar PRODUT baseado no CODPROD
    private const val BASE_URL_PCPRODUT = ""
    private const val TOKEN = ""
    private const val TABLE_ID = ""
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }
        install(Logging) {
            level = LogLevel.INFO
            logger = Logger.DEFAULT
        }
        defaultRequest {
            header("xc-token", TOKEN)
            accept(ContentType.Application.Json)
        }
    }

    /**
     * GET - Lista os produtos do inventário
     * Usa TAB_INVENTI apenas para COMPARAÇÃO (somente leitura)
     * Busca PRODUT da tabela PCPRODUT para cada produto usando CODPROD
     */
    suspend fun getProdutos(limit: Int = 1000, offset: Int = 0): List<ProdutoRecord> {
        return try {
            val res = client.get("$BASE_URL/records") {
                parameter("limit", limit)
                parameter("offset", offset)
            }

            if (res.status != HttpStatusCode.OK) {
                println("❌ getProdutos: HTTP ${res.status} - ${res.bodyAsText()}")
                emptyList()
            } else if (res.contentType()?.match(ContentType.Application.Json) != true) {
                println("❌ getProdutos: conteúdo não é JSON (${res.contentType()})")
                emptyList()
            } else {
                val payload = res.bodyAsText()
                val produtos = json.decodeFromString<ProdutosResponse>(payload).list
                
                // Busca PRODUT para cada produto usando CODPROD
                produtos.map { produto ->
                    val produt = produto.codProd?.let { buscarPRODUTPorCODPROD(it) }
                    produto.copy(produt = produt)
                }
            }
        } catch (e: Exception) {
            println("❌ getProdutos falhou: ${e.message}")
            emptyList()
        }
    }

    /**
     * GET - Busca registros completos da TAB_INVENTIC (QTCONT > 0)
     * Retorna um Set de CODBARIDs que já estão completos
     * Usado para determinar o status dos produtos na lista
     * Tenta cache persistente primeiro, depois busca online
     */
    suspend fun getCODBARIDsCompletosTAB_INVENTIC(forcarOnline: Boolean = false): Set<Long> {
        // Tenta usar cache persistente primeiro (se não forçar online)
        if (!forcarOnline) {
            val completosIds = TAB_INVENTICacheRepository.getCODBARIDsCompletosDoCache()
            if (completosIds.isNotEmpty()) {
                Log.d("InventarioApi", "📦 Usando cache: ${completosIds.size} CODBARIDs completos")
                return completosIds
            }
        }
        
        // Busca online se cache vazio ou forçado
        return try {
            Log.d("InventarioApi", "📡 Buscando CODBARIDs completos online...")
            val todosCompletos = mutableSetOf<Long>()
            var offset = 0
            val limit = 1000

            while (true) {
                val res = client.get("$BASE_URL_INVENTIC/records") {
                    // TODOS os registros da TAB_INVENTIC são completos (sem filtro de QTCONT)
                    parameter("limit", limit)
                    parameter("offset", offset)
                }

                if (res.status != HttpStatusCode.OK) {
                    Log.e("InventarioApi", "Erro ao buscar completos: ${res.status}")
                    break
                }

                val payload = res.bodyAsText()
                val response = json.decodeFromString<ProdutosResponse>(payload)
                
                response.list.forEach { registro ->
                    registro.codBarId?.toLongOrNull()?.let { codBarIdLong ->
                        todosCompletos.add(codBarIdLong)
                    }
                }

                if (response.list.size < limit) {
                    break // Não há mais registros
                }

                offset += limit
            }

            Log.d("InventarioApi", "✅ Encontrados ${todosCompletos.size} CODBARIDs completos online")
            todosCompletos
        } catch (e: Exception) {
            Log.e("InventarioApi", "Erro ao buscar completos online: ${e.message}")
            // Retorna cache em caso de erro
            TAB_INVENTICacheRepository.getAllCachedTAB_INVENTIC()
                .filter { (it.qtCont ?: 0) > 0 }
                .mapNotNull { it.codBarId?.toLongOrNull() }
                .toSet()
        }
    }

    /**
     * GET - Busca QTCONT da TAB_INVENTIC para um CODBARID específico
     * Retorna o QTCONT se existir na TAB_INVENTIC, null caso contrário
     */
    suspend fun getQTCONTdaTAB_INVENTIC(codBarId: String): Int? {
        return try {
            val codBarIdNumero = codBarId.trim().toLongOrNull()
            if (codBarIdNumero == null) {
                return null
            }

            val res = client.get("$BASE_URL_INVENTIC/records") {
                parameter("where", "(CODBARID,eq,$codBarIdNumero)")
                parameter("limit", 1)
            }

            if (res.status != HttpStatusCode.OK) {
                return null
            }

            val payload = res.bodyAsText()
            val data = json.decodeFromString<ProdutosResponse>(payload).list.firstOrNull()
            data?.qtCont
        } catch (e: Exception) {
            Log.d("InventarioApi", "Erro ao buscar QTCONT da TAB_INVENTIC: ${e.message}")
            null
        }
    }

    /**
     * GET - Lista os produtos do inventário com status baseado na TAB_INVENTIC
     * Busca da TAB_INVENTI e verifica se está completo na TAB_INVENTIC (QTCONT > 0)
     * Todos os registros são mostrados, mas o status é determinado pela TAB_INVENTIC
     * Também busca PRODUT da tabela PCPRODUT para cada produto
     */
    suspend fun getProdutosComStatus(limit: Int = 1000, offset: Int = 0): List<ProdutoRecord> {
        return try {
            // Busca produtos da TAB_INVENTI
            val produtos = getProdutos(limit = limit, offset = offset)
            
            if (produtos.isEmpty()) {
                Log.d("InventarioApi", "Nenhum produto encontrado")
                return emptyList()
            }
            
            // Busca CODBARIDs completos na TAB_INVENTIC (QTCONT > 0)
            val completos = getCODBARIDsCompletosTAB_INVENTIC()
            
            // Busca PRODUT em lote (otimizado)
            val codProds = produtos.mapNotNull { it.codProd }
            val produtMap = buscarPRODUTEmLote(codProds)
            
            // Atualiza o status de cada produto baseado na TAB_INVENTIC e busca PRODUT
            val produtosComStatus = produtos.map { produto ->
                val codBarIdLong = produto.codBarId?.toLongOrNull()
                val estaCompleto = codBarIdLong != null && codBarIdLong in completos
                
                // Busca PRODUT do mapa (já buscado em lote)
                val produt = produto.codProd?.let { produtMap[it] }
                
                // Se está completo na TAB_INVENTIC, marca como concluído
                // Caso contrário, mantém o status original ou marca como pendente
                produto.copy(
                    status = if (estaCompleto) "Concluído" else (produto.status ?: "Pendente"),
                    produt = produt
                )
            }
            
            Log.d("InventarioApi", "📊 Produtos retornados: ${produtosComStatus.size}, Completos: ${completos.size}, PRODUTs buscados: ${produtMap.size}")
            produtosComStatus
        } catch (e: Exception) {
            Log.e("InventarioApi", "Erro ao buscar produtos com status: ${e.message}")
            emptyList()
        }
    }

    /**
     * GET - Busca QTPROD do banco baseado no CODBARID
     * Usa TAB_INVENTI apenas para COMPARAÇÃO (somente leitura)
     * CODBARID é NUMBER na tabela, então busca como número
     * @deprecated Use buscarDadosTAB_INVENTI() para buscar todos os campos
     */
    suspend fun buscarQTPRODPorCODBARID(codBarras: String): Int? {
        return buscarDadosTAB_INVENTI(codBarras)?.qtProd
    }

    /**
     * GET - Busca PRODUT da tabela PCPRODUT baseado no CODPROD
     * Tenta cache offline primeiro, depois online
     */
    suspend fun buscarPRODUTPorCODPROD(codProd: Int?): String? {
        if (codProd == null) return null
        
        // Tenta buscar no cache primeiro
        val produtCache = TAB_INVENTICacheRepository.buscarPRODUTNoCache(codProd)
        if (produtCache != null) {
            return produtCache
        }
        
        // Se não encontrou no cache, busca online
        return try {
            val res = client.get("$BASE_URL_PCPRODUT/records") {
                parameter("where", "(CODPROD,eq,$codProd)")
                parameter("limit", 1)
            }

            if (res.status != HttpStatusCode.OK) {
                Log.d("InventarioApi", "Erro ao buscar PRODUT: ${res.status}")
                return null
            }

            val payload = res.bodyAsText()
            val response = json.decodeFromString<PCPRODUTResponse>(payload)
            response.list.firstOrNull()?.produt
        } catch (e: Exception) {
            Log.e("InventarioApi", "Erro ao buscar PRODUT: ${e.message}")
            null
        }
    }

    /**
     * GET - Busca PRODUT em lote para múltiplos CODPRODs
     * Otimizado para reduzir chamadas à API - tenta cache primeiro
     */
    suspend fun buscarPRODUTEmLote(codProds: List<Int>): Map<Int, String> {
        if (codProds.isEmpty()) return emptyMap()
        
        val resultado = mutableMapOf<Int, String>()
        val codProdsUnicos = codProds.distinct()
        
        // Tenta buscar no cache primeiro
        val produtsCacheados = TAB_INVENTICacheRepository.buscarPRODUTEmLoteNoCache(codProdsUnicos)
        resultado.putAll(produtsCacheados)
        
        // Identifica quais ainda faltam buscar online
        val codProdsFaltantes = codProdsUnicos.filter { it !in resultado.keys }
        
        if (codProdsFaltantes.isEmpty()) {
            // Todos encontrados no cache
            return resultado
        }
        
        // Busca os faltantes online
        return try {
            // Busca em lotes de 100 para evitar URLs muito longas
            val tamanhoLote = 100
            codProdsFaltantes.chunked(tamanhoLote).forEach { lote ->
                // Cria expressão WHERE com múltiplos CODPRODs usando OR
                val whereExpression = lote.joinToString("~or") { codProd ->
                    "(CODPROD,eq,$codProd)"
                }
                
                val res = client.get("$BASE_URL_PCPRODUT/records") {
                    parameter("where", whereExpression)
                    parameter("limit", tamanhoLote)
                }
                
                if (res.status == HttpStatusCode.OK) {
                    val payload = res.bodyAsText()
                    val response = json.decodeFromString<PCPRODUTResponse>(payload)
                    response.list.forEach { produto ->
                        produto.codProd?.let { codProd ->
                            produto.produt?.let { produt ->
                                resultado[codProd] = produt
                            }
                        }
                    }
                }
            }
            resultado
        } catch (e: Exception) {
            Log.e("InventarioApi", "Erro ao buscar PRODUT em lote: ${e.message}")
            resultado
        }
    }

    /**
     * GET - Busca PRODUT baseado no CODBARID
     * 1. Busca CODPROD na TAB_INVENTI ou TAB_INVENTIC
     * 2. Busca PRODUT na PCPRODUT usando o CODPROD encontrado
     */
    suspend fun buscarPRODUTPorCODBARID(codBarras: String): String? {
        // Primeiro busca o CODPROD na TAB_INVENTI ou TAB_INVENTIC
        val codBarIdNumero = codBarras.trim().toLongOrNull() ?: return null
        
        // Tenta buscar na TAB_INVENTI primeiro
        val produtoTAB_INVENTI = try {
            val res = client.get("$BASE_URL/records") {
                parameter("where", "(CODBARID,eq,$codBarIdNumero)")
                parameter("limit", 1)
            }
            if (res.status == HttpStatusCode.OK) {
                val payload = res.bodyAsText()
                json.decodeFromString<ProdutosResponse>(payload).list.firstOrNull()
            } else null
        } catch (e: Exception) {
            null
        }
        
        // Se não encontrou na TAB_INVENTI, tenta na TAB_INVENTIC
        val produto = produtoTAB_INVENTI ?: try {
            val res = client.get("$BASE_URL_INVENTIC/records") {
                parameter("where", "(CODBARID,eq,$codBarIdNumero)")
                parameter("limit", 1)
            }
            if (res.status == HttpStatusCode.OK) {
                val payload = res.bodyAsText()
                json.decodeFromString<ProdutosResponse>(payload).list.firstOrNull()
            } else null
        } catch (e: Exception) {
            null
        }
        
        // Se encontrou o produto, busca o PRODUT usando o CODPROD
        return produto?.codProd?.let { buscarPRODUTPorCODPROD(it) }
    }

    /**
     * GET - Busca registro na TAB_INVENTIC (dados já coletados)
     * Tenta cache offline primeiro, depois online
     */
    suspend fun buscarNaTAB_INVENTIC(codBarId: String): ProdutoRecord? {
        // Tenta buscar no cache primeiro
        val cache = TAB_INVENTICacheRepository.buscarNoTAB_INVENTICCache(codBarId)
        if (cache != null) {
            return cache
        }
        
        // Se não encontrou no cache, busca online
        val codBarIdNumero = codBarId.trim().toLongOrNull() ?: return null
        
        return try {
            val res = client.get("$BASE_URL_INVENTIC/records") {
                parameter("where", "(CODBARID,eq,$codBarIdNumero)")
                parameter("limit", 1)
            }
            
            if (res.status == HttpStatusCode.OK) {
                val payload = res.bodyAsText()
                json.decodeFromString<ProdutosResponse>(payload).list.firstOrNull()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("InventarioApi", "Erro ao buscar na TAB_INVENTIC: ${e.message}")
            null
        }
    }
    
    /**
     * GET - Busca dados completos na TAB_INVENTI baseado no CODBARID
     * Retorna QTPROD, CODFILIAL, NUMINVENT, CODPROD e PRODUT
     * Usa TAB_INVENTI apenas para COMPARAÇÃO (somente leitura)
     * CODBARID é NUMBER na tabela, então busca como número
     * 
     * Tenta buscar online primeiro, se falhar usa cache offline
     * Também busca o PRODUT usando o CODPROD
     */
    suspend fun buscarDadosTAB_INVENTI(codBarras: String): DadosTAB_INVENTI? {
        // Converte CODBARID para número (já que é NUMBER na tabela)
        val codBarIdNumero = codBarras.trim().toLongOrNull()
        if (codBarIdNumero == null) {
            Log.e("InventarioApi", "CODBARID inválido (não é número): $codBarras")
            return null
        }

        // Tenta buscar online primeiro
        val dadosOnline = try {
            val searchResponse = client.get("$BASE_URL/records") {
                parameter("where", "(CODBARID,eq,$codBarIdNumero)")
                parameter("limit", 1)
            }

            if (searchResponse.status == HttpStatusCode.OK) {
                val searchPayload = searchResponse.bodyAsText()
                val data = json.decodeFromString<ProdutosResponse>(searchPayload).list.firstOrNull()
                data
            } else {
                null
            }
        } catch (e: Exception) {
            Log.d("InventarioApi", "Erro ao buscar online, tentando cache: ${e.message}")
            null
        }

        // Se encontrou online, busca PRODUT e retorna
        if (dadosOnline != null) {
            val produt = dadosOnline.codProd?.let { buscarPRODUTPorCODPROD(it) }
            return DadosTAB_INVENTI(
                qtProd = dadosOnline.qtProd,
                codFilial = dadosOnline.codFilial,
                numInvent = dadosOnline.numInvent,
                codProd = dadosOnline.codProd,
                produt = produt
            )
        }

        // Se não encontrou online, tenta cache offline
        val dadosCache = TAB_INVENTICacheRepository.buscarNoCache(codBarras)
        if (dadosCache != null) {
            Log.d("InventarioApi", "✅ Dados encontrados no cache offline para $codBarras")
            val produt = dadosCache.codProd?.let { buscarPRODUTPorCODPROD(it) }
            return DadosTAB_INVENTI(
                qtProd = dadosCache.qtProd,
                codFilial = dadosCache.codFilial,
                numInvent = dadosCache.numInvent,
                codProd = dadosCache.codProd,
                produt = produt
            )
        }

        Log.d("InventarioApi", "CODBARID $codBarras não encontrado (online e cache)")
        return null
    }

    /**
     * Sincroniza todos os dados da TAB_INVENTI para cache offline
     * Baixa todos os registros e salva localmente
     */
    suspend fun sincronizarCacheTAB_INVENTI(): ResultadoSincronizacao {
        return try {
            var totalBaixado = 0
            var offset = 0
            val limit = 1000 // Baixa em lotes de 1000
            
            Log.d("InventarioApi", "🔄 Iniciando sincronização completa (PCPRODUT primeiro, depois TAB_INVENTI e TAB_INVENTIC)...")

            // ===== 1. SINCRONIZA PCPRODUT PRIMEIRO (para ter cache disponível) =====
            val todosProdutosPCPRODUT = mutableListOf<PCPRODUTRecord>()
            offset = 0
            
            Log.d("InventarioApi", "📥 Sincronizando PCPRODUT...")
            while (true) {
                val res = client.get("$BASE_URL_PCPRODUT/records") {
                    parameter("limit", limit)
                    parameter("offset", offset)
                }

                if (res.status != HttpStatusCode.OK) {
                    Log.e("InventarioApi", "Erro ao buscar PCPRODUT: ${res.status}")
                    break
                }

                val payload = res.bodyAsText()
                val produtos = json.decodeFromString<PCPRODUTResponse>(payload).list
                
                if (produtos.isEmpty()) {
                    break
                }

                todosProdutosPCPRODUT.addAll(produtos)
                offset += limit

                Log.d("InventarioApi", "   📊 PCPRODUT: ${todosProdutosPCPRODUT.size} registros baixados...")

                if (produtos.size < limit) {
                    break
                }
            }

            TAB_INVENTICacheRepository.atualizarCachePCPRODUT(todosProdutosPCPRODUT)
            val totalPCPRODUT = todosProdutosPCPRODUT.size
            Log.d("InventarioApi", "✅ PCPRODUT: $totalPCPRODUT registros sincronizados (cache disponível)")

            // ===== 2. SINCRONIZA TAB_INVENTI =====
            val todosProdutosTAB_INVENTI = mutableListOf<ProdutoRecord>()
            offset = 0
            
            Log.d("InventarioApi", "📥 Sincronizando TAB_INVENTI...")
            while (true) {
                val produtos = getProdutos(limit = limit, offset = offset)
                
                if (produtos.isEmpty()) {
                    break
                }

                todosProdutosTAB_INVENTI.addAll(produtos)
                totalBaixado += produtos.size
                offset += limit

                Log.d("InventarioApi", "   📊 TAB_INVENTI: $totalBaixado registros baixados...")

                if (produtos.size < limit) {
                    break
                }
            }

            TAB_INVENTICacheRepository.atualizarCache(todosProdutosTAB_INVENTI)
            val totalTAB_INVENTI = todosProdutosTAB_INVENTI.size
            Log.d("InventarioApi", "✅ TAB_INVENTI: $totalTAB_INVENTI registros sincronizados")

            // ===== 3. SINCRONIZA TAB_INVENTIC =====
            val todosProdutosTAB_INVENTIC = mutableListOf<ProdutoRecord>()
            offset = 0
            
            Log.d("InventarioApi", "📥 Sincronizando TAB_INVENTIC...")
            while (true) {
                val res = client.get("$BASE_URL_INVENTIC/records") {
                    parameter("limit", limit)
                    parameter("offset", offset)
                }

                if (res.status != HttpStatusCode.OK) {
                    Log.e("InventarioApi", "Erro ao buscar TAB_INVENTIC: ${res.status}")
                    break
                }

                val payload = res.bodyAsText()
                val produtos = json.decodeFromString<ProdutosResponse>(payload).list
                
                if (produtos.isEmpty()) {
                    break
                }

                todosProdutosTAB_INVENTIC.addAll(produtos)
                offset += limit

                Log.d("InventarioApi", "   📊 TAB_INVENTIC: ${todosProdutosTAB_INVENTIC.size} registros baixados...")

                if (produtos.size < limit) {
                    break
                }
            }

            // Busca PRODUT para registros da TAB_INVENTIC em lote (após baixar todos)
            Log.d("InventarioApi", "📥 Buscando PRODUTs para TAB_INVENTIC...")
            val codProdsTAB_INVENTIC = todosProdutosTAB_INVENTIC.mapNotNull { it.codProd }
            val produtMapTAB_INVENTIC = buscarPRODUTEmLote(codProdsTAB_INVENTIC)
            
            // Atualiza registros com PRODUT
            val todosProdutosTAB_INVENTICComProdut = todosProdutosTAB_INVENTIC.map { registro ->
                val produt = registro.codProd?.let { produtMapTAB_INVENTIC[it] }
                registro.copy(produt = produt)
            }

            TAB_INVENTICacheRepository.atualizarCacheTAB_INVENTIC(todosProdutosTAB_INVENTICComProdut)
            val totalTAB_INVENTIC = todosProdutosTAB_INVENTICComProdut.size
            Log.d("InventarioApi", "✅ TAB_INVENTIC: $totalTAB_INVENTIC registros sincronizados (com PRODUT)")

            // ===== 4. SINCRONIZA USUARIOS =====
            Log.d("InventarioApi", "📥 Sincronizando USUARIOS...")
            val todosUsuarios = try {
                NocoDBApi.listarUsuarios(limit = 1000)
            } catch (e: Exception) {
                Log.e("InventarioApi", "Erro ao buscar USUARIOS: ${e.message}")
                emptyList()
            }
            
            UsuariosCacheRepository.atualizarCache(todosUsuarios)
            val totalUsuarios = todosUsuarios.size
            Log.d("InventarioApi", "✅ USUARIOS: $totalUsuarios registros sincronizados")

            // ===== RESULTADO FINAL =====
            val totalGeral = totalTAB_INVENTI + totalTAB_INVENTIC + totalPCPRODUT + totalUsuarios
            Log.d("InventarioApi", "✅ Sincronização completa! TAB_INVENTI=$totalTAB_INVENTI, TAB_INVENTIC=$totalTAB_INVENTIC, PCPRODUT=$totalPCPRODUT, USUARIOS=$totalUsuarios (TOTAL=$totalGeral)")
            
            ResultadoSincronizacao(
                sucesso = true,
                totalRegistros = totalGeral,
                mensagem = "$totalGeral registros sincronizados (INVENTI: $totalTAB_INVENTI, INVENTIC: $totalTAB_INVENTIC, PCPRODUT: $totalPCPRODUT, USUARIOS: $totalUsuarios)"
            )
        } catch (e: Exception) {
            Log.e("InventarioApi", "❌ Erro ao sincronizar cache: ${e.message}")
            LogsRepository.erro("API", "Erro na sincronização de cache", "Exceção: ${e.message}\nStack: ${e.stackTraceToString().take(500)}")
            ResultadoSincronizacao(
                sucesso = false,
                totalRegistros = 0,
                mensagem = "Erro ao sincronizar: ${e.message}"
            )
        }
    }

    /**
     * PATCH - Atualiza QTCONT do registro cujo CODBARID = [codBarras]
     * ⚠️ LEGADO: Esta função modifica TAB_INVENTI (tabela de comparação)
     * ⚠️ NÃO DEVE SER USADA - TAB_INVENTI é apenas para leitura/comparação
     * Use enviarScanneadosParaTAB_INVENTIC() para salvar dados
     */
    @Deprecated("TAB_INVENTI é apenas para comparação. Use enviarScanneadosParaTAB_INVENTIC()")
    suspend fun atualizarContagem(codBarras: String): CadastroRegistroResultado {
        return try {
            // Converte CODBARID para número (já que é NUMBER na tabela)
            val codBarIdNumero = codBarras.trim().toLongOrNull()
            if (codBarIdNumero == null) {
                return CadastroRegistroResultado(
                    sucesso = false,
                    mensagem = "CODBARID inválido (não é número): $codBarras"
                )
            }
            
            // 🔍 Buscar produto no banco (CODBARID é NUMBER)
            val searchResponse = client.get("$BASE_URL/records") {
                parameter("where", "(CODBARID,eq,$codBarIdNumero)")
                parameter("limit", 1)
            }

            if (searchResponse.status != HttpStatusCode.OK) {
                return CadastroRegistroResultado(
                    sucesso = false,
                    mensagem = "Erro HTTP ao buscar: ${searchResponse.status}"
                )
            }

            val searchPayload = searchResponse.bodyAsText()
            val data = json.decodeFromString<ProdutosResponse>(searchPayload).list.firstOrNull()

            if (data == null) {
                return CadastroRegistroResultado(
                    sucesso = false,
                    mensagem = "Código $codBarras não encontrado no banco."
                )
            }

            val recordId = data.id ?: return CadastroRegistroResultado(
                sucesso = false,
                mensagem = "Registro sem ID"
            )

            // Validação: se CODBARID existe, então atualiza QTCONT = QTPROD (se QTPROD existir)
            val codBarIdExiste = data.codBarId?.isNotBlank() == true

            if (codBarIdExiste) {
                // Se QTPROD existe, usa QTPROD, senão mantém QTCONT atual ou 0
                val qtContAtualizar = data.qtProd ?: data.qtCont ?: 0

                // 🟢 Atualiza QTCONT - Tenta formato como JsonObject primeiro (igual ao POST)
                val payload = buildJsonObject {
                    put("QTCONT", qtContAtualizar)
                }

                val payloadString = json.encodeToString(payload)
                Log.d("InventarioApi", "PATCH Request - URL: $BASE_URL/records/$recordId")
                Log.d("InventarioApi", "PATCH Request - Record ID: $recordId")
                Log.d("InventarioApi", "PATCH Request - Body (JSON): $payloadString")
                Log.d("InventarioApi", "PATCH Request - QTCONT value: $qtContAtualizar")
                Log.d("InventarioApi", "PATCH Request - Data encontrada: id=${data.id}, CODBARID=${data.codBarId}, QTPROD=${data.qtProd}, QTCONT=${data.qtCont}")

                val updateResponse = try {
                    client.patch("$BASE_URL/records/$recordId") {
                        contentType(ContentType.Application.Json)
                        setBody(payload) // Usa JsonObject diretamente (como no POST)
                    }
                } catch (e: Exception) {
                    Log.e("InventarioApi", "❌ Exceção ao fazer PATCH: ${e.message}", e)
                    return CadastroRegistroResultado(
                        sucesso = false,
                        mensagem = "Exceção ao atualizar: ${e.message}"
                    )
                }

                val responseBody = updateResponse.bodyAsText()
                Log.d("InventarioApi", "PATCH Response - Status: ${updateResponse.status}")
                Log.d("InventarioApi", "PATCH Response - Body: $responseBody")

                if (updateResponse.status == HttpStatusCode.OK || updateResponse.status == HttpStatusCode.Created) {
                    Log.d("InventarioApi", "✅ PATCH bem-sucedido!")
                    CadastroRegistroResultado(sucesso = true)
                } else {
                    // Log detalhado do erro 422
                    if (updateResponse.status == HttpStatusCode.UnprocessableEntity) {
                        Log.e("InventarioApi", "❌ Erro 422 - Unprocessable Entity")
                        Log.e("InventarioApi", "Request URL: $BASE_URL/records/$recordId")
                        Log.e("InventarioApi", "Request Body enviado: $payloadString")
                        Log.e("InventarioApi", "Response Status: ${updateResponse.status}")
                        Log.e("InventarioApi", "Response Body recebido: $responseBody")
                        Log.e("InventarioApi", "Record ID usado: $recordId")
                        Log.e("InventarioApi", "QTCONT valor: $qtContAtualizar")

                        // Tenta extrair mensagem de erro do JSON
                        val errorMessage = try {
                            val errorJson = Json.parseToJsonElement(responseBody).jsonObject
                            errorJson["msg"]?.jsonPrimitive?.content
                                ?: errorJson["message"]?.jsonPrimitive?.content
                                ?: errorJson["error"]?.jsonPrimitive?.content
                                ?: errorJson["errors"]?.toString()
                                ?: responseBody
                        } catch (e: Exception) {
                            Log.e("InventarioApi", "Erro ao parsear resposta de erro: ${e.message}")
                            responseBody
                        }

                        CadastroRegistroResultado(
                            sucesso = false,
                            mensagem = "Erro 422: $errorMessage"
                        )
                    } else {
                        CadastroRegistroResultado(
                            sucesso = false,
                            mensagem = "Falha ao atualizar: ${updateResponse.status}. $responseBody"
                        )
                    }
                }
            } else {
                CadastroRegistroResultado(
                    sucesso = false,
                    mensagem = "CODBARID não encontrado ou vazio no registro."
                )
            }

        } catch (e: Exception) {
            CadastroRegistroResultado(
                sucesso = false,
                mensagem = "Erro: ${e.message}"
            )
        }
    }

    /**
     * ⚠️ LEGADO: Esta função modifica TAB_INVENTI (tabela de comparação)
     * ⚠️ NÃO DEVE SER USADA - TAB_INVENTI é apenas para leitura/comparação
     * Use enviarScanneadosParaTAB_INVENTIC() para salvar dados
     */
    @Deprecated("TAB_INVENTI é apenas para comparação. Use enviarScanneadosParaTAB_INVENTIC()")
    suspend fun enviarScanneados(itens: List<ScanneadoItem>): ResultadoEnvio {
        val erros = mutableListOf<String>()
        var enviados = 0

        val itensValidos = itens.filter { it.codBarId.isNotBlank() }

        for (item in itensValidos) {
            val codigo = item.codBarId.trim()
            if (codigo.isBlank()) {
                erros.add("Código em branco ignorado")
                continue
            }

            try {
                // Converte CODBARID para número (já que é NUMBER na tabela)
                val codBarIdNumero = codigo.trim().toLongOrNull()
                if (codBarIdNumero == null) {
                    erros.add("$codigo (CODBARID inválido - não é número)")
                    continue
                }
                
                val search = client.get("$BASE_URL/records") {
                    parameter("where", "(CODBARID,eq,$codBarIdNumero)")
                    parameter("limit", 1)
                }

                Log.d("enviarScanneados", "${search.status}")
                if (search.status != HttpStatusCode.OK) {
                    erros.add("$codigo (HTTP ${search.status})")
                    continue
                }

                if (search.contentType()?.match(ContentType.Application.Json) != true) {
                    erros.add("$codigo (resposta não JSON)")
                    continue
                }

                val searchPayload = search.bodyAsText()
                val data = json.decodeFromString<ProdutosResponse>(searchPayload).list.firstOrNull()

                if (data == null) {
                    val qtParaContagem = item.qtCont ?: item.qtProd
                    val camposObrigatorios = listOf(
                        item.numInvent,
                        item.codFilial,
                        item.codProd,
                        item.qtProd,
                        qtParaContagem,
                        item.codFunc
                    )

                    if (camposObrigatorios.any { it == null }) {
                        erros.add("$codigo (dados insuficientes para criar novo registro)")
                        continue
                    }

                    val novoRegistro = NovoRegistroInventario(
                        numInvent = item.numInvent,
                        codFilial = item.codFilial,
                        codProd = item.codProd,
                        codBarId = item.codBarId,
                        qtProd = item.qtProd,
                        qtCont = qtParaContagem,
                        codFunc = item.codFunc,
                        status = item.status ?: "Pendente"
                    )

                    val resultadoCadastro = criarRegistro(novoRegistro)
                    if (resultadoCadastro.sucesso) {
                        enviados++
                    } else {
                        val mensagem = resultadoCadastro.mensagem ?: "falha ao cadastrar"
                        erros.add("$codigo ($mensagem)")
                    }
                    continue
                }

                val recordId = data.id
                if (recordId == null) {
                    erros.add("$codigo (registro sem ID)")
                    continue
                }

                val codBarIdExiste = data.codBarId?.isNotBlank() == true

                if (codBarIdExiste) {
                    // Se QTPROD existe, usa QTPROD, senão usa o valor do item ou mantém QTCONT atual
                    val qtContAtualizar = item.qtCont ?: data.qtProd ?: data.qtCont ?: 0

                    val payload = buildJsonObject {
                        put("QTCONT", qtContAtualizar)
                        item.status?.let { put("STATUS", it) }
                        item.numInvent?.let { put("NUMINVENT", it) }
                        item.codFilial?.let { put("CODFILIAL", it) }
                        item.codProd?.let { put("CODPROD", it) }
                        item.codFunc?.let { put("CODFUNC", it) }
                    }

                    val payloadString = json.encodeToString(payload)
                    Log.d("InventarioApi", "enviarScanneados - PATCH para $codigo: $payloadString")

                    val patchResponse = client.patch("$BASE_URL/records/$recordId") {
                        contentType(ContentType.Application.Json)
                        setBody(payload)
                    }

                    if (patchResponse.status.isSuccess()) {
                        enviados++
                        Log.d("InventarioApi", "✅ enviarScanneados - $codigo atualizado com sucesso")
                    } else {
                        val mensagem = patchResponse.bodyAsText()
                        if (patchResponse.status == HttpStatusCode.UnprocessableEntity) {
                            Log.e("InventarioApi", "❌ enviarScanneados - Erro 422 para $codigo: $mensagem")
                        }
                        erros.add("$codigo (falha PATCH: ${patchResponse.status}. $mensagem)")
                    }
                } else {
                    erros.add("$codigo (CODBARID não encontrado ou vazio no registro)")
                }

            } catch (e: Exception) {
                erros.add("$codigo (erro ${e.message})")
            }
        }

        return ResultadoEnvio(
            enviados = enviados,
            erros = erros
        )
    }

    /**
     * GET - Obtém resumo de status do inventário
     * Usa TAB_INVENTI apenas para COMPARAÇÃO (somente leitura)
     */
    suspend fun getResumoStatus(limit: Int = 10_000): StatusResumo {
        val produtos = getProdutos(limit)
        if (produtos.isEmpty()) {
            return StatusResumo(concluidos = 0, pendentes = 0)
        }

        var concluidos = 0
        var pendentes = 0

        produtos.forEach { registro ->
            val qtCont = registro.qtCont ?: 0
            if (qtCont > 0) {
                concluidos++
            } else {
                pendentes++
            }
        }

        return StatusResumo(concluidos = concluidos, pendentes = pendentes)
    }

    /**
     * GET - Busca registros das últimas 24 horas na TAB_INVENTIC
     * Filtra por campo DATA (formato: dd/MM/yyyy HH:mm:ss)
     * Funciona online (busca na API) ou offline (usa dados locais)
     */
    suspend fun getRegistrosUltimas24h(): ResultadoResumo24h {
        val agora = Date()
        val vinteQuatroHorasAtras = Date(agora.time - (24 * 60 * 60 * 1000))
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        
        // Tenta buscar online primeiro
        val registrosOnline = try {
            val todosRegistros = mutableListOf<ProdutoRecord>()
            var offset = 0
            val limit = 1000

            // Busca todos os registros da TAB_INVENTIC
            while (true) {
                val res = client.get("$BASE_URL_INVENTIC/records") {
                    parameter("limit", limit)
                    parameter("offset", offset)
                }

                if (res.status != HttpStatusCode.OK) {
                    Log.d("InventarioApi", "Erro ao buscar registros 24h online: ${res.status}")
                    break
                }

                val payload = res.bodyAsText()
                val response = json.decodeFromString<ProdutosResponse>(payload)
                
                todosRegistros.addAll(response.list)

                if (response.list.size < limit) {
                    break
                }

                offset += limit
            }

            // Filtra registros das últimas 24 horas baseado no campo DATA
            val registrosFiltrados = todosRegistros.filter { registro ->
                val dataRegistro = registro.data
                if (dataRegistro.isNullOrBlank()) {
                    false
                } else {
                    try {
                        val data = sdf.parse(dataRegistro)
                        data != null && (data.after(vinteQuatroHorasAtras) || data.equals(vinteQuatroHorasAtras)) && (data.before(agora) || data.equals(agora))
                    } catch (e: Exception) {
                        Log.d("InventarioApi", "Erro ao parsear data: $dataRegistro - ${e.message}")
                        false
                    }
                }
            }
            
            // Busca PRODUT para cada registro usando CODPROD
            registrosFiltrados.map { registro ->
                val produt = registro.codProd?.let { buscarPRODUTPorCODPROD(it) }
                registro.copy(produt = produt)
            }
        } catch (e: Exception) {
            Log.d("InventarioApi", "Erro ao buscar online, usando dados locais: ${e.message}")
            emptyList()
        }

        // Se encontrou registros online, retorna
        if (registrosOnline.isNotEmpty()) {
            Log.d("InventarioApi", "✅ Encontrados ${registrosOnline.size} registros nas últimas 24h (online)")
            return ResultadoResumo24h(registros = registrosOnline, usandoOffline = false)
        }

        // Se não encontrou online, usa dados locais (offline)
        try {
            val itensLocais = ScannedCodesRepository.getItems()
            
            // Converte ScanneadoItem para ProdutoRecord e filtra por data
            val registrosLocais = itensLocais.mapNotNull { item ->
                val dataRegistro = item.data
                if (dataRegistro.isNullOrBlank()) {
                    null
                } else {
                    try {
                        val data = sdf.parse(dataRegistro)
                        if (data != null && (data.after(vinteQuatroHorasAtras) || data.equals(vinteQuatroHorasAtras)) && (data.before(agora) || data.equals(agora))) {
                            // Converte ScanneadoItem para ProdutoRecord
                            ProdutoRecord(
                                codBarId = item.codBarId,
                                codFunc = item.codFunc,
                                qtProd = item.qtProd,
                                qtCont = item.qtCont,
                                codFilial = item.codFilial,
                                numInvent = item.numInvent,
                                codProd = item.codProd,
                                data = item.data
                            )
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        Log.d("InventarioApi", "Erro ao parsear data local: $dataRegistro - ${e.message}")
                        null
                    }
                }
            }
            
            // Busca PRODUT para cada registro offline usando CODPROD
            val registrosComProdut = registrosLocais.map { registro ->
                val produt = registro.codProd?.let { buscarPRODUTPorCODPROD(it) }
                registro.copy(produt = produt)
            }

            Log.d("InventarioApi", "✅ Encontrados ${registrosComProdut.size} registros nas últimas 24h (offline/local)")
            return ResultadoResumo24h(registros = registrosComProdut, usandoOffline = true)
        } catch (e: Exception) {
            Log.e("InventarioApi", "Erro ao buscar registros locais: ${e.message}")
            return ResultadoResumo24h(
                registros = emptyList(),
                erro = "Erro ao buscar registros: ${e.message}",
                usandoOffline = true
            )
        }
    }

    /**
     * GET - Conta total de registros na TAB_INVENTIC
     * Retorna a quantidade de linhas/registros na tabela
     */
    suspend fun contarRegistrosTAB_INVENTIC(): Int {
        return try {
            var total = 0
            var offset = 0
            val limit = 1000

            while (true) {
                val res = client.get("$BASE_URL_INVENTIC/records") {
                    parameter("limit", limit)
                    parameter("offset", offset)
                }

                if (res.status != HttpStatusCode.OK) {
                    Log.e("InventarioApi", "Erro ao contar registros TAB_INVENTIC: ${res.status}")
                    break
                }

                val payload = res.bodyAsText()
                val response = json.decodeFromString<ProdutosResponse>(payload)
                
                total += response.list.size

                if (response.list.size < limit) {
                    break // Não há mais registros
                }

                offset += limit
            }

            Log.d("InventarioApi", "✅ Total de registros na TAB_INVENTIC: $total")
            total
        } catch (e: Exception) {
            Log.e("InventarioApi", "Erro ao contar registros TAB_INVENTIC: ${e.message}")
            0
        }
    }

    /**
     * GET - Busca NUMINVENT únicos da TAB_INVENTI
     * Retorna lista de NUMINVENT distintos para seleção
     */
    suspend fun getNUMINVENTUnicos(limit: Int = 10_000): List<Int> {
        return try {
            val produtos = getProdutos(limit = limit, offset = 0)
            produtos.mapNotNull { it.numInvent }
                .distinct()
                .sorted()
        } catch (e: Exception) {
            Log.e("InventarioApi", "Erro ao buscar NUMINVENT únicos: ${e.message}")
            emptyList()
        }
    }

    /**
     * GET - Busca todos os registros da TAB_INVENTIC
     * Retorna todos os registros com todos os campos
     * Usado para exibir no painel de status
     * Também busca PRODUT da tabela PCPRODUT para cada registro
     */
    suspend fun getRegistrosTAB_INVENTIC(limit: Int = 1000, offset: Int = 0): List<ProdutoRecord> {
        return try {
            val res = client.get("$BASE_URL_INVENTIC/records") {
                parameter("limit", limit)
                parameter("offset", offset)
            }

            if (res.status != HttpStatusCode.OK) {
                Log.e("InventarioApi", "Erro ao buscar TAB_INVENTIC: ${res.status}")
                emptyList()
            } else if (res.contentType()?.match(ContentType.Application.Json) != true) {
                Log.e("InventarioApi", "Conteúdo não é JSON: ${res.contentType()}")
                emptyList()
            } else {
                val payload = res.bodyAsText()
                val response = json.decodeFromString<ProdutosResponse>(payload)
                
                // Busca PRODUT em lote (otimizado)
                val codProds = response.list.mapNotNull { it.codProd }
                val produtMap = buscarPRODUTEmLote(codProds)
                
                // Marca status baseado em QTCONT e adiciona PRODUT
                response.list.map { registro ->
                    val produt = registro.codProd?.let { produtMap[it] }
                    registro.copy(
                        status = if ((registro.qtCont ?: 0) > 0) "Concluído" else "Pendente",
                        produt = produt
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("InventarioApi", "Erro ao buscar registros TAB_INVENTIC: ${e.message}")
            emptyList()
        }
    }

    /**
     * GET - Busca todos os registros da TAB_INVENTIC com paginação
     * Retorna todos os registros disponíveis
     * Tenta cache persistente primeiro, depois busca online se necessário
     */
    suspend fun getAllRegistrosTAB_INVENTIC(forcarOnline: Boolean = false): List<ProdutoRecord> {
        // Tenta usar cache persistente primeiro (se não forçar online)
        if (!forcarOnline) {
            val cacheCompletos = TAB_INVENTICacheRepository.getAllCachedTAB_INVENTIC()
            if (cacheCompletos.isNotEmpty()) {
                Log.d("InventarioApi", "📦 Usando cache TAB_INVENTIC: ${cacheCompletos.size} registros")
                return cacheCompletos
            }
        }
        
        // Busca online se cache vazio ou forçado
        return try {
            Log.d("InventarioApi", "📡 Buscando TAB_INVENTIC online...")
            val todosRegistros = mutableListOf<ProdutoRecord>()
            var offset = 0
            val limit = 1000

            while (true) {
                val registros = getRegistrosTAB_INVENTIC(limit = limit, offset = offset)
                
                if (registros.isEmpty()) {
                    break
                }

                todosRegistros.addAll(registros)
                offset += limit

                // Se retornou menos que o limit, chegou ao fim
                if (registros.size < limit) {
                    break
                }
            }

            Log.d("InventarioApi", "📊 Total de registros TAB_INVENTIC online: ${todosRegistros.size}")
            
            // Atualiza cache persistente
            if (todosRegistros.isNotEmpty()) {
                TAB_INVENTICacheRepository.atualizarCacheTAB_INVENTIC(todosRegistros)
            }
            
            todosRegistros
        } catch (e: Exception) {
            Log.e("InventarioApi", "Erro ao buscar TAB_INVENTIC online: ${e.message}")
            // Tenta retornar cache em caso de erro
            TAB_INVENTICacheRepository.getAllCachedTAB_INVENTIC()
        }
    }

    /**
     * GET - Retorna resumo de status baseado na TAB_INVENTIC
     * Completos = quantidade de linhas/registros na TAB_INVENTIC
     * Pendentes = quantidade de registros na TAB_INVENTI que não estão na TAB_INVENTIC
     */
    suspend fun getResumoStatusBaseadoTAB_INVENTIC(limit: Int = 10_000): StatusResumo {
        return try {
            // Busca todos os produtos da TAB_INVENTI
            val produtos = getProdutos(limit)
            val totalTAB_INVENTI = produtos.size

            // Conta total de registros na TAB_INVENTIC (quantidade de linhas)
            val concluidos = contarRegistrosTAB_INVENTIC()

            // Pendentes = total na TAB_INVENTI - completos na TAB_INVENTIC
            // Mas como pode haver múltiplos registros na TAB_INVENTIC para o mesmo CODBARID,
            // vamos calcular pendentes como: total TAB_INVENTI - CODBARIDs únicos na TAB_INVENTIC
            val codBarIdsTAB_INVENTIC = mutableSetOf<Long>()
            var offset = 0
            val limitBusca = 1000

            while (true) {
                val res = client.get("$BASE_URL_INVENTIC/records") {
                    parameter("limit", limitBusca)
                    parameter("offset", offset)
                }

                if (res.status != HttpStatusCode.OK) {
                    break
                }

                val payload = res.bodyAsText()
                val response = json.decodeFromString<ProdutosResponse>(payload)
                
                response.list.forEach { registro ->
                    registro.codBarId?.toLongOrNull()?.let { codBarIdLong ->
                        codBarIdsTAB_INVENTIC.add(codBarIdLong)
                    }
                }

                if (response.list.size < limitBusca) {
                    break
                }

                offset += limitBusca
            }

            // Pendentes = produtos na TAB_INVENTI que não estão na TAB_INVENTIC
            var pendentes = 0
            produtos.forEach { produto ->
                val codBarIdLong = produto.codBarId?.toLongOrNull()
                if (codBarIdLong == null || codBarIdLong !in codBarIdsTAB_INVENTIC) {
                    pendentes++
                }
            }

            Log.d("InventarioApi", "📊 Resumo: $concluidos completos (linhas TAB_INVENTIC), $pendentes pendentes")
            StatusResumo(concluidos = concluidos, pendentes = pendentes)
        } catch (e: Exception) {
            Log.e("InventarioApi", "Erro ao buscar resumo baseado na TAB_INVENTIC: ${e.message}")
            StatusResumo(concluidos = 0, pendentes = 0)
        }
    }

    suspend fun criarRegistro(novo: NovoRegistroInventario): CadastroRegistroResultado {
        return runCatching {
            val resposta = client.post("$BASE_URL/records") {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        novo.numInvent?.let { put("NUMINVENT", it) }
                        novo.codFilial?.let { put("CODFILIAL", it) }
                        novo.codProd?.let { put("CODPROD", it) }
                        novo.codBarId?.let { put("CODBARID", it) }
                        novo.qtProd?.let { put("QTPROD", it) }
                        novo.qtCont?.let { put("QTCONT", it) }
                        novo.codFunc?.let { put("CODFUNC", it) }
                        novo.status?.let { put("STATUS", it) }
                    }
                )
            }

            val bodyTexto = runCatching { resposta.bodyAsText() }.getOrNull()

            if (resposta.status.isSuccess()) {
                CadastroRegistroResultado(sucesso = true)
            } else {
                val mensagem = bodyTexto?.let { texto ->
                    runCatching {
                        val jsonBody = Json.parseToJsonElement(texto).jsonObject
                        jsonBody["msg"]?.jsonPrimitive?.content
                            ?: jsonBody["message"]?.jsonPrimitive?.content
                    }.getOrNull() ?: texto
                }
                CadastroRegistroResultado(sucesso = false, mensagem = mensagem)
            }
        }.getOrElse {
            CadastroRegistroResultado(sucesso = false, mensagem = it.message)
        }
    }

    /**
     * POST - Insere registro na tabela TAB_INVENTIC (tabela de dados)
     * Campos: CODBARID (NUMBER), CODFUNC (NUMBER), QTPROD (NUMBER), QTCONT (NUMBER), CODFILIAL (NUMBER), NUMINVENT (NUMBER), CODPROD (NUMBER), DATA (TEXT)
     * Esta é a tabela onde os dados scanneados são salvos
     */
    suspend fun inserirNaTAB_INVENTIC(
        codBarId: String,
        codFunc: Int?,
        qtProd: Int?,
        qtCont: Int?,
        codFilial: Int? = null,
        numInvent: Int? = null,
        codProd: Int? = null,
        data: String? = null
    ): CadastroRegistroResultado {
        return runCatching {
            // Converte CODBARID para número (já que é NUMBER na tabela)
            val codBarIdNumero = codBarId.trim().toLongOrNull()
            if (codBarIdNumero == null) {
                return CadastroRegistroResultado(
                    sucesso = false,
                    mensagem = "CODBARID inválido (não é número): $codBarId"
                )
            }

            val resposta = client.post("$BASE_URL_INVENTIC/records") {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("CODBARID", codBarIdNumero) // Envia como NUMBER
                        codFunc?.let { put("CODFUNC", it) }
                        qtProd?.let { put("QTPROD", it) }
                        qtCont?.let { put("QTCONT", it) }
                        codFilial?.let { put("CODFILIAL", it) }
                        numInvent?.let { put("NUMINVENT", it) }
                        codProd?.let { put("CODPROD", it) } // Inclui CODPROD
                        data?.let { put("DATA", it) } // Envia como TEXT
                    }
                )
            }

            val bodyTexto = runCatching { resposta.bodyAsText() }.getOrNull()

            if (resposta.status.isSuccess()) {
                CadastroRegistroResultado(sucesso = true)
            } else {
                val mensagem = bodyTexto?.let { texto ->
                    runCatching {
                        val jsonBody = Json.parseToJsonElement(texto).jsonObject
                        jsonBody["msg"]?.jsonPrimitive?.content
                            ?: jsonBody["message"]?.jsonPrimitive?.content
                            ?: texto
                    }.getOrNull() ?: bodyTexto
                }
                Log.e("InventarioApi", "Erro ao inserir na TAB_INVENTIC: $mensagem")
                CadastroRegistroResultado(sucesso = false, mensagem = mensagem)
            }
        }.getOrElse {
            Log.e("InventarioApi", "Exceção ao inserir na TAB_INVENTIC: ${it.message}")
            CadastroRegistroResultado(sucesso = false, mensagem = it.message)
        }
    }

    /**
     * Envia lista de itens scanneados para TAB_INVENTIC (tabela de dados)
     * 
     * Fluxo:
     * 1. Para cada item, busca QTPROD na TAB_INVENTI (tabela de comparação) se não estiver no item
     * 2. Insere o registro completo na TAB_INVENTIC (tabela de dados)
     * 
     * TAB_INVENTI: apenas leitura para comparação
     * TAB_INVENTIC: escrita dos dados scanneados
     */
    suspend fun enviarScanneadosParaTAB_INVENTIC(itens: List<ScanneadoItem>): ResultadoEnvio {
        val erros = mutableListOf<String>()
        var enviados = 0

        for (item in itens) {
            if (item.codBarId.isBlank()) {
                erros.add("Código em branco ignorado")
                continue
            }

            try {
                // Prioriza dados do item (já coletados), depois busca na TAB_INVENTI
                val dadosTAB_INVENTI = if (item.qtProd != null && item.codFilial != null && item.numInvent != null) {
                    // Dados já estão no item, usa eles
                    DadosTAB_INVENTI(
                        qtProd = item.qtProd,
                        codFilial = item.codFilial,
                        numInvent = item.numInvent,
                        codProd = item.codProd
                    )
                } else {
                    // Tenta buscar na TAB_INVENTI (pode não encontrar, ok)
                    buscarDadosTAB_INVENTI(item.codBarId)
                }
                
                // Usa dados do item ou da TAB_INVENTI (ou valores do próprio item se não encontrou)
                val qtProdFinal = dadosTAB_INVENTI?.qtProd ?: item.qtProd
                val qtContFinal = dadosTAB_INVENTI?.qtProd ?: item.qtCont ?: item.qtProd
                val codFilialFinal = dadosTAB_INVENTI?.codFilial ?: item.codFilial
                val numInventFinal = dadosTAB_INVENTI?.numInvent ?: item.numInvent
                val codProdFinal = item.codProd ?: dadosTAB_INVENTI?.codProd
                
                // Validação mínima: precisa de pelo menos CODFILIAL e NUMINVENT
                if (codFilialFinal == null || numInventFinal == null) {
                    erros.add("${item.codBarId}: Faltam dados obrigatórios (CODFILIAL ou NUMINVENT)")
                    continue
                }
                
                val resultado = inserirNaTAB_INVENTIC(
                    codBarId = item.codBarId,
                    codFunc = item.codFunc,
                    qtProd = qtProdFinal,
                    qtCont = qtContFinal,
                    codFilial = codFilialFinal,
                    numInvent = numInventFinal,
                    codProd = codProdFinal,
                    data = item.data
                )

                if (resultado.sucesso) {
                    enviados++
                    Log.d("InventarioApi", "✅ Item ${item.codBarId} enviado para TAB_INVENTIC")
                } else {
                    erros.add("${item.codBarId}: ${resultado.mensagem ?: "Erro desconhecido"}")
                    LogsRepository.erro("Envio Item", "Falha ao enviar ${item.codBarId}", resultado.mensagem ?: "Erro desconhecido")
                }
            } catch (e: Exception) {
                erros.add("${item.codBarId}: ${e.message}")
                Log.e("InventarioApi", "Erro ao enviar ${item.codBarId}: ${e.message}")
                LogsRepository.erro("Envio Item", "Exceção ao enviar ${item.codBarId}", e.message ?: "Exceção desconhecida")
            }
        }

        return ResultadoEnvio(enviados = enviados, erros = erros)
    }
}
