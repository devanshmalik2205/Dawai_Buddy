package com.ebookfrenzy.dawaibuddy.checkout_product

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.ebookfrenzy.dawaibuddy.R
import com.ebookfrenzy.dawaibuddy.adapters.ProductAdapter
import com.ebookfrenzy.dawaibuddy.databinding.FragmentProductListBinding
import com.ebookfrenzy.dawaibuddy.models.SharedCartViewModel
import com.ebookfrenzy.dawaibuddy.objects.Product
import com.google.firebase.firestore.FirebaseFirestore

class ProductListFragment : Fragment() {

    private var _binding: FragmentProductListBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()

    // Connect to the shared cart so we can pass it to the Adapter!
    private val cartViewModel: SharedCartViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProductListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Hide bottom navigation immediately
        setBottomNavigationVisibility(View.GONE)

        // Back navigation
        binding.ivBack.setOnClickListener { findNavController().popBackStack() }

        // Fetch arguments from the navigation bundle
        val categoryId = arguments?.getString("CATEGORY_ID")
        val filterType = arguments?.getString("FILTER_TYPE")
        val filterValue = arguments?.getString("FILTER_VALUE")
        val rawHeaderName = arguments?.getString("CATEGORY_NAME")

        // Set Header Title
        val headerName = if (!filterValue.isNullOrEmpty()) {
            filterValue
        } else {
            rawHeaderName?.replace("\\n", " ")?.replace("\n", " ") ?: "Products"
        }
        binding.tvCategoryTitle.text = headerName

        // Setup Recycler
        binding.rvProducts.layoutManager = GridLayoutManager(requireContext(), 2)

        // Routing for different filter/category types
        when {
            filterType == "brand" && !filterValue.isNullOrEmpty() -> {
                fetchProductsByField("brand", filterValue)
            }
            filterType == "category" && !filterValue.isNullOrEmpty() -> {
                fetchProductsByField("categoryId", filterValue)
            }
            !categoryId.isNullOrEmpty() -> {
                fetchProductsByField("categoryId", categoryId)
            }
            else -> {
                Toast.makeText(requireContext(), "Error loading products", Toast.LENGTH_SHORT).show()
                binding.progressBar.visibility = View.GONE
            }
        }

        // Setup Floating Cart Observer
        setupCartObserver()

        // Navigation to checkout when floating cart is clicked
        binding.cvFloatingCart.setOnClickListener {
            findNavController().navigate(R.id.action_productListFragment_to_checkoutFragment)
        }
    }

    private fun setupCartObserver() {
        cartViewModel.cartItems.observe(viewLifecycleOwner) { items ->
            if (items.isEmpty()) {
                binding.cvFloatingCart.visibility = View.GONE
            } else {
                binding.cvFloatingCart.visibility = View.VISIBLE

                val totalItems = items.values.sumOf { it.quantity }
                val uniqueItems = items.values.toList()

                binding.tvFloatingCartItems.text = "$totalItems item${if (totalItems > 1) "s" else ""}"

                // Manage stacked images visually based on cart variety using Glide
                if (uniqueItems.isNotEmpty()) {
                    binding.ivCartImg1.visibility = View.VISIBLE
                    Glide.with(requireContext())
                        .load(uniqueItems[0].imageUrl.takeIf { it.isNotBlank() } ?: R.drawable.image_icon)
                        .into(binding.ivCartImg1)
                } else {
                    binding.ivCartImg1.visibility = View.GONE
                }

                if (uniqueItems.size > 1) {
                    binding.ivCartImg2.visibility = View.VISIBLE
                    Glide.with(requireContext())
                        .load(uniqueItems[1].imageUrl.takeIf { it.isNotBlank() } ?: R.drawable.image_icon)
                        .into(binding.ivCartImg2)
                } else {
                    binding.ivCartImg2.visibility = View.GONE
                }

                if (uniqueItems.size > 2) {
                    binding.ivCartImg3.visibility = View.VISIBLE
                    Glide.with(requireContext())
                        .load(uniqueItems[2].imageUrl.takeIf { it.isNotBlank() } ?: R.drawable.image_icon)
                        .into(binding.ivCartImg3)
                } else {
                    binding.ivCartImg3.visibility = View.GONE
                }
            }
        }
    }

    private fun fetchProductsByField(fieldName: String, value: String) {
        binding.progressBar.visibility = View.VISIBLE

        db.collection("products")
            .whereEqualTo(fieldName, value)
            .get()
            .addOnSuccessListener { result ->
                binding.progressBar.visibility = View.GONE
                val productList = mutableListOf<Product>()
                for (document in result) {
                    val product = document.toObject(Product::class.java).copy(id = document.id)
                    productList.add(product)
                }

                // Pass the cartViewModel AND lifecycleOwner to the ProductAdapter so buttons work!
                binding.rvProducts.adapter = ProductAdapter(productList, cartViewModel, viewLifecycleOwner)

                if (productList.isEmpty()) {
                    Toast.makeText(requireContext(), "No products found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Log.e("Firestore", "Error fetching products", e)
                Toast.makeText(requireContext(), "Failed to load products", Toast.LENGTH_SHORT).show()
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
        setBottomNavigationVisibility(View.GONE)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Restore bottom navigation bar visibility when leaving fragment
        setBottomNavigationVisibility(View.VISIBLE)
        _binding = null
    }
}