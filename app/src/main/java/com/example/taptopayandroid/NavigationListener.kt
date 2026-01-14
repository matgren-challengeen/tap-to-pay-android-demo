package com.example.taptopayandroid


import com.stripe.stripeterminal.external.models.Reader

/**
 * An `Activity` that should be notified when various navigation activities have been triggered
 */
interface NavigationListener {
    /**
     * Notify the `Activity` that the user has requested to connect to the reader.
     */
    fun onConnectReader()

    fun onStartLoginFlow(email: String? = null)

    fun onStartReturnFlow()

    fun onSessionCompleted(totalAmount: String)
    
    fun onOpenSettings()
    
    fun onSettingsClosed()

    fun onCancel()
}
