package com.example.homecare_bill

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.delay
import java.util.Locale

private data class AdminService(
    val id: String,
    val name: String,
    val rateCents: Long,
    val billingUnit: String,
    val isActive: Boolean
)

private data class AdminInvoice(
    val id: String,
    val recipientName: String,
    val serviceDate: String,
    val totalCents: Long,
    val status: String,
    val clientId: String,
    val caregiverId: String,
    val clientName: String,
    val caregiverName: String,
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

    var services by remember {
        mutableStateOf<List<AdminService>>(emptyList())
    }

    var servicesLoading by remember {
        mutableStateOf(true)
    }

    var servicesError by remember {
        mutableStateOf("")
    }

    var showAddServiceDialog by remember {
        mutableStateOf(false)
    }

    var newServiceName by remember {
        mutableStateOf("")
    }

    var newServiceRate by remember {
        mutableStateOf("")
    }

    var newServiceBillingUnit by remember {
        mutableStateOf("HOUR")
    }

    var serviceSaveInProgress by remember {
        mutableStateOf(false)
    }

    var serviceSaveError by remember {
        mutableStateOf("")
    }

    var serviceSuccessMessage by remember {
        mutableStateOf("")
    }

    var highlightedServiceId by remember {
        mutableStateOf<String?>(null)
    }

    val servicesListState = rememberLazyListState()

    var serviceBeingEdited by remember {
        mutableStateOf<AdminService?>(null)
    }

    var editServiceName by remember {
        mutableStateOf("")
    }

    var editServiceRate by remember {
        mutableStateOf("")
    }

    var editServiceBillingUnit by remember {
        mutableStateOf("HOUR")
    }

    var editServiceInProgress by remember {
        mutableStateOf(false)
    }

    var editServiceError by remember {
        mutableStateOf("")
    }

    var serviceToToggle by remember {
        mutableStateOf<AdminService?>(null)
    }

    var serviceToggleInProgress by remember {
        mutableStateOf(false)
    }

    var serviceToggleError by remember {
        mutableStateOf("")
    }

    var selectedTab by rememberSaveable {
        mutableStateOf("home")
    }

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

    var selectedFilter by remember {
        mutableStateOf("NEEDS_REVIEW")
    }

    val needsReviewCount = invoices.count {
        it.adminReviewStatus != "REVIEWED"
    }

    val reviewedCount = invoices.count {
        it.adminReviewStatus == "REVIEWED"
    }

    val filteredInvoices = when (selectedFilter) {
        "NEEDS_REVIEW" -> invoices.filter {
            it.adminReviewStatus != "REVIEWED"
        }

        "REVIEWED" -> invoices.filter {
            it.adminReviewStatus == "REVIEWED"
        }

        else -> invoices
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
                        clientName = doc.getString("clientName") ?: "",
                        caregiverName = doc.getString("caregiverName") ?: "",
                        lineItems = (doc.get("items") as? List<*>)
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

    DisposableEffect(db) {

        val listener = db.collection("services")
            .addSnapshotListener { snapshot, error ->

                servicesLoading = false

                if (error != null) {
                    servicesError = "Unable to load services."
                    android.util.Log.e(
                        "AdminServices",
                        "Firestore service listener failed",
                        error
                    )
                    return@addSnapshotListener
                }

                services = snapshot?.documents?.map { document ->

                    AdminService(
                        id = document.id,
                        name = document.getString("name") ?: "",
                        rateCents = document.getLong("rateCents") ?: 0L,
                        billingUnit = document.getString("billingUnit") ?: "",
                        isActive = document.getBoolean("isActive") ?: false
                    )

                }?.sortedBy { it.name.lowercase() } ?: emptyList()

                servicesError = ""
            }

        onDispose {
            listener.remove()
        }
    }

    LaunchedEffect(highlightedServiceId, services) {

        val targetId = highlightedServiceId ?: return@LaunchedEffect

        val index = services.indexOfFirst { it.id == targetId }

        if (index >= 0) {
            servicesListState.animateScrollToItem(index)
        }
    }

    LaunchedEffect(highlightedServiceId) {

        if (highlightedServiceId != null) {

            delay(2500)

            highlightedServiceId = null
        }
    }

    LaunchedEffect(serviceSuccessMessage) {
        if (serviceSuccessMessage.isNotBlank()) {
            delay(3000)
            serviceSuccessMessage = ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (selectedTab) {
                            "monitoring" -> "Invoice Monitoring"
                            "services" -> "Service Catalogue"
                            else -> "Admin Dashboard"
                        }
                    )
                },
                actions = {
                    if (selectedTab == "home") {
                        TextButton(onClick = onLogout) {
                            Text("Logout")
                        }
                    }
                }
            )
        },
        bottomBar = {
            AdminBottomNavigation(
                selectedTab = selectedTab,
                onTabSelected = { tab ->
                    selectedTab = tab
                }
            )
        }
    ) { innerPadding ->

        when (selectedTab) {

            "home" -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {

                    Text(
                        text = "Welcome, Admin!",
                        style = MaterialTheme.typography.headlineMedium
                    )

                    Text(
                        text = "Manage invoice reviews and caregiving services.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    HorizontalDivider()

                    // Summary cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {

                        Card(
                            onClick = {
                                selectedFilter = "ALL"
                                selectedTab = "monitoring"
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Flagged Invoices",
                                    style = MaterialTheme.typography.titleSmall
                                )

                                Text(
                                    text = "${invoices.size}",
                                    style = MaterialTheme.typography.headlineLarge
                                )
                            }
                        }

                        Card(
                            onClick = {
                                selectedFilter = "NEEDS_REVIEW"
                                selectedTab = "monitoring"
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Needs Review",
                                    style = MaterialTheme.typography.titleSmall
                                )

                                Text(
                                    text = "$needsReviewCount",
                                    style = MaterialTheme.typography.headlineLarge
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Quick Actions",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Card(
                        onClick = {
                            selectedTab = "monitoring"
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Invoice Monitoring",
                                style = MaterialTheme.typography.titleMedium
                            )

                            Text(
                                text = "Review invoices flagged for unusual billing activity.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Card(
                        onClick = {
                            selectedTab = "services"
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Service Catalogue",
                                style = MaterialTheme.typography.titleMedium
                            )

                            Text(
                                text = "Manage available caregiving services.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            "services" -> {

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {

                    Text(
                        text = "Manage the services available to caregivers.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    HorizontalDivider()

                    Text(
                        text = "Total Services: ${services.size}",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Button(
                        onClick = {
                            newServiceName = ""
                            newServiceRate = ""
                            newServiceBillingUnit = "HOUR"
                            serviceSaveError = ""
                            showAddServiceDialog = true
                            serviceSuccessMessage = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add New Service")
                    }

                    if (serviceSuccessMessage.isNotBlank()) {

                        Text(
                            text = serviceSuccessMessage,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    when {

                        servicesLoading -> {
                            CircularProgressIndicator()
                        }

                        servicesError.isNotBlank() -> {
                            Text(
                                text = servicesError,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        services.isEmpty() -> {
                            Text("No services have been added yet.")
                        }

                        else -> {

                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                state = servicesListState,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {

                                items(services, key = { it.id }) { service ->

                                    Card(
                                        modifier = Modifier.fillMaxWidth(),

                                        border = if (highlightedServiceId == service.id) {
                                            BorderStroke(
                                                width = 2.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        } else {
                                            null
                                        },

                                        colors = CardDefaults.cardColors(
                                            containerColor = if (highlightedServiceId == service.id) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            }
                                        )
                                    ) {

                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {

                                            Text(
                                                text = service.name,
                                                style = MaterialTheme.typography.titleMedium
                                            )

                                            Text(
                                                text = String.format(
                                                    Locale.US,
                                                    "KES %,.2f",
                                                    service.rateCents / 100.0
                                                ),
                                                style = MaterialTheme.typography.titleLarge
                                            )

                                            Text(
                                                text = "Billing Unit: ${
                                                    when (service.billingUnit) {
                                                        "HOUR" -> "Per Hour"
                                                        "SESSION" -> "Per Session"
                                                        else -> service.billingUnit
                                                    }
                                                }"
                                            )

                                            Text(
                                                text = if (service.isActive) {
                                                    "Status: Active"
                                                } else {
                                                    "Status: Inactive"
                                                },
                                                color = if (service.isActive) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.error
                                                }
                                            )

                                            Button(
                                                onClick = {
                                                    serviceBeingEdited = service
                                                    editServiceName = service.name
                                                    editServiceRate = java.math.BigDecimal.valueOf(
                                                        service.rateCents,
                                                        2
                                                    ).toPlainString()
                                                    editServiceBillingUnit = service.billingUnit
                                                    editServiceError = ""
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                                            ) {
                                                Text("Edit Service")
                                            }

                                            Button(
                                                onClick = {
                                                    serviceToToggle = service
                                                    serviceToggleError = ""
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (service.isActive) {
                                                        MaterialTheme.colorScheme.errorContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.primaryContainer
                                                    },
                                                    contentColor = if (service.isActive) {
                                                        MaterialTheme.colorScheme.onErrorContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    }
                                                )
                                            ) {
                                                Text(
                                                    if (service.isActive) {
                                                        "Deactivate Service"
                                                    } else {
                                                        "Activate Service"
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {

                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Card(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp)
                                    ) {
                                        Text(
                                            text = "Flagged invoices",
                                            style = MaterialTheme.typography.bodyMedium
                                        )

                                        Text(
                                            text = "${invoices.size}",
                                            style = MaterialTheme.typography.headlineLarge
                                        )
                                    }
                                }

                                Card(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp)
                                    ) {
                                        Text(
                                            text = "Needs review",
                                            style = MaterialTheme.typography.bodyMedium
                                        )

                                        Text(
                                            text = "$needsReviewCount",
                                            style = MaterialTheme.typography.headlineLarge
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        text = "Invoices requiring attention",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedFilter == "NEEDS_REVIEW",
                            onClick = { selectedFilter = "NEEDS_REVIEW" },
                            label = { Text("Needs Review") }
                        )

                        FilterChip(
                            selected = selectedFilter == "REVIEWED",
                            onClick = { selectedFilter = "REVIEWED" },
                            label = { Text("Reviewed") }
                        )

                        FilterChip(
                            selected = selectedFilter == "ALL",
                            onClick = { selectedFilter = "ALL" },
                            label = { Text("All") }
                        )
                    }

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

                        filteredInvoices.isEmpty() -> {
                            Text(
                                text = when (selectedFilter) {
                                    "NEEDS_REVIEW" -> "No invoices awaiting review."
                                    "REVIEWED" -> "No reviewed invoices yet."
                                    else -> "No flagged invoices found."
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        else -> {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredInvoices, key = { it.id }) { invoice ->

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

                    Text(
                        text = "People Involved",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = "Caregiver: ${
                            invoice.caregiverName.ifBlank { "Not available" }
                        }"
                    )

                    Text(
                        text = "Client: ${
                            invoice.clientName.ifBlank { "Not available" }
                        }"
                    )

                    Text(
                        text = "Care recipient: ${invoice.recipientName}"
                    )

                    HorizontalDivider()

                    Text(
                        text = "Invoice Information",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text("Service date: ${invoice.serviceDate}")

                    Text(
                        text = "Invoice status: ${invoice.status.replace("_", " ")}"
                    )

                    HorizontalDivider()

                    Text(
                        text = "Invoice Charges",
                        style = MaterialTheme.typography.titleMedium
                    )

                    invoice.lineItems.forEach { item ->

                        val serviceName =
                            item["serviceName"] as? String ?: "Unknown service"

                        val billingUnit =
                            item["billingUnit"] as? String ?: ""

                        val quantity =
                            (item["quantity"] as? Number)?.toDouble() ?: 0.0

                        val unitRateCents =
                            (item["unitRateCents"] as? Number)?.toLong() ?: 0L

                        val lineTotalCents =
                            (item["lineTotalCents"] as? Number)?.toLong() ?: 0L

                        val quantityDescription = when (billingUnit) {

                            "HOUR" -> {
                                val hours = quantity / 60.0

                                String.format(
                                    Locale.US,
                                    "%g hours",
                                    hours
                                )
                            }

                            "SESSION" -> {
                                "${quantity.toInt()} sessions"
                            }

                            else -> {
                                "Quantity: $quantity"
                            }
                        }

                        val rateDescription = when (billingUnit) {
                            "HOUR" -> "Rate per hour"
                            "SESSION" -> "Rate per session"
                            else -> "Unit rate"
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {

                                Text(
                                    text = serviceName,
                                    style = MaterialTheme.typography.titleMedium
                                )

                                Text(
                                    text = "Duration / Quantity: $quantityDescription"
                                )

                                Text(
                                    text = String.format(
                                        Locale.US,
                                        "%s: KES %,.2f",
                                        rateDescription,
                                        unitRateCents / 100.0
                                    )
                                )

                                Text(
                                    text = String.format(
                                        Locale.US,
                                        "Service total: KES %,.2f",
                                        lineTotalCents / 100.0
                                    ),
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
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
                        text = "Automated Invoice Review",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = "⚠ Unusual billing pattern detected",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleSmall
                    )

                    Text(
                        text = "Please examine this invoice's details to determine whether further investigation is necessary.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        text = "This alert does not confirm overcharging or fraud.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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

    if (showAddServiceDialog) {

        AlertDialog(
            onDismissRequest = {
                if (!serviceSaveInProgress) {
                    showAddServiceDialog = false
                }
            },

            title = {
                Text("Add New Service")
            },

            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    OutlinedTextField(
                        value = newServiceName,
                        onValueChange = { newServiceName = it },
                        label = { Text("Service Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !serviceSaveInProgress
                    )

                    OutlinedTextField(
                        value = newServiceRate,
                        onValueChange = { newServiceRate = it },
                        label = { Text("Rate (KES)") },
                        placeholder = { Text("e.g. 1500.00") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !serviceSaveInProgress,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        )
                    )

                    Text(
                        text = "Billing Unit",
                        style = MaterialTheme.typography.titleSmall
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {

                        FilterChip(
                            selected = newServiceBillingUnit == "HOUR",
                            onClick = {
                                newServiceBillingUnit = "HOUR"
                            },
                            label = { Text("Per Hour") },
                            enabled = !serviceSaveInProgress
                        )

                        FilterChip(
                            selected = newServiceBillingUnit == "SESSION",
                            onClick = {
                                newServiceBillingUnit = "SESSION"
                            },
                            label = { Text("Per Session") },
                            enabled = !serviceSaveInProgress
                        )
                    }

                    if (serviceSaveError.isNotBlank()) {
                        Text(
                            text = serviceSaveError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },

            confirmButton = {
                Button(
                    onClick = {

                        val serviceName = newServiceName.trim()
                        val rate = newServiceRate.trim()
                            .toBigDecimalOrNull()

                        when {
                            serviceName.isBlank() -> {
                                serviceSaveError = "Please enter a service name."
                            }

                            serviceName.length > 100 -> {
                                serviceSaveError = "Service name must not exceed 100 characters."
                            }

                            rate == null || rate.signum() <= 0 -> {
                                serviceSaveError = "Please enter a valid rate greater than zero."
                            }

                            rate.scale() > 2 -> {
                                serviceSaveError = "Rate cannot have more than two decimal places."
                            }

                            else -> {

                                val rateCents = try {
                                    rate.movePointRight(2).longValueExact()
                                } catch (e: ArithmeticException) {
                                    null
                                }

                                if (rateCents == null || rateCents <= 0L) {
                                    serviceSaveError = "The rate is too large or invalid."
                                } else {

                                    serviceSaveInProgress = true
                                    serviceSaveError = ""

                                    val serviceData = hashMapOf(
                                        "name" to serviceName,
                                        "rateCents" to rateCents,
                                        "billingUnit" to newServiceBillingUnit,
                                        "isActive" to true
                                    )

                                    db.collection("services")
                                        .add(serviceData)
                                        .addOnSuccessListener { documentReference ->

                                            serviceSaveInProgress = false
                                            showAddServiceDialog = false

                                            serviceSuccessMessage = "Service added successfully!"

                                            highlightedServiceId = documentReference.id
                                        }
                                        .addOnFailureListener { error ->
                                            serviceSaveInProgress = false
                                            serviceSaveError =
                                                error.localizedMessage
                                                    ?: "Unable to add service."
                                        }
                                }
                            }
                        }
                    },
                    enabled = !serviceSaveInProgress
                ) {
                    if (serviceSaveInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Save Service")
                    }
                }
            },

            dismissButton = {
                TextButton(
                    onClick = {
                        showAddServiceDialog = false
                    },
                    enabled = !serviceSaveInProgress
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    serviceBeingEdited?.let { service ->

        AlertDialog(
            onDismissRequest = {
                if (!editServiceInProgress) {
                    serviceBeingEdited = null
                }
            },

            title = {
                Text("Edit Service")
            },

            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    OutlinedTextField(
                        value = editServiceName,
                        onValueChange = {
                            editServiceName = it
                        },
                        label = { Text("Service Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !editServiceInProgress
                    )

                    OutlinedTextField(
                        value = editServiceRate,
                        onValueChange = {
                            editServiceRate = it
                        },
                        label = { Text("Rate (KES)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !editServiceInProgress,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        )
                    )

                    Text(
                        text = "Billing Unit",
                        style = MaterialTheme.typography.titleSmall
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {

                        FilterChip(
                            selected = editServiceBillingUnit == "HOUR",
                            onClick = {
                                editServiceBillingUnit = "HOUR"
                            },
                            label = { Text("Per Hour") },
                            enabled = !editServiceInProgress
                        )

                        FilterChip(
                            selected = editServiceBillingUnit == "SESSION",
                            onClick = {
                                editServiceBillingUnit = "SESSION"
                            },
                            label = { Text("Per Session") },
                            enabled = !editServiceInProgress
                        )
                    }

                    if (editServiceError.isNotBlank()) {
                        Text(
                            text = editServiceError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },

            confirmButton = {
                Button(
                    onClick = {

                        val serviceName = editServiceName.trim()
                        val rate = editServiceRate.trim()
                            .toBigDecimalOrNull()

                        when {
                            serviceName.isBlank() -> {
                                editServiceError = "Please enter a service name."
                            }

                            serviceName.length > 100 -> {
                                editServiceError =
                                    "Service name must not exceed 100 characters."
                            }

                            rate == null || rate.signum() <= 0 -> {
                                editServiceError =
                                    "Please enter a valid rate greater than zero."
                            }

                            rate.scale() > 2 -> {
                                editServiceError =
                                    "Rate cannot have more than two decimal places."
                            }

                            editServiceBillingUnit !in listOf("HOUR", "SESSION") -> {
                                editServiceError = "Please select a billing unit."
                            }

                            else -> {

                                val rateCents = try {
                                    rate.movePointRight(2).longValueExact()
                                } catch (e: ArithmeticException) {
                                    null
                                }

                                if (rateCents == null || rateCents <= 0L) {
                                    editServiceError =
                                        "The rate is too large or invalid."
                                } else {

                                    editServiceInProgress = true
                                    editServiceError = ""

                                    db.collection("services")
                                        .document(service.id)
                                        .update(
                                            mapOf(
                                                "name" to serviceName,
                                                "rateCents" to rateCents,
                                                "billingUnit" to editServiceBillingUnit
                                            )
                                        )
                                        .addOnSuccessListener {

                                            editServiceInProgress = false
                                            serviceBeingEdited = null

                                            serviceSuccessMessage =
                                                "Service updated successfully!"

                                            highlightedServiceId = service.id
                                        }
                                        .addOnFailureListener { error ->

                                            editServiceInProgress = false
                                            editServiceError =
                                                error.localizedMessage
                                                    ?: "Unable to update service."
                                        }
                                }
                            }
                        }
                    },
                    enabled = !editServiceInProgress
                ) {
                    if (editServiceInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Save Changes")
                    }
                }
            },

            dismissButton = {
                TextButton(
                    onClick = {
                        serviceBeingEdited = null
                    },
                    enabled = !editServiceInProgress
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    serviceToToggle?.let { service ->

        val activating = !service.isActive

        AlertDialog(
            onDismissRequest = {
                if (!serviceToggleInProgress) {
                    serviceToToggle = null
                }
            },

            title = {
                Text(
                    if (activating) "Activate Service"
                    else "Deactivate Service"
                )
            },

            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        if (activating) {
                            "Are you sure you want to activate ${service.name}? " +
                                    "Caregivers will be able to select this service again."
                        } else {
                            "Are you sure you want to deactivate ${service.name}? " +
                                    "It will no longer be available for caregivers " +
                                    "to select when creating new invoices."
                        }
                    )

                    if (serviceToggleError.isNotBlank()) {
                        Text(
                            text = serviceToggleError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },

            confirmButton = {
                Button(
                    onClick = {

                        serviceToggleInProgress = true
                        serviceToggleError = ""

                        db.collection("services")
                            .document(service.id)
                            .update("isActive", activating)
                            .addOnSuccessListener {

                                serviceToggleInProgress = false
                                serviceToToggle = null

                                serviceSuccessMessage =
                                    if (activating) {
                                        "Service activated successfully!"
                                    } else {
                                        "Service deactivated successfully!"
                                    }

                                highlightedServiceId = service.id
                            }
                            .addOnFailureListener { error ->

                                serviceToggleInProgress = false
                                serviceToggleError =
                                    error.localizedMessage
                                        ?: "Unable to update service status."
                            }
                    },
                    enabled = !serviceToggleInProgress,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (activating) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                ) {
                    if (serviceToggleInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            if (activating) "Activate"
                            else "Deactivate"
                        )
                    }
                }
            },

            dismissButton = {
                TextButton(
                    onClick = {
                        serviceToToggle = null
                    },
                    enabled = !serviceToggleInProgress
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}


@Composable
fun AdminBottomNavigation(
    selectedTab: String,
    onTabSelected: (String) -> Unit
) {
    NavigationBar {

        NavigationBarItem(
            selected = selectedTab == "home",
            onClick = { onTabSelected("home") },
            icon = {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Home"
                )
            },
            label = { Text("Home") }
        )

        NavigationBarItem(
            selected = selectedTab == "monitoring",
            onClick = { onTabSelected("monitoring") },
            icon = {
                Icon(
                    imageVector = Icons.Default.Assignment,
                    contentDescription = "Monitoring"
                )
            },
            label = { Text("Monitoring") }
        )

        NavigationBarItem(
            selected = selectedTab == "services",
            onClick = { onTabSelected("services") },
            icon = {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = "Services"
                )
            },
            label = { Text("Services") }
        )
    }
}