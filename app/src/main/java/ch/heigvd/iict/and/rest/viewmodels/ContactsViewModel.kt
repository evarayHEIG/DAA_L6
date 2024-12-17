package ch.heigvd.iict.and.rest.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ch.heigvd.iict.and.rest.ContactsRepository
import ch.heigvd.iict.and.rest.models.Contact
import kotlinx.coroutines.launch

class ContactsViewModel(private val repository: ContactsRepository) : ViewModel() {

    // Expose les contacts pour l'UI
    val allContacts: LiveData<List<Contact>> = repository.allContacts

    fun enroll() {
        viewModelScope.launch {
            try {
                // Supprime d'abord toutes les données locales
                repository.deleteAllContacts()

                // Obtient un nouvel UUID et récupère les contacts
                repository.enrollAndFetchContacts()
            } catch(e: Exception) {
                // Gestion des erreurs - à adapter selon les besoins de l'UI
                e.printStackTrace()
            }
        }
    }

    // Fonction de synchronisation qui synchronise tous les contacts "dirty"
    fun refresh() {
        viewModelScope.launch {
            try {
                repository.synchronizeAllContacts()
            } catch(e: Exception) {
                // Gestion des erreurs - à adapter selon les besoins de l'UI
                e.printStackTrace()
            }
        }
    }

    // Fonctions CRUD pour les contacts individuels
    fun insert(contact: Contact) = viewModelScope.launch {
        repository.insert(contact)
    }

    fun update(contact: Contact) = viewModelScope.launch {
        repository.update(contact)
    }

    fun delete(contact: Contact) = viewModelScope.launch {
        repository.delete(contact)
    }
}

class ContactsViewModelFactory(private val repository: ContactsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ContactsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ContactsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}