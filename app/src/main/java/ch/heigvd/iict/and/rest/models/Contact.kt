package ch.heigvd.iict.and.rest.models

import android.os.Parcel
import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import ch.heigvd.iict.and.rest.adapters.CalendarSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.*

@Serializable
@Entity
data class Contact(
    @PrimaryKey(autoGenerate = true)
    var id: Long? = null,

    var remoteId: Long? = null,

    @Transient
    var state: ContactState = ContactState.SYNCED,

    @SerialName("name")
    var name: String,

    @SerialName("firstname")
    var firstname: String?,

    @SerialName("birthday")
    @Serializable(with = CalendarSerializer::class)
    var birthday: Calendar?,

    @SerialName("email")
    var email: String?,

    @SerialName("address")
    var address: String?,

    @SerialName("zip")
    var zip: String?,

    @SerialName("city")
    var city: String?,

    @SerialName("type")
    var type: PhoneType?,

    @SerialName("phoneNumber")
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
        phoneNumber = ""
    )

    fun isSynced() = state == ContactState.SYNCED

    override fun toString(): String {
        return "Contact(" +
                "id: $id, " +
                "name: $name, " +
                "firstname: $firstname, " +
                "birthday: $birthday, " +
                "email: $email, " +
                "address: $address, " +
                "zip: $zip, " +
                "city: $city, " +
                "type: $type, " +
                "phoneNumber: $phoneNumber" +
                ")"
    }

    override fun describeContents(): Int {
        return 0
    }

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

    companion object {
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