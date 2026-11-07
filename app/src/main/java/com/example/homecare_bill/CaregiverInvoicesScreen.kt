
package com.example.homecare_bill

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

private data class CaregiverInvoice(
    val id: String,
    val recipientName: String,
    val clientName: String,
    val serviceDate: String,
    val totalCents: Long,
    val status: String,
    val lineItems: List<Map<String, Any>>
)

private fun formatCaregiverInvoiceMoney(cents: Long): String =
    String.format(Locale.US, "%,.2f", cents / 100.0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaregiverInvoicesScreen(onBack: () -> Unit) {

    val db = remember { FirebaseFirestore.getInstance() }
    val caregiverId = FirebaseAuth.getInstance().currentUser?.uid

    var invoices by remember {
        mutableStateOf<List<CaregiverInvoice>>(emptyList())
    }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    var selectedInvoiceId by remember {
        mutableStateOf<String?>(null)
    }

    DisposableEffect(caregiverId) {
        if (caregiverId == null) {
            loading = false
            errorMessage = "Please sign in again."
            onDispose { }
        } else {
            val listener = db.collection("invoices")
                .whereEqualTo("caregiverId", caregiverId)
                .addSnapshotListener { snapshot, exception ->

                    loading = false

                    if (exception != null) {
                        errorMessage = exception.localizedMessage
                            ?: "Unable to load invoices."
                        return@addSnapshotListener
                    }

                    invoices = snapshot?.documents?.map { doc ->
                        val rawItems = doc.get("items") as? List<*>
                            ?: emptyList<Any>()

                        val parsedItems = rawItems.mapNotNull { raw ->
                            @Suppress("UNCHECKED_CAST")
                            raw as? Map<String, Any>
                        }

                        CaregiverInvoice(
                            id = doc.id,
                            recipientName = doc.getString("recipientName")
                                ?: "Care recipient",
                            clientName = doc.getString("clientName")
                                ?: "Client",
                            serviceDate = doc.getString("serviceDate")
                                ?: "",
                            totalCents = doc.getLong("totalCents")
                                ?: 0L,
                            status = doc.getString("status")
                                ?: "UNKNOWN",
                            lineItems = parsedItems
                        )
                    }?.sortedByDescending { it.serviceDate }
                        ?: emptyList()

                    errorMessage = ""
                }

            onDispose { listener.remove() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Invoices") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (errorMessage.isNotBlank()) {
                Text(
                    errorMessage,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(12.dp))
            }

            when {
                loading -> CircularProgressIndicator()

                invoices.isEmpty() && errorMessage.isBlank() -> {
                    Text("You haven't submitted any invoices yet.")
                }

                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(invoices, key = { it.id }) { invoice ->

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement =
                                    Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    invoice.recipientName,
                                    style =
                                        MaterialTheme.typography.titleMedium
                                )

                                Text("Client: ${invoice.clientName}")
                                Text("Service date: ${invoice.serviceDate}")

                                Text(
                                    "Status: ${
                                        invoice.status.replace("_", " ")
                                    }"
                                )

                                Text(
                                    "KES ${
                                        formatCaregiverInvoiceMoney(
                                            invoice.totalCents
                                        )
                                    }",
                                    style =
                                        MaterialTheme.typography.titleLarge
                                )

                                OutlinedButton(
                                    onClick = {
                                        selectedInvoiceId = invoice.id
                                    }
                                ) {
                                    Text("View Details")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedInvoiceId?.let { id ->
        val invoice = invoices.find { it.id == id }

        if (invoice == null) {
            selectedInvoiceId = null
        } else {
            AlertDialog(
                onDismissRequest = {
                    selectedInvoiceId = null
                },
                title = { Text("Invoice Details") },
                text = {
                    Column(
                        verticalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Recipient: ${invoice.recipientName}")
                        Text("Client: ${invoice.clientName}")
                        Text("Service date: ${invoice.serviceDate}")
                        Text("Status: ${invoice.status.replace("_", " ")}")

                        HorizontalDivider()

                        invoice.lineItems.forEach { item ->
                            val serviceName =
                                item["serviceName"] as? String
                                    ?: "Service"

                            val billingUnit =
                                item["billingUnit"] as? String
                                    ?: ""

                            val quantity =
                                (item["quantity"] as? Number)
                                    ?.toLong() ?: 0L

                            val lineTotal =
                                (item["lineTotalCents"] as? Number)
                                    ?.toLong() ?: 0L

                            val quantityLabel =
                                if (billingUnit == "HOUR") {
                                    "$quantity minutes"
                                } else {
                                    "$quantity sessions"
                                }

                            Text("$serviceName — $quantityLabel")
                            Text(
                                "KES ${
                                    formatCaregiverInvoiceMoney(lineTotal)
                                }"
                            )
                        }

                        HorizontalDivider()

                        Text(
                            "Total: KES ${
                                formatCaregiverInvoiceMoney(
                                    invoice.totalCents
                                )
                            }",
                            style =
                                MaterialTheme.typography.titleMedium
                        )

                        if (invoice.status == "DISPUTED") {
                            Text(
                                "The client has disputed this invoice. " +
                                        "It requires review before payment.",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { selectedInvoiceId = null }
                    ) {
                        Text("Close")
                    }
                }
            )
        }
    }
}
