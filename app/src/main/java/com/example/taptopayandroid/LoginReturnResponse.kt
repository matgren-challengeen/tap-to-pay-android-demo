package com.example.taptopayandroid

data class LoginReturnResponse(
    val session_id: String,
    val customer_id: String,
    val returnable_count: Int
)
