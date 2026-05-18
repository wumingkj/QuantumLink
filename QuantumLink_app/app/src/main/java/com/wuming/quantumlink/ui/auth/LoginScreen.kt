package com.wuming.quantumlink.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wuming.quantumlink.data.remote.api.AuthApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 登录/注册结果回调 */
data class AuthSuccess(
    val token: String,
    val userId: String,
    val username: String,
    val nickname: String
)

@Composable
fun LoginScreen(
    onLoginSuccess: (AuthSuccess) -> Unit
) {
    var isRegisterMode by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo
            Icon(
                Icons.Default.Forum,
                null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "量子飞信",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "轻量级安全通信",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))

            // 用户名
            OutlinedTextField(
                value = username,
                onValueChange = { username = it; errorMsg = "" },
                label = { Text("用户名") },
                leadingIcon = { Icon(Icons.Default.Person, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            // 昵称（注册模式才显示）
            if (isRegisterMode) {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it; errorMsg = "" },
                    label = { Text("昵称") },
                    leadingIcon = { Icon(Icons.Default.Badge, null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it; errorMsg = "" },
                    label = { Text("手机号（选填）") },
                    leadingIcon = { Icon(Icons.Default.Phone, null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
            }

            // 密码
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorMsg = "" },
                label = { Text("密码") },
                leadingIcon = { Icon(Icons.Default.Lock, null) },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            null
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))

            // 错误提示
            if (errorMsg.isNotEmpty()) {
                Text(
                    errorMsg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
            }

            // 登录/注册按钮
            Button(
                onClick = {
                    if (username.isBlank() || password.isBlank()) {
                        errorMsg = "用户名和密码不能为空"
                        return@Button
                    }
                    if (isRegisterMode && nickname.isBlank()) {
                        errorMsg = "昵称不能为空"
                        return@Button
                    }
                    isLoading = true
                    errorMsg = ""
                    scope.launch(Dispatchers.IO) {
                        val result = if (isRegisterMode) {
                            // 先注册再登录
                            val reg = AuthApi.register(username, password, nickname, phone)
                            if (reg.success) {
                                AuthApi.login(username, password)
                            } else reg
                        } else {
                            AuthApi.login(username, password)
                        }
                        withContext(Dispatchers.Main) {
                            isLoading = false
                            if (result.success) {
                                onLoginSuccess(AuthSuccess(
                                    token = result.token,
                                    userId = result.userId,
                                    username = result.username,
                                    nickname = result.nickname
                                ))
                            } else {
                                errorMsg = result.error
                            }
                        }
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(if (isRegisterMode) "注册" else "登录")
                }
            }
            Spacer(Modifier.height(16.dp))

            // 切换登录/注册
            TextButton(onClick = {
                isRegisterMode = !isRegisterMode
                errorMsg = ""
            }) {
                Text(
                    if (isRegisterMode) "已有账号？去登录"
                    else "没有账号？去注册"
                )
            }
        }
    }
}
