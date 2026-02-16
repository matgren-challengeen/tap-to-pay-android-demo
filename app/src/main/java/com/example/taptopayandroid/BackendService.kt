package com.example.taptopayandroid

import com.example.taptopayandroid.ConnectionToken
import com.example.taptopayandroid.PaymentIntentCreationResponse
import com.example.taptopayandroid.models.StoreCartResponse
import retrofit2.Call
import retrofit2.http.Field
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * The `BackendService` interface defines the API contract for the Tap-to-Pay backend.
 * This contract is implemented by both the Stub Backend (for development) and the 
 * Medusa.js Backend (for production).
 */
interface BackendService {

    /**
     * Get a connection token string from the backend.
     * Required by Stripe Terminal SDK to authenticate.
     */
    @POST("connection_token")
    fun getConnectionToken(): Call<ConnectionToken>

    /**
     * Create a PaymentIntent with pre-authorization (capture_method: 'manual').
     * This is called at tap time to authorize the maximum possible amount.
     */
    @FormUrlEncoded
    @POST("create_payment_intent")
    fun createPaymentIntent(
        @FieldMap createPaymentIntentParams: Map<String, String>
    ): Call<PaymentIntentCreationResponse>

    /**
     * Capture a pre-authorized PaymentIntent.
     * Called by backend when door closes (not directly by app in normal flow).
     */
    @FormUrlEncoded
    @POST("capture_payment_intent")
    fun capturePaymentIntent(@Field("payment_intent_id") id: String): Call<Void>

    /**
     * Cancel a pre-authorized PaymentIntent.
     * Called if cart is empty when door closes.
     */
    @FormUrlEncoded
    @POST("cancel_payment_intent")
    fun cancelPaymentIntent(@Field("payment_intent_id") id: String): Call<Void>

    /**
     * Login using an authorized PaymentIntent.
     * Backend extracts card fingerprint, identifies/creates customer, returns session.
     */
    @FormUrlEncoded
    @POST("store/auth/login-by-payment")
    fun loginByPayment(
        @Field("payment_intent_id") paymentIntentId: String,
        @Field("manual_fingerprint") manualFingerprint: String? = null
    ): Call<LoginByPaymentResponse>

    /**
     * Login for return flow (returning containers).
     */
    @FormUrlEncoded
    @POST("store/auth/login-return")
    fun loginReturn(
        @Field("payment_intent_id") paymentIntentId: String?,
        @Field("manual_fingerprint") manualFingerprint: String? = null
    ): Call<LoginReturnResponse>

    /**
     * Get cart data by ID.
     */
    @GET("store/carts/{id}")
    fun getCart(@Path("id") id: String): Call<StoreCartResponse>
}

