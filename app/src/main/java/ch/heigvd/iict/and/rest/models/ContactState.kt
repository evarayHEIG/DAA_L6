package ch.heigvd.iict.and.rest.models

enum class ContactState {
    /**
     * Contact synchronisé avec le serveur
     */
    SYNCED,

    /**
     * Contact créé localement, en attente de synchronisation
     */
    CREATED,

    /**
     * Contact modifié localement, en attente de synchronisation
     */
    UPDATED,

    /**
     * Contact supprimé localement, en attente de synchronisation
     */
    DELETED
}