package com.example.taptopayandroid.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.taptopayandroid.BuildConfig
import com.example.taptopayandroid.NavigationListener
import com.example.taptopayandroid.R
import com.example.taptopayandroid.adapter.ShoppingAdapter
import com.example.taptopayandroid.models.LineItem
import com.example.taptopayandroid.viewmodel.ReturningViewModel
import java.text.NumberFormat
import java.util.Locale

class ReturningFragment : Fragment() {

    private val viewModel: ReturningViewModel by viewModels()
    private lateinit var adapter: ShoppingAdapter
    private var currentSessionId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_returning, container, false)

        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view_returns)
        val totalRefund = view.findViewById<TextView>(R.id.total_refund)
        val itemCount = view.findViewById<TextView>(R.id.item_count)
        val sessionStatus = view.findViewById<TextView>(R.id.session_status)
        val sessionIdLabel = view.findViewById<TextView>(R.id.session_id_label)
        val appVersion = view.findViewById<TextView>(R.id.app_version)

        adapter = ShoppingAdapter()
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        // Set app version
        appVersion.text = "v${BuildConfig.VERSION_NAME}"

        // Arguments passed from MainActivity
        currentSessionId = arguments?.getString("session_id")
        if (currentSessionId != null) {
            viewModel.startSession(currentSessionId!!)
            // Show truncated session ID
            val shortId = if (currentSessionId!!.length > 20) {
                "...${currentSessionId!!.takeLast(12)}"
            } else {
                currentSessionId
            }
            sessionIdLabel.text = "Session: $shortId"
        }

        viewModel.returnSession.observe(viewLifecycleOwner) { session ->
            if (session != null) {
                // Convert returned items to LineItems for the adapter
                val lineItems = session.returned_items.map { item ->
                    LineItem(
                        id = item.id,
                        title = item.title,
                        quantity = item.quantity,
                        unit_price = (item.refund_amount / item.quantity).toLong(),
                        thumbnail = null
                    )
                }
                adapter.submitList(lineItems)
                
                // Update item count
                val totalItems = session.returned_items.sumOf { it.quantity }
                itemCount.text = totalItems.toString()
                
                // Format refund with currency (positive value for display)
                val refundValue = session.total_refund / 100.0
                val formatter = NumberFormat.getCurrencyInstance(Locale.US)
                totalRefund.text = "+${formatter.format(refundValue)}"
            }
        }

        viewModel.sessionStatus.observe(viewLifecycleOwner) { status ->
            sessionStatus.text = "Status: $status"
            if (status == "completed") {
                val refundText = totalRefund.text.toString()
                (activity as? NavigationListener)?.onSessionCompleted(refundText)
            }
        }

        return view
    }

    companion object {
        const val TAG = "com.example.taptopayandroid.fragments.ReturningFragment"
        fun newInstance(sessionId: String): ReturningFragment {
            val fragment = ReturningFragment()
            val args = Bundle()
            args.putString("session_id", sessionId)
            fragment.arguments = args
            return fragment
        }
    }
}
