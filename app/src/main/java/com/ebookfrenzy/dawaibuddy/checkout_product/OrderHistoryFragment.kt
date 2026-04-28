package com.ebookfrenzy.dawaibuddy.checkout_product

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ebookfrenzy.dawaibuddy.R
import com.ebookfrenzy.dawaibuddy.databinding.FragmentOrderHistoryBinding
import com.ebookfrenzy.dawaibuddy.databinding.ItemOrderBinding
import com.ebookfrenzy.dawaibuddy.objects.Order
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale

class OrderHistoryFragment : Fragment() {
    private var _binding: FragmentOrderHistoryBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOrderHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Hide bottom navigation immediately
        setBottomNavigationVisibility(View.GONE)

        binding.ivBack.setOnClickListener { findNavController().popBackStack() }

        val adapter = OrderAdapter()
        binding.rvOrders.layoutManager = LinearLayoutManager(requireContext())
        binding.rvOrders.adapter = adapter

        fetchOrders(adapter)
    }

    private fun fetchOrders(adapter: OrderAdapter) {
        val userId = auth.currentUser?.uid ?: return

        binding.progressBar.visibility = View.VISIBLE

        db.collection("orders")
            .whereEqualTo("userId", userId) // Filter by logged-in user!
            .get()
            .addOnSuccessListener { result ->
                if (_binding == null) return@addOnSuccessListener
                binding.progressBar.visibility = View.GONE

                // Map the documents to the Order object and sort by timestamp (newest first)
                val ordersList = result.mapNotNull { doc ->
                    try {
                        doc.toObject(Order::class.java).apply { orderId = doc.id }
                    } catch(e: Exception) {
                        null
                    }
                }.sortedByDescending { it.timestamp }

                adapter.submitList(ordersList)

                if (ordersList.isEmpty()) {
                    binding.tvNoOrders.visibility = View.VISIBLE
                } else {
                    binding.tvNoOrders.visibility = View.GONE
                }
            }
            .addOnFailureListener {
                if (_binding == null) return@addOnFailureListener
                binding.progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Failed to load orders", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Hides or shows the BottomNavigationView in the hosting activity.
     */
    private fun setBottomNavigationVisibility(visibility: Int) {
        val activity = requireActivity()

        // Using your specific ID 'bottomNavigation'
        val navView = activity.findViewById<View>(R.id.bottomNavigation)
            ?: activity.findViewById<View>(resources.getIdentifier("bottom_nav", "id", activity.packageName))
            ?: activity.findViewById<View>(resources.getIdentifier("nav_view", "id", activity.packageName))

        if (navView != null) {
            navView.visibility = visibility
        } else {
            // Fallback: Search hierarchy if ID didn't match
            val rootView = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)
            findAndSetBottomNav(rootView, visibility)
        }
    }

    private fun findAndSetBottomNav(viewGroup: ViewGroup, visibility: Int) {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child.javaClass.simpleName.contains("BottomNavigationView")) {
                child.visibility = visibility
                return
            } else if (child is ViewGroup) {
                findAndSetBottomNav(child, visibility)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Ensure it stays hidden when returning to this fragment
        setBottomNavigationVisibility(View.GONE)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Restore bottom navigation bar visibility when leaving fragment
        setBottomNavigationVisibility(View.VISIBLE)
        _binding = null
    }

    inner class OrderAdapter : RecyclerView.Adapter<OrderAdapter.OrderViewHolder>() {
        private var orders = listOf<Order>()

        fun submitList(newOrders: List<Order>) {
            orders = newOrders
            notifyDataSetChanged()
        }

        inner class OrderViewHolder(val binding: ItemOrderBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
            return OrderViewHolder(ItemOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
            val order = orders[position]
            with(holder.binding) {
                tvOrderId.text = "Order #${order.orderId.takeLast(6).uppercase()}"
                tvOrderStatus.text = order.status
                tvOrderTotal.text = "₹${order.totalAmount.toInt()}"

                val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                tvOrderDate.text = order.timestamp?.toDate()?.let { sdf.format(it) } ?: "Just now"

                val itemNames = order.items.joinToString(", ") { "${it.quantity}x ${it.name}" }
                tvOrderItems.text = itemNames
            }
        }

        override fun getItemCount() = orders.size
    }
}