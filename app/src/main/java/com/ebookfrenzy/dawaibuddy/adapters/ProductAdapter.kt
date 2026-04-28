package com.ebookfrenzy.dawaibuddy.adapters

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.ebookfrenzy.dawaibuddy.R
import com.ebookfrenzy.dawaibuddy.databinding.ItemProductBinding
import com.ebookfrenzy.dawaibuddy.objects.CartItem
import com.ebookfrenzy.dawaibuddy.models.SharedCartViewModel
import com.ebookfrenzy.dawaibuddy.objects.Product

class ProductAdapter(
    private val productList: List<Product>,
    private val cartViewModel: SharedCartViewModel,
    private val lifecycleOwner: LifecycleOwner
) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    inner class ProductViewHolder(val binding: ItemProductBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val binding = ItemProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ProductViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val product = productList[position]
        val binding = holder.binding

        binding.tvProductName.text = product.name
        binding.tvProductVolume.text = product.volume
        binding.tvPrice.text = "₹${product.price.toInt()}"

        Glide.with(holder.itemView.context)
            .load(product.imageUrl.takeIf { it.isNotBlank() } ?: R.drawable.health)
            .into(binding.ivProductImage)

        // NAVIGATION: Click anywhere on the card to open Details
        holder.itemView.setOnClickListener { view ->
            val bundle = Bundle().apply {
                putString("PRODUCT_ID", product.id)
            }
            view.findNavController().navigate(R.id.productDetailFragment, bundle)
        }

        // CART LOGIC
        val cartItem = CartItem(
            id = product.id,
            name = product.name,
            price = product.price,
            volume = product.volume,
            imageUrl = product.imageUrl,
            quantity = 1
        )

        cartViewModel.cartItems.observe(lifecycleOwner) { cartMap ->
            val quantity = cartMap[product.id]?.quantity ?: 0
            if (quantity > 0) {
                binding.vsAddController.displayedChild = 1
                binding.tvQuantity.text = quantity.toString()
            } else {
                binding.vsAddController.displayedChild = 0
            }
        }

        binding.tvAddBtn.setOnClickListener { cartViewModel.updateQuantity(cartItem, 1) }
        binding.tvPlus.setOnClickListener { cartViewModel.updateQuantity(cartItem, 1) }
        binding.tvMinus.setOnClickListener { cartViewModel.updateQuantity(cartItem, -1) }
    }

    override fun getItemCount(): Int = productList.size
}