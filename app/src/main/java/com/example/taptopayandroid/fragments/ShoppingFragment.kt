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
import com.example.taptopayandroid.viewmodel.ShoppingViewModel
import java.text.NumberFormat
import java.util.Locale

class ShoppingFragment : Fragment() {

    private val viewModel: ShoppingViewModel by viewModels()
    private lateinit var adapter: ShoppingAdapter
    private var currentSessionId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_shopping, container, false)

        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view_cart)
        val totalAmount = view.findViewById<TextView>(R.id.total_amount)
        val itemCount = view.findViewById<TextView>(R.id.item_count)
        val sessionStatus = view.findViewById<TextView>(R.id.session_status)
        val sessionIdLabel = view.findViewById<TextView>(R.id.session_id_label)
        val appVersion = view.findViewById<TextView>(R.id.app_version)

        adapter = ShoppingAdapter()
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        // Set app version
        appVersion.text = "v${BuildConfig.VERSION_NAME}"

        // Arguments passed from MainActivity logic
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

        viewModel.cart.observe(viewLifecycleOwner) { cart ->
            if (cart != null) {
                // Group items by title and combine quantities
                val groupedItems = groupLineItems(cart.items)
                adapter.submitList(groupedItems)
                
                // Update item count (total of all quantities)
                val totalItems = groupedItems.sumOf { it.quantity }
                itemCount.text = totalItems.toString()
                
                // Format total with currency
                val totalValue = cart.total / 100.0
                val formatter = NumberFormat.getCurrencyInstance(Locale.US)
                totalAmount.text = formatter.format(totalValue)
            }
        }

        viewModel.sessionStatus.observe(viewLifecycleOwner) { status ->
            sessionStatus.text = "Status: $status"
            if (status == "completed") {
                val totalText = totalAmount.text.toString()
                (activity as? NavigationListener)?.onSessionCompleted(totalText)
            }
        }

        return view
    }
    
    /**
     * Groups line items by title and combines their quantities
     */
    private fun groupLineItems(items: List<LineItem>): List<LineItem> {
        return items.groupBy { it.title }
            .map { (title, group) ->
                LineItem(
                    id = group.first().id,
                    title = title,
                    quantity = group.sumOf { it.quantity },
                    unit_price = group.first().unit_price
                )
            }
    }

    companion object {
        const val TAG = "com.example.taptopayandroid.fragments.ShoppingFragment"
        fun newInstance(sessionId: String): ShoppingFragment {
            val fragment = ShoppingFragment()
            val args = Bundle()
            args.putString("session_id", sessionId)
            fragment.arguments = args
            return fragment
        }
    }
}
