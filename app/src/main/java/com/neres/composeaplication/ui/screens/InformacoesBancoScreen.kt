package com.neres.composeaplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.input.pointer.pointerInput
import com.neres.composeaplication.data.InventarioApi
import com.neres.composeaplication.data.ProdutoRecord
import com.neres.composeaplication.data.TAB_INVENTICacheRepository
import kotlinx.coroutines.launch
import android.util.Log
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

private enum class FiltroStatus(val label: String) {
    TODOS("Todos"),
    CONCLUIDOS("Concluídos"),
    PENDENTES("Pendentes")
}

private enum class ColunaTabela(val label: String, val largura: androidx.compose.ui.unit.Dp) {
    PRODUT("PRODUT", 150.dp),
    CODPROD("CODPROD", 70.dp),
    CODFUNC("CODFUNC", 80.dp),
    CODFILIAL("CODFILIAL", 80.dp),
    NUMINVENT("NUMINVENT", 90.dp),
    QTPROD("QTPROD", 80.dp),
    QTCONT("QTCONT", 80.dp),
    STATUS("STATUS", 100.dp)
}

@Composable
private fun StatusFilterChips(
    filtroSelecionado: FiltroStatus,
    onFiltroSelecionado: (FiltroStatus) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FiltroStatus.values().forEach { filtro ->
            FilterChip(
                selected = filtroSelecionado == filtro,
                onClick = { onFiltroSelecionado(filtro) },
                label = { Text(filtro.label) }
            )
        }
    }
}

private fun List<ProdutoRecord>.filterByStatus(filtro: FiltroStatus): List<ProdutoRecord> {
    return when (filtro) {
        FiltroStatus.TODOS -> this
        // Todos os registros já são pendentes (completos foram filtrados na busca)
        // Mas verificamos se há algum com QTCONT > 0 que possa ter sido inserido depois
        FiltroStatus.CONCLUIDOS -> {
            // Se algum registro tem QTCONT > 0, significa que foi completado
            // Mas normalmente não deveria aparecer aqui pois são filtrados na busca
            filter { registro -> estaConcluido(registro.status, registro.qtCont) }
        }
        FiltroStatus.PENDENTES -> {
            // Todos são pendentes por padrão, mas filtra explicitamente
            filter { registro -> !estaConcluido(registro.status, registro.qtCont) }
        }
    }
}

