
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

private data class ClientInvoice(
    val id: String,
    val caregiverId: String,
    val recipientName: String,
    val serviceDate: String,
    val totalCents: Long,
    val status: String,
    val lineItems: List<Map<String, Any>>
)

private fun formatInvoiceMoney(cents: Long): String =
    String.format(Locale.US, "%,.2f", cents / 100.0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientInvoicesScreen(onBack: () -> Unit) {

    val db = remember { FirebaseFirestore.getInstance() }
    val userId = FirebaseAuth.getInstance().currentUser?.uid

    var invoices by remember {
        mutableStateOf<List<ClientInvoice>>(emptyList())
    }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    var actionInProgress by remember { mutableStateOf<String?>(null) }
    var selectedInvoice by remember {
        mutableStateOf<ClientInvoice?>(null)
    }
    var pendingAction by remember {
        mutableStateOf<String?>(null)
    }

    DisposableEffect(userId) {
        if (userId == null) {
            errorMessage = "Please sign in again."
            loading = false
            onDispose { }
        } else {
            val listener = db.collection("invoices")
                .whereEqualTo("clientId", userId)
                .addSnapshotListener { snapshot, exception ->

                    if (exception != null) {
                        errorMessage = exception.localizedMessage
                            ?: "Unable to load invoices."
                        loading = false
                        return@addSnapshotListener
                    }

                    invoices = snapshot?.documents?.map { doc ->
                        val rawItems = doc.get("items") as? List<*>
                            ?: emptyList<Any>()

                        val parsedItems = rawItems.mapNotNull { raw ->
                            @Suppress("UNCHECKED_CAST")
                            raw as? Map<String, Any>
                        }

                        ClientInvoice(
                            id = doc.id,
                            caregiverId = doc.getString("caregiverId")
                                ?: "",
                            recipientName = doc.getString("recipientName")
                                ?: "Care recipient",
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
                    loading = false
                }

            onDispose { listener.remove() }
        }
    }

    fun updateInvoiceStatus(invoice: ClientInvoice, newStatus: String) {
        if (actionInProgress != null) return
        if (invoice.status != "PENDING_APPROVAL") return

        actionInProgress = invoice.id
        errorMessage = ""

        db.collection("invoices")
            .document(invoice.id)
            .update("status", newStatus)
            .addOnSuccessListener {
                actionInProgress = null
                pendingAction = null
                selectedInvoice = null
            }
            .addOnFailureListener { exception ->
                actionInProgress = null
                pendingAction = null
                errorMessage = exception.localizedMessage
                    ?: "Unable to update invoice."
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

                invoices.isEmpty() -> {
                    Text("You have no invoices yet.")
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

                                Text("Service date: ${invoice.serviceDate}")
                                Text("Status: ${invoice.status}")

                                Text(
                                    "KES ${
                                        formatInvoiceMoney(
                                            invoice.totalCents
                                        )
                                    }",
                                    style =
                                        MaterialTheme.typography.titleLarge
                                )

                                OutlinedButton(
                                    onClick = {
                                        selectedInvoice = invoice
                                    }
                                ) {
                                    Text("View Invoice")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedInvoice?.let { selected ->
        // Use the latest snapshot, since the status may have changed.
        val invoice = invoices.find { it.id == selected.id } ?: selected

        AlertDialog(
            onDismissRequest = {
                if (actionInProgress == null) {
                    selectedInvoice = null
                    pendingAction = null
                }
            },
            title = {
                Text("Invoice Details")
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Recipient: ${invoice.recipientName}")
                    Text("Service date: ${invoice.serviceDate}")
                    Text("Status: ${invoice.status}")

                    HorizontalDivider()

                    invoice.lineItems.forEach { item ->
                        val name = item["serviceName"] as? String
                            ?: "Service"
                        val unit = item["billingUnit"] as? String
                            ?: ""
                        val quantity = (item["quantity"] as? Number)
                            ?.toLong() ?: 0L
                        val lineTotal =
                            (item["lineTotalCents"] as? Number)
                                ?.toLong() ?: 0L

                        val quantityLabel = if (unit == "HOUR") {
                            "$quantity minutes"
                        } else {
                            "$quantity sessions"
                        }

                        Text("$name — $quantityLabel")
                        Text(
                            "KES ${formatInvoiceMoney(lineTotal)}"
                        )
                    }

                    HorizontalDivider()

                    Text(
                        "Total: KES ${
                            formatInvoiceMoney(invoice.totalCents)
                        }",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            },
            confirmButton = {
                if (invoice.status == "PENDING_APPROVAL") {
                    Button(
                        enabled = actionInProgress == null,
                        onClick = {
                            pendingAction = "APPROVED"
                        }
                    ) {
                        Text("Approve")
                    }
                } else {
                    TextButton(onClick = { selectedInvoice = null }) {
                        Text("Close")
                    }
                }
            },
            dismissButton = {
                if (invoice.status == "PENDING_APPROVAL") {
                    TextButton(
                        enabled = actionInProgress == null,
                        onClick = {
                            pendingAction = "DISPUTED"
                        }
                    ) {
                        Text("Dispute")
                    }
                }
            }
        )

        if (pendingAction != null) {
            val action = pendingAction!!

            AlertDialog(
                onDismissRequest = {
                    if (actionInProgress == null) {
                        pendingAction = null
                    }
                },
                title = {
                    Text(
                        if (action == "APPROVED")
                            "Approve Invoice?"
                        else
                            "Dispute Invoice?"
                    )
                },
                text = {
                    Text(
                        if (action == "APPROVED")
                            "Confirm that the listed services " +
                                    "and total are correct."
                        else
                            "This invoice will be marked as " +
                                    "disputed and cannot proceed " +
                                    "to payment."
                    )
                },
                confirmButton = {
                    Button(
                        enabled = actionInProgress == null,
                        onClick = {
                            updateInvoiceStatus(invoice, action)
                        }
                    ) {
                        Text(
                            if (actionInProgress != null)
                                "Saving..."
                            else
                                "Confirm"
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = actionInProgress == null,
                        onClick = { pendingAction = null }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
