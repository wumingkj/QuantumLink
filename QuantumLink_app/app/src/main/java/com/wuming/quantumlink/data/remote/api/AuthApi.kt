package com.wuming.quantumlink.data.remote.api

import org.json.JSONObject

/**
 * 认证相关 API 调用
 */
object AuthApi {

    data class AuthResult(
        val success: Boolean,
        val token: String = "",
        val userId: String = "",
        val username: String = "",
        val nickname: String = "",
        val error: String = ""
    )

    /** 注册 */
    fun register(username: String, password: String, nickname: String, phone: String = ""): AuthResult {
        val body = JSONObject().apply {
            put("username", username)
            put("password", password)
            put("nickname", nickname)
            if (phone.isNotBlank()) put("phone", phone)
        }
        val result = ApiClient.post("/auth/register", body)
        return when (result) {
            is ApiResult.Success -> {
                val json = result.json()
                AuthResult(
                    success = true,
                    userId = json.optString("id", ""),
                    username = json.optString("username", ""),
                    nickname = json.optString("nickname", "")
                )
            }
            is ApiResult.Error -> AuthResult(
                success = false, error = result.errorMessage()
            )
            is ApiResult.Failure -> AuthResult(
                success = false, error = result.message
            )
        }
    }

    /** 登录 */
    fun login(username: String, password: String): AuthResult {
        val body = JSONObject().apply {
            put("username", username)
            put("password", password)
        }
        val result = ApiClient.post("/auth/login", body)
        return when (result) {
            is ApiResult.Success -> {
                val json = result.json()
                val token = json.optString("token", "")
                val user = json.optJSONObject("user")
                ApiClient.token = token // 保存 token
                AuthResult(
                    success = true,
                    token = token,
                    userId = user?.optString("id", "") ?: "",
                    username = user?.optString("username", "") ?: "",
                    nickname = user?.optString("nickname", "") ?: ""
                )
            }
            is ApiResult.Error -> AuthResult(
                success = false, error = result.errorMessage()
            )
            is ApiResult.Failure -> AuthResult(
                success = false, error = result.message
            )
        }
    }
}
