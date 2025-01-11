package ch.heigvd.iict.and.rest

import android.app.Application
import ch.heigvd.iict.and.rest.database.ContactsDatabase

/**
 * Application class used to provide a global repository instance to the whole application
 * and to initialize the database.
 * @author Rachel Tranchida
 * @author Massimo Stefani
 * @author Eva Ray
 */
class ContactsApplication : Application() {

    private val database by lazy { ContactsDatabase.getDatabase(this) }
    val repository by lazy { ContactsRepository(database.contactsDao(), this) }
}