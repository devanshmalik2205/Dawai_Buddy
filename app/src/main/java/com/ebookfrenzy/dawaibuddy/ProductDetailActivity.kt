package com.ebookfrenzy.dawaibuddy

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.ebookfrenzy.dawaibuddy.databinding.ActivityProductDetailBinding
import com.ebookfrenzy.dawaibuddy.objects.Product
import com.google.firebase.firestore.FirebaseFirestore

class ProductDetailActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var binding: ActivityProductDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivBack.setOnClickListener { finish() }

        val productId = intent.getStringExtra("PRODUCT_ID")
        if (productId != null) {
            fetchProductDetails(productId)
        } else {
            Toast.makeText(this, "Product not found", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.btnAddToCart.setOnClickListener {
            Toast.makeText(this, "Item added to cart!", Toast.LENGTH_SHORT).show()
            // Add cart logic here later
        }
    }

    private fun fetchProductDetails(productId: String) {
        db.collection("products").document(productId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val product = document.toObject(Product::class.java)
                    if (product != null) {
                        populateUI(product)
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error loading product", Toast.LENGTH_SHORT).show()
            }
    }

    private fun populateUI(product: Product) {
        binding.tvDetailTitle.text = product.name
        binding.tvDetailVolume.text = product.volume
        binding.tvDeliveryTime.text = "⏱ ${product.deliveryTime}"
        binding.tvDescription.text = product.description

        // Prices only displayed in the bottom sticky bar now
        val priceText = "₹${product.price}"
        binding.tvBottomPrice.text = priceText
        binding.tvBottomVolume.text = product.volume

        Glide.with(this)
            .load(product.imageUrl)
            .placeholder(R.drawable.health) // Fallback
            .into(binding.ivDetailImage)
    }
}