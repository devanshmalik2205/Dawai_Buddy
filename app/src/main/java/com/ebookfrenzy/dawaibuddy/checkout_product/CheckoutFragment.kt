package com.ebookfrenzy.dawaibuddy.checkout_product

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.ebookfrenzy.dawaibuddy.R
import com.ebookfrenzy.dawaibuddy.databinding.FragmentCheckoutBinding
import com.ebookfrenzy.dawaibuddy.databinding.ItemCheckoutProductBinding
import com.ebookfrenzy.dawaibuddy.objects.CartItem
import com.ebookfrenzy.dawaibuddy.models.SharedCartViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class CheckoutFragment : Fragment() {

    private var _binding: FragmentCheckoutBinding? = null
    private val binding get() = _binding!!

    private val cartViewModel: SharedCartViewModel by activityViewModels()
    private val db = FirebaseFirestore.getInstance()

    private lateinit var adapter: CheckoutAdapter

    // Add a flag to track if we are processing an order
    private var isOrderPlaced = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCheckoutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Aggressively hide bottom navigation using a post to win race conditions
        binding.root.post {
            setBottomNavigationVisibility(View.GONE)
        }

        binding.ivBack.setOnClickListener { findNavController().popBackStack() }

        adapter = CheckoutAdapter { product, delta -> cartViewModel.updateQuantity(product, delta) }
        binding.rvCheckoutItems.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCheckoutItems.adapter = adapter

        cartViewModel.cartItems.observe(viewLifecycleOwner) { items ->
            // Prevent the "Cart is empty" action if the user just placed an order
            if (items.isEmpty() && !isOrderPlaced) {
                Toast.makeText(context, "Cart is empty", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
                return@observe
            }

            if (items.isNotEmpty()) {
                adapter.submitList(items.values.toList())
                updateBillDetails()
            }
        }

        binding.btnPlaceOrder.setOnClickListener {
            placeOrder()
        }
    }

    private fun updateBillDetails() {
        val total = cartViewModel.getTotalPrice()
        val handlingCharge = 8.0 // Standard generic charge mapped
        val grandTotal = total + handlingCharge

        binding.tvItemsTotal.text = "₹${total.toInt()}"
        binding.tvHandlingCharge.text = "₹${handlingCharge.toInt()}"
        binding.tvGrandTotal.text = "₹${grandTotal.toInt()}"
        binding.tvBottomTotal.text = "₹${grandTotal.toInt()}"
    }

    private fun placeOrder() {
        val items = cartViewModel.cartItems.value ?: return
        if (items.isEmpty()) return

        // Set flag to true so the observer ignores the cleared cart
        isOrderPlaced = true

        // Display full screen success loading graphics
        binding.cvOrderPlacedSuccess.visibility = View.VISIBLE
        binding.btnPlaceOrder.isEnabled = false

        // Fetch the currently logged-in user's ID
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"

        val orderData = hashMapOf(
            "userId" to userId,
            "items" to items.values.toList(),
            "totalAmount" to cartViewModel.getTotalPrice() + 8.0,
            "status" to "Placed",
            "timestamp" to FieldValue.serverTimestamp()
        )

        db.collection("orders").add(orderData).addOnSuccessListener {
            cartViewModel.clearCart()

            // Allow users to see the graphic for 3.5 seconds, then send home
            binding.root.postDelayed({
                if (isAdded) {
                    findNavController().popBackStack(R.id.nav_home, false)
                }
            }, 3500)

        }.addOnFailureListener {
            if (isAdded) {
                // Reset the flag if the order fails so the cart works normally again
                isOrderPlaced = false
                Toast.makeText(requireContext(), "Failed to place order. Try again.", Toast.LENGTH_SHORT).show()
                binding.cvOrderPlacedSuccess.visibility = View.GONE
                binding.btnPlaceOrder.isEnabled = true
            }
        }
    }

    /**
     * Hides or shows the BottomNavigationView in the hosting activity.
     */
    private fun setBottomNavigationVisibility(visibility: Int) {
        if (!isAdded) return
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

    override fun onStart() {
        super.onStart()
        // Re-enforce the hide policy on start
        binding.root.post {
            setBottomNavigationVisibility(View.GONE)
        }
    }

    override fun onResume() {
        super.onResume()
        // Ensure it stays hidden when returning to this fragment
        setBottomNavigationVisibility(View.GONE)
    }

    override fun onDestroyView() {
        // We only restore it if we aren't waiting for the order success to finish
        // because once the success timer hits, it will navigate back to home anyway.
        if (!isOrderPlaced) {
            setBottomNavigationVisibility(View.VISIBLE)
        }
        super.onDestroyView()
        _binding = null
    }

    // Inner adapter built for the checkout page list specifically.
    inner class CheckoutAdapter(private val onQuantityChange: (CartItem, Int) -> Unit) : RecyclerView.Adapter<CheckoutAdapter.ViewHolder>() {

        private var items = listOf<CartItem>()

        fun submitList(newItems: List<CartItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        inner class ViewHolder(val binding: ItemCheckoutProductBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemCheckoutProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            with(holder.binding) {
                tvProductName.text = item.name
                tvProductVolume.text = item.volume
                tvProductPrice.text = "₹${item.price.toInt()}"
                tvQuantity.text = item.quantity.toString()

                // Actually load the product image using Glide!
                Glide.with(holder.itemView.context)
                    .load(item.imageUrl.takeIf { it.isNotBlank() } ?: R.drawable.image_icon)
                    .into(ivProductImage)

                tvMinus.setOnClickListener { onQuantityChange(item, -1) }
                tvPlus.setOnClickListener { onQuantityChange(item, 1) }
            }
        }

        override fun getItemCount() = items.size
    }
}