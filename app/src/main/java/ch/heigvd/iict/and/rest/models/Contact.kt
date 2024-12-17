package ch.heigvd.iict.and.rest.models

import android.annotation.SuppressLint
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.JsonDeserializer
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.*

@Entity
data class Contact(
    @PrimaryKey(autoGenerate = true)
    var id: Long? = null,

    var remoteId: Long? = null,

    @Expose(serialize = false, deserialize = false)
    var state: ContactState = ContactState.SYNCED,

    @SerializedName("name")
    var name: String,

    @SerializedName("firstname")
    var firstname: String?,

    @SerializedName("birthday")
    var birthday: Calendar?,

    @SerializedName("email")
    var email: String?,

    @SerializedName("address")
    var address: String?,

    @SerializedName("zip")
    var zip: String?,

    @SerializedName("city")
    var city: String?,

    @SerializedName("type")
    var type: PhoneType?,

    @SerializedName("phoneNumber")
    var phoneNumber: String?
) {
    constructor() : this(
        id = null,
        remoteId = null,
        state = ContactState.CREATED,
        name = "",
        firstname = "",
        birthday = null,
        email = "",
        address = "",
        zip = "",
        city = "",
        type = PhoneType.HOME,
        phoneNumber = "",
    )

    fun isSynced() = state == ContactState.SYNCED

    override fun toString(): String {
        return "Contact(id: $id, name: $name, firstname: $firstname, " +
                "birthday: $birthday, email: $email, address: $address, zip: $zip, city: $city, " +
                "type: $type, phoneNumber: $phoneNumber)"
    }

    companion object {
        // Adaptateur Gson pour Calendar
        val calendarTypeAdapter = object {
            private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())

            val serializer = JsonSerializer<Calendar> { calendar, _, _ ->
                if (calendar == null) null
                else JsonPrimitive(dateFormat.format(calendar.time))
            }

            val deserializer = JsonDeserializer<Calendar> { json, _, _ ->
                if (json.isJsonNull) null
                else Calendar.getInstance().apply {
                    time = dateFormat.parse(json.asString)!!
                }
            }
        }
    }
}
