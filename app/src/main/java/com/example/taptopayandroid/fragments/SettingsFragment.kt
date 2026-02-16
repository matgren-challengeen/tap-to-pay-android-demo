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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.taptopayandroid.R
import com.example.taptopayandroid.adapter.LocationsAdapter
import com.example.taptopayandroid.models.Location
import com.example.taptopayandroid.settings.SettingsManager

class SettingsFragment : Fragment() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var locationsAdapter: LocationsAdapter
    
    private var onBackPressed: (() -> Unit)? = null
    private var onSettingsSaved: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        
        settingsManager = SettingsManager(requireContext())
        
        // UI Elements
        val backButton = view.findViewById<Button>(R.id.back_button)
        val appTitleInput = view.findViewById<EditText>(R.id.app_title_input)
        val selectedLocationName = view.findViewById<TextView>(R.id.selected_location_name)
        val selectedLocationId = view.findViewById<TextView>(R.id.selected_location_id)
        val refreshLocationsButton = view.findViewById<Button>(R.id.refresh_locations_button)
        val locationsRecycler = view.findViewById<RecyclerView>(R.id.locations_recycler)
        val currentPasswordInput = view.findViewById<EditText>(R.id.current_password_input)
        val newPasswordInput = view.findViewById<EditText>(R.id.new_password_input)
        val confirmPasswordInput = view.findViewById<EditText>(R.id.confirm_password_input)
        val changePasswordButton = view.findViewById<Button>(R.id.change_password_button)
        val saveSettingsButton = view.findViewById<Button>(R.id.save_settings_button)
        
        // Load current values
        appTitleInput.setText(settingsManager.getAppTitle())
        selectedLocationName.text = settingsManager.getSelectedLocationName()
        selectedLocationId.text = settingsManager.getSelectedLocationId() ?: "Select a location below"
        
        // Language buttons
        val languageEnglishButton = view.findViewById<Button>(R.id.language_english_button)
        val languagePolishButton = view.findViewById<Button>(R.id.language_polish_button)
        
        // Update button styles based on current language
        fun updateLanguageButtons() {
            val currentLang = settingsManager.getLanguage()
            if (currentLang == "en") {
                languageEnglishButton.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFE65100.toInt())
                languagePolishButton.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF757575.toInt())
            } else {
                languageEnglishButton.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF757575.toInt())
                languagePolishButton.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFE65100.toInt())
            }
        }
        updateLanguageButtons()
        
        languageEnglishButton.setOnClickListener {
            settingsManager.setLanguage("en")
            setLocaleAndRefresh("en")
            updateLanguageButtons()
            Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
        }
        
        languagePolishButton.setOnClickListener {
            settingsManager.setLanguage("pl")
            setLocaleAndRefresh("pl")
            updateLanguageButtons()
            Toast.makeText(context, "Ustawienia zapisane", Toast.LENGTH_SHORT).show()
        }
        
        // Setup locations adapter
        locationsAdapter = LocationsAdapter { location ->
            settingsManager.setSelectedLocation(location.id, location.name)
            selectedLocationName.text = location.name
            selectedLocationId.text = location.id
            Toast.makeText(context, "Location selected: ${location.name}", Toast.LENGTH_SHORT).show()
        }
        locationsRecycler.layoutManager = LinearLayoutManager(context)
        locationsRecycler.adapter = locationsAdapter
        
        // Load mock locations (in production, fetch from API)
        loadLocations()
        
        // Back button
        backButton.setOnClickListener {
            onBackPressed?.invoke()
        }
        
        // Refresh locations
        refreshLocationsButton.setOnClickListener {
            loadLocations()
            Toast.makeText(context, "Locations refreshed", Toast.LENGTH_SHORT).show()
        }
        
        // Change password
        changePasswordButton.setOnClickListener {
            val currentPassword = currentPasswordInput.text.toString()
            val newPassword = newPasswordInput.text.toString()
            val confirmPassword = confirmPasswordInput.text.toString()
            
            if (!settingsManager.verifyPassword(currentPassword)) {
                Toast.makeText(context, "Current password is incorrect", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (newPassword.isEmpty()) {
                Toast.makeText(context, "New password cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (newPassword != confirmPassword) {
                Toast.makeText(context, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            settingsManager.changePassword(newPassword)
            currentPasswordInput.text.clear()
            newPasswordInput.text.clear()
            confirmPasswordInput.text.clear()
            Toast.makeText(context, "Password changed successfully", Toast.LENGTH_SHORT).show()
        }
        
        // Save settings
        saveSettingsButton.setOnClickListener {
            val appTitle = appTitleInput.text.toString().trim()
            if (appTitle.isNotEmpty()) {
                settingsManager.setAppTitle(appTitle)
            }
            
            Toast.makeText(context, "Settings saved!", Toast.LENGTH_SHORT).show()
            onSettingsSaved?.invoke()
        }
        
        return view
    }
    
    private fun loadLocations() {
        // For development, use mock locations
        // In production, this would fetch from the backend API: GET /store/locations
        val mockLocations = listOf(
            Location("loc_001", "Venloop HQ"),
            Location("loc_002", "LPP Office"),
            Location("loc_003", "Tech Park Building A"),
            Location("loc_004", "Coworking Space"),
            Location("loc_005", "Mall Food Court")
        )
        locationsAdapter.submitList(mockLocations)
    }
    
    /**
     * Set locale and refresh the current fragment view without closing settings.
     * This updates the configuration and then detaches/attaches the fragment to refresh UI.
     */
    private fun setLocaleAndRefresh(languageCode: String) {
        val locale = java.util.Locale(languageCode)
        java.util.Locale.setDefault(locale)
        
        val config = resources.configuration
        config.setLocale(locale)
        
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
        
        // Refresh the fragment to update all text
        parentFragmentManager.beginTransaction()
            .detach(this)
            .commitNow()
        parentFragmentManager.beginTransaction()
            .attach(this)
            .commitNow()
    }
    
    fun setOnBackPressed(callback: () -> Unit) {
        onBackPressed = callback
    }
    
    fun setOnSettingsSaved(callback: () -> Unit) {
        onSettingsSaved = callback
    }

    companion object {
        const val TAG = "com.example.taptopayandroid.fragments.SettingsFragment"
        
        fun newInstance(): SettingsFragment {
            return SettingsFragment()
        }
    }
}
