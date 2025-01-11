# Rapport laboraoire 6

#### Auteurs: Rachel Tranchida, Massimo Stefani, Eva Ray

## Choix d'implémentation

### Volley

Nous avons choisi d'utiliser Volley pour gérer les requêtes HTTP dans notre application, bien que l'utilisation de l'API de base java.net.URL aurait été suffisante compte tenu de la petite ampleur de notre projet. Nous avons fait ce choix car nous voulions explorer des outils modernes offrant une gestion plus simple et efficace des requêtes réseau. En effet, Volley simplifie le traitement des requêtes HTTP grâce à ses fonctionnalités intégrées, comme la mise en cache, le traitement automatique des erreurs et la gestion asynchrone, ce qui réduit le risque d'écritures complexes ou d'erreurs liées aux threads. En adoptant Volley, nous profitons d'une solution robuste tout en nous formant à des pratiques reconnues pour des projets plus ambitieux à l'avenir.

### 4.1 Implémentation de l’inscription (enrollment)

Un bouton est ajouté à l'UI pour permettre de lancer le processus d'inscription. Lorsqu'on clique sur ce bouton, la fonction `enroll()` de `ContactsViewModel` est appelée. Cette méthode s'occupe de supprimer toutes les données locales puis d'obtenir un nouvel uuid et de récupérer tous les contacts.

```kotlin
fun enroll() {
        viewModelScope.launch {
            try {
                repository.deleteAllContacts()
                repository.enrollAndFetchContacts()
            } catch(e: Exception) {             
                e.printStackTrace()
            }
        }
    }
```

La méthode enroll() repose sur deux fonctions principales du repository : ``deleteAllContacts`` et ``enrollAndFetchContacts``, qui gèrent l’interaction avec la base de données locale et les appels réseau.

- ``deleteAllContacts`` : Cette fonction supprime toutes les entrées de la table des contacts dans la base de données locale. L’opération est exécutée de manière asynchrone à l’aide des coroutines Kotlin pour éviter de bloquer le thread principal. Elle utilise le contexte ``Dispatchers.IO``, qui est adapté aux tâches d’entrée/sortie, comme les opérations sur la base de données. Ces tâches peuvent être longues et bloquer le thread sur lequel elles s'exécutent. En déléguant ces opérations à un pool de threads optimisé pour les I/O via ``Dispatchers.IO``, on évite de bloquer le thread principal de l'application.
- ``enrollAndFetchContacts`` : Cette fonction commence par appeler la méthode ``enroll``, qui effectue une requête HTTP vers l'endpoint /enroll pour obtenir un nouvel UUID unique. Cet UUID est ensuite stocké dans les SharedPreferences afin d'être réutilisé lors des prochaines sessions. Lors du démarrage de l'application, cet UUID est récupéré des SharedPreferences pour les requêtes ultérieures, notamment celles adressées à l'endpoint /contacts. Une fois l'uuid récupéré, `enrollAndFetchContacts` continue avec la récupération des contacts en appelant la méthode `getAllContacts`, qui fait une requête vers le endpoint \contacts, en passant l'uuid. Encore une fois, la requête est faite dans une coroutine pour ne pas bloquer le thread principal. Chaque contact est alors ajouté dans la base de donnée local en appelant la méthode `insert` du DAO.

On utilise le `ViewModelScope` pour exécuter la coroutine `enroll()` car les coroutines lancées dans ce scope sont automatiquement annulées lorsque le ViewModel est détruit. Cela évite les fuites de mémoire en s'assurant que les tâches asynchrones ne continuent pas inutilement une fois que le ViewModel est détruit.

En résumé, l'implémentation de l'inscription repose sur des coroutines Kotlin pour effectuer des requêtes réseau et des opérations sur la base de données de manière asynchrone, garantissant que le thread principal reste réactif. Le repository est utilisé pour centraliser l'accès aux données locales et distantes, en utilisant le contexte ``Dispatchers.IO`` pour les tâches d’entrée/sortie. Enfin, le ViewModelScope est utilisé pour gérer la durée de vie des coroutines, assurant leur annulation automatique lorsque le ViewModel est détruit, afin d'éviter les fuites de mémoire.

#### Scénario d'utilisation

1. L'utilisateur clique sur le bouton d'inscription.
2. L'application supprime toutes les données de contact de la base de données locale.
3. L'application envoie une requête GET à /enroll pour obtenir un nouvel UUID.
4. L'application stocke le nouvel UUID dans les SharedPreferences.
5. L'application envoie une requête GET à /contacts avec le nouvel UUID.
6. Le serveur renvoie une liste de contacts.
7. L'application insère les contacts dans la base de données locale.
8. L'application affiche les contacts à l'utilisateur.
9. Au prochain lancement de l'application, l'UUID est récupéré des SharedPreferences et utilisé pour les requêtes vers /contacts.


### 4.2 Création, modification et suppression de contacts

### 4.3 Synchronisation de tous les contacts
Nous avons introduit une méthode `refresh()` dans `ContactBiewModel`
```kotlin
 fun refresh() {
        viewModelScope.launch {
            try {
                repository.synchronizeAllContacts()
            } catch(e: Exception) {
                
                e.printStackTrace()
            }
        }
    }
```
Lorsque l'on appuie sur le bouton de syncronisation cette fonction est appelée. Elle lance une coroutine qui va appeler la fonction du repository permettant de syncroniser les contacts. La fonction `syncronizeAllContacts()` est une fonction supsensive qui s'exécute dans le contexte Dispatchers.IO car c'est le contexte adapté aux opérations IO et va permettre de ne pas bloquer l'UI-thread.
```kotlin
suspend fun synchronizeAllContacts() = withContext(Dispatchers.IO) {
        val currentUuid = uuid ?: throw Exception("No UUID available")
        val unsyncedContacts = contactsDao.getAllContacts().filter {
            it.state != ContactState.SYNCED
        }

        unsyncedContacts.forEach { contact ->
            try {
                when (contact.state) {
                    ContactState.CREATED -> insert(contact)
                    ContactState.UPDATED -> update(contact)
                    ContactState.DELETED -> delete(contact)
                    else -> { /* Ignore SYNCED contacts */ }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
```
La fonction va syncroniser uniquement les chagements qui n'ont pas été syncronisés en effectuant les insert, update et delete nécessaires. En cas d'un erreur lors de insert, update or delete, elle catche l'exception et continue à syncroniser le reste des contacts non syncronisés.  
