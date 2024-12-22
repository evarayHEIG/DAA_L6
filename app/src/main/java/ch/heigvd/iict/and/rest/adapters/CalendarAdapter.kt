package ch.heigvd.iict.and.rest.adapters

import ch.heigvd.iict.and.rest.database.converters.CalendarConverter
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object CalendarAdapter : KSerializer<Calendar> {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())

    override val descriptor: SerialDescriptor
            = PrimitiveSerialDescriptor("Calendar", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Calendar {
        val calendar = Calendar.getInstance()
        calendar.time = dateFormat.parse(decoder.decodeString())!!
        return calendar
    }

    override fun serialize(encoder: Encoder, value: Calendar) {
        encoder.encodeString(dateFormat.format(value.time))
    }
}