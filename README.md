# App de Inventário com Leitor de Código de Barras

Aplicativo Android de contador de inventário com leitura de código de barras e notas fiscais, registro local e sincronização com backend via API REST, seguindo um fluxo offline-first.

## Stack Tecnológica

**Linguagens e Frameworks:**
Kotlin, Jetpack Compose, Android SDK (API 24+)

**Bibliotecas:**
CameraX, ML Kit Barcode Scanning, Zebra DataWedge, Ktor Client, Kotlinx Serialization, Coroutines, SharedPreferences, StateFlow, NocoDB (REST API)

O projeto é focado em boas práticas de arquitetura mobile, consumo de API, cache offline e integração com scanners industriais, usando IA como apoio em código e documentação.

---

## Acesso ao Sistema

**Credenciais de login:**
- Usuário: `admin`
- Senha: `admin`

---

## Configuração do Banco de Dados

Para o funcionamento completo do aplicativo, é recomendado conectar com seu próprio banco de dados e configurar as tabelas necessárias.

**Tabelas utilizadas:**
- `TAB_INVENTI`
- `TAB_INVENTIC`

Estas tabelas são baseadas na estrutura do Winthor da TOTVS.

---

## Gemini CLI

O projeto inclui o Gemini CLI para auxiliar no desenvolvimento. Para utilizar:

