package ch.heigvd.iict.and.rest

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import ch.heigvd.iict.and.rest.database.ContactsDao
import ch.heigvd.iict.and.rest.models.Contact
import ch.heigvd.iict.and.rest.models.ContactState
import com.android.volley.Request
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ContactsRepository(private val contactsDao: ContactsDao, private val context: Context) {

    private val baseUrl = "https://daa.iict.ch/"
    private val queue = Volley.newRequestQueue(context)
    private val prefs: SharedPreferences = context.getSharedPreferences("contacts_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    val allContacts: LiveData<List<Contact>> = contactsDao.getAllContactsLiveData()

    private var uuid: String?
        get() = prefs.getString("uuid", null)
        set(value) = prefs.edit().putString("uuid", value).apply()

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
                    val type = object : TypeToken<List<Contact>>() {}.type
                    val contacts = gson.fromJson<List<Contact>>(jsonArray.toString(), type)
                    continuation.resume(contacts)
                },
                onError = { error -> continuation.resumeWithException(Exception(error)) }
            )
        }

        // Insertion des contacts dans la base locale
        contactsDao.clearAllContacts() // Supprime les anciens contacts
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
        val jsonContact = gson.toJson(contact)

        try {
            val jsonResponse = suspendCancellableCoroutine<JSONObject> { continuation ->
                addContact(
                    currentUuid,
                    JSONObject(jsonContact),
                    onSuccess = continuation::resume,
                    onError = { error -> continuation.resumeWithException(Exception(error)) }
                )
            }
            val createdContact = gson.fromJson(jsonResponse.toString(), Contact::class.java).apply {
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
        val jsonContact = gson.toJson(contact)

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
                val updatedContact = gson.fromJson(jsonResponse.toString(), Contact::class.java).apply {
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
                contactsDao.delete(contact) // Suppression locale directe
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

    // Implémentation des fonctions réseau
    private fun enroll(onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        val url = "$baseUrl/enroll"
        val request = StringRequest(
            Request.Method.GET, url,
            { response -> onSuccess(response) },
            { error -> onError(error.message ?: "Unknown error") }
        )
        queue.add(request)
    }

    private fun getAllContacts(uuid: String, onSuccess: (JSONArray) -> Unit, onError: (String) -> Unit) {
        val url = "$baseUrl/contacts"
        val request = JsonArrayRequest(
            Request.Method.GET, url, null,
            { response -> onSuccess(response) },
            { error -> onError(error.message ?: "Unknown error") }
        ).apply {
            headers["X-UUID"] = uuid
        }
        queue.add(request)
    }

    private fun addContact(uuid: String, contact: JSONObject, onSuccess: (JSONObject) -> Unit, onError: (String) -> Unit) {
        val url = "$baseUrl/contacts/"
        val request = JsonObjectRequest(
            Request.Method.POST, url, contact,
            { response -> onSuccess(response) },
            { error -> onError(error.message ?: "Unknown error") }
        ).apply {
            headers["X-UUID"] = uuid
        }
        queue.add(request)
    }

    private fun updateContact(uuid: String, contactId: Int, contact: JSONObject, onSuccess: (JSONObject) -> Unit, onError: (String) -> Unit) {
        val url = "$baseUrl/contacts/$contactId"
        val request = JsonObjectRequest(
            Request.Method.PUT, url, contact,
            { response -> onSuccess(response) },
            { error -> onError(error.message ?: "Unknown error") }
        ).apply {
            headers["X-UUID"] = uuid
        }
        queue.add(request)
    }

    private fun deleteContact(uuid: String, contactId: Int, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val url = "$baseUrl/contacts/$contactId"
        val request = StringRequest(
            Request.Method.DELETE, url,
            { onSuccess() },
            { error -> onError(error.message ?: "Unknown error") }
        ).apply {
            headers["X-UUID"] = uuid
        }
        queue.add(request)
    }

    private val <T> T.headers: MutableMap<String, String>
        get() = mutableMapOf()
}
