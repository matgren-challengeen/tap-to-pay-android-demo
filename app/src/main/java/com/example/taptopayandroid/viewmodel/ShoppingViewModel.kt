package com.example.taptopayandroid.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taptopayandroid.models.Cart
import com.example.taptopayandroid.repository.CartRepository
import com.example.taptopayandroid.repository.SessionData
import com.example.taptopayandroid.repository.SessionRepository
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class ShoppingViewModel : ViewModel() {
    private val sessionRepository = SessionRepository()
    private val cartRepository = CartRepository()

    private val _cart = MutableLiveData<Cart?>()
    val cart: LiveData<Cart?> = _cart

    private val _sessionStatus = MutableLiveData<String>()
    val sessionStatus: LiveData<String> = _sessionStatus

    fun startSession(sessionId: String) {
        viewModelScope.launch {
            sessionRepository.listenToSession(sessionId).collect { sessionData ->
                _sessionStatus.postValue(sessionData.status)
                if (sessionData.cart_id != null) {
                    fetchCart(sessionData.cart_id)
                }
            }
        }
    }

    private fun fetchCart(cartId: String) {
        viewModelScope.launch {
            cartRepository.getCart(cartId).collect { cart ->
                _cart.postValue(cart)
            }
        }
    }
}
