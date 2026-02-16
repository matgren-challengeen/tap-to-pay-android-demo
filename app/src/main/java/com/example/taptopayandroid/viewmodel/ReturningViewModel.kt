package com.example.taptopayandroid.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.taptopayandroid.repository.SessionRepository
import kotlinx.coroutines.launch

data class ReturnedItem(
    val id: String,
    val title: String,
    val quantity: Int,
    val refund_amount: Int // in cents
)

data class ReturnSession(
    val status: String = "return_mode",
    val returned_items: List<ReturnedItem> = emptyList(),
    val total_refund: Int = 0, // in cents
    val eligible_count: Int = 0,
    val foreign_count: Int = 0
)

class ReturningViewModel : ViewModel() {
    private val sessionRepository = SessionRepository()

    private val _returnSession = MutableLiveData<ReturnSession>()
    val returnSession: LiveData<ReturnSession> = _returnSession

    private val _sessionStatus = MutableLiveData<String>()
    val sessionStatus: LiveData<String> = _sessionStatus

    fun startSession(sessionId: String) {
        viewModelScope.launch {
            sessionRepository.listenToSession(sessionId).collect { sessionData ->
                _sessionStatus.postValue(sessionData.status)
                
                // Parse returned items from session data
                val items = mutableListOf<ReturnedItem>()
                var totalRefund = 0
                
                // The backend stores returned items in the session
                // We'll use cart_total as the refund amount (negative value means refund)
                val refundAmount = sessionData.cart_total ?: 0
                
                // Create synthetic items for display based on session data
                // In a real implementation, the backend would store actual returned item details
                if (refundAmount > 0) {
                    // Estimate based on refund amount (50 cents per small jar, 70 cents per big jar)
                    val estimatedSmallJars = refundAmount / 50
                    if (estimatedSmallJars > 0) {
                        items.add(ReturnedItem(
                            id = "small_jar",
                            title = "Small Jar Deposit",
                            quantity = estimatedSmallJars,
                            refund_amount = estimatedSmallJars * 50
                        ))
                    }
                    totalRefund = refundAmount
                }
                
                _returnSession.postValue(ReturnSession(
                    status = sessionData.status,
                    returned_items = items,
                    total_refund = totalRefund
                ))
            }
        }
    }
}
