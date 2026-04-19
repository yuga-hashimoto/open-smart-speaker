package com.opendash.app.tool.system

/**
 * Reads contacts from the device. Requires READ_CONTACTS.
 * Inspired by OpenClaw's contacts.* Android node commands.
 */
interface ContactsProvider {
    suspend fun search(query: String, limit: Int = 10): List<ContactInfo>
    suspend fun listAll(limit: Int = 50): List<ContactInfo>
    fun hasPermission(): Boolean

    /** True if WRITE_CONTACTS is granted. */
    fun hasWritePermission(): Boolean = false

    /**
     * Insert a new contact in the device's local account.
     * Returns the raw_contact id on success, or null on failure / missing permission.
     */
    suspend fun addContact(
        displayName: String,
        phoneNumber: String? = null,
        email: String? = null
    ): Long? = null
}

data class ContactInfo(
    val id: String,
    val displayName: String,
    val phoneNumbers: List<String>,
    val emails: List<String>
)
