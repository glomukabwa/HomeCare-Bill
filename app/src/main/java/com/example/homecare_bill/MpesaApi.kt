
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
import org.json.JSONObject
import java.io.IOException

object MpesaApi {
    private const val BASE_URL = "http://10.0.2.2:8000"
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(75, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val jsonType = "application/json".toMediaType()

    private fun authenticatedRequest(
        path: String,
        body: JSONObject?,
        onResult: (JSONObject?, String?) -> Unit
    ) {
        val user = FirebaseAuth.getInstance().currentUser

        if (user == null) {
            onResult(null, "Please sign in again.")
            return
        }

        user.getIdToken(true)
            .addOnSuccessListener { tokenResult ->
                val token = tokenResult.token

                if (token.isNullOrBlank()) {
                    onResult(null, "Unable to get authentication token.")
                    return@addOnSuccessListener
                }

                val builder = Request.Builder()
                    .url("$BASE_URL$path")
                    .header("Authorization", "Bearer $token")

                val request = if (body == null) {
                    builder.get().build()
                } else {
                    builder.post(
                        body.toString().toRequestBody(jsonType)
                    ).build()
                }

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        mainHandler.post {
                            onResult(
                                null,
                                "Unable to contact payment server: ${e.message}"
                            )
                        }
                    }

                    override fun onResponse(
                        call: Call,
                        response: Response
                    ) {
                        response.use {
                            val text = it.body?.string().orEmpty()

                            val json = try {
                                JSONObject(text)
                            } catch (_: Exception) {
                                JSONObject()
                            }

                            mainHandler.post {
                                if (it.isSuccessful) {
                                    onResult(json, null)
                                } else {
                                    onResult(
                                        null,
                                        json.optString(
                                            "detail",
                                            "Payment request failed (${it.code})."
                                        )
                                    )
                                }
                            }
                        }
                    }
                })
            }
            .addOnFailureListener {
                onResult(null, "Authentication failed: ${it.message}")
            }
    }

    fun initiatePayment(
        invoiceId: String,
        phoneNumber: String,
        onResult: (JSONObject?, String?) -> Unit
    ) {
        val body = JSONObject().apply {
            put("invoiceId", invoiceId)
            put("phoneNumber", phoneNumber)
        }

        authenticatedRequest(
            "/payments/initiate",
            body,
            onResult
        )
    }

    fun checkPaymentStatus(
        paymentId: String,
        onResult: (JSONObject?, String?) -> Unit
    ) {
        authenticatedRequest(
            "/payments/$paymentId/status",
            null,
            onResult
        )
    }
}
