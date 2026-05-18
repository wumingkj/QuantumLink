package com.wuming.quantumlink.ui.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wuming.quantumlink.data.remote.api.ApiClient
import com.wuming.quantumlink.data.remote.api.ApiResult
import com.wuming.quantumlink.manager.LoginManager
import com.wuming.quantumlink.ui.auth.AuthSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(
    authInfo: AuthSuccess,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onProfileUpdated: ((String) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var currentNickname by remember { mutableStateOf(authInfo.nickname) }
    var currentAvatar by remember { mutableStateOf(authInfo.nickname.take(1)) }

    // 编辑昵称对话框
    var showNicknameDialog by remember { mutableStateOf(false) }
    var editNickname by remember { mutableStateOf(currentNickname) }
    var saveStatus by remember { mutableStateOf("") }

    // 确认注销对话框
    var showLogoutDialog by remember { mutableStateOf(false) }

    // 注销账号对话框
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteConfirmText by remember { mutableStateOf("") }
    var deleteStatus by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账号设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // ── 头像区域 ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp, bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(80.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                currentAvatar,
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        currentNickname,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "UID: ${authInfo.userId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── 资料设置 ──
            SectionTitle("资料设置")

            SettingsRow(
                icon = Icons.Default.Badge,
                title = "昵称",
                summary = currentNickname,
                onClick = {
                    editNickname = currentNickname
                    saveStatus = ""
                    showNicknameDialog = true
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            SettingsRow(
                icon = Icons.Default.Person,
                title = "用户名",
                summary = "@${authInfo.username}",
                enabled = false
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            SettingsRow(
                icon = Icons.Default.Phone,
                title = "手机号",
                summary = "暂不支持修改",
                enabled = false
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            SettingsRow(
                icon = Icons.Default.Numbers,
                title = "UID",
                summary = authInfo.userId,
                enabled = false
            )

            Spacer(Modifier.height(24.dp))

            // ── 账号操作 ──
            SectionTitle("账号操作")

            Button(
                onClick = { showLogoutDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, null)
                Spacer(Modifier.width(8.dp))
                Text("退出登录")
            }

            Spacer(Modifier.height(8.dp))

            // ── 注销账号 ──
            OutlinedButton(
                onClick = {
                    deleteConfirmText = ""
                    deleteStatus = ""
                    showDeleteDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.DeleteForever, null)
                Spacer(Modifier.width(8.dp))
                Text("注销账号")
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // ── 编辑昵称对话框 ──
    if (showNicknameDialog) {
        AlertDialog(
            onDismissRequest = { showNicknameDialog = false },
            title = { Text("修改昵称") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editNickname,
                        onValueChange = { editNickname = it; saveStatus = "" },
                        label = { Text("昵称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (saveStatus.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            saveStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (saveStatus.startsWith("✅")) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editNickname.isBlank()) {
                        saveStatus = "❌ 昵称不能为空"
                        return@TextButton
                    }
                    scope.launch(Dispatchers.IO) {
                        val body = JSONObject().apply { put("nickname", editNickname) }
                        val result = ApiClient.put("/users/me", body)
                        withContext(Dispatchers.Main) {
                            if (result is ApiResult.Success) {
                                currentNickname = editNickname
                                currentAvatar = editNickname.take(1)
                                saveStatus = "✅ 已更新"
                                LoginManager.getSavedLogin()?.let {
                                    LoginManager.saveLogin(it.copy(nickname = editNickname))
                                }
                                onProfileUpdated?.invoke(editNickname)
                            } else {
                                val err = when (result) {
                                    is ApiResult.Error -> result.errorMessage()
                                    is ApiResult.Failure -> result.message
                                    else -> "未知错误"
                                }
                                saveStatus = "❌ $err"
                            }
                        }
                    }
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showNicknameDialog = false }) { Text("取消") }
            }
        )
    }

    // ── 退出登录对话框 ──
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("退出登录") },
            text = { Text("确定要退出登录吗？下次使用需要重新输入账号密码。") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    LoginManager.clearLogin()
                    onLogout()
                }) {
                    Text("退出", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("取消") }
            }
        )
    }

    // ── 注销账号对话框 ──
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("注销账号", color = MaterialTheme.colorScheme.error) },
            text = {
                Column {
                    Text("此操作不可撤销！账号数据将被清空。")
                    Text("法律合规：服务端依法保留必要记录。", style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = deleteConfirmText,
                        onValueChange = { deleteConfirmText = it; deleteStatus = "" },
                        label = { Text("输入 YES 确认注销") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (deleteStatus.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(deleteStatus, style = MaterialTheme.typography.bodySmall,
                             color = if (deleteStatus.startsWith("✅")) MaterialTheme.colorScheme.primary
                                     else MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (deleteConfirmText != "YES") {
                            deleteStatus = "❌ 请输入 YES 确认"
                            return@TextButton
                        }
                        scope.launch(Dispatchers.IO) {
                            val result = com.wuming.quantumlink.data.remote.api.ApiClient.delete("/users/me")
                            withContext(Dispatchers.Main) {
                                when (result) {
                                    is com.wuming.quantumlink.data.remote.api.ApiResult.Success -> {
                                        deleteStatus = "✅ 账号已注销"
                                        LoginManager.clearLogin()
                                        kotlinx.coroutines.delay(1000)
                                        onLogout()
                                    }
                                    is com.wuming.quantumlink.data.remote.api.ApiResult.Error -> {
                                        deleteStatus = "❌ ${result.errorMessage()}"
                                    }
                                    is com.wuming.quantumlink.data.remote.api.ApiResult.Failure -> {
                                        deleteStatus = "❌ ${result.message}"
                                    }
                                }
                            }
                        }
                    },
                    enabled = deleteConfirmText == "YES"
                ) {
                    Text("确认注销", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    summary: String,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon, null,
            modifier = Modifier.size(24.dp),
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (enabled) 0.8f else 0.4f
                )
            )
        }
        if (enabled && onClick != null) {
            Icon(
                Icons.Default.ChevronRight, null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    }
}
