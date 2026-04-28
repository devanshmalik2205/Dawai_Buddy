package com.ebookfrenzy.dawaibuddy.models

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.ebookfrenzy.dawaibuddy.objects.CartItem

class SharedCartViewModel : ViewModel() {
    // Map of Product ID to CartItem for fast lookups
    private val _cartItems = MutableLiveData<Map<String, CartItem>>(emptyMap())
    val cartItems: LiveData<Map<String, CartItem>> get() = _cartItems

    fun updateQuantity(product: CartItem, delta: Int) {
        val currentMap = _cartItems.value?.toMutableMap() ?: mutableMapOf()
        val currentItem = currentMap[product.id]

        if (currentItem != null) {
            val newQuantity = currentItem.quantity + delta
            if (newQuantity <= 0) {
                currentMap.remove(product.id)
            } else {
                currentMap[product.id] = currentItem.copy(quantity = newQuantity)
            }
        } else if (delta > 0) {
            currentMap[product.id] = product.copy(quantity = delta)
        }

        _cartItems.value = currentMap
    }

    fun getQuantity(productId: String): Int {
        return _cartItems.value?.get(productId)?.quantity ?: 0
    }

    fun getTotalPrice(): Double {
        return _cartItems.value?.values?.sumOf { it.price * it.quantity } ?: 0.0
    }

    fun getTotalItemsCount(): Int {
        return _cartItems.value?.values?.sumOf { it.quantity } ?: 0
    }

    fun clearCart() {
        _cartItems.value = emptyMap()
    }
}