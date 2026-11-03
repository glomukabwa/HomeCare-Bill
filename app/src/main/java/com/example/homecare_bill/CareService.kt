
package com.example.homecare_bill

data class CareService(
    val id: String = "",
    val name: String = "",
    val rateCents: Long = 0L,
    val billingUnit: String = "",
    val isActive: Boolean = false
)
