package com.ebookfrenzy.dawaibuddy.objects

import com.ebookfrenzy.dawaibuddy.objects.CartItem
import com.google.firebase.Timestamp

data class Order(
    var orderId: String = "",
    var userId: String = "",
    var totalAmount: Double = 0.0,
    var status: String = "",
    var timestamp: Timestamp? = null,
    var items: List<CartItem> = emptyList()
)