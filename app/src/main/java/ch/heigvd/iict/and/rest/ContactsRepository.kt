package ch.heigvd.iict.and.rest

import ch.heigvd.iict.and.rest.adapters.CalendarAdapter
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.LiveData
import ch.heigvd.iict.and.rest.database.ContactsDao
import ch.heigvd.iict.and.rest.models.Contact
import ch.heigvd.iict.and.rest.models.ContactState
import com.android.volley.toolbox.*
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ContactsRepository(private val contactsDao: ContactsDao, private val context: Context) {

    private val baseUrl = "https://daa.iict.ch"
    private val queue = Volley.newRequestQueue(context)
    private val prefs: SharedPreferences = context.getSharedPreferences("contacts_prefs", Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }


    val allContacts: LiveData<List<Contact>> = contactsDao.getAllContactsLiveData()

    private var uuid: String?
        get() = prefs.getString("uuid", null)
        set(value) = prefs.edit().putString("uuid", value).apply()

    // Méthodes de gestion de la base de données locale
    suspend fun deleteAllContacts() = withContext(Dispatchers.IO) {
        contactsDao.clearAllContacts()
        uuid = null
    }

    suspend fun enrollAndFetchContacts() = withContext(Dispatchers.IO) {
        val newUuid = suspendCancellableCoroutine<String> { continuation ->
            enroll(
                onSuccess = continuation::resume,
                onError = { error -> continuation.resumeWithException(Exception(error)) }
            )
        }

        uuid = newUuid

        val contacts = suspendCancellableCoroutine<List<Contact>> { continuation ->
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

        contactsDao.clearAllContacts()
        contacts.forEach { contact ->
            contact.apply {
                remoteId = id
                state = ContactState.SYNCED
            }
            contactsDao.insert(contact)
        }
    }

    suspend fun insert(contact: Contact) = withContext(Dispatchers.IO) {
        val currentUuid = uuid ?: throw Exception("No UUID available")
        val jsonContact = json.encodeToString(contact)

        try {
            val jsonResponse = suspendCancellableCoroutine<JSONObject> { continuation ->
                addContact(
                    currentUuid,
                    JSONObject(jsonContact),
                    onSuccess = continuation::resume,
                    onError = { error -> continuation.resumeWithException(Exception(error)) }
                )
            }
            val createdContact = json.decodeFromString<Contact>(jsonResponse.toString()).apply {
                remoteId = id
                state = ContactState.SYNCED
            }
            contactsDao.insert(createdContact)
        } catch (e: Exception) {
            contact.state = ContactState.CREATED
            contactsDao.insert(contact)
        }
    }

    suspend fun update(contact: Contact) = withContext(Dispatchers.IO) {
        val currentUuid = uuid ?: throw Exception("No UUID available")
        val jsonContact = json.encodeToString(contact)
        Log.d("ContactsRepository", "JSON à envoyer pour la mise à jour: $jsonContact")

        try {
            if (contact.remoteId != null) {
                val jsonResponse = suspendCancellableCoroutine<JSONObject> { continuation ->
                    updateContact(
                        currentUuid,
                        contact.remoteId!!.toInt(),
                        JSONObject(jsonContact),
                        onSuccess = continuation::resume,
                        onError = { error -> continuation.resumeWithException(Exception(error)) }
                    )
                }
                val updatedContact = json.decodeFromString<Contact>(jsonResponse.toString()).apply {
                    state = ContactState.SYNCED
                }
                contactsDao.update(updatedContact)
            } else {
                contact.state = ContactState.UPDATED
                contactsDao.update(contact)
            }
        } catch (e: Exception) {
            if (contact.state == ContactState.SYNCED) {
                contact.state = ContactState.UPDATED
            }
            contactsDao.update(contact)
        }
    }

    suspend fun delete(contact: Contact) = withContext(Dispatchers.IO) {
        val currentUuid = uuid ?: throw Exception("No UUID available")

        try {
            if (contact.remoteId != null) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    deleteContact(
                        currentUuid,
                        contact.remoteId!!.toInt(),
                        onSuccess = { continuation.resume(Unit) },
                        onError = { error -> continuation.resumeWithException(Exception(error)) }
                    )
                }
                contactsDao.delete(contact)
            } else {
                contactsDao.delete(contact)
            }
        } catch (e: Exception) {
            contactsDao.delete(contact)
        }
    }

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

    // ENROLL - Création d'un nouveau jeu de données et attribution d'un token
    // GET https://daa.iict.ch/enroll
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

    // CONTACTS - Obtenir tous les contacts
    // GET https://daa.iict.ch/contacts
    private fun getAllContacts(uuid: String, onSuccess: (JSONArray) -> Unit, onError: (String) -> Unit) {
        val url = "$baseUrl/contacts"
        val request = object : JsonArrayRequest(
            Method.GET, url, null,
            { response -> onSuccess(response) },
            { error -> onError(error.message ?: "Unknown error") }
        ) {
            override fun getHeaders(): MutableMap<String, String> = mutableMapOf(
                "X-UUID" to uuid
            )
        }
        queue.add(request)
    }

    // CONTACTS - Obtenir un contact spécifique
    // GET https://daa.iict.ch/contacts/34
    private fun getContact(uuid: String, contactId: Int, onSuccess: (JSONObject) -> Unit, onError: (String) -> Unit) {
        val url = "$baseUrl/contacts/$contactId"
        val request = object : JsonObjectRequest(
            Method.GET, url, null,
            { response -> onSuccess(response) },
            { error -> onError(error.message ?: "Unknown error") }
        ) {
            override fun getHeaders(): MutableMap<String, String> = mutableMapOf(
                "X-UUID" to uuid
            )
        }
        queue.add(request)
    }

    // CONTACTS - Créer un nouveau contact
    // POST https://daa.iict.ch/contacts/
    private fun addContact(uuid: String, contact: JSONObject, onSuccess: (JSONObject) -> Unit, onError: (String) -> Unit) {
        val url = "$baseUrl/contacts"
        val request = object : JsonObjectRequest(
            Method.POST, url, contact,
            { response -> onSuccess(response) },
            { error -> onError(error.message ?: "Unknown error") }
        ) {
            override fun getHeaders(): MutableMap<String, String> = mutableMapOf(
                "X-UUID" to uuid,
                "Content-Type" to "application/json"
            )
        }
        queue.add(request)
    }

    // CONTACTS - Modifier un contact
    // PUT https://daa.iict.ch/contacts/34
    private fun updateContact(uuid: String, contactId: Int, contact: JSONObject, onSuccess: (JSONObject) -> Unit, onError: (String) -> Unit) {
        val url = "$baseUrl/contacts/$contactId"
        val request = object : JsonObjectRequest(
            Method.PUT, url, contact,
            { response -> onSuccess(response) },
            { error -> onError(error.message ?: "Unknown error") }
        ) {
            override fun getHeaders(): MutableMap<String, String> = mutableMapOf(
                "X-UUID" to uuid,
                "Content-Type" to "application/json"
            )
        }
        queue.add(request)
    }

    // CONTACTS - Supprimer un contact
    // DELETE https://daa.iict.ch/contacts/34
    private fun deleteContact(uuid: String, contactId: Int, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val url = "$baseUrl/contacts/$contactId"
        val request = object : StringRequest(
            Method.DELETE, url,
            { onSuccess() },
            { error -> onError(error.message ?: "Unknown error") }
        ) {
            override fun getHeaders(): MutableMap<String, String> = mutableMapOf(
                "X-UUID" to uuid
            )
        }
        queue.add(request)
    }
}