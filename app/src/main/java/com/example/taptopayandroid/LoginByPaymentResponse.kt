package com.example.taptopayandroid

/**
 * Response from POST /store/auth/login-by-payment
 * Contains session information after successful card authorization.
 */
data class LoginByPaymentResponse(
    val session_id: String,
    val customer_id: String,
    val fingerprint: String?,
    val cart_id: String?
)
