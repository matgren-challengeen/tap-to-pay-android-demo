package com.example.taptopayandroid.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class SessionData(
    val status: String = "",
    val cart_id: String? = null,
    val cart_total: Int? = null,
    val door_status: String = "locked"
)

class SessionRepository {
    private val db = FirebaseFirestore.getInstance()
    private var listenerRegistration: ListenerRegistration? = null

    fun listenToSession(sessionId: String): Flow<SessionData> = callbackFlow {
        Log.d("SessionRepository", "Listening to session: $sessionId")
        val docRef = db.collection("sessions").document(sessionId)

        listenerRegistration = docRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.w("SessionRepository", "Listen failed.", e)
                close(e)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val sessionData = snapshot.toObject(SessionData::class.java)
                if (sessionData != null) {
                    trySend(sessionData)
                }
            } else {
                Log.d("SessionRepository", "Current data: null")
            }
        }

        awaitClose {
            Log.d("SessionRepository", "Stopping listener")
            listenerRegistration?.remove()
        }
    }
}
