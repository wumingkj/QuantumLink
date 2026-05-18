package com.wuming.quantumlink.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.wuming.quantumlink.domain.model.Contact
import com.wuming.quantumlink.domain.model.ContactStatus

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = "",

    @ColumnInfo(name = "name")
    val name: String = "",

    @ColumnInfo(name = "avatar")
    val avatar: String = "",

    @ColumnInfo(name = "status")
    val status: Int = ContactStatus.OFFLINE.ordinal,

    @ColumnInfo(name = "last_seen")
    val lastSeen: Long = 0
) {
    fun toDomain(): Contact = Contact(
        id = id, name = name, avatar = avatar,
        status = ContactStatus.entries.getOrElse(status) { ContactStatus.OFFLINE },
        lastSeen = lastSeen
    )

    companion object {
        fun fromDomain(c: Contact): ContactEntity = ContactEntity(
            id = c.id, name = c.name, avatar = c.avatar,
            status = c.status.ordinal, lastSeen = c.lastSeen
        )
    }
}
