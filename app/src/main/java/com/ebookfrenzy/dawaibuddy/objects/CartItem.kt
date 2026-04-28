package com.ebookfrenzy.dawaibuddy.objects

// Added default values so Firebase can automatically deserialize the list of items
data class CartItem(
    val id: String = "",
    val name: String = "",
    val price: Double = 0.0,
    val volume: String = "",
    val imageUrl: String = "",
    var quantity: Int = 0
)