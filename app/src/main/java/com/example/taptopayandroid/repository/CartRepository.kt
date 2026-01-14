package com.example.taptopayandroid.repository

import com.example.taptopayandroid.ApiClient
import com.example.taptopayandroid.models.Cart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.awaitResponse

class CartRepository {
    fun getCart(cartId: String): Flow<Cart?> = flow {
        try {
            val response = ApiClient.getCart(cartId).awaitResponse()
            if (response.isSuccessful) {
                emit(response.body()?.cart)
            } else {
                emit(null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emit(null)
        }
    }
}
