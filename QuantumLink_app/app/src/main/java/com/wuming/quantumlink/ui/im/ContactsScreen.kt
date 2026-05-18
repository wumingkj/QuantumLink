package com.wuming.quantumlink.ui.im

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wuming.quantumlink.data.remote.api.ApiClient
import com.wuming.quantumlink.data.remote.api.ApiResult
import com.wuming.quantumlink.domain.model.Contact
import com.wuming.quantumlink.domain.model.ContactStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ContactsScreen(
    contacts: List<Contact>,
    onContactClick: (Contact) -> Unit,
    onAddContact: ((String) -> Unit)? = null
) {
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResult by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        // ── 顶部搜索栏 ──
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = if (showSearch) 2.dp else 0.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showSearch) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it; searchResult = "" },
                        placeholder = { Text("输入 UID 或用户名添加好友") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            IconButton(onClick = { showSearch = false; searchQuery = ""; searchResult = "" }) {
                                Icon(Icons.Default.Close, null)
                            }
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (searchQuery.isBlank()) return@Button
                            isSearching = true
                            scope.launch(Dispatchers.IO) {
                                val result = ApiClient.get("/users/search?q=$searchQuery")
                                withContext(Dispatchers.Main) {
                                    isSearching = false
                                    when (result) {
                                        is ApiResult.Success -> {
                                            val arr = result.jsonArray()
                                            if (arr.length() > 0) {
                                                val user = arr.getJSONObject(0)
                                                val uid = user.optString("id", "")
                                                val name = user.optString("nickname", user.optString("username", ""))
                                                searchResult = "找到: $name (${uid.take(8)}...)"
                                                // 自动添加联系人
                                                if (onAddContact != null) {
                                                    onAddContact(uid)
                                                    searchResult = "✅ 已添加: $name"
                                                }
                                            } else {
                                                searchResult = "❌ 未找到用户"
                                            }
                                        }
                                        is ApiResult.Error -> searchResult = "❌ ${result.errorMessage()}"
                                        is ApiResult.Failure -> searchResult = "❌ ${result.message}"
                                    }
                                }
                            }
                        },
                        enabled = !isSearching
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("添加")
                        }
                    }
                } else {
                    Text(
                        "通讯录",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showSearch = true }) {
                        Icon(Icons.Default.PersonAdd, "添加好友")
                    }
                }
            }
        }

        // 搜索结果提示
        if (searchResult.isNotEmpty()) {
            Text(
                searchResult,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = if (searchResult.startsWith("✅")) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ── 联系人列表 ──
        if (contacts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Contacts, null, modifier = Modifier.size(48.dp),
                         tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Spacer(Modifier.height(8.dp))
                    Text("暂无联系人", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("点击右上角 + 添加好友", style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(contacts, key = { it.id }) { contact ->
                    ContactItem(contact = contact, onClick = { onContactClick(contact) })
                }
            }
        }
    }
}

@Composable
private fun ContactItem(contact: Contact, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, null, modifier = Modifier.size(26.dp))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    contact.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    when (contact.status) {
                        ContactStatus.ONLINE -> "在线"
                        ContactStatus.AWAY -> "离开"
                        ContactStatus.BUSY -> "忙碌"
                        ContactStatus.OFFLINE -> "离线"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when (contact.status) {
                        ContactStatus.ONLINE -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Surface(
                shape = CircleShape,
                color = when (contact.status) {
                    ContactStatus.ONLINE -> MaterialTheme.colorScheme.primary
                    ContactStatus.AWAY -> MaterialTheme.colorScheme.tertiary
                    ContactStatus.BUSY -> MaterialTheme.colorScheme.error
                    ContactStatus.OFFLINE -> MaterialTheme.colorScheme.outline
                },
                modifier = Modifier.size(10.dp)
            ) {}
        }
    }
}
