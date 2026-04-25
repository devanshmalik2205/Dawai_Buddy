package com.ebookfrenzy.dawaibuddy.objects

// This data class maps directly to the fields in your Firestore database
data class MeditationTrack(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val category: String = "",
    val audioUrl: String = ""
)