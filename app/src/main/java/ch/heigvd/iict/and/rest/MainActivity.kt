package ch.heigvd.iict.and.rest

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.addCallback
import androidx.activity.viewModels
import ch.heigvd.iict.and.rest.databinding.ActivityMainBinding
import ch.heigvd.iict.and.rest.fragments.ContactEditorFragment
import ch.heigvd.iict.and.rest.viewmodels.ContactsViewModel
import ch.heigvd.iict.and.rest.viewmodels.ContactsViewModelFactory

/**
 * MainActivity is the entry point of the application.
 * It manages the main user interface and navigation between fragments.
 * @author Rachel Tranchida
 * @author Massimo Stefani
 * @author Eva Ray
 */
class MainActivity : AppCompatActivity() {

    // View binding to access layout elements
    private lateinit var binding: ActivityMainBinding

    // ViewModel for accessing and managing contact data
    private val contactsViewModel: ContactsViewModel by viewModels {
        ContactsViewModelFactory((application as ContactsApplication).repository)
    }

    // Menu reference to control visibility dynamically
    private var menu: Menu? = null

    /**
     * Called when the activity is created.
     * Sets up the layout, action bar, and listeners for UI interactions.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configure the ActionBar
        supportActionBar?.setDisplayHomeAsUpEnabled(false)

        // Set up the floating action button (FAB) for creating new contacts
        binding.mainFabNew.setOnClickListener {
            // Launch the editor fragment in creation mode
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_content_fragment, ContactEditorFragment.newInstance(null))
                .addToBackStack(null)
                .commit()

            // Update UI to show the back arrow and hide buttons
            updateUIForEdition(true)
        }

        // Listen for fragment back stack changes to manage UI state
        supportFragmentManager.addOnBackStackChangedListener {
            val isEditing = supportFragmentManager.backStackEntryCount > 0
            updateUIForEdition(isEditing)
        }
    }

    /**
     * Inflate the main menu.
     * @param menu The menu to be inflated
     * @return True if the menu is successfully created
     */
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        this.menu = menu
        return super.onCreateOptionsMenu(menu)
    }

    /**
     * Updates the user interface when switching between editing and main views.
     * @param isEditing True if the user is in editing mode, false otherwise
     */
    private fun updateUIForEdition(isEditing: Boolean) {
        // Handle the back arrow visibility
        supportActionBar?.setDisplayHomeAsUpEnabled(isEditing)

        // Handle the FAB visibility
        binding.mainFabNew.visibility = if (isEditing) View.GONE else View.VISIBLE

        // Handle menu item visibility
        menu?.findItem(R.id.menu_main_synchronize)?.isVisible = !isEditing
        menu?.findItem(R.id.menu_main_populate)?.isVisible = !isEditing
    }

    /**
     * Handles menu item selections.
     * @param item The selected menu item
     * @return True if the selection is handled, false otherwise
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // Handle the back button
                onBackPressedDispatcher.addCallback(this) {
                    finish()
                }.handleOnBackPressed()
                true
            }

            R.id.menu_main_synchronize -> {
                // Trigger contact synchronization
                contactsViewModel.refresh()
                true
            }

            R.id.menu_main_populate -> {
                // Trigger contact enrollment
                contactsViewModel.enroll()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        // Tag for debugging purposes
        private val TAG = MainActivity::class.java.simpleName
    }
}
