package ch.heigvd.iict.and.rest

import android.content.Context
import ch.heigvd.iict.and.rest.database.ContactsDao
import com.android.volley.Request
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject

class ContactsRepository(private val contactsDao: ContactsDao, private val context: Context) {

    val allContacts = contactsDao.getAllContactsLiveData()

    companion object {
        private val TAG = "ContactsRepository"
    }

    private val baseUrl = "https://daa.iict.ch/"
    private val queue = Volley.newRequestQueue(context)

    /**
     * Enroll - GET /enroll
     * Creates a new user (UUID) and a set of default contacts.
     */
    fun enroll(onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        val url = "${baseUrl}enroll"

        val request = StringRequest(
            Request.Method.GET, url,
            { response ->
                onSuccess(response)
            },
            { error ->
                onError(error.localizedMessage ?: "An error occurred")
            })

        queue.add(request)
    }

    /**
     * Get All Contacts - GET /contacts
     */
    fun getAllContacts(uuid: String, onSuccess: (List<JSONObject>) -> Unit, onError: (String) -> Unit) {
        val url = "${baseUrl}contacts"

        val request = JsonArrayRequest(Request.Method.GET, url, null,
            { response ->
                val contacts = mutableListOf<JSONObject>()
                for (i in 0 until response.length()) {
                    contacts.add(response.getJSONObject(i))
                }
                onSuccess(contacts)
            },
            { error ->
                onError(error.localizedMessage ?: "An error occurred")
            }).apply {
            headers["X-UUID"] = uuid
        }

        queue.add(request)
    }

    /**
     * Get Contact by ID - GET /contacts/{id}
     */
    fun getContactById(uuid: String, contactId: Int, onSuccess: (JSONObject) -> Unit, onError: (String) -> Unit) {
        val url = "${baseUrl}contacts/$contactId"

        val request = JsonObjectRequest(Request.Method.GET, url, null,
            { response ->
                onSuccess(response)
            },
            { error ->
                onError(error.localizedMessage ?: "An error occurred")
            }).apply {
            headers["X-UUID"] = uuid
        }

        queue.add(request)
    }

    /**
     * Create a Contact - POST /contacts
     */
    fun addContact(uuid: String, contact: JSONObject, onSuccess: (JSONObject) -> Unit, onError: (String) -> Unit) {
        val url = "${baseUrl}contacts"

        val request = JsonObjectRequest(Request.Method.POST, url, contact,
            { response ->
                onSuccess(response)
            },
            { error ->
                onError(error.localizedMessage ?: "An error occurred")
            }).apply {
            headers["X-UUID"] = uuid
            headers["Content-Type"] = "application/json"
        }

        queue.add(request)
    }

    /**
     * Update a Contact - PUT /contacts/{id}
     */
    fun updateContact(uuid: String, contactId: Int, contact: JSONObject, onSuccess: (JSONObject) -> Unit, onError: (String) -> Unit) {
        val url = "${baseUrl}contacts/$contactId"

        val request = JsonObjectRequest(Request.Method.PUT, url, contact,
            { response ->
                onSuccess(response)
            },
            { error ->
                onError(error.localizedMessage ?: "An error occurred")
            }).apply {
            headers["X-UUID"] = uuid
            headers["Content-Type"] = "application/json"
        }

        queue.add(request)
    }

    /**
     * Delete a Contact - DELETE /contacts/{id}
     */
    fun deleteContact(uuid: String, contactId: Int, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val url = "${baseUrl}contacts/$contactId"

        val request = StringRequest(Request.Method.DELETE, url,
            {
                onSuccess()
            },
            { error ->
                onError(error.localizedMessage ?: "An error occurred")
            }).apply {
            headers["X-UUID"] = uuid
        }

        queue.add(request)
    }

    /**
     * Add custom headers to a request.
     */
    private val <T> T.headers: MutableMap<String, String>
        get() = mutableMapOf()

}