package com.example.taptopayandroid.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.taptopayandroid.NavigationListener
import com.example.taptopayandroid.R
import com.example.taptopayandroid.settings.SettingsManager
import com.example.taptopayandroid.utils.DeviceUtils

/**
 * Fragment that displays return instructions before initiating the return flow.
 * This screen mirrors the purchase flow but with return-specific messaging.
 */
class ReturnInstructionsFragment : Fragment() {
    
    private lateinit var settingsManager: SettingsManager
    private var fingerprintInput: EditText? = null

    companion object {
        const val TAG = "com.example.taptopayandroid.fragments.ReturnInstructionsFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_return_instructions, container, false)
        
        settingsManager = SettingsManager(requireContext())

        val simulateButton = view.findViewById<Button>(R.id.simulate_return_button)
        fingerprintInput = view.findViewById(R.id.fingerprint_input)
        val backButton = view.findViewById<Button>(R.id.back_button)
        val readerId = view.findViewById<TextView>(R.id.reader_id)

        // Load saved location
        val locationName = settingsManager.getSelectedLocationName()
        readerId.text = "${getString(R.string.selected_location)}: $locationName"

        if (DeviceUtils.isEmulator()) {
            // In emulator mode, show the simulate button
            simulateButton.visibility = View.VISIBLE
            fingerprintInput?.visibility = View.VISIBLE
            
            // Auto-generate a random seed for testing
            if (fingerprintInput?.text.isNullOrEmpty()) {
                val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
                val randomString = (1..10)
                    .map { allowedChars.random() }
                    .joinToString("")
                fingerprintInput?.setText("fp_$randomString")
            }
        }

        simulateButton.setOnClickListener {
            // Trigger the return flow with simulated card tap
            (activity as? NavigationListener)?.onStartReturnFlow()
        }

        backButton.setOnClickListener {
            // Go back to the main screen
            parentFragmentManager.popBackStack()
        }

        return view
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh location from settings
        val readerId = view?.findViewById<TextView>(R.id.reader_id)
        val locationName = settingsManager.getSelectedLocationName()
        readerId?.text = "${getString(R.string.selected_location)}: $locationName"
    }
    
    fun getEnteredFingerprint(): String? {
        return fingerprintInput?.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    }
}
