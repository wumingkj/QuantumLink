package com.wuming.quantumlink.ui.secretchat

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wuming.quantumlink.domain.model.SecretChatSession
import com.wuming.quantumlink.domain.model.VpnConfig
import com.wuming.quantumlink.manager.SecretDestroyManager
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

// ==================== 主界面：密信会话列表 ====================

@Composable
fun SecretChatListScreen(
    sessions: List<SecretChatSession>,
    vpnConfig: VpnConfig,
    onCreateSession: () -> Unit,
    onSessionClick: (SecretChatSession) -> Unit,
    onExpiredSessions: (List<String>) -> Unit,
    destroyManager: SecretDestroyManager = remember { SecretDestroyManager() }
) {
    // 定时刷新 UI（倒计时更新）
    var tick by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            tick = System.currentTimeMillis()
        }
    }

    // 过滤：显示活跃且未销毁的会话
    val displaySessions = remember(sessions, tick, destroyManager.destroyedSessions.value) {
        sessions.filter { it.isActive && !destroyManager.isDestroyed(it.id) }
    }

    // 已销毁的会话（可供查看历史）
    val destroyedSessions = remember(sessions, destroyManager.destroyedSessions.value) {
        sessions.filter { destroyManager.isDestroyed(it.id) }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateSession) {
                Icon(Icons.Default.Lock, "新建密信")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // VPN 状态提示
            VpnRequireBanner(vpnConfig = vpnConfig)

            if (displaySessions.isEmpty() && destroyedSessions.isEmpty()) {
                EmptySecretHint()
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    // 活跃会话
                    if (displaySessions.isNotEmpty()) {
                        item {
                            Text(
                                "密信会话",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        items(displaySessions, key = { it.id }) { session ->
                            SecretSessionItem(
                                session = session,
                                destroyManager = destroyManager,
                                onClick = { onSessionClick(session) }
                            )
                        }
                    }
                    // 已销毁会话（灰显）
                    if (destroyedSessions.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "已销毁",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        items(destroyedSessions, key = { "${it.id}_destroyed" }) { session ->
                            SecretSessionItem(
                                session = session,
                                destroyManager = destroyManager,
                                isDestroyedView = true,
                                onClick = { onSessionClick(session) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySecretHint() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.Lock,
                null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(Modifier.height(16.dp))
            Text("暂无密信会话", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "密信强制启用 VPN，服务端计时到期自动销毁",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
            )
            Text(
                "服务端依法保留记录，客户端可在到期前导出",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

@Composable
private fun VpnRequireBanner(vpnConfig: VpnConfig) {
    val isReady = vpnConfig.isConnected && vpnConfig.serverAddress.isNotBlank()
    Surface(
        color = if (isReady) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isReady) Icons.Default.Shield else Icons.Default.Warning,
                null,
                tint = if (isReady) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (isReady) "VPN 已启用 · 密信安全通道就绪"
                else "VPN 未连接 — 请在设置中配置并连接 VPN",
                style = MaterialTheme.typography.labelMedium,
                color = if (isReady) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun SecretSessionItem(
    session: SecretChatSession,
    destroyManager: SecretDestroyManager,
    isDestroyedView: Boolean = false,
    onClick: () -> Unit
) {
    val alpha = if (isDestroyedView) 0.4f else 1.0f
    val remainMs = destroyManager.getRemainingMs(session)
    val remainMin = (remainMs / 60_000).toInt()
    val remainSec = ((remainMs % 60_000) / 1000).toInt()

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = alpha)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (isDestroyedView) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (isDestroyedView) Icons.Default.LockOpen else Icons.Default.Lock,
                        null,
                        modifier = Modifier.size(26.dp),
                        tint = if (isDestroyedView) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                               else MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    session.peerName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        session.lastMessage.ifEmpty { "暂无消息" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    if (isDestroyedView) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "已销毁",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        // 倒计时
                        val isUrgent = remainMin < 5
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isUrgent) MaterialTheme.colorScheme.errorContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = if (remainMin > 0) "${remainMin}分${remainSec}秒"
                                       else "${remainSec}秒",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isUrgent) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== 密信聊天界面 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecretChatDetailScreen(
    session: SecretChatSession,
    vpnConfig: VpnConfig,
    messages: List<SecretMessage>,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit,
    onExport: (() -> Unit)? = null,
    destroyManager: SecretDestroyManager = remember { SecretDestroyManager() },
    context: Context = LocalContext.current
) {
    var inputText by remember { mutableStateOf("") }
    val isDestroyed = destroyManager.isDestroyed(session.id)

    // 倒计时（基于服务端时间）
    var remainLabel by remember { mutableStateOf("") }
    LaunchedEffect(session.id) {
        while (true) {
            val ms = destroyManager.getRemainingMs(session)
            remainLabel = when {
                isDestroyed -> "已销毁 · 服务端依法保留记录"
                ms <= 0 -> "服务端处理中..."
                ms < 60_000 -> "${(ms / 1000)}秒后自动销毁"
                else -> "${(ms / 60_000)}分${((ms % 60_000) / 1000)}秒后自动销毁"
            }
            delay(1_000L)
        }
    }

    // 导出功能：通过 Intent 分享文本
    val doExport: () -> Unit = onExport ?: {
        val text = buildString {
            appendLine("=== 量子飞信 密信会话导出 ===")
            appendLine("会话: ${session.peerName}")
            appendLine("创建: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(session.createdAt))}")
            appendLine("注意: 服务端依法保留完整记录")
            appendLine("=================================")
            appendLine()
            for (msg in messages) {
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
                val who = if (msg.isMine) "我" else session.peerName
                appendLine("[$time] $who: ${msg.content}")
            }
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, "密信导出 - ${session.peerName}")
        }
        context.startActivity(Intent.createChooser(intent, "导出密信记录"))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(session.peerName, style = MaterialTheme.typography.titleSmall)
                        Text(
                            remainLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDestroyed) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.error
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    // 导出按钮（销毁前后都可导出）
                    IconButton(onClick = doExport) {
                        Icon(Icons.Default.Share, "导出密信记录")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDestroyed)
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                )
            )
        },
        bottomBar = {
            // 已销毁的会话不显示输入框
            if (!isDestroyed) {
                Surface(shadowElevation = 8.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val canSend = vpnConfig.isConnected && vpnConfig.serverAddress.isNotBlank()

                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(if (canSend) "输入密信..." else "请先连接 VPN") },
                            maxLines = 4,
                            enabled = canSend,
                            shape = RoundedCornerShape(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                val text = inputText.trim()
                                if (text.isNotEmpty() && canSend) {
                                    onSendMessage(text)
                                    inputText = ""
                                }
                            },
                            enabled = canSend
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, "发送",
                                 tint = if (canSend) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                        }
                    }
                }
            } else {
                // 已销毁提示
                Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Info, null,
                             tint = MaterialTheme.colorScheme.onSurfaceVariant,
                             modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("该会话已销毁，服务端依法保留记录",
                             style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = doExport) {
                            Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("导出")
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (isDestroyed) Icons.Default.LockOpen else Icons.Outlined.Lock,
                        null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isDestroyed) 0.3f else 0.4f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (isDestroyed) "密信已销毁 · 无本地记录"
                        else "端到端加密 · 到期自动销毁",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    SecretMessageBubble(msg, isDestroyed)
                }
            }
        }
    }
}

data class SecretMessage(
    val id: String,
    val content: String,
    val isMine: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@Composable
private fun SecretMessageBubble(msg: SecretMessage, isDestroyed: Boolean = false) {
    val alpha = if (isDestroyed) 0.4f else 1.0f
    val color = if (msg.isMine)
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = alpha)
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (msg.isMine) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (msg.isMine) 16.dp else 4.dp,
                bottomEnd = if (msg.isMine) 4.dp else 16.dp
            ),
            color = color
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isDestroyed) Icons.Default.LockOpen else Icons.Default.Lock,
                        null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isDestroyed) 0.3f else 0.5f)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        msg.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                    )
                }
                if (isDestroyed) {
                    Text(
                        "已销毁",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.align(Alignment.End)
                    )
                } else {
                    Text(
                        text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }
}
