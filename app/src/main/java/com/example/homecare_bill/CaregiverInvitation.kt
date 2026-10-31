
package com.example.homecare_bill

import com.google.firebase.Timestamp

data class CaregiverInvitation(
    val id: String = "",
    val clientId: String = "",
    val recipientId: String = "",
    val caregiverEmail: String = "",
    val clientName: String = "",
    val recipientName: String = "",
    val relationship: String = "",
    val status: String = "",
    val createdAt: Timestamp? = null
)
