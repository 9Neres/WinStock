package com.neres.composeaplication.ui.screens

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.rememberCoroutineScope
import com.neres.composeaplication.data.NocoDBApi
import com.neres.composeaplication.data.SessionManager
import com.neres.composeaplication.data.UltimoUsuarioLogado
import com.neres.composeaplication.data.UserSession
import com.neres.composeaplication.data.UsuariosCacheRepository
import com.neres.composeaplication.data.LogsRepository
import kotlinx.coroutines.launch
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LoginScreen(onLogin: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var nome by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var erro by remember { mutableStateOf<String?>(null) }
    var sessoes by remember { mutableStateOf<List<UserSession>>(emptyList()) }
    var modoOffline by remember { mutableStateOf(false) }
    var usuariosCacheados by remember { mutableStateOf(0) }

    var usuariosCacheadosLista by remember { mutableStateOf<List<com.neres.composeaplication.data.UsuarioRecord>>(emptyList()) }
    var mostrarMenuUsuarios by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        sessoes = SessionManager.getSessions(context)
        // NÃO preenche senha automaticamente por segurança

        // Carrega lista de usuários do cache
        usuariosCacheadosLista = UsuariosCacheRepository.getAllCachedUsuarios()
        usuariosCacheados = usuariosCacheadosLista.size
        modoOffline = !isWifiConnected(context)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(16.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("WinStock", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                Text("Login", fontSize = 15.sp, fontWeight = FontWeight.Light, color = MaterialTheme.colorScheme.onBackground)

                Spacer(modifier = Modifier.height(24.dp))

                // Seleção de usuário (sessões anteriores + usuários em cache)
                val todosUsuariosDisponiveis = buildList {
                    // Adiciona usuários das sessões
                    addAll(sessoes.map { it.user })
                    // Adiciona usuários do cache que não estão nas sessões
                    usuariosCacheadosLista.forEach { usuario ->
                        usuario.user?.let { user ->
                            if (user !in sessoes.map { it.user }) {
                                add(user)
                            }
                        }
                    }
                }.distinct()

                if (todosUsuariosDisponiveis.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { mostrarMenuUsuarios = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (nome.isNotBlank()) nome else "Selecione o usuário",
                                    color = if (nome.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    fontWeight = if (nome.isNotBlank()) FontWeight.SemiBold else FontWeight.Normal
                                )
                                Text("▼", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                        
                        DropdownMenu(
                            expanded = mostrarMenuUsuarios,
                            onDismissRequest = { mostrarMenuUsuarios = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            todosUsuariosDisponiveis.forEach { user ->
                                // Verifica se tem sessão salva ou está no cache
                                val temSessao = sessoes.any { it.user == user }
                                val estaNoCacheUsuarios = usuariosCacheadosLista.any { it.user == user }

                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = user,
                                                fontSize = 14.sp,
                                                fontWeight = if (nome == user) FontWeight.Bold else FontWeight.Normal,
                                                color = if (nome == user) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    },
                                    onClick = {
                                        nome = user
                                        senha = "" // NÃO preenche senha automaticamente
                                        erro = null
                                        mostrarMenuUsuarios = false
                                    }
                                )
                            }
                        }
                    }
                    
                    // Legenda pequena
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {}
                }

                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("Nome de usuário") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = senha,
                    onValueChange = { senha = it },
                    label = { Text("Senha") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        scope.launch {
                            if (nome == "admin" && senha == "admin") {
                                Log.d("LoginScreen", "✓ Login de admin override")
                                LogsRepository.sucesso("Login", "Login de admin override", "user: admin")
                                UltimoUsuarioLogado.codFunc = 0 // CODFUNC padrão para admin
                                UltimoUsuarioLogado.user = "admin"
                                onLogin()
                            } else {
                                erro = null
                                val online = isWifiConnected(context)

                                // Tenta autenticar online primeiro
                                val registroOnline = if (online) {
                                    NocoDBApi.autenticarUsuario(nome, senha)
                                } else null

                                when {
                                    // 1. Autenticação online bem-sucedida
                                    registroOnline != null -> {
                                        Log.d("LoginScreen", "✅ Login online bem-sucedido: ${registroOnline.user}")
                                        LogsRepository.sucesso("Login", "Login online: ${registroOnline.user}", "CODFUNC: ${registroOnline.codFunc}")
                                        SessionManager.saveSession(context, nome, senha, registroOnline.codFunc)
                                        UltimoUsuarioLogado.codFunc = registroOnline.codFunc
                                        UltimoUsuarioLogado.user = registroOnline.user ?: nome
                                        sessoes = SessionManager.getSessions(context)
                                        onLogin()
                                    }

                                    // 2. Tenta autenticar usando cache de usuários (offline)
                                    !online -> {
                                        val usuarioCache = UsuariosCacheRepository.autenticarOffline(nome, senha)
                                        if (usuarioCache != null) {
                                            Log.d("LoginScreen", "✅ Login offline bem-sucedido: ${usuarioCache.user}")
                                            LogsRepository.sucesso("Login", "Login offline: ${usuarioCache.user}", "CODFUNC: ${usuarioCache.codFunc}")
                                            // Salva sessão para próximas vezes
                                            SessionManager.saveSession(context, nome, senha, usuarioCache.codFunc)
                                            UltimoUsuarioLogado.codFunc = usuarioCache.codFunc
                                            UltimoUsuarioLogado.user = usuarioCache.user ?: nome
                                            sessoes = SessionManager.getSessions(context)
                                            onLogin()
                                        } else if (SessionManager.hasSession(context, nome, senha)) {
                                            // 3. Usa sessão salva anteriormente
                                            val sessao = SessionManager.getSession(context, nome, senha)
                                            sessao?.let {
                                                Log.d("LoginScreen", "✅ Login com sessão salva: ${it.user}")
                                                LogsRepository.info("Login", "Login com sessão salva: ${it.user}", "CODFUNC: ${it.codFunc}")
                                                UltimoUsuarioLogado.codFunc = it.codFunc
                                                UltimoUsuarioLogado.user = it.user
                                            }
                                            onLogin()
                                        } else {
                                            if (usuariosCacheados > 0) {
                                                erro = "Senha incorreta ou usuário não sincronizado.\n💾 ${sessoes.size} sessões salvas | 📦 $usuariosCacheados no cache"
                                                LogsRepository.aviso("Login", "Tentativa de login falhou offline: $nome", "Usuário não encontrado no cache")
                                            } else {
                                                erro = "Sem conexão. Sincronize os dados online primeiro."
                                                LogsRepository.aviso("Login", "Tentativa de login sem cache", "Usuário: $nome")
                                            }
                                        }
                                    }

                                    // 4. Online mas falhou - tenta sessão salva
                                    SessionManager.hasSession(context, nome, senha) -> {
                                        val sessao = SessionManager.getSession(context, nome, senha)
                                        sessao?.let {
                                            Log.d("LoginScreen", "✅ Login com sessão salva: ${it.user}")
                                            UltimoUsuarioLogado.codFunc = it.codFunc
                                            UltimoUsuarioLogado.user = it.user
                                        }
                                        onLogin()
                                    }

                                    else -> {
                                        erro = "Usuário ou senha inválidos."
                                        LogsRepository.aviso("Login", "Tentativa de login falhou: $nome", "Credenciais inválidas")
                                    }
                                 }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Entrar", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }

                erro?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
                Spacer(modifier = Modifier.height(20.dp)) // Add some space before the copyright
                Text(
                    text = "© Desenvolvido por Neres",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f) // Use a subtle color
                )
            }
        }
    }
}

private fun isWifiConnected(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
}
