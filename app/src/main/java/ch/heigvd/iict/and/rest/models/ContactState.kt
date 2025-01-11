package ch.heigvd.iict.and.rest.models

/**
 * Enum class representing the different states a contact can be in
 * @author Rachel Tranchida
 * @author Massimo Stefani
 * @author Eva Ray
 */
enum class ContactState {
    /**
     * Contact is synchronised
     */
    SYNCED,

    /**
     * Contact created locally, waiting for synchronisation
     */
    CREATED,

    /**
     * Contact modified locally, waiting for synchronisation
     */
    UPDATED,

    /**
     * Contact deleted locally, waiting for synchronisation
     */
    DELETED
}