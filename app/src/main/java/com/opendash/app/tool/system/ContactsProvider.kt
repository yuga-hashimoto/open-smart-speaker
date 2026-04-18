package com.opendash.app.tool.system

/**
 * Reads contacts from the device. Requires READ_CONTACTS.
 * Inspired by OpenClaw's contacts.* Android node commands.
 */
interface ContactsProvider {
    suspend fun search(query: String, limit: Int = 10): List<ContactInfo>
    suspend fun listAll(limit: Int = 50): List<ContactInfo>
    fun hasPermission(): Boolean
}

data class ContactInfo(
    val id: String,
    val displayName: String,
    val phoneNumbers: List<String>,
    val emails: List<String>
)
