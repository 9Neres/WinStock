package com.neres.composeaplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neres.composeaplication.data.LogItem
import com.neres.composeaplication.data.LogsRepository
import com.neres.composeaplication.data.TipoLog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(onBack: () -> Unit) {
    val logs by LogsRepository.logsFlow.collectAsState()
    var filtroSelecionado by remember { mutableStateOf<TipoLog?>(null) }
    var mostrarDialogoLimpar by remember { mutableStateOf(false) }
    
    val logsFiltrados = if (filtroSelecionado != null) {
        logs.filter { it.tipo == filtroSelecionado }
    } else {
        logs
    }
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Logs do Sistema") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Voltar"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { mostrarDialogoLimpar = true },
                        enabled = logs.isNotEmpty()
                    ) {
                        Text("🗑️", fontSize = 20.sp)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(Color(0xFFF9FAFB))
                .padding(16.dp)
        ) {
            // Filtros por tipo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filtroSelecionado == null,
                    onClick = { filtroSelecionado = null },
                    label = { Text("Todos (${logs.size})", fontSize = 11.sp) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = filtroSelecionado == TipoLog.ERRO,
                    onClick = { filtroSelecionado = TipoLog.ERRO },
                    label = { 
                        Text(
                            "Erros (${logs.count { it.tipo == TipoLog.ERRO }})", 
                            fontSize = 11.sp
                        ) 
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFDC2626).copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = filtroSelecionado == TipoLog.AVISO,
                    onClick = { filtroSelecionado = TipoLog.AVISO },
                    label = { 
                        Text(
                            "Avisos (${logs.count { it.tipo == TipoLog.AVISO }})", 
                            fontSize = 11.sp
                        ) 
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFF59E0B).copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Lista de logs
            if (logsFiltrados.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "📋",
                            fontSize = 48.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (filtroSelecionado != null) 
                                "Nenhum log deste tipo" 
                            else 
                                "Nenhum log registrado",
                            fontSize = 14.sp,
                            color = Color(0xFF9CA3AF)
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(logsFiltrados) { log ->
                        LogCard(log)
                    }
                }
            }
        }
        
        // Dialog de confirmação para limpar logs
        if (mostrarDialogoLimpar) {
            AlertDialog(
                onDismissRequest = { mostrarDialogoLimpar = false },
                title = { Text("Limpar Logs") },
                text = { Text("Deseja limpar todos os ${logs.size} logs registrados? Esta ação não pode ser desfeita.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            LogsRepository.clearLogs()
                            mostrarDialogoLimpar = false
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color(0xFFDC2626)
                        )
                    ) {
                        Text("Limpar", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { mostrarDialogoLimpar = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}

@Composable
private fun LogCard(log: LogItem) {
    val (corFundo, corBorda, corTexto, icone) = when (log.tipo) {
        TipoLog.ERRO -> Quadrupla(
            Color(0xFFFEE2E2),
            Color(0xFFDC2626),
            Color(0xFF991B1B),
            "❌"
        )
        TipoLog.AVISO -> Quadrupla(
            Color(0xFFFEF3C7),
            Color(0xFFF59E0B),
            Color(0xFF92400E),
            "⚠️"
        )
        TipoLog.INFO -> Quadrupla(
            Color(0xFFE0E7FF),
            Color(0xFF6366F1),
            Color(0xFF3730A3),
            "ℹ️"
        )
        TipoLog.SUCESSO -> Quadrupla(
            Color(0xFFE7F8ED),
            Color(0xFF16A34A),
            Color(0xFF15803D),
            "✅"
        )
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = corFundo),
        border = androidx.compose.foundation.BorderStroke(1.dp, corBorda.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: ícone, categoria e timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(icone, fontSize = 16.sp)
                    Text(
                        text = log.categoria,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = corTexto
                    )
                }
                Text(
                    text = LogsRepository.formatTimestamp(log.timestamp),
                    fontSize = 10.sp,
                    color = Color(0xFF6B7280),
                    fontFamily = FontFamily.Monospace
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Mensagem
            Text(
                text = log.mensagem,
                fontSize = 13.sp,
                color = Color(0xFF111827),
                lineHeight = 18.sp
            )
            
            // Detalhes (se houver)
            log.detalhes?.let { detalhes ->
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = Color(0xFF000000).copy(alpha = 0.05f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = detalhes,
                        fontSize = 11.sp,
                        color = Color(0xFF6B7280),
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(8.dp),
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

// Helper para retornar 4 valores
private data class Quadrupla<A, B, C, D>(
    val primeiro: A,
    val segundo: B,
    val terceiro: C,
    val quarto: D
)

