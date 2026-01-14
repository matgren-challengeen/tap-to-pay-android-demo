package com.example.taptopayandroid.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Manages app settings with secure storage for sensitive data like password.
 * Password can be reset by connecting phone via USB and clearing app data,
 * or by placing a config file in external storage.
 */
class SettingsManager(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "venloop_tap_settings"
        private const val KEY_ADMIN_PASSWORD = "admin_password"
        private const val KEY_SELECTED_LOCATION_ID = "selected_location_id"
        private const val KEY_SELECTED_LOCATION_NAME = "selected_location_name"
        private const val KEY_APP_TITLE = "app_title"
        
        private const val DEFAULT_PASSWORD = "admin"
        private const val DEFAULT_APP_TITLE = "Venloop Tap"
    }
    
    private val prefs: SharedPreferences
    
    init {
        // Use encrypted preferences for security
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    // Password Management
    fun verifyPassword(password: String): Boolean {
        val storedPassword = prefs.getString(KEY_ADMIN_PASSWORD, DEFAULT_PASSWORD)
        return password == storedPassword
    }
    
    fun changePassword(newPassword: String) {
        prefs.edit().putString(KEY_ADMIN_PASSWORD, newPassword).apply()
    }
    
    fun resetPasswordToDefault() {
        prefs.edit().putString(KEY_ADMIN_PASSWORD, DEFAULT_PASSWORD).apply()
    }
    
    // Location Management
    fun getSelectedLocationId(): String? {
        return prefs.getString(KEY_SELECTED_LOCATION_ID, null)
    }
    
    fun getSelectedLocationName(): String {
        return prefs.getString(KEY_SELECTED_LOCATION_NAME, "Not selected") ?: "Not selected"
    }
    
    fun setSelectedLocation(id: String, name: String) {
        prefs.edit()
            .putString(KEY_SELECTED_LOCATION_ID, id)
            .putString(KEY_SELECTED_LOCATION_NAME, name)
            .apply()
    }
    
    // App Title Management
    fun getAppTitle(): String {
        return prefs.getString(KEY_APP_TITLE, DEFAULT_APP_TITLE) ?: DEFAULT_APP_TITLE
    }
    
    fun setAppTitle(title: String) {
        prefs.edit().putString(KEY_APP_TITLE, title).apply()
    }
    
    // Clear all settings (for USB reset)
    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
