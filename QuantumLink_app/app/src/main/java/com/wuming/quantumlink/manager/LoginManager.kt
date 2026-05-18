package com.wuming.quantumlink.manager

import android.content.Context
import android.content.SharedPreferences
import com.wuming.quantumlink.data.remote.api.ApiClient
import com.wuming.quantumlink.ui.auth.AuthSuccess

/**
 * 登录状态管理器 — 持久化 token，避免每次启动都要登录
 */
object LoginManager {

    private const val PREFS_NAME = "login_prefs"
    private const val KEY_TOKEN = "token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USERNAME = "username"
    private const val KEY_NICKNAME = "nickname"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // 恢复 token
        val token = prefs.getString(KEY_TOKEN, "") ?: ""
        if (token.isNotEmpty()) {
            ApiClient.token = token
        }
    }

    /** 保存登录信息 */
    fun saveLogin(info: AuthSuccess) {
        prefs.edit().apply {
            putString(KEY_TOKEN, info.token)
            putString(KEY_USER_ID, info.userId)
            putString(KEY_USERNAME, info.username)
            putString(KEY_NICKNAME, info.nickname)
            apply()
        }
        ApiClient.token = info.token
    }

    /** 清除登录信息（退出登录） */
    fun clearLogin() {
        prefs.edit().clear().apply()
        ApiClient.token = ""
    }

    /** 是否有已保存的登录信息 */
    fun hasSavedLogin(): Boolean {
        return prefs.getString(KEY_TOKEN, "")?.isNotEmpty() == true
    }

    /** 获取已保存的登录信息 */
    fun getSavedLogin(): AuthSuccess? {
        val token = prefs.getString(KEY_TOKEN, "") ?: return null
        if (token.isEmpty()) return null
        return AuthSuccess(
            token = token,
            userId = prefs.getString(KEY_USER_ID, "") ?: "",
            username = prefs.getString(KEY_USERNAME, "") ?: "",
            nickname = prefs.getString(KEY_NICKNAME, "") ?: ""
        )
    }
}
