package com.ebookfrenzy.dawaibuddy.objects

/**
 * Data object to store wellness records (water, mood, etc.) in Firebase Firestore.
 */
data class WellnessData(
    var id: String = "",
    var userId: String = "",
    var type: String = "", // Used to identify the type of log: "water" or "mood"

    // Water Logging specific
    var amountMl: Int = 0,

    // Mood Tracking specific
    var mood: String = "",
    var journalNote: String = "",

    // Universal fields
    var timestamp: Long = 0L,
    var date: String = ""  // Stored as "yyyy-MM-dd" for easy daily querying
)