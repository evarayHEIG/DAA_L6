package ch.heigvd.iict.and.rest

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import ch.heigvd.iict.and.rest.database.ContactsDao
import ch.heigvd.iict.and.rest.models.Contact
import ch.heigvd.iict.and.rest.models.ContactState
import com.android.volley.toolbox.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ContactsRepository(private val contactsDao: ContactsDao, private val context: Context) {

    private val baseUrl = "https://daa.iict.ch"
    private val queue = Volley.newRequestQueue(context)
    private val prefs: SharedPreferences = context.getSharedPreferences("contacts_prefs", Context.MODE_PRIVATE)
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
     * Enregistre l'application auprès du serveur et récupère les contacts.
     * Cette méthode doit être appelée au démarrage de l'application.
     */
    suspend fun enrollAndFetchContacts() = withContext(Dispatchers.IO) {
        // Obtention d'un nouveau UUID
        val newUuid = suspendCancellableCoroutine { continuation ->
            enroll(
                onSuccess = continuation::resume,
                onError = { error -> continuation.resumeWithException(Exception(error)) }
            )
        }

        uuid = newUuid

        // Récupération des contacts depuis le serveur
        val contacts = suspendCancellableCoroutine { continuation ->
            getAllContacts(
                newUuid,
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
     * Insert a new contact in the database and try to insert it on the server as well.
     * If the insertion on the server fails, the contact is marked as CREATED.
     * If the contact already exists in the database and the call succeeds, it is updated.
     * @param contact The contact to insert
     * @throws Exception If no UUID is available, or if an error occurs during the insertion
     */
    suspend fun insert(contact: Contact) = withContext(Dispatchers.IO) {
        val currentUuid = uuid ?: throw Exception("No UUID available")
        val jsonContact = json.encodeToString(Contact.serializer(), contact)

        try {
            val jsonResponse = suspendCancellableCoroutine { continuation ->
                addContact(
                    currentUuid,
                    JSONObject(jsonContact),
                    onSuccess = continuation::resume,
                    onError = { error -> continuation.resumeWithException(Exception(error)) }
                )
            }
            // If we reach this point, the contact was successfully created on the server
            val createdContact = json.decodeFromString(Contact.serializer(), jsonResponse.toString()).apply {
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
            // If an error occurred during the insertion, we mark the contact as CREATED
            contact.state = ContactState.CREATED
            contactsDao.insert(contact)
        }
    }

    /**
     * Update a contact.
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
                    updateContact(
                        currentUuid,
                        contact.remoteId!!.toInt(),
                        JSONObject(jsonContact),
                        onSuccess = continuation::resume,
                        onError = { error -> continuation.resumeWithException(Exception(error)) }
                    )
                }
                val updatedContact = json.decodeFromString(Contact.serializer(), jsonResponse.toString()).apply {
                    remoteId = this.id
                    state = ContactState.SYNCED
                }
                contactToUpdate = updatedContact
            } else {
                // If the contact is not on the server yet and is not marked as CREATED, we mark it as UPDATED
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
     * Supprime un contact.
     * Si la suppression sur le serveur échoue, le contact est marqué comme DELETED localement.
     */
    suspend fun delete(contact: Contact) = withContext(Dispatchers.IO) {
        val currentUuid = uuid ?: throw Exception("No UUID available")

        try {
            if (contact.remoteId != null) {
                try {
                    suspendCancellableCoroutine { continuation ->
                        deleteContact(
                            currentUuid,
                            contact.remoteId!!.toInt(),
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
            // If the deletion on the server fails, we mark the contact as DELETED
            contact.state = ContactState.DELETED
            contactsDao.update(contact)
        }
    }

    /**
     * Synchronise tous les contacts non synchronisés avec le serveur.
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
                    else -> { /* Ignore SYNCED contacts */ }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Méthodes privées pour les appels API

    private fun enroll(onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        val url = "$baseUrl/enroll"
        val request = object : StringRequest(
            Method.GET, url,
            { response -> onSuccess(response) },
            { error -> onError(error.message ?: "Unknown error") }
        ) {
            override fun getHeaders(): MutableMap<String, String> = mutableMapOf()
        }
        queue.add(request)
    }

    private fun getAllContacts(uuid: String, onSuccess: (JSONArray) -> Unit, onError: (String) -> Unit) {
        val url = "$baseUrl/contacts"
        val request = object : JsonArrayRequest(
            Method.GET, url, null,
            { response -> onSuccess(response) },
            { error -> onError(error.message ?: "Error while fetching contacts") }
        ) {
            override fun getHeaders(): MutableMap<String, String> = mutableMapOf(
                "X-UUID" to uuid
            )
        }
        queue.add(request)
    }

    private fun getContact(uuid: String, contactId: Int, onSuccess: (JSONObject) -> Unit, onError: (String) -> Unit) {
        val url = "$baseUrl/contacts/$contactId"
        val request = object : JsonObjectRequest(
            Method.GET, url, null,
            { response -> onSuccess(response) },
            { error -> onError(error.message ?: "Error fetching contact") }
        ) {
            override fun getHeaders(): MutableMap<String, String> = mutableMapOf(
                "X-UUID" to uuid
            )
        }
        queue.add(request)
    }

    private fun addContact(uuid: String, contact: JSONObject, onSuccess: (JSONObject) -> Unit, onError: (String) -> Unit) {
        val url = "$baseUrl/contacts"
        val request = object : JsonObjectRequest(
            Method.POST, url, contact,
            { response -> onSuccess(response) },
            { error -> onError(error.message ?: "Error adding contact") }
        ) {
            override fun getHeaders(): MutableMap<String, String> = mutableMapOf(
                "X-UUID" to uuid,
                "Content-Type" to "application/json"
            )
        }
        queue.add(request)
    }

    private fun updateContact(uuid: String, contactId: Int, contact: JSONObject, onSuccess: (JSONObject) -> Unit, onError: (String) -> Unit) {
        val url = "$baseUrl/contacts/$contactId"
        val request = object : JsonObjectRequest(
            Method.PUT, url, contact,
            { response -> onSuccess(response) },
            { error -> onError(error.message ?: "Error updating contact") }
        ) {
            override fun getHeaders(): MutableMap<String, String> = mutableMapOf(
                "X-UUID" to uuid,
                "Content-Type" to "application/json"
            )
        }
        queue.add(request)
    }

    private fun deleteContact(uuid: String, contactId: Int, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val url = "$baseUrl/contacts/$contactId"
        val request = object : StringRequest(
            Method.DELETE, url,
            { onSuccess() },
            { error -> onError(error.message ?: "Error deleting contact") }
        ) {
            override fun getHeaders(): MutableMap<String, String> = mutableMapOf(
                "X-UUID" to uuid
            )
        }
        queue.add(request)
    }
}