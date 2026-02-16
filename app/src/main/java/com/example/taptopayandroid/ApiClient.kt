package com.example.taptopayandroid

import com.example.taptopayandroid.BuildConfig
import com.example.taptopayandroid.PaymentIntentCreationResponse
import com.example.taptopayandroid.models.StoreCartResponse
import com.stripe.stripeterminal.external.models.ConnectionTokenException
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException

/**
 * The `ApiClient` is a singleton object used to make calls to our backend.
 * This implements the API contract defined in BackendService.
 */
object ApiClient {

    private val client = OkHttpClient.Builder()
        .build()
    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.EXAMPLE_BACKEND_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    private val service: BackendService = retrofit.create(BackendService::class.java)

    /**
     * Get connection token for Stripe Terminal SDK.
     */
    @Throws(ConnectionTokenException::class)
    internal fun createConnectionToken(): String {
        try {
            val result = service.getConnectionToken().execute()
            if (result.isSuccessful && result.body() != null) {
                return result.body()!!.secret
            } else {
                throw ConnectionTokenException("Creating connection token failed")
            }
        } catch (e: IOException) {
            throw ConnectionTokenException("Creating connection token failed", e)
        }
    }

    /**
     * Create a PaymentIntent with pre-authorization.
     * For Tap-to-Pay, this is called before collecting payment.
     */
    internal fun createPaymentIntent(
        amount: Long,
        currency: String,
        callback: Callback<PaymentIntentCreationResponse>
    ) {
        val createPaymentIntentParams = buildMap<String, String> {
            put("amount", amount.toString())
            put("currency", currency)
            // capture_method: 'manual' is set by backend
        }

        service.createPaymentIntent(createPaymentIntentParams).enqueue(callback)
    }

    /**
     * Login using an authorized PaymentIntent.
     * Called after successful card tap and payment confirmation.
     */
    internal fun loginByPayment(
        paymentIntentId: String,
        manualFingerprint: String? = null,
        callback: Callback<LoginByPaymentResponse>
    ) {
        service.loginByPayment(paymentIntentId, manualFingerprint).enqueue(callback)
    }

    /**
     * Login for return containers flow.
     */
    fun loginReturn(
        paymentIntentId: String?,
        manualFingerprint: String? = null,
        callback: (String?, Int) -> Unit
    ) {
        service.loginReturn(paymentIntentId, manualFingerprint).enqueue(object : Callback<LoginReturnResponse> {
            override fun onResponse(call: Call<LoginReturnResponse>, response: retrofit2.Response<LoginReturnResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    callback(body.session_id, body.returnable_count)
                } else {
                    callback(null, 0)
                }
            }

            override fun onFailure(call: Call<LoginReturnResponse>, t: Throwable) {
                t.printStackTrace()
                callback(null, 0)
            }
        })
    }

    /**
     * Capture a pre-authorized PaymentIntent (usually called by backend, not app).
     */
    internal fun capturePaymentIntent(id: String) {
        service.capturePaymentIntent(id).execute()
    }

    /**
     * Cancel a pre-authorized PaymentIntent.
     */
    internal fun cancelPaymentIntent(
        id: String,
        callback: Callback<Void>
    ) {
        service.cancelPaymentIntent(id).enqueue(callback)
    }

    /**
     * Get cart data by ID.
     */
    internal fun getCart(id: String): Call<StoreCartResponse> {
        return service.getCart(id)
    }
}

