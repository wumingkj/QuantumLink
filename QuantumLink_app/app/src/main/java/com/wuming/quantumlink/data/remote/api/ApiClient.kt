package com.wuming.quantumlink.data.remote.api

import android.util.Log
import com.wuming.quantumlink.core.Constants
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * HTTP API 客户端 — 基于 HttpURLConnection，零额外依赖
 * 用于注册、登录、获取联系人/会话/论坛等 REST 调用
 */
object ApiClient {

    private const val TAG = "ApiClient"
    private const val TIMEOUT = 10_000

    private val baseUrl: String get() = Constants.Server.apiUrl

    /** 当前登录用户的 JWT token */
    var token: String = ""

    // ===== GET =====

    fun get(path: String): ApiResult {
        return request("GET", path)
    }

    // ===== POST =====

    fun post(path: String, body: JSONObject? = null): ApiResult {
        return request("POST", path, body)
    }

    // ===== PUT =====

    fun put(path: String, body: JSONObject? = null): ApiResult {
        return request("PUT", path, body)
    }

    // ===== DELETE =====

    fun delete(path: String): ApiResult {
        return request("DELETE", path)
    }

    // ===== 底层请求 =====

    private fun request(method: String, path: String, body: JSONObject? = null): ApiResult {
        val urlStr = baseUrl + path
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT
            conn.readTimeout = TIMEOUT
            conn.requestMethod = method
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Accept", "application/json")

            // 添加 Authorization 头
            if (token.isNotEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer $token")
            }

            // 写入请求体
            if (body != null && method in listOf("POST", "PUT")) {
                conn.doOutput = true
                OutputStreamWriter(conn.outputStream, "utf-8").use { writer ->
                    writer.write(body.toString())
                    writer.flush()
                }
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val reader = BufferedReader(InputStreamReader(stream, "utf-8"))
            val response = reader.readText()
            reader.close()
            conn.disconnect()

            if (code in 200..299) {
                ApiResult.Success(code, response)
            } else {
                ApiResult.Error(code, response)
            }
        } catch (e: Exception) {
            Log.e(TAG, "请求失败: $method $path", e)
            ApiResult.Failure(e.localizedMessage ?: "网络错误")
        }
    }
}

/** API 请求结果 */
sealed class ApiResult {
    data class Success(val code: Int, val raw: String) : ApiResult() {
        fun json(): JSONObject = JSONObject(raw)
        fun jsonArray(): JSONArray = JSONArray(raw)
        fun optionalString(key: String): String = json().optString(key, "")
        fun optionalInt(key: String): Int = json().optInt(key, 0)
        fun optionalLong(key: String): Long = json().optLong(key, 0L)
        fun errorMessage(): String = json().optString("error", "")
    }
    data class Error(val code: Int, val raw: String) : ApiResult() {
        fun errorMessage(): String = try {
            JSONObject(raw).optString("error", "未知错误")
        } catch (_: Exception) { raw }
    }
    data class Failure(val message: String) : ApiResult()
}
