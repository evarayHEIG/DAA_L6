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
import ch.heigvd.iict.and.rest.database.converters.CalendarConverter
import ch.heigvd.iict.and.rest.viewmodels.ContactsViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ContactEditorFragment : Fragment() {

    private var _binding: FragmentContactEditorBinding? = null
    private val binding get() = _binding!!

    private val contactsViewModel: ContactsViewModel by activityViewModels()
    private var contact: Contact? = null
    private var tmpContact = Contact()
    private val calendarConverter = CalendarConverter()
    private val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentContactEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        arguments?.let {
            contact = it.getParcelable(ARG_CONTACT, Contact::class.java)
            tmpContact = contact?.copy() ?: Contact()
        }

        setupViews()
        setupListeners()
    }

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
                else -> radioHome.isChecked = true
            }

            deleteButton.visibility = if (contact != null) View.VISIBLE else View.GONE
        }
    }

    private fun setupListeners() {
        binding.apply {
            birthdayInput.setOnClickListener {
                showDatePicker()
            }

            cancelButton.setOnClickListener {
                parentFragmentManager.popBackStack()
            }

            saveButton.setOnClickListener {
                saveContact()
            }

            deleteButton.setOnClickListener {
                contact?.let {
                    contactsViewModel.delete(it)
                    parentFragmentManager.popBackStack()
                }
            }

            phoneTypeGroup.setOnCheckedChangeListener { _, checkedId ->
                tmpContact.type = when (checkedId) {
                    R.id.radioHome -> PhoneType.HOME
                    R.id.radioMobile -> PhoneType.MOBILE
                    R.id.radioWork -> PhoneType.OFFICE
                    else -> PhoneType.HOME
                }
            }

            // Listeners pour les champs texte
            nameInput.doAfterTextChanged { tmpContact.name = it?.toString() ?: "" }
            firstnameInput.doAfterTextChanged { tmpContact.firstname = it?.toString() }
            addressInput.doAfterTextChanged { tmpContact.address = it?.toString() }
            zipInput.doAfterTextChanged { tmpContact.zip = it?.toString() }
            cityInput.doAfterTextChanged { tmpContact.city = it?.toString() }
            phoneNumberInput.doAfterTextChanged { tmpContact.phoneNumber = it?.toString() }
        }
    }

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

    private fun saveContact() {
        if (contact != null) {
            contactsViewModel.update(tmpContact)
        } else {
            contactsViewModel.insert(tmpContact)
        }
        parentFragmentManager.popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_CONTACT = "contact"

        fun newInstance(contact: Contact?) = ContactEditorFragment().apply {
            arguments = Bundle().apply {
                putParcelable(ARG_CONTACT, contact)
            }
        }
    }
}