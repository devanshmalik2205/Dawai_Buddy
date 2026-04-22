package com.ebookfrenzy.dawaibuddy

import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.ebookfrenzy.dawaibuddy.databinding.ItemCategoryBinding
import com.ebookfrenzy.dawaibuddy.objects.Category

class CategoryAdapter(private val categoryList: List<Category>) :
    RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    class CategoryViewHolder(val binding: ItemCategoryBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemCategoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categoryList[position]

        val binding = holder.binding

        // TEXT
        binding.tvCategoryTitle.text = category.title
        binding.tvCategorySubtitle.text = category.subtitle

        // COLORS (safe parsing + fallback)
        val bgColor = parseColorSafe(category.bgColor, "#E8F5E9")
        val textColor = parseColorSafe(category.textColor, "#388E3C")

        binding.cvCategory.setCardBackgroundColor(bgColor)
        binding.tvCategoryTitle.setTextColor(textColor)
        binding.tvCategorySubtitle.setTextColor(lightenColor(textColor, 0.3f)) // slight variation

        // ICON
        if (category.iconUrl.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(category.iconUrl)
                .into(binding.ivCategoryIcon)
        } else {
            binding.ivCategoryIcon.setImageResource(R.drawable.image_icon) // optional fallback
        }

        // Handle Item Click to open the Products List Activity
        holder.itemView.setOnClickListener {
            val intent = Intent(it.context, ProductListActivity::class.java)
            // Replace 'title' with 'id' if your Category class has a unique ID field
            intent.putExtra("CATEGORY_ID", category.title)
            intent.putExtra("CATEGORY_NAME", category.title)
            it.context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = categoryList.size

    // 🔥 Safe color parser
    private fun parseColorSafe(colorString: String, defaultColor: String): Int {
        return try {
            Color.parseColor(colorString)
        } catch (e: Exception) {
            Log.e("ColorError", "Invalid color: $colorString")
            Color.parseColor(defaultColor)
        }
    }

    // 🔥 Lighten color (for subtle UI depth)
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