package ch.heigvd.iict.and.rest.utils


import com.android.volley.RequestQueue
import com.android.volley.toolbox.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * Utility class to handle API requests.
 * @author Rachel Tranchida
 * @author Massimo Stefani
 * @author Eva Ray
 */
class APIRequest {

    companion object {
        /**
         * Enrolls the application with the server.
         * @param baseUrl The base URL of the server.
         * @param queue The request queue to use.
         * @param onSuccess The callback to call on success.
         * @param onError The callback to call on error.
         * @return The UUID of the application.
         * @throws Exception If an error occurred during the request.
         */
        fun enroll(baseUrl: String, queue: RequestQueue, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
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

        /**
         * Fetches all contacts from the server.
         * @param baseUrl The base URL of the server.
         * @param uuid The UUID of the application.
         * @param queue The request queue to use.
         * @param onSuccess The callback to call on success.
         * @param onError The callback to call on error.
         * @throws Exception If an error occurred during the request.
         */
        fun getAllContacts(baseUrl: String, uuid: String, queue: RequestQueue, onSuccess: (JSONArray) -> Unit, onError: (String) -> Unit) {
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

        /**
         * Adds a contact to the server.
         * @param baseUrl The base URL of the server.
         * @param uuid The UUID of the application.
         * @param contact The contact to add.
         * @param queue The request queue to use.
         * @param onSuccess The callback to call on success.
         * @param onError The callback to call on error.
         * @throws Exception If an error occurred during the request.
         */
        fun addContact(baseUrl: String, uuid: String, contact: JSONObject, queue: RequestQueue, onSuccess: (JSONObject) -> Unit, onError: (String) -> Unit) {
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

        /**
         * Updates a contact on the server.
         * @param baseUrl The base URL of the server.
         * @param uuid The UUID of the application.
         * @param contactId The ID of the contact to update.
         * @param contact The new contact data.
         * @param queue The request queue to use.
         * @param onSuccess The callback to call on success.
         * @param onError The callback to call on error.
         * @throws Exception If an error occurred during the request.
         */
        fun updateContact(baseUrl: String, uuid: String, contactId: Int, contact: JSONObject, queue: RequestQueue, onSuccess: (JSONObject) -> Unit, onError: (String) -> Unit) {
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


        /**
         * Deletes a contact from the server.
         * @param baseUrl The base URL of the server.
         * @param uuid The UUID of the application.
         * @param contactId The ID of the contact to delete.
         * @param queue The request queue to use.
         * @param onSuccess The callback to call on success.
         * @param onError The callback to call on error.
         * @throws Exception If an error occurred during the request.
         */
        fun deleteContact(baseUrl: String, uuid: String, contactId: Int, queue: RequestQueue, onSuccess: () -> Unit, onError: (String) -> Unit) {
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
}
