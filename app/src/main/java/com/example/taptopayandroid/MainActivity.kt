package com.example.taptopayandroid

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.example.taptopayandroid.fragments.ConnectReaderFragment
import com.example.taptopayandroid.fragments.ShoppingFragment
import com.example.taptopayandroid.fragments.SummaryFragment
import android.widget.Toast
import com.example.taptopayandroid.viewmodel.ShoppingViewModel
import com.example.taptopayandroid.utils.DeviceUtils

import com.stripe.stripeterminal.Terminal
import com.stripe.stripeterminal.external.callable.*
import com.stripe.stripeterminal.external.models.*
import com.stripe.stripeterminal.external.models.*
import com.stripe.stripeterminal.external.models.DiscoveryConfiguration
import com.stripe.stripeterminal.external.models.CollectSetupIntentConfiguration
import com.stripe.stripeterminal.external.models.ConnectionConfiguration
import com.stripe.stripeterminal.external.callable.TapToPayReaderListener
import com.stripe.stripeterminal.external.models.DisconnectReason
import com.stripe.stripeterminal.external.callable.Cancelable
import com.stripe.stripeterminal.external.models.Reader
import com.stripe.stripeterminal.external.models.AllowRedisplay
import com.stripe.stripeterminal.log.LogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import com.stripe.stripeterminal.external.models.PaymentIntent
import com.stripe.stripeterminal.external.models.SetupIntent
import retrofit2.Call
import retrofit2.Response
import retrofit2.Callback

var SKIP_TIPPING: Boolean = true

