package com.wuming.quantumlink

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.wuming.quantumlink.core.Constants
import com.wuming.quantumlink.core.IMManager
import com.wuming.quantumlink.core.ServiceLocator
import com.wuming.quantumlink.domain.model.*
import com.wuming.quantumlink.ui.forum.ForumScreen
import com.wuming.quantumlink.ui.im.ChatScreen
import com.wuming.quantumlink.ui.im.ContactsScreen
import com.wuming.quantumlink.ui.im.ConversationListScreen
import com.wuming.quantumlink.ui.secretchat.SecretChatDetailScreen
import com.wuming.quantumlink.ui.secretchat.SecretChatListScreen
import com.wuming.quantumlink.data.remote.api.ApiClient
import com.wuming.quantumlink.manager.ConnectionManager
import com.wuming.quantumlink.manager.LoginManager
import com.wuming.quantumlink.ui.auth.AuthSuccess
import com.wuming.quantumlink.ui.auth.LoginScreen
import com.wuming.quantumlink.ui.secretchat.SecretMessage
import com.wuming.quantumlink.ui.settings.SettingsScreen
import com.wuming.quantumlink.ui.theme.QuantumLinkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 量子飞信 — IM + VPN + 论坛 轻量级通信应用
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "量子飞信"
    }

    private val imManager = IMManager.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "量子飞信启动")

        // 加载保存的服务器配置
        loadServerConfig()
        // 初始化登录状态管理器
        LoginManager.init(this)
        // 初始化连接管理器（前后台切换自动重连）
        ConnectionManager.init(applicationContext)

        setContent {
            QuantumLinkTheme {
                // 检查是否有已保存的登录信息
                val savedLogin = remember { LoginManager.getSavedLogin() }
                var authInfo by remember { mutableStateOf(savedLogin) }

                if (authInfo == null) {
                    LoginScreen(onLoginSuccess = { info ->
                        // 保存登录信息
                        LoginManager.saveLogin(info)
                        authInfo = info
                        // 连接 WebSocket（同时发送 AUTH 帧）
                        imManager.connect(
                            Constants.Server.host,
                            Constants.Server.port,
                            info.userId
                        )
                    })
                } else {
                    // 确保 token 已设置
                    LaunchedEffect(authInfo) {
                        ApiClient.token = authInfo!!.token
                        // 如果 WebSocket 未连接，重新连接
                        if (!imManager.isConnected) {
                            imManager.connect(
                                Constants.Server.host,
                                Constants.Server.port,
                                authInfo!!.userId
                            )
                        }
                    }
                    QuantumLinkApp(
                        imManager = imManager,
                        authInfo = authInfo!!,
                        onAuthExpired = { authInfo = null }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ServiceLocator.destroy()
    }

    /** 从 SharedPreferences 加载服务器配置 */
    private fun loadServerConfig() {
        val prefs = getSharedPreferences("quantumlink_prefs", MODE_PRIVATE)
        // 清除旧的端口配置，统一使用默认值
        prefs.edit().remove("server_port").apply()
        val host = prefs.getString("server_host", Constants.Server.host) ?: Constants.Server.host
        Constants.Server.host = host
        Constants.Server.port = 443 // Cloudflare 默认端口
        Log.d(TAG, "加载服务器配置: $host:443 (Cloudflare)")
    }
}

// ========== 底部导航定义 ==========

private data class NavItem(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val navItems = listOf(
    NavItem("消息", Icons.AutoMirrored.Filled.Chat, Icons.Outlined.ChatBubbleOutline),
    NavItem("通讯录", Icons.Filled.Contacts, Icons.Outlined.Contacts),
    NavItem("密信", Icons.Filled.Lock, Icons.Outlined.Lock),
    NavItem("论坛", Icons.Filled.Forum, Icons.Outlined.Forum),
    NavItem("设置", Icons.Filled.Settings, Icons.Outlined.Settings)
)

// ========== 主界面 ==========

@Composable
fun QuantumLinkApp(imManager: IMManager, authInfo: AuthSuccess, onAuthExpired: () -> Unit = {}) {
    val pagerState = rememberPagerState(pageCount = { navItems.size })
    val coroutineScope = rememberCoroutineScope()

    // 用 derivedStateOf 追踪当前页面，确保 BackHandler 条件准确响应
    val currentPage by remember { derivedStateOf { pagerState.currentPage } }

    // 昵称可变状态，编辑资料后立即刷新界面
    var currentNickname by remember { mutableStateOf(authInfo.nickname) }

    // 设置 API token
    LaunchedEffect(authInfo) {
        ApiClient.token = authInfo.token
    }

    // 监听 WebSocket 事件
    LaunchedEffect(Unit) {
        imManager.messageEvents.collect { event ->
            when (event) {
                is com.wuming.quantumlink.data.remote.websocket.MessageEvent.MessageReceived -> {
                    // 收到新消息，追加到对应会话
                    Log.d("WS", "收到消息: ${event.message.content}")
                }
                is com.wuming.quantumlink.data.remote.websocket.MessageEvent.Connected -> {
                    Log.d("WS", "WebSocket 已连接")
                }
                is com.wuming.quantumlink.data.remote.websocket.MessageEvent.Disconnected -> {
                    Log.d("WS", "WebSocket 已断开")
                }
                is com.wuming.quantumlink.data.remote.websocket.MessageEvent.SecretDestroy -> {
                    Log.d("WS", "密信销毁: ${event.conversationId}")
                }
                else -> {}
            }
        }
    }

    // ── 从 API 加载真实数据 ──
    var conversations by remember { mutableStateOf(listOf<Conversation>()) }
    var contacts by remember { mutableStateOf(listOf<Contact>()) }
    var forumPosts by remember { mutableStateOf(listOf<ForumPost>()) }
    var isLoading by remember { mutableStateOf(true) }

    // 首次加载数据
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                // 加载联系人
                val contactResult = ApiClient.get("/contacts")
                if (contactResult is com.wuming.quantumlink.data.remote.api.ApiResult.Success) {
                    val arr = contactResult.jsonArray()
                    contacts = (0 until arr.length()).map { i ->
                        val j = arr.getJSONObject(i)
                        Contact(
                            id = j.optString("id", ""),
                            name = j.optString("nickname", j.optString("username", "")),
                            status = ContactStatus.ONLINE
                        )
                    }
                }
                // 加载会话列表
                val convResult = ApiClient.get("/conversations")
                if (convResult is com.wuming.quantumlink.data.remote.api.ApiResult.Success) {
                    val arr = convResult.jsonArray()
                    conversations = (0 until arr.length()).map { i ->
                        val j = arr.getJSONObject(i)
                        Conversation(
                            id = j.optLong("id", i.toLong()),
                            name = j.optString("name", "会话"),
                            lastMessage = "",
                            lastMessageTime = j.optLong("created_at", 0) * 1000
                        )
                    }
                }
                // 加载论坛帖子
                val postResult = ApiClient.get("/posts")
                if (postResult is com.wuming.quantumlink.data.remote.api.ApiResult.Success) {
                    val data = postResult.json()
                    val arr = data.optJSONArray("posts")
                    if (arr != null) {
                        forumPosts = (0 until arr.length()).map { i ->
                            val j = arr.getJSONObject(i)
                            ForumPost(
                                id = j.optLong("id", i.toLong()),
                                title = j.optString("title", ""),
                                authorName = j.optString("author_id", ""),
                                content = j.optString("content", ""),
                                timestamp = j.optLong("created_at", 0) * 1000,
                                replyCount = j.optInt("reply_count", 0),
                                likeCount = j.optInt("like_count", 0),
                                isPinned = j.optInt("is_pinned", 0) == 1
                            )
                        }
                    }
                }
            } catch (_: Exception) { }
        }
        isLoading = false
    }

    var vpnConfig by remember { mutableStateOf(VpnConfig(isEnabled = true)) }

    // ── 普通聊天 ──
    var selectedConversation by remember { mutableStateOf<Conversation?>(null) }
    var chatMessages by remember { mutableStateOf(listOf<Message>()) }

    if (selectedConversation != null) {
        // 系统返回键 → 返回会话列表
        BackHandler(enabled = true) {
            selectedConversation = null
            chatMessages = emptyList()
        }
        ChatScreen(
            conversationName = selectedConversation!!.name,
            messages = chatMessages,
            onBack = {
                selectedConversation = null
                chatMessages = emptyList()
            },
            onSendMessage = { text ->
                val msg = Message(
                    id = chatMessages.size.toLong() + 1,
                    conversationId = selectedConversation!!.id,
                    content = text, isMine = true,
                    timestamp = System.currentTimeMillis(),
                    status = MessageStatus.SENT
                )
                chatMessages = chatMessages + msg
            }
        )
        return
    }

    // ── 密信 ──
    var secretSessions by remember {
        mutableStateOf(listOf(
            SecretChatSession(id = "s1", peerName = "匿名用户A", lastMessage = "你好，收到请回复", createdAt = System.currentTimeMillis() - 120_000),
            SecretChatSession(id = "s2", peerName = "匿名用户B", lastMessage = "文件已加密传输"),
        ))
    }
    var selectedSecretSession by remember { mutableStateOf<SecretChatSession?>(null) }
    var secretMessages by remember { mutableStateOf(listOf<SecretMessage>()) }
    var nextSecretId by remember { mutableStateOf(3) }

    if (selectedSecretSession != null) {
        // 系统返回键 → 返回密信列表
        BackHandler(enabled = true) {
            selectedSecretSession?.let { s ->
                secretSessions = secretSessions.map {
                    if (it.id == s.id) it.copy(lastMessageAt = System.currentTimeMillis())
                    else it
                }
            }
            selectedSecretSession = null
            secretMessages = emptyList()
        }
        SecretChatDetailScreen(
            session = selectedSecretSession!!,
            vpnConfig = vpnConfig,
            messages = secretMessages,
            onBack = {
                // 更新最后消息时间
                selectedSecretSession?.let { s ->
                    secretSessions = secretSessions.map {
                        if (it.id == s.id) it.copy(lastMessageAt = System.currentTimeMillis())
                        else it
                    }
                }
                selectedSecretSession = null
                secretMessages = emptyList()
            },
            onSendMessage = { text ->
                val msg = SecretMessage(
                    id = "sm_${secretMessages.size}",
                    content = text, isMine = true,
                    timestamp = System.currentTimeMillis()
                )
                secretMessages = secretMessages + msg
                selectedSecretSession?.let { s ->
                    secretSessions = secretSessions.map {
                        if (it.id == s.id) it.copy(
                            lastMessageAt = System.currentTimeMillis(),
                            lastMessage = text
                        ) else it
                    }
                }
            }
        )
        return
    }

    // 返回键：非首页→回首页；首页→退出应用
    BackHandler(currentPage != 0) {
        coroutineScope.launch {
            pagerState.animateScrollToPage(0)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                navItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = pagerState.currentPage == index,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                        icon = {
                            Icon(
                                imageVector = if (pagerState.currentPage == index) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.title
                            )
                        },
                        label = { Text(item.title) }
                    )
                }
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) { page ->
            when (page) {
                0 -> ConversationListScreen(
                    conversations = conversations,
                    onConversationClick = {
                        selectedConversation = it
                    },
                    onNewChat = { /* TODO: 新建会话 */ }
                )
                1 -> ContactsScreen(
                    contacts = contacts,
                    onContactClick = { /* TODO: 打开聊天 */ },
                    onAddContact = { uid ->
                        coroutineScope.launch(Dispatchers.IO) {
                            // 先调用 API 添加联系人
                            ApiClient.post("/contacts", org.json.JSONObject().apply {
                                put("contact_id", uid)
                            })
                            // 重新加载联系人列表
                            val result = ApiClient.get("/contacts")
                            if (result is com.wuming.quantumlink.data.remote.api.ApiResult.Success) {
                                val arr = result.jsonArray()
                                val newContacts = (0 until arr.length()).map { i ->
                                    val j = arr.getJSONObject(i)
                                    Contact(
                                        id = j.optString("id", ""),
                                        name = j.optString("nickname", j.optString("username", "")),
                                        status = ContactStatus.ONLINE
                                    )
                                }
                                withContext(Dispatchers.Main) { contacts = newContacts }
                            }
                        }
                    }
                )
                2 -> SecretChatListScreen(
                    sessions = secretSessions,
                    vpnConfig = vpnConfig,
                    onCreateSession = {
                        val newId = "s${nextSecretId++}"
                        secretSessions = secretSessions + SecretChatSession(
                            id = newId,
                            peerName = "匿名用户${nextSecretId}",
                            createdAt = System.currentTimeMillis()
                        )
                    },
                    onSessionClick = { selectedSecretSession = it },
                    onExpiredSessions = { ids ->
                        secretSessions = secretSessions.filter { it.id !in ids }
                    }
                )
                3 -> ForumScreen(
                    posts = forumPosts,
                    onPostClick = { /* TODO: 查看帖子详情 */ },
                    onCreatePost = { /* TODO: 发帖 */ },
                    onRefresh = { /* TODO: 刷新 */ }
                )
                4 -> SettingsScreen(
                    authInfo = authInfo.copy(nickname = currentNickname),
                    vpnConfig = vpnConfig,
                    onVpnToggle = {
                        vpnConfig = vpnConfig.copy(
                            isConnected = !vpnConfig.isConnected,
                            serverAddress = if (vpnConfig.serverAddress.isNotEmpty()) vpnConfig.serverAddress else "wg.example.com:51820"
                        )
                    },
                    onVpnConfigChange = { vpnConfig = it },
                    onProfileUpdated = { newNickname ->
                        currentNickname = newNickname
                    },
                    onLogout = onAuthExpired
                )
            }
        }
    }
}