private fun List<ProdutoRecord>.aplicarFiltros(
    filtroPRODUT: String,
    filtroCODFUNC: String,
    filtroCODFILIAL: String,
    filtroNUMINVENT: String
): List<ProdutoRecord> {
    return this.filter { registro ->
        (filtroPRODUT.isEmpty() || registro.produt?.contains(filtroPRODUT, ignoreCase = true) == true) &&
        (filtroCODFUNC.isEmpty() || registro.codFunc?.toString()?.contains(filtroCODFUNC, ignoreCase = true) == true) &&
        (filtroCODFILIAL.isEmpty() || registro.codFilial?.toString()?.contains(filtroCODFILIAL, ignoreCase = true) == true) &&
        (filtroNUMINVENT.isEmpty() || registro.numInvent?.toString()?.contains(filtroNUMINVENT, ignoreCase = true) == true)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InformacoesBancoScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var registros by remember { mutableStateOf<List<ProdutoRecord>>(emptyList()) }
    var carregando by remember { mutableStateOf(true) }
    var mensagemErro by remember { mutableStateOf<String?>(null) }
    var filtroSelecionado by remember { mutableStateOf(FiltroStatus.TODOS) }
    val scope = rememberCoroutineScope()
    val pageSize = 200 // Aumentado para reduzir número de requisições
    var paginaAtual by remember { mutableStateOf(0) }
    var carregandoMais by remember { mutableStateOf(false) }
    var temMais by remember { mutableStateOf(true) }
    var usandoOffline by remember { mutableStateOf(false) }
    
    // Cache de registros completos para evitar recarregar
    var cacheRegistros by remember { mutableStateOf<List<ProdutoRecord>>(emptyList()) }
    var ultimaAtualizacaoCache by remember { mutableStateOf(0L) }
    val tempoCacheValido = 5 * 60 * 1000L // 5 minutos
    
    // Função para verificar conectividade
    fun isWifiConnected(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
    
    // Ordem das colunas (pode ser reordenada)
    var ordemColunas by remember {
        mutableStateOf(
            listOf(
                ColunaTabela.PRODUT,
                ColunaTabela.CODPROD,
                ColunaTabela.CODFUNC,
                ColunaTabela.CODFILIAL,
                ColunaTabela.NUMINVENT,
                ColunaTabela.QTPROD,
                ColunaTabela.QTCONT,
                ColunaTabela.STATUS
            )
        )
    }
    
    // Filtros com debounce
    var filtroPRODUT by remember { mutableStateOf("") }
    var filtroCODFUNC by remember { mutableStateOf("") }
    var filtroCODFILIAL by remember { mutableStateOf("") }
    var filtroNUMINVENT by remember { mutableStateOf("") }
    var mostrarFiltros by remember { mutableStateOf(false) }
    
    // Job para debounce dos filtros
    var filtroDebounceJob by remember { mutableStateOf<Job?>(null) }

    suspend fun carregarPagina(reset: Boolean = false, usarCache: Boolean = true) {
        if (carregandoMais && !reset) return
        
        val agora = System.currentTimeMillis()
        val isOnline = isWifiConnected()
        
        // Verifica se pode usar cache temporário (sessão)
        val cacheTemporarioValido = usarCache && 
                                   cacheRegistros.isNotEmpty() && 
                                   (agora - ultimaAtualizacaoCache) < tempoCacheValido
        
        if (cacheTemporarioValido && reset) {
            // Usa cache temporário se disponível e válido
            registros = cacheRegistros
            carregando = false
            mensagemErro = null
            temMais = cacheRegistros.size >= pageSize
            Log.d("InformacoesBancoScreen", "✅ Usando cache temporário (${cacheRegistros.size} registros)")
            return
        }
        
        carregandoMais = true

        if (reset) {
            paginaAtual = 0
            registros = emptyList()
            temMais = true
            mensagemErro = null
        }

        try {
            if (isOnline) {
                // ONLINE: Busca do servidor
                Log.d("InformacoesBancoScreen", "📡 Buscando dados online...")
                
                // Busca completos da TAB_INVENTIC (TODOS são completos)
                val completosOnline = InventarioApi.getAllRegistrosTAB_INVENTIC()
                    .map { it.copy(status = "Concluído") }
                
                // Busca pendentes da TAB_INVENTI
                val completosIds = completosOnline.mapNotNull { it.codBarId?.toLongOrNull() }.toSet()
                val offset = paginaAtual * pageSize
                val todosTAB_INVENTI = InventarioApi.getProdutos(limit = pageSize, offset = offset)
                
                val pendentesOnline = todosTAB_INVENTI.filter { produto ->
                    val codBarIdLong = produto.codBarId?.toLongOrNull()
                    codBarIdLong == null || codBarIdLong !in completosIds
                }.map { it.copy(status = "Pendente", qtCont = 0) }
                
                // Combina: completos (da TAB_INVENTIC) + pendentes (da TAB_INVENTI)
                val todosRegistros = completosOnline + pendentesOnline
                
                if (todosRegistros.isEmpty()) {
                    if (paginaAtual == 0) {
                        mensagemErro = "Nenhum registro encontrado ou erro ao consultar o banco."
                    }
                    temMais = false
                } else {
                    // Implementa paginação
                    val offsetPaginacao = paginaAtual * pageSize
                    val novaPagina = todosRegistros.drop(offsetPaginacao).take(pageSize)
                    
                    if (novaPagina.isNotEmpty()) {
                        paginaAtual++
                        registros = if (reset) novaPagina else registros + novaPagina
                        temMais = offsetPaginacao + novaPagina.size < todosRegistros.size
                        usandoOffline = false
                        
                        // Atualiza cache temporário
                        if (reset) {
                            cacheRegistros = novaPagina
                            ultimaAtualizacaoCache = agora
                        }
                        
                        Log.d("InformacoesBancoScreen", "✅ Carregados ${novaPagina.size} registros online (Completos: ${completosOnline.size}, Pendentes: ${pendentesOnline.size})")
                    } else {
                        temMais = false
                    }
                }
            } else {
                // OFFLINE: Usa cache persistente
                Log.d("InformacoesBancoScreen", "📦 Modo offline - usando cache persistente...")
                
                // Busca do cache persistente TAB_INVENTIC (completos - tem CODFUNC e QTCONT)
                val cacheTAB_INVENTIC = TAB_INVENTICacheRepository.getAllCachedTAB_INVENTIC()
                
                // Busca do cache persistente TAB_INVENTI (pendentes - pode não ter CODFUNC/QTCONT)
                val cacheTAB_INVENTI = TAB_INVENTICacheRepository.getAllCachedProducts()
                
                // Cria mapa de CODBARIDs da TAB_INVENTIC (TODOS são completos)
                val completosPorCodBarId = cacheTAB_INVENTIC
                    .associateBy { it.codBarId }
                
                // Separa completos e pendentes corretamente
                val completos = completosPorCodBarId.values
                    .map { it.copy(status = "Concluído") }
                    .toList()
                
                // Pendentes: registros da TAB_INVENTI que NÃO estão completos na TAB_INVENTIC
                val pendentes = cacheTAB_INVENTI.filter { produto ->
                    produto.codBarId !in completosPorCodBarId.keys
                }.map { it.copy(status = "Pendente", qtCont = 0) }
                
                // Combina: completos primeiro (da TAB_INVENTIC com CODFUNC/QTCONT), depois pendentes
                val todosRegistros = completos + pendentes
                
                // Log para debug
                val completosComCODFUNC = completos.count { it.codFunc != null }
                val completosComQTCONT = completos.count { it.qtCont != null && it.qtCont > 0 }
                Log.d("InformacoesBancoScreen", "📊 Completos: ${completos.size} (com CODFUNC: $completosComCODFUNC, com QTCONT: $completosComQTCONT)")
                Log.d("InformacoesBancoScreen", "📊 Pendentes: ${pendentes.size}")
                
                if (todosRegistros.isEmpty()) {
                    mensagemErro = "📦 Modo offline: Nenhum dado em cache. Sincronize quando estiver online."
                    temMais = false
                } else {
                    // Implementa paginação no cache offline
                    val offset = paginaAtual * pageSize
                    val paginaCache = todosRegistros.drop(offset).take(pageSize)
                    
                    if (paginaCache.isEmpty() && paginaAtual == 0) {
                        mensagemErro = "Nenhum registro encontrado no cache offline."
                        temMais = false
                    } else if (paginaCache.isNotEmpty()) {
                        paginaAtual++
                        registros = if (reset) paginaCache else registros + paginaCache
                        temMais = offset + paginaCache.size < todosRegistros.size
                        usandoOffline = true
                        
                        if (reset) {
                            cacheRegistros = paginaCache
                            ultimaAtualizacaoCache = agora
                        }
                        
                        Log.d("InformacoesBancoScreen", "✅ Carregados ${paginaCache.size} registros do cache offline (total em cache: ${todosRegistros.size})")
                    } else {
                        temMais = false
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("InformacoesBancoScreen", "Erro ao carregar página: ${e.message}")
            mensagemErro = "Erro ao consultar dados: ${e.message}"
            temMais = false
        }

        carregando = false
        carregandoMais = false
    }

    // Carrega dados iniciais
    LaunchedEffect(Unit) {
        carregando = true
        carregarPagina(reset = true, usarCache = true)
    }
    
    // Debounce nos filtros - recarrega apenas quando o usuário parar de digitar
    LaunchedEffect(filtroPRODUT, filtroCODFUNC, filtroCODFILIAL, filtroNUMINVENT) {
        filtroDebounceJob?.cancel()
        filtroDebounceJob = scope.launch {
            delay(500) // Aguarda 500ms após última digitação
            // Não recarrega do servidor, apenas filtra localmente
            // Os filtros são aplicados localmente na lista já carregada
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Informações do Banco de Dados")
                        if (usandoOffline) {
                            Text(
                                text = "📦 Modo Offline",
                                fontSize = 11.sp,
                                color = Color(0xFFDC2626),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Voltar"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            when {
                carregando -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (!isWifiConnected()) "Carregando do cache offline..." else "Carregando informações...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (!isWifiConnected()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "📦 Sem conexão Wi-Fi",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                mensagemErro != null -> {
                    Text(
                        text = mensagemErro ?: "",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                else -> {
                    // Indicador de modo offline
                    if (usandoOffline) {
                        val lastSyncTAB_INVENTI = TAB_INVENTICacheRepository.getLastSyncTimestamp()
                        val lastSyncTAB_INVENTIC = TAB_INVENTICacheRepository.getTAB_INVENTICLastSyncTimestamp()
                        val lastSyncPCPRODUT = TAB_INVENTICacheRepository.getPCPRODUTLastSyncTimestamp()
                        val ultimaSync = maxOf(lastSyncTAB_INVENTI, lastSyncTAB_INVENTIC, lastSyncPCPRODUT)
                        
                        val lastSyncText = if (ultimaSync > 0) {
                            val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                            sdf.format(java.util.Date(ultimaSync))
                        } else {
                            "Nunca"
                        }
                        
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("📦", fontSize = 16.sp)
                                Column {
                                    Text(
                                        text = "Modo Offline",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = "Exibindo dados do cache local",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = "Última sincronização: $lastSyncText",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    
                    StatusFilterChips(
                        filtroSelecionado = filtroSelecionado,
                        onFiltroSelecionado = { filtroSelecionado = it }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Botão para mostrar/ocultar filtros
                    OutlinedButton(
                        onClick = { mostrarFiltros = !mostrarFiltros },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (mostrarFiltros) "Ocultar Filtros" else "Mostrar Filtros")
                    }
                    
                    // Painel de filtros
                    if (mostrarFiltros) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = androidx.compose.material3.CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "Filtros",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                OutlinedTextField(
                                    value = filtroPRODUT,
                                    onValueChange = { filtroPRODUT = it },
                                    label = { Text("PRODUT") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = filtroCODFUNC,
                                    onValueChange = { filtroCODFUNC = it },
                                    label = { Text("CODFUNC") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = filtroCODFILIAL,
                                    onValueChange = { filtroCODFILIAL = it },
                                    label = { Text("CODFILIAL") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = filtroNUMINVENT,
                                    onValueChange = { filtroNUMINVENT = it },
                                    label = { Text("NUMINVENT") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    singleLine = true
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Dica para o usuário
                    Text(
                        text = "💡 Dica: Arraste as colunas do cabeçalho para reordená-las",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Tabela estilo banco de dados
                    val scrollStateHorizontal = rememberScrollState()
                    val scrollStateHorizontalCorpo = rememberScrollState()
                    
                    // Sincroniza scroll horizontal
                    LaunchedEffect(scrollStateHorizontal.value) {
                        scrollStateHorizontalCorpo.scrollTo(scrollStateHorizontal.value)
                    }
                    LaunchedEffect(scrollStateHorizontalCorpo.value) {
                        if (scrollStateHorizontal.value != scrollStateHorizontalCorpo.value) {
                            scrollStateHorizontal.scrollTo(scrollStateHorizontalCorpo.value)
                        }
                    }
                    
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Cabeçalho da tabela (fixo com scroll horizontal)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(scrollStateHorizontal)
                        ) {
                            TabelaCabecalho(
                                ordemColunas = ordemColunas,
                                onOrdemChanged = { novaOrdem -> ordemColunas = novaOrdem }
                            )
                        }
                        
                        // Corpo da tabela (scrollável vertical e horizontal)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .horizontalScroll(scrollStateHorizontalCorpo)
                        ) {
                            LazyColumn(
                                state = rememberLazyListState(),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                val registrosFiltrados = registros
                                    .filterByStatus(filtroSelecionado)
                                    .aplicarFiltros(filtroPRODUT, filtroCODFUNC, filtroCODFILIAL, filtroNUMINVENT)
                                
                                items(registrosFiltrados) { registro ->
                                    LinhaTabela(registro, ordemColunas)
                                    // Debug: Verifica se completos tem CODFUNC e QTCONT
                                    if (registro.status == "Concluído") {
                                        LaunchedEffect(registro.codBarId) {
                                            if (registro.codFunc == null || registro.qtCont == null) {
                                                Log.w("InformacoesBancoScreen", "⚠️ Registro completo sem CODFUNC/QTCONT: ${registro.codBarId}")
                                            }
                                        }
                                    }
                                }
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (temMais) {
                                            OutlinedButton(
                                                onClick = {
                                                    scope.launch {
                                                        carregarPagina(reset = false, usarCache = false)
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                                enabled = !carregandoMais
                                            ) {
                                                Text(
                                                    text = if (carregandoMais) "Carregando..." else "Carregar mais"
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = "Todos os registros foram carregados.",
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(vertical = 8.dp),
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                fontSize = 12.sp
                                            )
                                        }
                                        
                                        // Botão para recarregar (do servidor se online, do cache se offline)
                                        OutlinedButton(
                                            onClick = {
                                                scope.launch {
                                                    cacheRegistros = emptyList()
                                                    ultimaAtualizacaoCache = 0L
                                                    carregarPagina(reset = true, usarCache = false)
                                                }
                                            },
                                            enabled = !carregandoMais && !carregando,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = if (isWifiConnected()) "🔄 Atualizar" else "🔄 Recarregar Cache"
                                            )
                                        }
                                    }
                                }
                                
                                // Informação sobre registros em cache (modo offline)
                                if (usandoOffline) {
                                    item {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Text(
                                                    text = "ℹ️ Dados do cache sincronizado",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabelaCabecalho(
    ordemColunas: List<ColunaTabela>,
    onOrdemChanged: (List<ColunaTabela>) -> Unit
) {
    var colunaSendoArrastada by remember { mutableStateOf<Int?>(null) }
    var posicaoDestino by remember { mutableStateOf<Int?>(null) }
    var offsetAcumulado by remember { mutableStateOf(0f) }
    
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primary)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        ordemColunas.forEachIndexed { index, coluna ->
            val estaSendoArrastada = colunaSendoArrastada == index
            val corFundo = if (estaSendoArrastada) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                Color.Transparent
            }
            
            Box(
                modifier = Modifier
                    .width(coluna.largura)
                    .background(corFundo)
                    .pointerInput(coluna, index) {
                        detectDragGestures(
                            onDragStart = { 
                                colunaSendoArrastada = index
                                posicaoDestino = index
                                offsetAcumulado = 0f
                            },
                            onDragEnd = {
                                colunaSendoArrastada?.let { origem ->
                                    posicaoDestino?.let { destino ->
                                        if (origem != destino && destino >= 0 && destino < ordemColunas.size) {
                                            val novaOrdem = ordemColunas.toMutableList()
                                            val item = novaOrdem.removeAt(origem)
                                            val posicaoFinal = if (destino > origem) destino - 1 else destino
                                            novaOrdem.add(posicaoFinal.coerceIn(0, novaOrdem.size), item)
                                            onOrdemChanged(novaOrdem)
                                        }
                                    }
                                }
                                colunaSendoArrastada = null
                                posicaoDestino = null
                                offsetAcumulado = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                offsetAcumulado += dragAmount.x
                                
                                // Calcula nova posição baseada no movimento acumulado
                                val larguraMedia = ordemColunas.map { it.largura.value }.average()
                                val deslocamento = offsetAcumulado / larguraMedia
                                val posicaoInicial = colunaSendoArrastada ?: index
                                val novaPosicao = (posicaoInicial + deslocamento).toInt().coerceIn(0, ordemColunas.size - 1)
                                
                                if (novaPosicao != posicaoDestino) {
                                    posicaoDestino = novaPosicao
                                }
                            }
                        )
                    }
            ) {
                CelulaTabela(
                    coluna.label,
                    coluna.largura,
                    MaterialTheme.colorScheme.onPrimary,
                    FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun LinhaTabela(registro: ProdutoRecord, ordemColunas: List<ColunaTabela>) {
    val estaConcluido = estaConcluido(registro.status, registro.qtCont)
    val corFundo = if (estaConcluido) {
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
    } else {
        MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
    }
    
    Column {
        Row(
            modifier = Modifier
                .background(corFundo)
                .padding(vertical = 8.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            ordemColunas.forEach { coluna ->
                when (coluna) {
                    ColunaTabela.PRODUT -> CelulaTabela(
                        registro.produt ?: "-",
                        coluna.largura,
                        MaterialTheme.colorScheme.onSurface,
                        FontWeight.Normal
                    )
                    ColunaTabela.CODPROD -> CelulaTabela(
                        registro.codProd?.toString() ?: "-",
                        coluna.largura,
                        MaterialTheme.colorScheme.onSurface,
                        FontWeight.Normal
                    )
                    ColunaTabela.CODFUNC -> CelulaTabela(
                        registro.codFunc?.toString() ?: "-",
                        coluna.largura,
                        MaterialTheme.colorScheme.onSurface,
                        FontWeight.Normal
                    )
                    ColunaTabela.CODFILIAL -> CelulaTabela(
                        registro.codFilial?.toString() ?: "-",
                        coluna.largura,
                        MaterialTheme.colorScheme.onSurface,
                        FontWeight.Normal
                    )
                    ColunaTabela.NUMINVENT -> CelulaTabela(
                        registro.numInvent?.toString() ?: "-",
                        coluna.largura,
                        MaterialTheme.colorScheme.onSurface,
                        FontWeight.Normal
                    )
                    ColunaTabela.QTPROD -> CelulaTabela(
                        registro.qtProd?.toString() ?: "-",
                        coluna.largura,
                        MaterialTheme.colorScheme.onSurface,
                        FontWeight.Normal
                    )
                    ColunaTabela.QTCONT -> CelulaTabela(
                        registro.qtCont?.toString() ?: "-",
                        coluna.largura,
                        MaterialTheme.colorScheme.onSurface,
                        FontWeight.Normal
                    )
                    ColunaTabela.STATUS -> CelulaTabela(
                        if (estaConcluido) "Concluído" else "Pendente",
                        coluna.largura,
                        if (estaConcluido) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                        FontWeight.SemiBold
                    )
                }
            }
        }
        
        // Linha separadora
        Spacer(
            modifier = Modifier
                .height(1.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
        )
    }
}

@Composable
private fun CelulaTabela(
    texto: String,
    largura: androidx.compose.ui.unit.Dp,
    corTexto: Color,
    pesoFonte: FontWeight
) {
    Box(
        modifier = Modifier
            .width(largura)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = texto,
            fontSize = 12.sp,
            color = corTexto,
            fontWeight = pesoFonte,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}


private fun estaConcluido(status: String?, qtCont: Int?): Boolean {
    // Verifica se está completo baseado em QTCONT > 0
    // (lógica da TAB_INVENTIC)
    return (qtCont ?: 0) > 0 ||
            status.equals("concluido", ignoreCase = true) ||
            status.equals("concluídos", ignoreCase = true) ||
            status.equals("concluído", ignoreCase = true) ||
            status.equals("concluidos", ignoreCase = true)
}

