package com.ebookfrenzy.dawaibuddy.checkout_product

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.ebookfrenzy.dawaibuddy.R
import com.ebookfrenzy.dawaibuddy.databinding.ActivityProductDetailBinding
import com.ebookfrenzy.dawaibuddy.objects.CartItem
import com.ebookfrenzy.dawaibuddy.models.SharedCartViewModel
import com.ebookfrenzy.dawaibuddy.objects.Product
import com.google.firebase.firestore.FirebaseFirestore

class ProductDetailFragment : Fragment() {

    private var _binding: ActivityProductDetailBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val cartViewModel: SharedCartViewModel by activityViewModels()

    private var currentProduct: Product? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        // Reuse your existing activity_product_detail.xml layout
        _binding = ActivityProductDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Hide bottom navigation immediately
        setBottomNavigationVisibility(View.GONE)

        binding.ivBack.setOnClickListener { findNavController().popBackStack() }

        val productId = arguments?.getString("PRODUCT_ID")
        if (productId != null) {
            fetchProductDetails(productId)
        }

        setupCartObserver(productId ?: "")
    }

    private fun setupCartObserver(productId: String) {
        cartViewModel.cartItems.observe(viewLifecycleOwner) { cartMap ->
            val qty = cartMap[productId]?.quantity ?: 0
            if (qty > 0) {
                binding.vsAddDetailController.displayedChild = 1 // Show - Qty +
                binding.tvDetailQuantity.text = qty.toString()
            } else {
                binding.vsAddDetailController.displayedChild = 0 // Show ADD Button
            }
        }
    }

    private fun fetchProductDetails(productId: String) {
        db.collection("products").document(productId).get()
            .addOnSuccessListener { document ->
                val product = document.toObject(Product::class.java)?.copy(id = document.id)
                if (product != null) {
                    currentProduct = product
                    populateUI(product)
                }
            }
    }

    private fun populateUI(product: Product) {
        binding.tvDetailTitle.text = product.name
        binding.tvDetailVolume.text = product.volume
        binding.tvDeliveryTime.text = "⏱ ${product.deliveryTime}"
        binding.tvDescription.text = product.description
        binding.tvBottomPrice.text = "₹${product.price.toInt()}"
        binding.tvBottomVolume.text = product.volume

        Glide.with(this)
            .load(product.imageUrl.takeIf { it.isNotBlank() } ?: R.drawable.health)
            .into(binding.ivDetailImage)

        val cartItem = CartItem(
            id = product.id,
            name = product.name,
            price = product.price,
            volume = product.volume,
            imageUrl = product.imageUrl,
            quantity = 1
        )

        // Detail Page Cart Button Listeners
        binding.btnAddToCart.setOnClickListener { cartViewModel.updateQuantity(cartItem, 1) }
        binding.tvDetailPlus.setOnClickListener { cartViewModel.updateQuantity(cartItem, 1) }
        binding.tvDetailMinus.setOnClickListener { cartViewModel.updateQuantity(cartItem, -1) }
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
}