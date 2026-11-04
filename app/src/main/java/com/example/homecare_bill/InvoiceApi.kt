
package com.example.homecare_bill

import android.os.Handler
import android.os.Looper
import com.google.firebase.auth.FirebaseAuth
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

data class InvoiceSubmissionItem(
    val serviceId: String,
    val quantity: Long
)

object InvoiceApi {

    private const val BASE_URL = "http://10.0.2.2:8000"

    private val mainHandler = Handler(Looper.getMainLooper())

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun submitInvoice(
        assignmentId: String,
        serviceDate: String,
        items: List<InvoiceSubmissionItem>,
        requestId: UUID,
        onSuccess: (String, Long) -> Unit,
        onError: (String) -> Unit
    ) {
        val user = FirebaseAuth.getInstance().currentUser

        if (user == null) {
            onError("Please sign in again.")
            return
        }

        // Obtain a fresh Firebase ID token.
        user.getIdToken(true).addOnCompleteListener { tokenTask ->

            if (!tokenTask.isSuccessful) {
                onError(
                    tokenTask.exception?.localizedMessage
                        ?: "Unable to verify your session."
                )
                return@addOnCompleteListener
            }

            val token = tokenTask.result?.token

            if (token.isNullOrBlank()) {
                onError("Authentication token is unavailable.")
                return@addOnCompleteListener
            }

            val itemArray = JSONArray()

            items.forEach { item ->
                itemArray.put(
                    JSONObject()
                        .put("serviceId", item.serviceId)
                        .put("quantity", item.quantity)
                )
            }

            val payload = JSONObject()
                .put("assignmentId", assignmentId)
                .put("serviceDate", serviceDate)
                .put("requestId", requestId.toString())
                .put("items", itemArray)

            val request = Request.Builder()
                .url("$BASE_URL/invoices")
                .header("Authorization", "Bearer $token")
                .post(
                    payload.toString().toRequestBody(
                        "application/json; charset=utf-8".toMediaType()
                    )
                )
                .build()

            client.newCall(request).enqueue(object : Callback {

                override fun onFailure(call: Call, e: IOException) {
                    mainHandler.post {
                        onError(
                            "Could not connect to the backend. " +
                                    "Check that FastAPI is running. " +
                                    (e.localizedMessage ?: "")
                        )
                    }
                }

                override fun onResponse(
                    call: Call,
                    response: Response
                ) {
                    response.use {
                        val responseText = it.body?.string().orEmpty()

                        if (it.isSuccessful) {
                            try {
                                val result = JSONObject(responseText)

                                val invoiceId =
                                    result.getString("invoiceId")

                                val totalCents =
                                    result.getLong("totalCents")

                                mainHandler.post {
                                    onSuccess(invoiceId, totalCents)
                                }
                            } catch (e: Exception) {
                                mainHandler.post {
                                    onError("Invalid response from backend.")
                                }
                            }
                        } else {
                            val message = try {
                                JSONObject(responseText)
                                    .optString("detail")
                                    .ifBlank { "Invoice submission failed." }
                            } catch (e: Exception) {
                                "Invoice submission failed."
                            }

                            mainHandler.post {
                                onError("Error ${it.code}: $message")
                            }
                        }
                    }
                }
            })
        }
    }
}
