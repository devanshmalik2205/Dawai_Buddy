package com.ebookfrenzy.dawaibuddy.adapters

import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.ebookfrenzy.dawaibuddy.objects.FlashDeal
import com.ebookfrenzy.dawaibuddy.ProductListActivity
import com.ebookfrenzy.dawaibuddy.R
import com.ebookfrenzy.dawaibuddy.databinding.ItemFlashDealBinding

class FlashDealAdapter(private val flashDeals: List<FlashDeal>) :
    RecyclerView.Adapter<FlashDealAdapter.FlashDealViewHolder>() {

    inner class FlashDealViewHolder(val binding: ItemFlashDealBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FlashDealViewHolder {
        val binding = ItemFlashDealBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FlashDealViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FlashDealViewHolder, position: Int) {
        val deal = flashDeals[position]
        val binding = holder.binding

        // TEXT
        binding.tvFlashDealTag.text = deal.tag
        binding.tvFlashDealTitle.text = deal.title.replace("\\n", "\n")

        // SAFE COLOR PARSE
        val baseColor = parseColorSafe(deal.backgroundColor, "#438BFF")

        // BACKGROUND
        binding.clFlashDealBackground.setBackgroundColor(baseColor)

        // BUTTON TEXT COLOR = SAME AS BACKGROUND
        binding.btnShopNow.setTextColor(baseColor)

        // TAG COLOR = LIGHTER SHADE
        binding.tvFlashDealTag.setTextColor(lightenColor(baseColor, 0.5f))

        // IMAGE
        if (deal.imageUrl.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(deal.imageUrl)
                .into(binding.ivFlashDealBg)
        } else {
            binding.ivFlashDealBg.setImageResource(R.drawable.promotion_1)
        }

        // Handle the "Shop Now" button click with filter logic
        binding.btnShopNow.setOnClickListener {
            val intent = Intent(it.context, ProductListActivity::class.java)

            // Pass the filtering data from Firebase to the Product List page
            intent.putExtra("FILTER_TYPE", deal.filterType)
            intent.putExtra("FILTER_VALUE", deal.filterValue)

            // Use the deal title as the page header (e.g., "Himalaya Mega Sale")
            intent.putExtra("CATEGORY_NAME", deal.title)

            it.context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = flashDeals.size

    // 🔥 Safe color parser
    private fun parseColorSafe(colorString: String, defaultColor: String): Int {
        return try {
            Color.parseColor(colorString)
        } catch (e: Exception) {
            Log.e("ColorError", "Invalid color: $colorString")
            Color.parseColor(defaultColor)
        }
    }

    // 🔥 Lighten color for tag
    private fun lightenColor(color: Int, factor: Float): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)

        val newR = r + ((255 - r) * factor).toInt()
        val newG = g + ((255 - g) * factor).toInt()
        val newB = b + ((255 - b) * factor).toInt()

        return Color.rgb(newR, newG, newB)
    }
}