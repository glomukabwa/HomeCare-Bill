
package com.example.homecare_bill

data class InvoiceLineItem(
    val id: Long,
    val serviceId: String = "",
    val quantity: String = "1" //The duration in minutes for hourly services, or the number of sessions for session-based services.
    //The values assigned above are the fallback values
)
