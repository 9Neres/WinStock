package com.neres.composeaplication.ui.screens

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.neres.composeaplication.data.InventarioApi
import com.neres.composeaplication.data.ScannedCodesRepository
import com.neres.composeaplication.data.NovoRegistroInventario
import com.neres.composeaplication.data.ScanneadoItem
import com.neres.composeaplication.data.StatusResumo
import com.neres.composeaplication.data.UltimoUsuarioLogado
import com.neres.composeaplication.scanner.ScannerController
import kotlinx.coroutines.launch

@Suppress("UnusedParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColetaInventarioScreen(navController: NavHostController, forceReload: Boolean = false) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var carregando by remember { mutableStateOf(false) }
    var mensagem by remember { mutableStateOf<String?>(null) }
    var termoBusca by remember { mutableStateOf("") }
    var codigoCapturado by remember { mutableStateOf("") }
    var ultimoCodigoRegistrado by remember { mutableStateOf<String?>(null) }
    var inicioCapturaMillis by remember { mutableStateOf<Long?>(null) }
    var resumoStatus by remember { mutableStateOf<StatusResumo?>(null) }
    var mostrarDialogoInfo by remember { mutableStateOf(false) }
    var mostrarDialogoEnvio by remember { mutableStateOf(false) }
    var mostrarFormularioInventario by remember { mutableStateOf(false) }
    var cadastroNumInvent by remember { mutableStateOf("") }
    var cadastroCodFilial by remember { mutableStateOf("") }
    var cadastroCodProd by remember { mutableStateOf("") }
    var cadastroCodBarId by remember { mutableStateOf("") }
    var cadastroQtProd by remember { mutableStateOf("") }
    var cadastroQtCont by remember { mutableStateOf("") }
    var cadastroCodFunc by remember { mutableStateOf("") }
    var cadastroStatus by remember { mutableStateOf("Pendente") }
    var cadastroMensagem by remember { mutableStateOf<String?>(null) }
    val statusOptions = listOf("Pendente", "Concluído")
    var statusDropdownAberto by remember { mutableStateOf(false) }
    val scanneados by ScannedCodesRepository.itemsFlow.collectAsState()
    val ultimoCodigo by ScannerController.ultimoCodigo.collectAsState()
    val resultadosBusca = remember(termoBusca, scanneados) {
        if (termoBusca.isBlank()) emptyList()
        else scanneados.filter { it.codBarId.contains(termoBusca, ignoreCase = true) }
    }

    LaunchedEffect(ultimoCodigo) {
        ultimoCodigo?.let { novoCodigo ->
            ultimoCodigoRegistrado = null
            inicioCapturaMillis = SystemClock.elapsedRealtime()
            codigoCapturado = novoCodigo
        }
    }

    LaunchedEffect(codigoCapturado) {
        if (codigoCapturado.isBlank()) {
            inicioCapturaMillis = null
            return@LaunchedEffect
        }

        val agora = SystemClock.elapsedRealtime()
        if (inicioCapturaMillis == null) {
            inicioCapturaMillis = agora
        }

        val decorrido = agora - (inicioCapturaMillis ?: agora)
        val codigo = codigoCapturado.trim()

        if (codigo.length >= 7 && decorrido <= 200L && codigo != ultimoCodigoRegistrado) {
            ScannedCodesRepository.addCode(codigo)
            ultimoCodigoRegistrado = codigo
            ScannerController.clearUltimoCodigo()
            codigoCapturado = ""
            inicioCapturaMillis = null
        }
    }

    LaunchedEffect(Unit) {
        if (isWifiConnected(context)) {
            resumoStatus = runCatching { InventarioApi.getResumoStatus() }
                .getOrElse { resumoStatus }
        } else {
            resumoStatus = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .background(Color(0xFFF9FAFB))
            .padding(bottom = 16.dp)
    ) {

        // 🔵 Cabeçalho
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2C6BFF))
                .padding(24.dp)
        ) {
            Text(
                "Coleta de Inventário",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Seja Bem-Vindo",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            OutlinedTextField(
                value = termoBusca,
                onValueChange = { termoBusca = it },
                placeholder = { Text("Buscar scanneado...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )

            if (termoBusca.isNotBlank()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    color = Color.White,
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                            .heightIn(max = 180.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (resultadosBusca.isEmpty()) {
                            Text(
                                text = "Nota não encontrada nos scanneados.",
                                color = Color(0xFFE11D48),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text(
                                text = "Notas encontradas:",
                                color = Color(0xFF111827),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            resultadosBusca.forEach { item ->
                                Text(
                                    text = item.codBarId,
                                    color = Color(0xFF16A34A),
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = codigoCapturado,
                    onValueChange = { codigoCapturado = it },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    placeholder = { Text("Scan ou digite") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        val codigo = codigoCapturado.trim()
                        if (codigo.isNotEmpty()) {
                            ScannedCodesRepository.addCode(codigo)
                            codigoCapturado = ""
                            ultimoCodigoRegistrado = null
                            inicioCapturaMillis = null
                            ScannerController.clearUltimoCodigo()
                        }
                    },
                    enabled = codigoCapturado.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Registrar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            OutlinedButton(
                onClick = {
                    if (!isWifiConnected(context)) {
                        mensagem = "alert_wifi_info"
                        return@OutlinedButton
                    }
                    mensagem = null
                    mostrarDialogoInfo = true
                },
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFBFD6FF)),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 12.dp)
            ) {
                Text("Informações do Banco de Dados", color = Color(0xFF111827), fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cadastrar Produto",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827)
                    )
                    TextButton(onClick = { mostrarFormularioInventario = !mostrarFormularioInventario }) {
                        Text(
                            text = if (mostrarFormularioInventario) "Ocultar" else "Adicionar",
                            color = Color(0xFF2563EB),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (mostrarFormularioInventario) {
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = cadastroNumInvent,
                        onValueChange = { cadastroNumInvent = it },
                        label = { Text("NUMINVENT") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = cadastroCodFilial,
                        onValueChange = { cadastroCodFilial = it },
                        label = { Text("CODFILIAL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = cadastroCodProd,
                        onValueChange = { cadastroCodProd = it },
                        label = { Text("CODPROD") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = cadastroCodBarId,
                        onValueChange = { cadastroCodBarId = it },
                        label = { Text("CODBARID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = cadastroQtProd,
                            onValueChange = { cadastroQtProd = it },
                            label = { Text("QTPROD") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = cadastroQtCont,
                            onValueChange = { cadastroQtCont = it },
                            label = { Text("QTCONT") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = cadastroCodFunc,
                        onValueChange = { cadastroCodFunc = it },
                        label = { Text("CODFUNC") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    ExposedDropdownMenuBox(
                        expanded = statusDropdownAberto,
                        onExpandedChange = { statusDropdownAberto = !statusDropdownAberto },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = cadastroStatus,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("STATUS") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusDropdownAberto) },
                            modifier = Modifier.menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = statusDropdownAberto,
                            onDismissRequest = { statusDropdownAberto = false }
                        ) {
                            statusOptions.forEach { status ->
                                DropdownMenuItem(
                                    text = { Text(status) },
                                    onClick = {
                                        cadastroStatus = status
                                        statusDropdownAberto = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            if (cadastroCodBarId.isBlank()) {
                                cadastroMensagem = "⚠ Informe o CODBARID para registrar localmente."
                                return@OutlinedButton
                            }

                            val novoItem = ScanneadoItem(
                                numInvent = cadastroNumInvent.toIntOrNull(),
                                status = cadastroStatus,
                                codFilial = cadastroCodFilial.toIntOrNull(),
                                codProd = cadastroCodProd.toIntOrNull(),
                                codBarId = cadastroCodBarId,
                                qtProd = cadastroQtProd.toIntOrNull(),
                                qtCont = cadastroQtCont.toIntOrNull(),
                                codFunc = cadastroCodFunc.toIntOrNull()
                            )

                            ScannedCodesRepository.addItem(novoItem)
                            cadastroMensagem = "✅ Registro adicionado à lista de scanneados."
                            cadastroNumInvent = ""
                            cadastroCodFilial = ""
                            cadastroCodProd = ""
                            cadastroCodBarId = ""
                            cadastroQtProd = ""
                            cadastroQtCont = ""
                            cadastroCodFunc = ""
                            cadastroStatus = "Pendente"
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF2563EB)
                        )
                    ) {
                        Text("Registrar localmente")
                    }

                    cadastroMensagem?.let { msg ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = msg,
                            color = if (msg.startsWith("✅")) Color(0xFF16A34A) else Color(0xFFE11D48),
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                if (!isWifiConnected(context)) {
                                    cadastroMensagem = "⚠ Conecte-se ao Wi-Fi para cadastrar."
                                    return@launch
                                }

                                val payload = NovoRegistroInventario(
                                    numInvent = cadastroNumInvent.toIntOrNull(),
                                    codFilial = cadastroCodFilial.toIntOrNull(),
                                    codProd = cadastroCodProd.toIntOrNull(),
                                    codBarId = cadastroCodBarId.ifBlank { null },
                                    qtProd = cadastroQtProd.toIntOrNull(),
                                    qtCont = cadastroQtCont.toIntOrNull(),
                                    codFunc = cadastroCodFunc.toIntOrNull(),
                                    status = cadastroStatus
                                )

                                val resultadoCadastro = InventarioApi.criarRegistro(payload)
                                if (resultadoCadastro.sucesso) {
                                    cadastroMensagem = "✅ Registro cadastrado com sucesso!"
                                    cadastroNumInvent = ""
                                    cadastroCodFilial = ""
                                    cadastroCodProd = ""
                                    cadastroCodBarId = ""
                                    cadastroQtProd = ""
                                    cadastroQtCont = ""
                                    resumoStatus = runCatching { InventarioApi.getResumoStatus() }
                                        .getOrElse { resumoStatus }
                                } else {
                                    val mensagemErro = resultadoCadastro.mensagem
                                        ?: "Erro ao cadastrar. Verifique campos obrigatórios."
                                    cadastroMensagem = "⚠ $mensagemErro"
                                }
                            }
                        },
                        modifier = Modifier.align(Alignment.End),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                    ) {
                        Text("Finalizar e Enviar ao Banco de Dados", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 🟣 Status Cards
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val concluidosTexto = resumoStatus?.concluidos?.toString() ?: "-"
            val pendentesTexto = resumoStatus?.pendentes?.toString() ?: "-"

            StatusCard(concluidosTexto, "Concluídos", Color(0xFFE7F8ED), Modifier.weight(1f))
            StatusCard(pendentesTexto, "Pendentes", Color(0xFFFEE2E2), Modifier.weight(1f))
        }

        // 🟢 Botões principais
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {

            Button(
                onClick = { navController.navigate("scanner") },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Text(
                    "Scannear com câmera",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            if (mostrarDialogoEnvio) {
                AlertDialog(
                    onDismissRequest = { mostrarDialogoEnvio = false },
                    title = { Text("Confirmar envio") },
                    text = { Text("Deseja finalizar e enviar todos os códigos scanneados para o banco de dados?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                mostrarDialogoEnvio = false
                                scope.launch {
                                    carregando = true
                                    val itensParaEnviar = ScannedCodesRepository.getItems()
                                    if (itensParaEnviar.isEmpty()) {
                                        mensagem = "alert_sem_codigos"
                                        carregando = false
                                        return@launch
                                    }

                                    val resultado = InventarioApi.enviarScanneados(itensParaEnviar)
                                    carregando = false

                                    if (resultado.erros.isEmpty()) {
                                        mensagem = "✔ Envio concluído com sucesso!"
                                        ScannedCodesRepository.clear()
                                        cadastroNumInvent = ""
                                        cadastroCodFilial = ""
                                        cadastroCodProd = ""
                                        cadastroCodBarId = ""
                                        cadastroQtProd = ""
                                        cadastroQtCont = ""
                                        cadastroCodFunc = ""
                                        cadastroStatus = "Pendente"
                                        scope.launch {
                                            if (isWifiConnected(context)) {
                                                resumoStatus = runCatching { InventarioApi.getResumoStatus() }
                                                    .getOrElse { resumoStatus }
                                            }
                                        }
                                    } else {
                                        mensagem = "⚠ Erros ao enviar:\n" + resultado.erros.joinToString("\n")
                                    }
                                }
                            }
                        ) {
                            Text("Enviar")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { mostrarDialogoEnvio = false }) {
                            Text("Cancelar")
                        }
                    }
                )
            }

            if (mostrarDialogoInfo) {
                AlertDialog(
                    onDismissRequest = { mostrarDialogoInfo = false },
                    title = { Text("Consultar inventário") },
                    text = { Text("Deseja consultar os registros do banco de dados agora?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                mostrarDialogoInfo = false
                                navController.navigate("informacoes_banco")
                            }
                        ) {
                            Text("Consultar")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { mostrarDialogoInfo = false }) {
                            Text("Cancelar")
                        }
                    }
                )
            }
        }

        // 🟡 Lista de códigos scanneados
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Scanneados",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF7E7E7E)
            )

            if (scanneados.isEmpty()) {
                Text(
                    text = "Nenhum código escaneado até o momento.",
                    color = Color(0xFF9CA3AF),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                scanneados.forEach { item ->
                    ScanneadoCard(item)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            when (mensagem) {
                "alert_wifi_envio" -> FeedbackDialog(
                    onDismiss = { mensagem = null },
                    titulo = "Sem conexão",
                    texto = "É necessário estar conectado ao Wi-Fi para enviar os dados ao banco."
                )
                "alert_sem_codigos" -> FeedbackDialog(
                    onDismiss = { mensagem = null },
                    titulo = "Nenhum código",
                    texto = "Não há códigos scanneados para enviar."
                )
                "alert_wifi_info" -> FeedbackDialog(
                    onDismiss = { mensagem = null },
                    titulo = "Sem conexão",
                    texto = "Conecte-se ao Wi-Fi para consultar as informações do banco."
                )
                null -> { /* nada */ }
                else -> {
                    Text(
                        text = mensagem!!,
                        color = if (mensagem!!.startsWith("✔")) Color(0xFF16A34A) else Color(0xFFE11D48),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StatusCard(count: String, label: String, bgColor: Color, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .background(bgColor, RoundedCornerShape(8.dp))
            .padding(12.dp)
            .height(70.dp)
    ) {
        Text(text = count, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
        Text(text = label, fontSize = 12.sp, color = Color(0xFF111827))
    }
}

@Composable
fun ScanneadoCard(item: ScanneadoItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.codBarId,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )
                item.status?.takeIf { it.isNotBlank() }?.let { status ->
                    StatusBadge(status)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            InfoLinha("NUMINVENT", item.numInvent?.toString())
            InfoLinha("CODFILIAL", item.codFilial?.toString())
            InfoLinha("CODPROD", item.codProd?.toString())
            InfoLinha("QTPROD", item.qtProd?.toString())
            InfoLinha("QTCONT", item.qtCont?.toString())
            InfoLinha("CODFUNC", item.codFunc?.toString())
        }
    }
}

@Composable
private fun InfoLinha(rotulo: String, valor: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(rotulo, fontSize = 12.sp, color = Color(0xFF6B7280))
        Text(
            text = valor?.takeIf { it.isNotBlank() } ?: "-",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF111827)
        )
    }
}

@Composable
private fun StatusBadge(status: String) {
    val cor = when (status.lowercase()) {
        "concluído", "concluido" -> Color(0xFF0CFA25)
        "pendente", "pendentes" -> Color(0xFFDC2626)
        else -> Color(0xFF6B7280)
    }
    Surface(
        color = cor.copy(alpha = 0.12f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, cor)
    ) {
        Text(
            text = status,
            color = cor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

private fun isWifiConnected(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
}

@Composable
private fun FeedbackDialog(onDismiss: () -> Unit, titulo: String, texto: String) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titulo) },
        text = { Text(texto) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Ok")
            }
        }
    )
}
