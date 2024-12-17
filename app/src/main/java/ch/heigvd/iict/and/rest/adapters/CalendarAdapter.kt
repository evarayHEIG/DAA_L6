package ch.heigvd.iict.and.rest.adapters
import com.google.gson.*
import java.lang.reflect.Type
import java.text.SimpleDateFormat
import java.util.*

class CalendarAdapter : JsonDeserializer<Calendar>, JsonSerializer<Calendar> {

    private val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Calendar {
        val dateStr = json.asString
        // Parse la date complète "YYYY-MM-DD'T'HH:mm:ss.SSS+ZZZZ"
        // Exemple : "1985-04-08T00:00:00.000+00:00"
        // Remarque : Selon la version de Java/Android, le fuseau horaire +00:00 peut
        // devoir être ajusté (parfois remplacez le ':' entre heures et minutes du fuseau par
        // quelque chose comme +0000).
        val cleanedDateStr = dateStr.replace("Z", "+0000") // Si nécessaire pour certains formats

        val date = sdf.parse(cleanedDateStr)
        val calendar = Calendar.getInstance()
        calendar.time = date
        return calendar
    }

    override fun serialize(src: Calendar?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        if (src == null) return JsonNull.INSTANCE
        // Convertir le Calendar en chaîne avec le même format
        return JsonPrimitive(sdf.format(src.time))
    }
}

