package ch.heigvd.iict.and.rest.models

import android.os.Parcel
import android.os.Parcelable
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
) : Parcelable {

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

    constructor(parcel: Parcel) : this(
        id = parcel.readValue(Long::class.java.classLoader) as? Long,
        remoteId = parcel.readValue(Long::class.java.classLoader) as? Long,
        state = ContactState.valueOf(parcel.readString() ?: ContactState.CREATED.name),
        name = parcel.readString() ?: "",
        firstname = parcel.readString(),
        birthday = (parcel.readSerializable() as? Calendar),
        email = parcel.readString(),
        address = parcel.readString(),
        zip = parcel.readString(),
        city = parcel.readString(),
        type = parcel.readString()?.let { PhoneType.valueOf(it) },
        phoneNumber = parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeValue(id)
        parcel.writeValue(remoteId)
        parcel.writeString(state.name)
        parcel.writeString(name)
        parcel.writeString(firstname)
        parcel.writeSerializable(birthday)
        parcel.writeString(email)
        parcel.writeString(address)
        parcel.writeString(zip)
        parcel.writeString(city)
        parcel.writeString(type?.name)
        parcel.writeString(phoneNumber)
    }

    override fun describeContents(): Int {
        return 0
    }

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

        @JvmField
        val CREATOR = object : Parcelable.Creator<Contact> {
            override fun createFromParcel(parcel: Parcel): Contact {
                return Contact(parcel)
            }

            override fun newArray(size: Int): Array<Contact?> {
                return arrayOfNulls(size)
            }
        }
    }
}