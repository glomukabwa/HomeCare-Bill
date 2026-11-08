package com.example.homecare_bill

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Locale

private data class AdminInvoice(
    val id: String,
    val recipientName: String,
    val serviceDate: String,
    val totalCents: Long,
    val status: String,
    val clientId: String,
    val caregiverId: String,
    val lineItems: List<Map<String, Any>>,
    val anomalyReasons: List<String>,
    val anomalyDetectionMethod: String,
    val mlAnomalyScore: Double?,
    val mlModelVersion: String,
    val adminReviewStatus: String = "NEEDS_REVIEW"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    onLogout: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }

    var invoices by remember {
        mutableStateOf<List<AdminInvoice>>(emptyList())
    }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    var selectedInvoice by remember { mutableStateOf<AdminInvoice?>(null) }
    var reviewInProgress by remember {
        mutableStateOf(false)
    }

    var reviewError by remember {
        mutableStateOf("")
    }

    DisposableEffect(db) {
        val listener: ListenerRegistration = db.collection("invoices")
            .whereEqualTo("anomalyStatus", "FLAGGED_FOR_REVIEW")
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    errorMessage = "Unable to load flagged invoices."
                    isLoading = false
                    return@addSnapshotListener
                }

                invoices = snapshot?.documents?.map { doc ->
                    AdminInvoice(
                        id = doc.id,
                        recipientName = doc.getString("recipientName")
                            ?: "Unknown recipient",
                        serviceDate = doc.getString("serviceDate") ?: "",
                        totalCents = doc.getLong("totalCents") ?: 0L,
                        status = doc.getString("status") ?: "UNKNOWN",
                        clientId = doc.getString("clientId") ?: "",
                        caregiverId = doc.getString("caregiverId") ?: "",
                        lineItems = (doc.get("lineItems") as? List<*>)
                            ?.mapNotNull { item ->
                                (item as? Map<*, *>)?.entries
                                    ?.filter { it.key is String }
                                    ?.associate { it.key as String to (it.value ?: "") }
                            } ?: emptyList(),
                        anomalyReasons = (doc.get("anomalyReasons") as? List<*>)
                            ?.filterIsInstance<String>() ?: emptyList(),
                        anomalyDetectionMethod =
                            doc.getString("anomalyDetectionMethod") ?: "",
                        mlAnomalyScore = doc.getDouble("mlAnomalyScore"),
                        mlModelVersion = doc.getString("mlModelVersion") ?: "",
                        adminReviewStatus =
                            doc.getString("adminReviewStatus") ?: "NEEDS_REVIEW"
                    )
                } ?: emptyList()

                errorMessage = ""
                isLoading = false
            }

        onDispose {
            listener.remove()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Dashboard") },
                actions = {
                    TextButton(onClick = onLogout) {
                        Text("Logout")
                    }
                }
            )
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            Text(
                text = "Invoice Monitoring",
                style = MaterialTheme.typography.headlineSmall
            )

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Flagged Invoices",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${invoices.size}",
                        style = MaterialTheme.typography.headlineLarge
                    )
                }
            }

            Text(
                text = "Invoices requiring attention",
                style = MaterialTheme.typography.titleMedium
            )

            when {
                isLoading -> {
                    CircularProgressIndicator()
                }

                errorMessage.isNotBlank() -> {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                invoices.isEmpty() -> {
                    Text(
                        text = "No flagged invoices found.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(invoices, key = { it.id }) { invoice ->

                            Card(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement =
                                        Arrangement.spacedBy(8.dp)
                                ) {

                                    Text(
                                        text = invoice.recipientName,
                                        style = MaterialTheme.typography.titleMedium
                                    )

                                    Text(
                                        text = "Service date: ${invoice.serviceDate}"
                                    )

                                    Text(
                                        text = "Invoice status: ${
                                            invoice.status.replace("_", " ")
                                        }"
                                    )

                                    Text(
                                        text = String.format(
                                            Locale.US,
                                            "KES %,.2f",
                                            invoice.totalCents / 100.0
                                        ),
                                        style = MaterialTheme.typography.titleLarge
                                    )

                                    Text(
                                        text = "Flagged for review",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodyMedium
                                    )

                                    Text(
                                        text = if (invoice.adminReviewStatus == "REVIEWED") {
                                            "Admin review: Reviewed"
                                        } else {
                                            "Admin review: Needs review"
                                        },
                                        color = if (invoice.adminReviewStatus == "REVIEWED") {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        }
                                    )

                                    TextButton(
                                        onClick = {
                                            selectedInvoice = invoice
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
    }


    fun markInvoiceReviewed(invoice: AdminInvoice) {
        if (reviewInProgress) return

        val adminUid = FirebaseAuth.getInstance().currentUser?.uid

        if (adminUid == null) {
            reviewError = "Your session has expired. Please log in again."
            return
        }

        reviewInProgress = true
        reviewError = ""

        db.collection("invoices")
            .document(invoice.id)
            .update(
                mapOf(
                    "adminReviewStatus" to "REVIEWED",
                    "adminReviewedBy" to adminUid,
                    "adminReviewedAt" to FieldValue.serverTimestamp()
                )
            )
            .addOnSuccessListener {
                reviewInProgress = false
                selectedInvoice = null
            }
            .addOnFailureListener {
                reviewInProgress = false
                reviewError = "Unable to mark this invoice as reviewed."
            }
    }


    selectedInvoice?.let { invoice ->

        AlertDialog(
            onDismissRequest = {
                selectedInvoice = null
            },

            title = {
                Text("Flagged Invoice Details")
            },

            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (invoice.adminReviewStatus == "REVIEWED") {
                            "Admin review: Reviewed"
                        } else {
                            "Admin review: Needs review"
                        },
                        style = MaterialTheme.typography.titleSmall
                    )

                    if (reviewError.isNotBlank()) {
                        Text(
                            text = reviewError,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Text("Recipient: ${invoice.recipientName}")
                    Text("Service date: ${invoice.serviceDate}")
                    Text("Invoice status: ${invoice.status.replace("_", " ")}")

                    HorizontalDivider()

                    Text(
                        text = "Invoice Charges",
                        style = MaterialTheme.typography.titleMedium
                    )

                    invoice.lineItems.forEachIndexed { index, item ->

                        Text(
                            text = "Service ${index + 1}",
                            style = MaterialTheme.typography.titleSmall
                        )

                        item.forEach { (key, value) ->
                            Text(
                                text = "$key: $value",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    HorizontalDivider()

                    Text(
                        text = String.format(
                            Locale.US,
                            "Total: KES %,.2f",
                            invoice.totalCents / 100.0
                        ),
                        style = MaterialTheme.typography.titleMedium
                    )

                    HorizontalDivider()

                    Text(
                        text = "AI Anomaly Analysis",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = "Flagged for review",
                        color = MaterialTheme.colorScheme.error
                    )

                    val detectionMethod = when (
                        invoice.anomalyDetectionMethod
                    ) {
                        "ISOLATION_FOREST" -> "Isolation Forest"
                        "RULE_BASED" -> "Rule-based checks"
                        else -> "Unavailable"
                    }

                    Text("Detection method: $detectionMethod")

                    invoice.mlAnomalyScore?.let { score ->
                        Text(
                            text = "Anomaly score: ${
                                String.format(Locale.US, "%+.6f", score)
                            }"
                        )
                    }

                    if (invoice.mlModelVersion.isNotBlank()) {
                        Text(
                            text = "Model version: ${invoice.mlModelVersion}"
                        )
                    }

                    Text(
                        text = "The anomaly score indicates how unusual " +
                                "the invoice is relative to the model's " +
                                "training data. It is not a fraud probability.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (invoice.anomalyReasons.isNotEmpty()) {

                        Text(
                            text = "Detection notes",
                            style = MaterialTheme.typography.titleSmall
                        )

                        invoice.anomalyReasons.forEach { reason ->
                            Text("• $reason")
                        }
                    }
                }
            },

            confirmButton = {
                if (invoice.adminReviewStatus != "REVIEWED") {
                    Button(
                        onClick = {
                            markInvoiceReviewed(invoice)
                        },
                        enabled = !reviewInProgress
                    ) {
                        if (reviewInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Mark as Reviewed")
                        }
                    }
                }
            },

            dismissButton = {
                TextButton(
                    onClick = {
                        selectedInvoice = null
                        reviewError = ""
                    },
                    enabled = !reviewInProgress
                ) {
                    Text("Close")
                }
            }
        )
    }
}