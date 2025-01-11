package ch.heigvd.iict.and.rest.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ch.heigvd.iict.and.rest.ContactsRepository
import ch.heigvd.iict.and.rest.models.Contact
import kotlinx.coroutines.launch

/**
 * ViewModel for managing contact data and providing it to the UI.
 * Handles operations such as fetching, synchronizing, and CRUD operations on contacts.
 * @author Rachel Tranchida
 * @author Massimo Stefani
 * @author Eva Ray
 */
class ContactsViewModel(private val repository: ContactsRepository) : ViewModel() {

    // LiveData containing all contacts, observed by the UI
    val allContacts: LiveData<List<Contact>> = repository.allContacts

    /**
     * Enrolls the application with the server and fetches all contacts.
     * Deletes local data before fetching new data from the server.
     */
    fun enroll() {
        viewModelScope.launch {
            try {
                // Delete all local data
                repository.deleteAllContacts()

                // Obtain a new UUID and fetch contacts from the server
                repository.enrollAndFetchContacts()
            } catch (e: Exception) {
                // Error handling - customize based on UI requirements
                e.printStackTrace()
            }
        }
    }

    /**
     * Synchronizes all unsynchronized (dirty) contacts with the server.
     */
    fun refresh() {
        viewModelScope.launch {
            try {
                repository.synchronizeAllContacts()
            } catch (e: Exception) {
                // Error handling - customize based on UI requirements
                e.printStackTrace()
            }
        }
    }

    // CRUD operations for individual contacts

    /**
     * Inserts a new contact into the repository.
     * @param contact The contact to insert
     */
    fun insert(contact: Contact) = viewModelScope.launch {
        repository.insert(contact)
    }

    /**
     * Updates an existing contact in the repository.
     * @param contact The contact to update
     */
    fun update(contact: Contact) = viewModelScope.launch {
        repository.update(contact)
    }

    /**
     * Deletes a contact from the repository.
     * @param contact The contact to delete
     */
    fun delete(contact: Contact) = viewModelScope.launch {
        repository.delete(contact)
    }
}

/**
 * Factory class for creating a ContactsViewModel with a specific ContactsRepository.
 */
class ContactsViewModelFactory(private val repository: ContactsRepository) : ViewModelProvider.Factory {
    /**
     * Creates a new instance of ContactsViewModel.
     * @param modelClass The class of the ViewModel to create
     * @return A new instance of the ViewModel
     * @throws IllegalArgumentException if the ViewModel class is not recognized
     */
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ContactsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ContactsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
