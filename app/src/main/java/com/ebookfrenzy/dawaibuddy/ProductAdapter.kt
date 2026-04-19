package com.ebookfrenzy.dawaibuddy

import android.content.Intent
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.ebookfrenzy.dawaibuddy.databinding.ItemProductBinding

class ProductAdapter(private val productList: List<Product>) :
    RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    class ProductViewHolder(val binding: ItemProductBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val binding = ItemProductBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ProductViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val product = productList[position]

        holder.binding.tvProductName.text = product.name
        holder.binding.tvProductVolume.text = product.volume
        holder.binding.tvPrice.text = "₹${product.price}"

        // Strikethrough for MRP
        holder.binding.tvMRP.text = "MRP ₹${product.mrp}"
        holder.binding.tvMRP.paintFlags = holder.binding.tvMRP.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG

        if (product.discountPercent > 0) {
            holder.binding.tvDiscountTag.visibility = View.VISIBLE
            holder.binding.tvDiscountTag.text = "${product.discountPercent}% OFF"
        } else {
            holder.binding.tvDiscountTag.visibility = View.GONE
            holder.binding.tvMRP.visibility = View.GONE
        }

        Glide.with(holder.itemView.context)
            .load(product.imageUrl)
            .placeholder(R.drawable.health) // Fallback image
            .into(holder.binding.ivProductImage)

        // Handle Item Click to open Details Activity
        holder.itemView.setOnClickListener {
            val intent = Intent(it.context, ProductDetailActivity::class.java)
            intent.putExtra("PRODUCT_ID", product.id)
            it.context.startActivity(intent)
        }
    }

    override fun getItemCount() = productList.size
}