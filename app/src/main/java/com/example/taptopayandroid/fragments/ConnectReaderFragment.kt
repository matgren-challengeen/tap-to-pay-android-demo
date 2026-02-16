package com.example.taptopayandroid.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.taptopayandroid.NavigationListener
import com.example.taptopayandroid.R
import com.example.taptopayandroid.settings.SettingsManager
import com.example.taptopayandroid.utils.DeviceUtils

var btnConnectReader: Button? = null
var loginButton: Button? = null
var simulateTapButton: Button? = null
var currentReaderDetails: String? = null

class ConnectReaderFragment : Fragment() {
    
    private lateinit var settingsManager: SettingsManager
    private var emailInput: EditText? = null
    private var fingerprintInput: EditText? = null
    
    companion object {
        const val TAG = "com.example.taptopayandroid.fragments.ConnectReaderFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_connect_reader, container, false)
        
        settingsManager = SettingsManager(requireContext())

        btnConnectReader = view.findViewById(R.id.connect_reader_button)
        loginButton = view.findViewById(R.id.login_button)
        simulateTapButton = view.findViewById(R.id.simulate_tap_button)
        emailInput = view.findViewById(R.id.email_input)
        fingerprintInput = view.findViewById(R.id.fingerprint_input)
        val readerId = view.findViewById<TextView>(R.id.reader_id)
        val settingsButton = view.findViewById<Button>(R.id.settings_button)

        // Load saved location
        val locationName = settingsManager.getSelectedLocationName()
        readerId.text = "Selected location: $locationName"

        // If the user is getting to this view after having already connected a reader
        if (currentReaderDetails != null) {
            readerId.text = "Selected location: $currentReaderDetails"
            btnConnectReader?.visibility = View.GONE
            loginButton?.visibility = View.VISIBLE
        }

        if (DeviceUtils.isEmulator()) {
            // In emulator mode, show the simulate button and hide connect reader
            btnConnectReader?.visibility = View.GONE
            simulateTapButton?.visibility = View.VISIBLE
            fingerprintInput?.visibility = View.VISIBLE
            
            // Auto-generate a random seed for testing
            if (fingerprintInput?.text.isNullOrEmpty()) {
                val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
                val randomString = (1..10)
                    .map { allowedChars.random() }
                    .joinToString("")
                fingerprintInput?.setText("fp_$randomString")
            }

            if (currentReaderDetails == null) {
                readerId.text = "Selected location: Simulator Mode"
            }
        } else {
            // Auto-connect on physical device
            simulateTapButton?.visibility = View.GONE
            btnConnectReader?.text = "Connecting to Reader..."
            btnConnectReader?.postDelayed({
                (activity as? NavigationListener)?.onConnectReader()
            }, 1000)
        }

        btnConnectReader?.setOnClickListener {
            if (DeviceUtils.isEmulator()) {
                (activity as? NavigationListener)?.onConnectReader()
            } else {
                btnConnectReader?.text = "Retrying..."
                (activity as? NavigationListener)?.onConnectReader()
            }
        }

        // Simulate button triggers the login flow directly in emulator mode
        simulateTapButton?.setOnClickListener {
            val email = emailInput?.text?.toString()?.trim()
            (activity as? NavigationListener)?.onStartLoginFlow(email)
        }

        loginButton?.setOnClickListener {
            val email = emailInput?.text?.toString()?.trim()
            (activity as? NavigationListener)?.onStartLoginFlow(email)
        }

        view.findViewById<View>(R.id.return_section)?.setOnClickListener {
            (activity as? NavigationListener)?.onShowReturnInstructions()
        }
        
        // Settings button with password dialog
        settingsButton?.setOnClickListener {
            showPasswordDialog()
        }

        return view
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh location from settings when returning from Settings screen
        val readerId = view?.findViewById<TextView>(R.id.reader_id)
        val locationName = settingsManager.getSelectedLocationName()
        if (currentReaderDetails == null) {
            readerId?.text = "${getString(R.string.selected_location)}: $locationName"
        }
    }
    
    private fun showPasswordDialog() {
        val input = EditText(context)
        input.hint = "Enter admin password"
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        
        AlertDialog.Builder(context)
            .setTitle("Settings Access")
            .setMessage("Enter admin password to access settings")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val password = input.text.toString()
                if (settingsManager.verifyPassword(password)) {
                    (activity as? NavigationListener)?.onOpenSettings()
                } else {
                    Toast.makeText(context, "Incorrect password", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun updateReaderId(location: String, reader_id: String) {
        val readerId = view?.findViewById<TextView>(R.id.reader_id)
        readerId?.text = "Selected location: $location"

        btnConnectReader?.visibility = View.GONE
        loginButton?.visibility = View.VISIBLE
        simulateTapButton?.visibility = View.GONE

        currentReaderDetails = location
    }

    fun resetUI() {
        activity?.runOnUiThread {
            if (DeviceUtils.isEmulator()) {
                simulateTapButton?.visibility = View.VISIBLE
                btnConnectReader?.visibility = View.GONE
            } else {
                btnConnectReader?.text = "Connect Reader"
                btnConnectReader?.visibility = View.VISIBLE
            }
            loginButton?.visibility = View.GONE
        }
    }
    
    fun getEnteredEmail(): String? {
        return emailInput?.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun getEnteredFingerprint(): String? {
        return fingerprintInput?.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    }
}