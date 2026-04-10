package com.ebookfrenzy.dawaibuddy

data class User(
    var phoneNumber: String = "",
    var age: Int? = null,
    var gender: String = "",
    var weight: Float? = null, // In kg
    var height: Float? = null, // In cm
    var medicalConditions: String = "",
    var medicinesTaken: String = "",
    var isNewUser: Boolean = true // Flag to determine if we skip the TUAY screen
)