package com.wuming.quantumlink.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wuming.quantumlink.domain.model.VpnConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 设置二级页面 */
enum class SettingsSubPage {
    NONE, NETWORK, PERMISSION, ABOUT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    authInfo: com.wuming.quantumlink.ui.auth.AuthSuccess? = null,
    vpnConfig: VpnConfig,
    onVpnToggle: () -> Unit,
    onVpnConfigChange: (VpnConfig) -> Unit
) {
    var subPage by remember { mutableStateOf(SettingsSubPage.NONE) }

    BackHandler(subPage != SettingsSubPage.NONE) {
        subPage = SettingsSubPage.NONE
    }

    when (subPage) {
        SettingsSubPage.NETWORK -> {
            NetworkSettingsScreen(
                config = vpnConfig,
                onToggleVpn = onVpnToggle,
                onConfigChange = onVpnConfigChange,
                onBack = { subPage = SettingsSubPage.NONE }
            )
        }
        SettingsSubPage.PERMISSION -> {
            PermissionSettingsScreen(onBack = { subPage = SettingsSubPage.NONE })
        }
        SettingsSubPage.ABOUT -> {
            AboutScreen(onBack = { subPage = SettingsSubPage.NONE })
        }
        SettingsSubPage.NONE -> {
            SettingsMainPage(
                authInfo = authInfo,
                onOpenNetwork = { subPage = SettingsSubPage.NETWORK },
                onOpenPermission = { subPage = SettingsSubPage.PERMISSION },
                onOpenAbout = { subPage = SettingsSubPage.ABOUT }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsMainPage(
    authInfo: com.wuming.quantumlink.ui.auth.AuthSuccess? = null,
    onOpenNetwork: () -> Unit,
    onOpenPermission: () -> Unit,
    onOpenAbout: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("设置") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // ── 账号信息 ──
            if (authInfo != null) {
                var showEditDialog by remember { mutableStateOf(false) }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    authInfo.nickname.take(1),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                authInfo.nickname,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "@${authInfo.username}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "UID: ${authInfo.userId}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        // 编辑按钮
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(Icons.Default.Edit, "编辑资料")
                        }
                    }
                }

                // ── 编辑资料对话框 ──
                if (showEditDialog) {
                    var newNickname by remember { mutableStateOf(authInfo.nickname) }
                    var saveStatus by remember { mutableStateOf("") }
                    val scope = rememberCoroutineScope()

                    AlertDialog(
                        onDismissRequest = { showEditDialog = false },
                        title = { Text("编辑资料") },
                        text = {
                            Column {
                                OutlinedTextField(
                                    value = newNickname,
                                    onValueChange = { newNickname = it; saveStatus = "" },
                                    label = { Text("昵称") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(8.dp))
                                Text("UID: ${authInfo.userId}", style = MaterialTheme.typography.labelSmall,
                                     color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                Text("用户名: ${authInfo.username}", style = MaterialTheme.typography.labelSmall,
                                     color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (saveStatus.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(saveStatus, style = MaterialTheme.typography.bodySmall,
                                         color = if (saveStatus.startsWith("✅")) MaterialTheme.colorScheme.primary
                                                 else MaterialTheme.colorScheme.error)
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                scope.launch(Dispatchers.IO) {
                                    val body = org.json.JSONObject().apply {
                                        put("nickname", newNickname)
                                    }
                                    val result = com.wuming.quantumlink.data.remote.api.ApiClient.put("/users/me", body)
                                    withContext(Dispatchers.Main) {
                                        if (result is com.wuming.quantumlink.data.remote.api.ApiResult.Success) {
                                            saveStatus = "✅ 已更新"
                                            // 更新本地 authInfo（注意这是不可变的，需要外部刷新）
                                        } else {
                                            val err = when (result) {
                                                is com.wuming.quantumlink.data.remote.api.ApiResult.Error -> result.errorMessage()
                                                is com.wuming.quantumlink.data.remote.api.ApiResult.Failure -> result.message
                                                else -> "未知错误"
                                            }
                                            saveStatus = "❌ $err"
                                        }
                                    }
                                }
                            }) {
                                Text("保存")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showEditDialog = false }) {
                                Text("取消")
                            }
                        }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            // 网络设置
            SectionHeader("网络设置")
            SettingsItem(
                icon = { Icon(Icons.Default.Language, contentDescription = null) },
                title = "网络设置",
                summary = "VPN 配置、服务器地址、客户端设置",
                onClick = onOpenNetwork
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // 应用设置
            SectionHeader("应用设置")
            SettingsItem(
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                title = "应用设置",
                summary = "通用设置、功能配置",
                onClick = { /* TODO */ }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // 权限管理
            SectionHeader("权限管理")
            SettingsItem(
                icon = { Icon(Icons.Default.Security, contentDescription = null) },
                title = "权限设置",
                summary = "管理悬浮窗、通知、电池优化等权限",
                onClick = onOpenPermission
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // 其他
            SectionHeader("其他")
            SettingsItem(
                icon = { Icon(Icons.Default.Info, contentDescription = null) },
                title = "关于",
                summary = "版本信息、开源许可",
                onClick = onOpenAbout
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsItem(
    icon: @Composable () -> Unit,
    title: String,
    summary: String,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) { icon() }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
