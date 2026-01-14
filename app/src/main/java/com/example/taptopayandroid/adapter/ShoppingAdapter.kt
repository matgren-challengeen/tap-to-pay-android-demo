package com.example.taptopayandroid.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.taptopayandroid.R
import com.example.taptopayandroid.models.LineItem
import java.text.NumberFormat
import java.util.Locale

class ShoppingAdapter : ListAdapter<LineItem, ShoppingAdapter.CartViewHolder>(CartDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_cart, parent, false)
        return CartViewHolder(view)
    }

    override fun onBindViewHolder(holder: CartViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    class CartViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.item_title)
        private val quantity: TextView = itemView.findViewById(R.id.item_quantity)
        private val price: TextView = itemView.findViewById(R.id.item_price)

        fun bind(item: LineItem) {
            title.text = item.title
            quantity.text = "Qty: ${item.quantity}"
            
            // Format price with currency symbol
            val priceValue = item.unit_price / 100.0
            val formatter = NumberFormat.getCurrencyInstance(Locale.US)
            price.text = formatter.format(priceValue)
        }
    }

    class CartDiffCallback : DiffUtil.ItemCallback<LineItem>() {
        override fun areItemsTheSame(oldItem: LineItem, newItem: LineItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: LineItem, newItem: LineItem): Boolean {
            return oldItem == newItem
        }
    }
}
