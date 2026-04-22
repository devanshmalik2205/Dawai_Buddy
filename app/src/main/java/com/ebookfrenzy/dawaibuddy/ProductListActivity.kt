package com.ebookfrenzy.dawaibuddy

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ebookfrenzy.dawaibuddy.databinding.ActivityProductListBinding
import com.ebookfrenzy.dawaibuddy.objects.Product
import com.google.firebase.firestore.FirebaseFirestore

class ProductListActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var binding: ActivityProductListBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivBack.setOnClickListener { finish() }

        // Get intents from either CategoryAdapter or FlashDealAdapter
        val categoryId = intent.getStringExtra("CATEGORY_ID")
        val filterType = intent.getStringExtra("FILTER_TYPE")
        val filterValue = intent.getStringExtra("FILTER_VALUE")
        val rawHeaderName = intent.getStringExtra("CATEGORY_NAME")

        // Use the targeted brand/category if available, otherwise use the cleaned category name
        val headerName = if (!filterValue.isNullOrEmpty()) {
            filterValue
        } else {
            rawHeaderName?.replace("\\n", " ")?.replace("\n", " ") ?: "Products"
        }

        binding.tvCategoryTitle.text = headerName

        // Determine how to filter the products based on who opened this activity
        when {
            // Opened from a Flash Deal specifically for a Brand (e.g., Himalaya)
            filterType == "brand" && !filterValue.isNullOrEmpty() -> {
                fetchProductsByField("brand", filterValue)
            }
            // Opened from a Flash Deal specifically for a Category (e.g., Medicines)
            filterType == "category" && !filterValue.isNullOrEmpty() -> {
                fetchProductsByField("categoryId", filterValue)
            }
            // Opened directly from the Category Grid
            !categoryId.isNullOrEmpty() -> {
                fetchProductsByField("categoryId", categoryId)
            }
            else -> {
                // If it hits here, it means neither an ID nor a Filter was passed
                Toast.makeText(this, "Error loading products", Toast.LENGTH_SHORT).show()
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    // A generic function that can filter by any field
    private fun fetchProductsByField(fieldName: String, value: String) {
        binding.progressBar.visibility = View.VISIBLE

        // This dynamically queries either 'brand' == 'Himalaya' OR 'categoryId' == 'Medicines'
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
                binding.rvProducts.adapter = ProductAdapter(productList)

                // Show a helpful message if the query returns empty
                if (productList.isEmpty()) {
                    Toast.makeText(this, "No products found for this deal", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Log.e("Firestore", "Error fetching products", e)
                Toast.makeText(this, "Failed to load products", Toast.LENGTH_SHORT).show()
            }
    }
}