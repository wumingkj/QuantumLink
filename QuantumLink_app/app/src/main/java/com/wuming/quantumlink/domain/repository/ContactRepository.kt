package com.wuming.quantumlink.domain.repository

import com.wuming.quantumlink.domain.model.Contact
import kotlinx.coroutines.flow.Flow

interface ContactRepository {
    fun getAllContacts(): Flow<List<Contact>>
    fun searchContacts(query: String): Flow<List<Contact>>
    suspend fun addContact(contact: Contact)
    suspend fun updateStatus(id: String, isOnline: Boolean)
}