class MainActivity : AppCompatActivity(), NavigationListener {
    // Register the permissions callback to handles the response to the system permissions dialog.
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
        ::onPermissionResult
    )

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Apply saved language preference before inflating any views
        applySavedLocale()
        
        setContentView(R.layout.activity_main)

        navigateTo(ConnectReaderFragment.TAG, ConnectReaderFragment(), false)

        requestPermissionsIfNecessarySdk31()

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            BluetoothAdapter.getDefaultAdapter()?.let { adapter ->
                if (!adapter.isEnabled) {
                    adapter.enable()
                }
            }
        } else {
            Log.w(MainActivity::class.java.simpleName, "Failed to acquire Bluetooth permission")
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun requestPermissionsIfNecessarySdk31() {
        // Check for location and bluetooth permissions
        val deniedPermissions = mutableListOf<String>().apply {
            if (!isGranted(Manifest.permission.ACCESS_FINE_LOCATION)) add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (!isGranted(Manifest.permission.BLUETOOTH_CONNECT)) add(Manifest.permission.BLUETOOTH_CONNECT)
            if (!isGranted(Manifest.permission.BLUETOOTH_SCAN)) add(Manifest.permission.BLUETOOTH_SCAN)
        }.toTypedArray()

        if (deniedPermissions.isNotEmpty()) {
            // If we don't have them yet, request them before doing anything else
            requestPermissionLauncher.launch(deniedPermissions)
        } else if (!Terminal.isInitialized() && verifyGpsEnabled()) {
            initialize()
        }
    }

    private fun isGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun onPermissionResult(result: Map<String, Boolean>) {
        val deniedPermissions: List<String> = result
            .filter { !it.value }
            .map { it.key }

        // If we receive a response to our permission check, initialize
        if (deniedPermissions.isEmpty() && !Terminal.isInitialized() && verifyGpsEnabled()) {
            initialize()
        }
    }

    private fun verifyGpsEnabled(): Boolean {
        val locationManager: LocationManager? =
            applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager?
        var gpsEnabled = false

        try {
            gpsEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) ?: false
        } catch (exception: Exception) {}

        if (!gpsEnabled) {
            // notify user
        }

        return gpsEnabled
    }

    private fun initialize() {
        // Initialize the Terminal as soon as possible
        try {
            if (!Terminal.isInitialized()) {
                Terminal.init(
                    applicationContext, LogLevel.VERBOSE, TokenProvider(),
                    TerminalEventListener(), null
                )
            }
        } catch (e: TerminalException) {
            throw RuntimeException(
                "Location services are required in order to initialize " +
                        "the Terminal.",
                e
            )
        }

        loadLocations()
    }

    private val mutableListState = MutableStateFlow(LocationListState())

    private val locationCallback = object : LocationListCallback {
        override fun onFailure(e: TerminalException) {
            e.printStackTrace()
        }

        override fun onSuccess(locations: List<Location>, hasMore: Boolean) {
            mutableListState.value = mutableListState.value.let {
                it.copy(
                    locations = it.locations + locations,
                    hasMore = hasMore,
                    isLoading = false,
                )
            }
        }
    }



    private fun loadLocations() {
        Terminal.getInstance().listLocations(
            ListLocationsParameters.Builder().apply {
                limit = 100
            }.build(),
            locationCallback
        )
    }

    private var isReturnMode = false

    override fun onShowReturnInstructions() {
        Log.d("MainActivity", "Showing Return Instructions")
        navigateTo(
            com.example.taptopayandroid.fragments.ReturnInstructionsFragment.TAG,
            com.example.taptopayandroid.fragments.ReturnInstructionsFragment(),
            true,
            true // Add to back stack so user can go back
        )
    }

    override fun onStartReturnFlow() {
        Log.d("MainActivity", "Starting Return Flow")
        isReturnMode = true
        onStartLoginFlow(null)
    }

    private fun connectReader(){
        if (DeviceUtils.isEmulator()) {
             Log.d("MainActivity", "Emulator detected. Bypassing Reader Connection...")
             runOnUiThread {
                 Toast.makeText(this@MainActivity, "Simulating Reader Connection (Emulator Mode)", Toast.LENGTH_LONG).show()
                 // Wait a moment to simulate "Connecting..."
                 android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                     // Call login with a fake PM ID
                     handleAuthSuccess("emulator_bypass_${System.currentTimeMillis()}")
                 }, 1500)
             }
             return
        }

        val config = DiscoveryConfiguration.TapToPayDiscoveryConfiguration(
            isSimulated = true,
        )

        Terminal.getInstance().discoverReaders(config, discoveryListener = object :
            DiscoveryListener {
            override fun onUpdateDiscoveredReaders(readers: List<Reader>) {
                Log.d("MainActivity", "Discovered readers: ${readers.size}")
                readers.forEach { reader ->
                    Log.d("MainActivity", "Reader: ${reader.serialNumber}, NetworkStatus: ${reader.networkStatus}, IsSimulated: ${reader.isSimulated}")
                }
                
                val validReaders = readers.filter { it.networkStatus != Reader.NetworkStatus.OFFLINE }
                if (validReaders.isEmpty()) {
                    Log.d("MainActivity", "No online readers found")
                    runOnUiThread {
                        val manager: FragmentManager = supportFragmentManager
                        val fragment: Fragment? = manager.findFragmentByTag(ConnectReaderFragment.TAG)
                        (fragment as? ConnectReaderFragment)?.resetUI()
                        Toast.makeText(this@MainActivity, "No online readers found", Toast.LENGTH_SHORT).show()
                    }
                    return
                }
                var reader = validReaders[0]

                val connectionConfig = ConnectionConfiguration.TapToPayConnectionConfiguration(
                    "${mutableListState.value.locations[0].id}",
                    tapToPayReaderListener = object : TapToPayReaderListener {
                        override fun onDisconnect(reason: DisconnectReason) {
                            Log.d("MainActivity", "Reader disconnected: $reason")
                        }
                        override fun onReaderReconnectFailed(reader: Reader) {
                            Log.d("MainActivity", "Reader reconnect failed")
                        }
                        override fun onReaderReconnectStarted(reader: Reader, cancelable: Cancelable, reason: DisconnectReason) {
                             Log.d("MainActivity", "Reader reconnect started: $reason")
                        }
                        override fun onReaderReconnectSucceeded(reader: Reader) {
                            Log.d("MainActivity", "Reader reconnect succeeded")
                        }
                    }
                )

                Terminal.getInstance().connectReader(
                    reader,
                    connectionConfig,
                    object: ReaderCallback {
                        override fun onFailure(e: TerminalException) {
                            e.printStackTrace()
                        }

                        override fun onSuccess(reader: Reader) {
                            // Update the UI with the location name and terminal ID
                            runOnUiThread {
                                val manager: FragmentManager = supportFragmentManager
                                val fragment: Fragment? = manager.findFragmentByTag(ConnectReaderFragment.TAG)

                                if(reader.id !== null && mutableListState.value.locations.isNotEmpty() && mutableListState.value.locations[0].displayName !== null){
                                    (fragment as ConnectReaderFragment).updateReaderId(
                                        mutableListState.value.locations[0].displayName!!, reader.id!!
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }, object : com.stripe.stripeterminal.external.callable.Callback {
            override fun onSuccess() {
                println("Finished discovering readers")
            }

            override fun onFailure(e: TerminalException) {
                Log.e("MainActivity", "Discover readers failed", e)
                runOnUiThread {
                    val manager: FragmentManager = supportFragmentManager
                    val fragment: Fragment? = manager.findFragmentByTag(ConnectReaderFragment.TAG)
                    (fragment as? ConnectReaderFragment)?.resetUI()
                    Toast.makeText(this@MainActivity, "Discovery failed: ${e.errorMessage}", Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    private fun handleAuthSuccess(paymentIntentId: String, manualFingerprint: String? = null) {
        if (isReturnMode) {
            performReturnLogin(paymentIntentId, manualFingerprint)
        } else {
            performLoginByPayment(paymentIntentId, manualFingerprint)
        }
    }

    private fun performReturnLogin(pmId: String?, fingerprint: String? = null) {
        ApiClient.loginReturn(pmId, fingerprint) { sessionId, count ->
             if (sessionId != null) {
                 Log.d("MainActivity", "Return Login Successful. Session: $sessionId, Count: $count")
                 isReturnMode = false // Reset
                 runOnUiThread {
                     Toast.makeText(this@MainActivity, "Return mode active! Insert jars now.", Toast.LENGTH_SHORT).show()
                     navigateTo(
                         com.example.taptopayandroid.fragments.ReturningFragment.TAG,
                         com.example.taptopayandroid.fragments.ReturningFragment.newInstance(sessionId),
                         true
                     )
                 }
             } else {
                 Log.e("MainActivity", "Return Login Failed")
                 runOnUiThread { Toast.makeText(this@MainActivity, "Return Login Failed", Toast.LENGTH_SHORT).show() }
             }
        }
    }

    // Navigate to Fragment
    private fun navigateTo(
        tag: String,
        fragment: Fragment,
        replace: Boolean = true,
        addToBackStack: Boolean = false,
    ) {
        val frag = supportFragmentManager.findFragmentByTag(tag) ?: fragment
        supportFragmentManager
            .beginTransaction()
            .apply {
                if (replace) {
                    replace(R.id.container, frag, tag)
                } else {
                    add(R.id.container, frag, tag)
                }

                if (addToBackStack) {
                    addToBackStack(tag)
                }
            }
            .commitAllowingStateLoss()
    }

    override fun onConnectReader(){
        connectReader()
    }

    // Maximum pre-authorization amount in cents (e.g., $50)
    private val MAX_PREAUTH_AMOUNT = 5000L
    private val CURRENCY = "usd"
    
    override fun onStartLoginFlow(email: String?) {
        Log.d("MainActivity", "Starting Pre-Auth login flow with email: $email")
        
        // In emulator mode, bypass Terminal SDK and directly simulate login
        if (DeviceUtils.isEmulator()) {
            Log.d("MainActivity", "Emulator detected. Bypassing Terminal SDK...")
            
            // Get manual fingerprint if available
            val fragment = supportFragmentManager.findFragmentByTag(ConnectReaderFragment.TAG) as? ConnectReaderFragment
            val fingerprint = fragment?.getEnteredFingerprint()
            
            runOnUiThread {
                Toast.makeText(this@MainActivity, "Simulating Card Tap (Fingerprint: $fingerprint)", Toast.LENGTH_SHORT).show()
            }
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                handleAuthSuccess("emulator_pi_${System.currentTimeMillis()}", fingerprint)
            }, 500)
            return
        }
        
        // Step 1: Create a PaymentIntent with capture_method: 'manual' (pre-auth)
        ApiClient.createPaymentIntent(MAX_PREAUTH_AMOUNT, CURRENCY, object : Callback<PaymentIntentCreationResponse> {
            override fun onResponse(call: Call<PaymentIntentCreationResponse>, response: Response<PaymentIntentCreationResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val secret = response.body()!!.secret
                    Log.d("MainActivity", "PaymentIntent created, retrieving...")
                    // Step 2: Retrieve the PaymentIntent to collect payment
                    Terminal.getInstance().retrievePaymentIntent(secret, retrievePaymentIntentCallback)
                } else {
                    Log.e("MainActivity", "Create PaymentIntent failed: ${response.errorBody()?.string()}")
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Failed to create payment", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onFailure(call: Call<PaymentIntentCreationResponse>, t: Throwable) {
                Log.e("MainActivity", "Create PaymentIntent failed", t)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }
    
    override fun onOpenSettings() {
        val settingsFragment = com.example.taptopayandroid.fragments.SettingsFragment.newInstance()
        settingsFragment.setOnBackPressed {
            navigateTo(ConnectReaderFragment.TAG, ConnectReaderFragment(), true)
        }
        settingsFragment.setOnSettingsSaved {
            // Refresh UI with new settings
            navigateTo(ConnectReaderFragment.TAG, ConnectReaderFragment(), true)
        }
        navigateTo(com.example.taptopayandroid.fragments.SettingsFragment.TAG, settingsFragment, true)
    }
    
    override fun onSettingsClosed() {
        navigateTo(ConnectReaderFragment.TAG, ConnectReaderFragment(), true)
    }

    // Step 2 callback: Retrieve PaymentIntent
    private val retrievePaymentIntentCallback by lazy {
        object : PaymentIntentCallback {
            override fun onSuccess(paymentIntent: PaymentIntent) {
                Log.d("MainActivity", "PaymentIntent retrieved: ${paymentIntent.id}")
                // Step 3: Collect payment method (user taps card)
                Terminal.getInstance().collectPaymentMethod(
                    paymentIntent, 
                    collectPaymentMethodCallback
                )
            }

            override fun onFailure(e: TerminalException) {
                Log.e("MainActivity", "Retrieve PaymentIntent failed", e)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Payment error: ${e.errorMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Step 3 callback: Collect payment method (card tap)
    private val collectPaymentMethodCallback by lazy {
        object : PaymentIntentCallback {
            override fun onSuccess(paymentIntent: PaymentIntent) {
                Log.d("MainActivity", "Payment method collected, confirming...")
                // Step 4: Confirm the PaymentIntent (authorize the pre-auth)
                Terminal.getInstance().confirmPaymentIntent(paymentIntent, confirmPaymentIntentCallback)
            }

            override fun onFailure(e: TerminalException) {
                Log.e("MainActivity", "Collect PaymentMethod failed", e)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Card read failed: ${e.errorMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Step 4 callback: Confirm PaymentIntent (authorize pre-auth)
    private val confirmPaymentIntentCallback by lazy {
        object : PaymentIntentCallback {
            override fun onSuccess(paymentIntent: PaymentIntent) {
                Log.d("MainActivity", "PaymentIntent confirmed: ${paymentIntent.id}, status: ${paymentIntent.status}")
                // Step 5: Login with the authorized PaymentIntent
                val piId = paymentIntent.id
                if (piId != null) {
                    performLoginByPayment(piId)
                } else {
                    Log.e("MainActivity", "PaymentIntent ID is null after confirm")
                }
            }

            override fun onFailure(e: TerminalException) {
                Log.e("MainActivity", "Confirm PaymentIntent failed", e)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Authorization failed: ${e.errorMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Step 5: Login using the authorized PaymentIntent
    private fun performLoginByPayment(paymentIntentId: String, manualFingerprint: String? = null) {
        ApiClient.loginByPayment(paymentIntentId, manualFingerprint, object : Callback<LoginByPaymentResponse> {
            override fun onResponse(call: Call<LoginByPaymentResponse>, response: Response<LoginByPaymentResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    Log.d("MainActivity", "Login Successful: ${response.body()}")
                    val sessionId = response.body()!!.session_id
                    navigateTo(ShoppingFragment.TAG, ShoppingFragment.newInstance(sessionId), true)
                } else {
                    Log.e("MainActivity", "Login Failed: ${response.code()}")
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Login Failed: ${response.code()}", Toast.LENGTH_LONG).show()
                    }
                }
            }

            override fun onFailure(call: Call<LoginByPaymentResponse>, t: Throwable) {
                Log.e("MainActivity", "Login API failed", t)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Login API Error: ${t.message}", Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    override fun onCancel(){
        navigateTo(ConnectReaderFragment.TAG, ConnectReaderFragment(), true)
    }

    override fun onSessionCompleted(totalAmount: String) {
        navigateTo(SummaryFragment.TAG, SummaryFragment.newInstance(totalAmount), true)
    }
    
    /**
     * Apply the saved language preference to the app's locale.
     * This ensures language persists across app restarts and all screens.
     */
    private fun applySavedLocale() {
        val settingsManager = com.example.taptopayandroid.settings.SettingsManager(this)
        val languageCode = settingsManager.getLanguage()
        val locale = java.util.Locale(languageCode)
        java.util.Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }
}