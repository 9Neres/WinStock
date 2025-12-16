package com.neres.composeaplication.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class UserSession(
    val user: String,
    val password: String,
    val codFunc: Int? = null
)

object SessionManager {

    private const val PREFS_NAME = "login_sessions"
    private const val KEY_SESSIONS = "sessions"

    fun getSessions(context: Context): List<UserSession> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(stored)
            buildList(array.length()) {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        UserSession(
                            user = obj.optString("user"),
                            password = obj.optString("password"),
                            codFunc = if (obj.has("codFunc") && !obj.isNull("codFunc")) {
                                obj.optInt("codFunc")
                            } else null
                        )
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    fun saveSession(context: Context, user: String, password: String, codFunc: Int?) {
        if (user.isBlank() || password.isBlank()) return
        val existing = getSessions(context).toMutableList()
        val existingIndex = existing.indexOfFirst { it.user == user && it.password == password }
        
        if (existingIndex >= 0) {
            // Atualiza sessão existente com o novo codFunc
            existing[existingIndex] = UserSession(user, password, codFunc)
        } else {
            // Adiciona nova sessão
            existing.add(UserSession(user, password, codFunc))
        }
        persist(context, existing)
    }

    fun hasSession(context: Context, user: String, password: String): Boolean =
        getSessions(context).any { it.user == user && it.password == password }

    fun getSession(context: Context, user: String, password: String): UserSession? =
        getSessions(context).firstOrNull { it.user == user && it.password == password }

    private fun persist(context: Context, sessions: List<UserSession>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray().apply {
            sessions.forEach { session ->
                put(
                    JSONObject().apply {
                        put("user", session.user)
                        put("password", session.password)
                        if (session.codFunc != null) {
                            put("codFunc", session.codFunc)
                        }
                    }
                )
            }
        }
        prefs.edit().putString(KEY_SESSIONS, array.toString()).apply()
    }
}

