package com.ebookfrenzy.dawaibuddy

data class FlashDeal(
    val id: String = "",
    val tag: String = "FLASH DEAL",
    val title: String = "",
    val backgroundColor: String = "#438BFF",
    val imageUrl: String = "",
    val filterType: String = "", // e.g., "brand" or "category"
    val filterValue: String = "" // e.g., "Himalaya" or "Health"
)