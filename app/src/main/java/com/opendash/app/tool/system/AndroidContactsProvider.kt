package com.opendash.app.tool.system

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import timber.log.Timber

/**
 * Android implementation of ContactsProvider using ContactsContract.
 */
class AndroidContactsProvider(
    private val context: Context
) : ContactsProvider {

    override suspend fun search(query: String, limit: Int): List<ContactInfo> {
        if (!hasPermission()) return emptyList()
        return queryContacts(
            selection = "${ContactsContract.Contacts.DISPLAY_NAME} LIKE ?",
            selectionArgs = arrayOf("%$query%"),
            limit = limit
        )
    }

    override suspend fun listAll(limit: Int): List<ContactInfo> {
        if (!hasPermission()) return emptyList()
        return queryContacts(selection = null, selectionArgs = null, limit = limit)
    }

    override fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun hasWritePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    override suspend fun addContact(
        displayName: String,
        phoneNumber: String?,
        email: String?
    ): Long? {
        if (!hasWritePermission()) return null
        if (displayName.isBlank()) return null

        val ops = ArrayList<ContentProviderOperation>()
        // Local-only RawContact (account_type=null) — sync to a Google account
        // can be done later by the user.
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build()
        )
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                )
                .withValue(
                    ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                    displayName.trim()
                )
                .build()
        )
        phoneNumber?.takeIf { it.isNotBlank() }?.let { number ->
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                    )
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number.trim())
                    .withValue(
                        ContactsContract.CommonDataKinds.Phone.TYPE,
                        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                    )
                    .build()
            )
        }
        email?.takeIf { it.isNotBlank() }?.let { addr ->
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE
                    )
                    .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, addr.trim())
                    .withValue(
                        ContactsContract.CommonDataKinds.Email.TYPE,
                        ContactsContract.CommonDataKinds.Email.TYPE_HOME
                    )
                    .build()
            )
        }

        return try {
            val results = context.contentResolver.applyBatch(
                ContactsContract.AUTHORITY, ops
            )
            results.firstOrNull()?.uri?.let { ContentUris.parseId(it) }
        } catch (e: SecurityException) {
            Timber.w(e, "Contact write blocked")
            null
        } catch (e: Exception) {
            Timber.e(e, "Failed to insert contact")
            null
        }
    }

    private fun queryContacts(
        selection: String?,
        selectionArgs: Array<String>?,
        limit: Int
    ): List<ContactInfo> {
        val contacts = mutableListOf<ContactInfo>()
        val resolver = context.contentResolver

        try {
            resolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME,
                    ContactsContract.Contacts.HAS_PHONE_NUMBER
                ),
                selection,
                selectionArgs,
                "${ContactsContract.Contacts.DISPLAY_NAME} ASC LIMIT ${limit.coerceIn(1, 200)}"
            )?.use { cursor ->
                while (cursor.moveToNext() && contacts.size < limit) {
                    val id = cursor.getString(0) ?: continue
                    val name = cursor.getString(1).orEmpty()
                    val hasPhone = cursor.getInt(2) == 1

                    val phones = if (hasPhone) fetchPhones(id) else emptyList()
                    val emails = fetchEmails(id)

                    contacts.add(ContactInfo(id, name, phones, emails))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to query contacts")
        }

        return contacts
    }

    private fun fetchPhones(contactId: String): List<String> {
        val phones = mutableListOf<String>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                cursor.getString(0)?.let { phones.add(it) }
            }
        }
        return phones.distinct()
    }

    private fun fetchEmails(contactId: String): List<String> {
        val emails = mutableListOf<String>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS),
            "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                cursor.getString(0)?.let { emails.add(it) }
            }
        }
        return emails.distinct()
    }
}
