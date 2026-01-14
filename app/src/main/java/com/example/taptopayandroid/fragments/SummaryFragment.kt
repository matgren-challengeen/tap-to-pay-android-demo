package com.example.taptopayandroid.fragments

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.taptopayandroid.NavigationListener
import com.example.taptopayandroid.R

class SummaryFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_summary, container, false)

        val totalText = arguments?.getString("total_amount") ?: "$0.00"
        val totalView = view.findViewById<TextView>(R.id.final_total)
        totalView.text = totalText

        val btnDone = view.findViewById<Button>(R.id.btn_done)
        btnDone.setOnClickListener {
            (activity as? NavigationListener)?.onCancel() // Reuses cancel to go back to Idle
        }

        // Auto-close after 10 seconds
        Handler(Looper.getMainLooper()).postDelayed({
             if (isAdded) {
                 (activity as? NavigationListener)?.onCancel()
             }
        }, 10000)

        return view
    }

    companion object {
        const val TAG = "com.example.taptopayandroid.fragments.SummaryFragment"
        fun newInstance(totalAmount: String): SummaryFragment {
            val fragment = SummaryFragment()
            val args = Bundle()
            args.putString("total_amount", totalAmount)
            fragment.arguments = args
            return fragment
        }
    }
}
