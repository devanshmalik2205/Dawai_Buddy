package com.ebookfrenzy.dawaibuddy.objects

import java.io.Serializable

// Data class representing a Product in Firestore
data class Product(
    val id: String = "",
    val name: String = "",
    val brand: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
    val volume: String = "",
    val price: Double = 0.0,
    val mrp: Double = 0.0,
    val discountPercent: Int = 0,
    val imageUrl: String = "",
    val deliveryTime: String = "15 mins",
    val description: String = ""
) : Serializable