package com.ebookfrenzy.dawaibuddy

data class User(
    var uid: String = "", // Added to store the Firebase Auth UID
    var phoneNumber: String = "",
    var name: String = "",
    var age: Int? = null,
    var gender: String = "",
    var weight: Float? = null, // In kg
    var height: Float? = null, // In cm
    var medicalConditions: String = "",
    var medicinesTaken: String = "",
    var isNewUser: Boolean = true // Note: We can also use Firestore document existence to check this now!
)