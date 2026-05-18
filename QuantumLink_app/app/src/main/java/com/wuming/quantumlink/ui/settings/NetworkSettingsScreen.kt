package com.wuming.quantumlink.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wuming.quantumlink.core.Constants
import com.wuming.quantumlink.domain.model.VpnConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkSettingsScreen(
    config: VpnConfig,
    onToggleVpn: () -> Unit,
    onConfigChange: (VpnConfig) -> Unit,
    onBack: () -> Unit
) {
    var showAdvanced by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // IM 服务器地址
    var serverHost by remember { mutableStateOf(Constants.Server.host) }
    var serverPort by remember { mutableStateOf(Constants.Server.port.toString()) }
    val prefs = context.getSharedPreferences("quantumlink_prefs", Context.MODE_PRIVATE)

    // 首次加载时从 SharedPreferences 读取
    LaunchedEffect(Unit) {
        serverHost = prefs.getString("server_host", Constants.Server.host) ?: Constants.Server.host
        serverPort = prefs.getInt("server_port", Constants.Server.port).toString()
        Constants.Server.host = serverHost
        Constants.Server.port = serverPort.toIntOrNull() ?: Constants.Server.port
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("网络设置") },
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
                .padding(16.dp)
        ) {
            // ── VPN 开关 ──
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("VPN", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                if (config.isConnected) "已连接 - ${config.serverAddress}" else "未连接",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = config.isConnected,
                            onCheckedChange = { onToggleVpn() }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── 服务器配置 ──
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("服务器配置", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = config.serverAddress,
                        onValueChange = { onConfigChange(config.copy(serverAddress = it)) },
                        label = { Text("服务器地址") },
                        placeholder = { Text("wg.example.com:51820") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = config.serverPublicKey,
                        onValueChange = { onConfigChange(config.copy(serverPublicKey = it)) },
                        label = { Text("服务器公钥") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = config.clientAddress,
                        onValueChange = { onConfigChange(config.copy(clientAddress = it)) },
                        label = { Text("客户端地址") },
                        placeholder = { Text("10.0.0.2/24") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = config.clientPrivateKey,
                        onValueChange = { onConfigChange(config.copy(clientPrivateKey = it)) },
                        label = { Text("客户端私钥") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = config.dnsServers,
                        onValueChange = { onConfigChange(config.copy(dnsServers = it)) },
                        label = { Text("DNS 服务器") },
                        placeholder = { Text("1.1.1.1, 8.8.8.8") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── 连接/断开按钮 ──
            Button(
                onClick = onToggleVpn,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (config.isConnected)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    if (config.isConnected) Icons.Default.PowerSettingsNew else Icons.Default.VpnLock,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (config.isConnected) "断开 VPN 连接" else "连接 VPN")
            }

            // ── 流量统计 ──
            if (config.isConnected) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("流量统计", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(formatBytes(config.trafficDown), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("下载", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(formatBytes(config.trafficUp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("上传", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "${bytes / (1024 * 1024 * 1024)} GB"
}
