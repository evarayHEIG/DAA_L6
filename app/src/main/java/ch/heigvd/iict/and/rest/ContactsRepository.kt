package ch.heigvd.iict.and.rest

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import ch.heigvd.iict.and.rest.database.ContactsDao
import ch.heigvd.iict.and.rest.models.Contact
import ch.heigvd.iict.and.rest.models.ContactState
import ch.heigvd.iict.and.rest.utils.APIRequest
import com.android.volley.toolbox.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ContactsRepository(private val contactsDao: ContactsDao, private val context: Context) {

    private val baseUrl = "https://daa.iict.ch"
    private val queue = Volley.newRequestQueue(context)
    private val prefs: SharedPreferences =
        context.getSharedPreferences("contacts_prefs", Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    val allContacts: LiveData<List<Contact>> = contactsDao.getAllContactsLiveData()

    private var uuid: String?
        get() = prefs.getString("uuid", null)
        set(value) = prefs.edit().putString("uuid", value).apply()

    suspend fun deleteAllContacts() = withContext(Dispatchers.IO) {
        contactsDao.clearAllContacts()
        uuid = null
    }

    /**
     * Registers the application with the server and fetches the contacts.
     * This method should be called at application startup.
     */
    suspend fun enrollAndFetchContacts() = withContext(Dispatchers.IO) {
        // Obtain a new UUID
        val newUuid = suspendCancellableCoroutine { continuation ->
            APIRequest.enroll(
                baseUrl,
                queue,
                onSuccess = continuation::resume,
                onError = { error -> continuation.resumeWithException(Exception(error)) }
            )
        }

        uuid = newUuid

        // Retrieve contacts from the server
        val contacts = suspendCancellableCoroutine { continuation ->
            APIRequest.getAllContacts(
                baseUrl,
                newUuid,
                queue,
                onSuccess = { jsonArray ->
                    val contactsString = jsonArray.toString()
                    val contacts = json.decodeFromString<List<Contact>>(contactsString)
                    continuation.resume(contacts)
                },
                onError = { error -> continuation.resumeWithException(Exception(error)) }
            )
        }

        // Update the local database with the fetched contacts
        contactsDao.clearAllContacts()
        contacts.forEach { contact ->
            contact.apply {
                remoteId = id
                state = ContactState.SYNCED
            }
            contactsDao.insert(contact)
        }
    }

    /**
     * Inserts a new contact into the database and tries to insert it on the server.
     * If the server insertion fails, the contact is marked as CREATED.
     * If the contact already exists in the database and the call succeeds, it is updated.
     * @param contact The contact to insert
     * @throws Exception If no UUID is available, or if an error occurs during the insertion
     */
    suspend fun insert(contact: Contact) = withContext(Dispatchers.IO) {
        val currentUuid = uuid ?: throw Exception("No UUID available")
        val jsonContact = json.encodeToString(Contact.serializer(), contact)

        try {
            val jsonResponse = suspendCancellableCoroutine { continuation ->
                APIRequest.addContact(
                    baseUrl,
                    currentUuid,
                    JSONObject(jsonContact),
                    queue,
                    onSuccess = continuation::resume,
                    onError = { error -> continuation.resumeWithException(Exception(error)) }
                )
            }
            // The contact was successfully created on the server
            val createdContact =
                json.decodeFromString(Contact.serializer(), jsonResponse.toString()).apply {
                    remoteId = id
                    state = ContactState.SYNCED
                }
            if (contact.id != null) {
                // If the contact already exists in the database, we update it
                contactsDao.update(createdContact)
            } else {
                // Otherwise we insert it in the database
                contactsDao.insert(createdContact)
            }
        } catch (e: Exception) {
            // Mark the contact as CREATED if an error occurred during insertion
            contact.state = ContactState.CREATED
            contactsDao.insert(contact)
        }
    }

    /**
     * Updates a contact.
     * If the update on the server fails, the contact is marked as UPDATED.
     * @param contact The contact to update
     * @throws Exception If no UUID is available, or if an error occurs during the update
     */
    suspend fun update(contact: Contact) = withContext(Dispatchers.IO) {
        val currentUuid = uuid ?: throw Exception("No UUID available")
        var contactToUpdate = contact

        try {
            if (contact.remoteId != null) {
                val jsonContact = json.encodeToString(Contact.serializer(), contact)
                val jsonResponse = suspendCancellableCoroutine<JSONObject> { continuation ->
                    APIRequest.updateContact(
                        baseUrl,
                        currentUuid,
                        contact.remoteId!!.toInt(),
                        JSONObject(jsonContact),
                        queue,
                        onSuccess = continuation::resume,
                        onError = { error -> continuation.resumeWithException(Exception(error)) }
                    )
                }
                val updatedContact =
                    json.decodeFromString(Contact.serializer(), jsonResponse.toString()).apply {
                        remoteId = this.id
                        state = ContactState.SYNCED
                    }
                contactToUpdate = updatedContact
            } else {
                // If the contact is not on the server and not marked as CREATED, mark it as UPDATED
                if (contact.state != ContactState.CREATED) {
                    // We are updating it locally, so we mark it as UPDATED
                    contact.state = ContactState.UPDATED
                }
            }
        } catch (e: Exception) {
            // Switch the contact to UPDATED if it was previously SYNCED
            if (contact.state == ContactState.SYNCED) {
                contact.state = ContactState.UPDATED
            }
        }
        contactsDao.update(contactToUpdate)
    }

    /**
     * Deletes a contact.
     * If the deletion on the server fails, the contact is marked as DELETED locally.
     * @param contact The contact to delete
     * @throws Exception If no UUID is available, or if an error occurs during the deletion
     */
    suspend fun delete(contact: Contact) = withContext(Dispatchers.IO) {
        val currentUuid = uuid ?: throw Exception("No UUID available")

        try {
            if (contact.remoteId != null) {
                try {
                    suspendCancellableCoroutine { continuation ->
                        APIRequest.deleteContact(
                            baseUrl,
                            currentUuid,
                            contact.remoteId!!.toInt(),
                            queue,
                            onSuccess = { continuation.resume(Unit) },
                            onError = { error -> continuation.resumeWithException(Exception(error)) }
                        )
                    }
                    contactsDao.delete(contact)
                } catch (e: Exception) {
                    contact.state = ContactState.DELETED
                    contactsDao.update(contact)
                }
            } else {
                // If the contact is not on the server yet, we can delete locally
                when (contact.state) {
                    // If the contact was only created locally, we can delete it directly
                    ContactState.CREATED -> contactsDao.delete(contact)
                    // If the contact was updated locally, we mark it as DELETED but keep it in the database until it is synchronized
                    else -> {
                        contact.state = ContactState.DELETED
                        contactsDao.update(contact)
                    }
                }
            }
        } catch (e: Exception) {
            contact.state = ContactState.DELETED
            contactsDao.update(contact)
        }
    }

    /**
     * Synchronizes all unsynchronized contacts with the server.
     */
    suspend fun synchronizeAllContacts() = withContext(Dispatchers.IO) {
        val currentUuid = uuid ?: throw Exception("No UUID available")
        val unsyncedContacts = contactsDao.getAllContacts().filter {
            it.state != ContactState.SYNCED
        }

        unsyncedContacts.forEach { contact ->
            try {
                when (contact.state) {
                    ContactState.CREATED -> insert(contact)
                    ContactState.UPDATED -> update(contact)
                    ContactState.DELETED -> delete(contact)
                    else -> { /* Ignore SYNCED contacts */
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
