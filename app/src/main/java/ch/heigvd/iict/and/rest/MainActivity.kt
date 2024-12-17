package ch.heigvd.iict.and.rest

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import ch.heigvd.iict.and.rest.databinding.ActivityMainBinding
import ch.heigvd.iict.and.rest.fragments.ContactEditorFragment
import ch.heigvd.iict.and.rest.viewmodels.ContactsViewModel
import ch.heigvd.iict.and.rest.viewmodels.ContactsViewModelFactory

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val contactsViewModel: ContactsViewModel by viewModels {
        ContactsViewModelFactory((application as ContactsApplication).repository)
    }

    private var menu: Menu? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configurer la ActionBar
        supportActionBar?.setDisplayHomeAsUpEnabled(false)

        binding.mainFabNew.setOnClickListener {
            // Lancer le fragment d'édition en mode création
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_content_fragment, ContactEditorFragment.newInstance(null))
                .addToBackStack(null)
                .commit()

            // Afficher la flèche de retour et masquer les boutons
            updateUIForEdition(true)
        }

        // Écouter les changements de fragments pour gérer l'UI
        supportFragmentManager.addOnBackStackChangedListener {
            val isEditing = supportFragmentManager.backStackEntryCount > 0
            updateUIForEdition(isEditing)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        this.menu = menu
        return super.onCreateOptionsMenu(menu)
    }

    private fun updateUIForEdition(isEditing: Boolean) {
        // Gestion de la flèche retour
        supportActionBar?.setDisplayHomeAsUpEnabled(isEditing)

        // Gestion du FAB
        binding.mainFabNew.visibility = if (isEditing) View.GONE else View.VISIBLE

        // Gestion des boutons du menu
        menu?.findItem(R.id.menu_main_synchronize)?.isVisible = !isEditing
        menu?.findItem(R.id.menu_main_populate)?.isVisible = !isEditing
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            R.id.menu_main_synchronize -> {
                contactsViewModel.refresh()
                true
            }
            R.id.menu_main_populate -> {
                contactsViewModel.enroll()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
    }
}