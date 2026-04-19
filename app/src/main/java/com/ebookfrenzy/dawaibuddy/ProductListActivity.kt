package com.ebookfrenzy.dawaibuddy

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ebookfrenzy.dawaibuddy.databinding.ActivityProductListBinding
import com.google.firebase.firestore.FirebaseFirestore

class ProductListActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var binding: ActivityProductListBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivBack.setOnClickListener { finish() }

        // Get Category details from Intent
        val categoryId = intent.getStringExtra("CATEGORY_ID") ?: ""
        val categoryName = intent.getStringExtra("CATEGORY_NAME") ?: "Products"

        binding.tvCategoryTitle.text = categoryName

        if (categoryId.isNotEmpty()) {
            fetchProductsByCategory(categoryId)
        } else {
            Toast.makeText(this, "Error loading category", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchProductsByCategory(categoryId: String) {
        binding.progressBar.visibility = View.VISIBLE
        // This is the query to only fetch products for the selected category
        db.collection("products")
            .whereEqualTo("categoryId", categoryId)
            .get()
            .addOnSuccessListener { result ->
                binding.progressBar.visibility = View.GONE
                val productList = mutableListOf<Product>()
                for (document in result) {
                    val product = document.toObject(Product::class.java).copy(id = document.id)
                    productList.add(product)
                }
                binding.rvProducts.adapter = ProductAdapter(productList)
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Log.e("Firestore", "Error fetching products", e)
                Toast.makeText(this, "Failed to load products", Toast.LENGTH_SHORT).show()
            }
    }
}