package com.ebookfrenzy.dawaibuddy.objects

data class User(
    var uid: String = "",
    var phoneNumber: String = "",
    var name: String = "",
    var age: Int? = null,
    var gender: String = "",
    var weight: Float? = null, // In kg
    var height: Float? = null, // In cm
    var medicalConditions: String = "",
    var medicinesTaken: String = "",
    var isNewUser: Boolean = true,
    var hasWatch: Boolean = false // ADDED: Tracks if the watch is linked
)