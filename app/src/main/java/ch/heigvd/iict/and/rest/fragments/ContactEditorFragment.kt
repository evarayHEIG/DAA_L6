package ch.heigvd.iict.and.rest.fragments

import android.app.DatePickerDialog
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import ch.heigvd.iict.and.rest.R
import ch.heigvd.iict.and.rest.databinding.FragmentContactEditorBinding
import ch.heigvd.iict.and.rest.models.Contact
import ch.heigvd.iict.and.rest.models.PhoneType
import ch.heigvd.iict.and.rest.viewmodels.ContactsViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * ContactEditorFragment allows the user to create or edit a contact.
 */
class ContactEditorFragment : Fragment() {

    // Binding for accessing UI elements in the fragment layout
    private var _binding: FragmentContactEditorBinding? = null
    private val binding get() = _binding!!

    // ViewModel for managing contact data
    private val contactsViewModel: ContactsViewModel by activityViewModels()

    // Current contact being edited
    private var contact: Contact? = null

    // Temporary contact used for changes
    private var tmpContact = Contact()

    private val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

    /**
     * Called to create the fragment's view hierarchy.
     * @param inflater The LayoutInflater object that can be used to inflate views
     * @param container The parent view that the fragment's UI will be attached to
     * @param savedInstanceState A Bundle containing the saved state of the fragment
     * @return The root view of the fragment
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentContactEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * Called after the fragment's view has been created.
     * Sets up the views and listeners for user interaction.
     * @param view The fragment's root view
     * @param savedInstanceState A Bundle containing the saved state of the fragment
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Retrieve the contact passed as an argument
        arguments?.let {
            contact = it.getParcelable(ARG_CONTACT, Contact::class.java)
            tmpContact = contact?.copy() ?: Contact()
        }

        // Setup the views and listeners
        setupViews()
        setupListeners()
    }

    /**
     * Configures the views based on the current contact data.
     */
    private fun setupViews() {
        binding.apply {
            title.text = getString(
                if (contact != null) R.string.fragment_detail_title_edit
                else R.string.fragment_detail_title_new
            )

            nameInput.setText(tmpContact.name)
            firstnameInput.setText(tmpContact.firstname)
            birthdayInput.setText(tmpContact.birthday?.let { dateFormat.format(it.time) } ?: "")
            addressInput.setText(tmpContact.address)
            zipInput.setText(tmpContact.zip)
            cityInput.setText(tmpContact.city)
            phoneNumberInput.setText(tmpContact.phoneNumber)

            when (tmpContact.type) {
                PhoneType.HOME -> radioHome.isChecked = true
                PhoneType.MOBILE -> radioMobile.isChecked = true
                PhoneType.OFFICE -> radioWork.isChecked = true
                PhoneType.FAX -> radioFax.isChecked = true
                else -> radioHome.isChecked = true
            }

            deleteButton.visibility = if (contact != null) View.VISIBLE else View.GONE
        }
    }

    /**
     * Sets up listeners for user interactions with the views.
     */
    private fun setupListeners() {
        binding.apply {
            // Listener for the birthday input
            birthdayInput.setOnClickListener {
                showDatePicker()
            }

            // Listener for the cancel button
            cancelButton.setOnClickListener {
                parentFragmentManager.popBackStack()
            }

            // Listener for the save button
            saveButton.setOnClickListener {
                saveContact()
            }

            // Listener for the delete button
            deleteButton.setOnClickListener {
                contact?.let {
                    contactsViewModel.delete(it)
                    parentFragmentManager.popBackStack()
                }
            }

            // Listener for phone type radio group
            phoneTypeGroup.setOnCheckedChangeListener { _, checkedId ->
                tmpContact.type = when (checkedId) {
                    R.id.radioHome -> PhoneType.HOME
                    R.id.radioMobile -> PhoneType.MOBILE
                    R.id.radioWork -> PhoneType.OFFICE
                    R.id.radioFax -> PhoneType.FAX
                    else -> PhoneType.HOME
                }
            }

            // Listeners for text fields
            nameInput.doAfterTextChanged { tmpContact.name = it?.toString() ?: "" }
            firstnameInput.doAfterTextChanged { tmpContact.firstname = it?.toString() }
            addressInput.doAfterTextChanged { tmpContact.address = it?.toString() }
            zipInput.doAfterTextChanged { tmpContact.zip = it?.toString() }
            cityInput.doAfterTextChanged { tmpContact.city = it?.toString() }
            phoneNumberInput.doAfterTextChanged { tmpContact.phoneNumber = it?.toString() }
        }
    }

    /**
     * Displays a date picker dialog for selecting a birthday.
     */
    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        tmpContact.birthday?.let {
            calendar.time = it.time
        }

        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                calendar.set(year, month, day)
                tmpContact.birthday = calendar
                binding.birthdayInput.setText(dateFormat.format(calendar.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    /**
     * Saves the contact by either updating or inserting it.
     */
    private fun saveContact() {
        if (contact != null) {
            contactsViewModel.update(tmpContact)
        } else {
            contactsViewModel.insert(tmpContact)
        }
        parentFragmentManager.popBackStack()
    }

    /**
     * Cleans up resources when the view is destroyed.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CONTACT = "contact"

        /**
         * Creates a new instance of ContactEditorFragment with the given contact.
         * @param contact The contact to be edited, or null for a new contact
         * @return A new instance of ContactEditorFragment
         */
        fun newInstance(contact: Contact?) = ContactEditorFragment().apply {
            arguments = Bundle().apply {
                putParcelable(ARG_CONTACT, contact)
            }
        }
    }
}
