package com.wuming.quantumlink.domain.model

/**
 * 联系人
 */
data class Contact(
    val id: String = "",
    val name: String = "",
    val avatar: String = "",
    val status: ContactStatus = ContactStatus.OFFLINE,
    val lastSeen: Long = 0
)

enum class ContactStatus {
    ONLINE, OFFLINE, AWAY, BUSY
}
