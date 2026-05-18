package com.wuming.quantumlink.data.repository

import com.wuming.quantumlink.data.local.dao.ContactDao
import com.wuming.quantumlink.data.local.entity.ContactEntity
import com.wuming.quantumlink.domain.model.Contact
import com.wuming.quantumlink.domain.model.ContactStatus
import com.wuming.quantumlink.domain.repository.ContactRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ContactRepositoryImpl(
    private val contactDao: ContactDao
) : ContactRepository {

    override fun getAllContacts(): Flow<List<Contact>> {
        return contactDao.getAllContacts().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun searchContacts(query: String): Flow<List<Contact>> {
        return contactDao.search(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun addContact(contact: Contact) {
        contactDao.insert(ContactEntity.fromDomain(contact))
    }

    override suspend fun updateStatus(id: String, isOnline: Boolean) {
        val status = if (isOnline) ContactStatus.ONLINE.ordinal else ContactStatus.OFFLINE.ordinal
        contactDao.updateStatus(id, status, System.currentTimeMillis())
    }
}
