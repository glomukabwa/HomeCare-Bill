
package com.example.homecare_bill

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateInvoiceScreen(
    assignmentId: String,
    onBack: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }

    var services by remember {
        mutableStateOf<List<CareService>>(emptyList())
    }

    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf("") }
    var submitError by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    var submittedInvoiceId by remember {
        mutableStateOf<String?>(null)
    }

    var submittedTotalCents by remember {
        mutableLongStateOf(0L)
    }

    val dateFormatter = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            isLenient = false
        }
    }

    val today = remember {
        dateFormatter.format(Date())
    }

    var serviceDate by remember {
        mutableStateOf(today)
    }

    var lineItems by remember {
        mutableStateOf(
            listOf(
                InvoiceLineItem(
                    id = 1L,
                    quantity = "60"
                )
            )
        )
    }

    var nextItemId by remember {
        mutableLongStateOf(2L)
    }

    // Reuse this ID if a submission must be retried.
    val requestId = remember {
        UUID.randomUUID()
    }

    DisposableEffect(Unit) {
        val listener = db.collection("services")
            .whereEqualTo("isActive", true)
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    loadError = error.localizedMessage
                        ?: "Unable to load services."
                    isLoading = false
                    return@addSnapshotListener
                }

                services = snapshot?.documents?.map { document ->
                    CareService(
                        id = document.id,
                        name = document.getString("name") ?: "",
                        rateCents = document.getLong("rateCents") ?: 0L,
                        billingUnit = document.getString("billingUnit") ?: "",
                        isActive = document.getBoolean("isActive") ?: false
                    )
                } ?: emptyList()

                loadError = ""
                isLoading = false
            }

        onDispose {
            listener.remove()
        }
    }

    fun calculateLineTotal(item: InvoiceLineItem): Long? {
        val service = services.find {
            it.id == item.serviceId
        } ?: return null

        val quantity = item.quantity.toLongOrNull()
            ?: return null

        if (quantity !in 1L..1440L) return null
        if (service.rateCents <= 0L) return null

        return when (service.billingUnit) {
            "HOUR" -> {
                if (quantity % 15L != 0L) {
                    null
                } else {
                    // Matches the backend's nearest-cent rounding.
                    (service.rateCents * quantity + 30L) / 60L
                }
            }

            "SESSION" -> {
                if (quantity > 100L) {
                    null
                } else {
                    service.rateCents * quantity
                }
            }

            else -> null
        }
    }

    val dateIsValid = remember(serviceDate, today) {
        try {
            val parsed = dateFormatter.parse(serviceDate)
            val parsedToday = dateFormatter.parse(today)

            parsed != null &&
                    parsedToday != null &&
                    parsed.time <= parsedToday.time &&
                    parsed.time >=
                    parsedToday.time - 365L * 24 * 60 * 60 * 1000
        } catch (e: Exception) {
            false
        }
    }

    val validItems = lineItems.mapNotNull { item ->
        val quantity = item.quantity.toLongOrNull()
        val lineTotal = calculateLineTotal(item)

        if (quantity != null && lineTotal != null) {
            InvoiceSubmissionItem(
                serviceId = item.serviceId,
                quantity = quantity
            )
        } else {
            null
        }
    }

    val allItemsValid =
        lineItems.isNotEmpty() &&
                lineItems.size <= 10 &&
                validItems.size == lineItems.size

    val invoiceTotal = lineItems.sumOf {
        calculateLineTotal(it) ?: 0L
    }

    fun formatMoney(cents: Long): String {
        return String.format(
            Locale.US,
            "%,.2f",
            cents / 100.0
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Invoice") },
                navigationIcon = {
                    TextButton(
                        onClick = onBack,
                        enabled = !isSubmitting
                    ) {
                        Text("Back")
                    }
                }
            )
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator()
                }

                loadError.isNotEmpty() -> {
                    Text(
                        text = loadError,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                submittedInvoiceId != null -> {
                    Text(
                        text = "Invoice Submitted!",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        "Your invoice has been sent to the client " +
                                "for approval."
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        "Official Total: KES " +
                                formatMoney(submittedTotalCents)
                    )

                    Spacer(Modifier.height(8.dp))

                    Text("Status: Pending Approval")

                    Spacer(Modifier.height(24.dp))

                    Button(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Done")
                    }
                }

                services.isEmpty() -> {
                    Text("No services are currently available.")
                }

                else -> {
                    LazyColumn(
                        verticalArrangement =
                            Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(
                            bottom = 24.dp
                        )
                    ) {
                        item {
                            Text(
                                text = "Invoice Details",
                                style =
                                    MaterialTheme.typography.titleLarge
                            )

                            Spacer(Modifier.height(12.dp))

                            OutlinedTextField(
                                value = serviceDate,
                                onValueChange = {
                                    serviceDate = it
                                    submitError = ""
                                },
                                label = {
                                    Text("Service Date (YYYY-MM-DD)")
                                },
                                supportingText = {
                                    Text(
                                        "Enter the date the care " +
                                                "service was provided."
                                    )
                                },
                                isError = !dateIsValid,
                                singleLine = true,
                                enabled = !isSubmitting,
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (!dateIsValid) {
                                Text(
                                    text =
                                        "Enter a valid date within " +
                                                "the past 365 days.",
                                    color =
                                        MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        item {
                            Text(
                                text = "Services Provided",
                                style =
                                    MaterialTheme.typography.titleLarge
                            )
                        }

                        items(
                            lineItems,
                            key = { it.id }
                        ) { item ->

                            var expanded by remember {
                                mutableStateOf(false)
                            }

                            val selectedService = services.find {
                                it.id == item.serviceId
                            }

                            val lineTotal = calculateLineTotal(item)

                            Card(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement =
                                        Arrangement.spacedBy(12.dp)
                                ) {
                                    ExposedDropdownMenuBox(
                                        expanded = expanded,
                                        onExpandedChange = {
                                            if (!isSubmitting) {
                                                expanded = !expanded
                                            }
                                        }
                                    ) {
                                        OutlinedTextField(
                                            value =
                                                selectedService?.name
                                                    ?: "Select Service",
                                            onValueChange = {},
                                            readOnly = true,
                                            enabled = !isSubmitting,
                                            label = {
                                                Text("Service")
                                            },
                                            trailingIcon = {
                                                ExposedDropdownMenuDefaults
                                                    .TrailingIcon(expanded)
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .menuAnchor()
                                        )

                                        ExposedDropdownMenu(
                                            expanded = expanded,
                                            onDismissRequest = {
                                                expanded = false
                                            }
                                        ) {
                                            services.forEach { service ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(service.name)
                                                    },
                                                    onClick = {
                                                        val defaultQuantity =
                                                            if (
                                                                service.billingUnit ==
                                                                "HOUR"
                                                            ) {
                                                                "60"
                                                            } else {
                                                                "1"
                                                            }

                                                        lineItems =
                                                            lineItems.map {
                                                                if (
                                                                    it.id ==
                                                                    item.id
                                                                ) {
                                                                    it.copy(
                                                                        serviceId =
                                                                            service.id,
                                                                        quantity =
                                                                            defaultQuantity
                                                                    )
                                                                } else {
                                                                    it
                                                                }
                                                            }

                                                        submitError = ""
                                                        expanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    if (selectedService != null) {
                                        Text(
                                            text =
                                                "Rate: KES " +
                                                        formatMoney(
                                                            selectedService.rateCents
                                                        ) +
                                                        " per " +
                                                        selectedService.billingUnit
                                                            .lowercase()
                                        )

                                        OutlinedTextField(
                                            value = item.quantity,
                                            onValueChange = { newValue ->
                                                if (
                                                    newValue.all {
                                                        it.isDigit()
                                                    }
                                                ) {
                                                    lineItems =
                                                        lineItems.map {
                                                            if (
                                                                it.id ==
                                                                item.id
                                                            ) {
                                                                it.copy(
                                                                    quantity =
                                                                        newValue
                                                                )
                                                            } else {
                                                                it
                                                            }
                                                        }

                                                    submitError = ""
                                                }
                                            },
                                            label = {
                                                Text(
                                                    if (
                                                        selectedService.billingUnit ==
                                                        "HOUR"
                                                    ) {
                                                        "Duration (minutes)"
                                                    } else {
                                                        "Number of sessions"
                                                    }
                                                )
                                            },
                                            supportingText = {
                                                Text(
                                                    if (
                                                        selectedService.billingUnit ==
                                                        "HOUR"
                                                    ) {
                                                        "Use 15-minute increments."
                                                    } else {
                                                        "Enter 1 to 100 sessions."
                                                    }
                                                )
                                            },
                                            isError = lineTotal == null,
                                            enabled = !isSubmitting,
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType =
                                                    KeyboardType.Number
                                            ),
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Text(
                                            text = if (lineTotal != null) {
                                                "Line Total: KES " +
                                                        formatMoney(lineTotal)
                                            } else {
                                                "Enter a valid quantity."
                                            },
                                            color = if (
                                                lineTotal == null
                                            ) {
                                                MaterialTheme.colorScheme.error
                                            } else {
                                                MaterialTheme.colorScheme
                                                    .onSurface
                                            }
                                        )
                                    }

                                    if (lineItems.size > 1) {
                                        OutlinedButton(
                                            onClick = {
                                                lineItems =
                                                    lineItems.filter {
                                                        it.id != item.id
                                                    }
                                                submitError = ""
                                            },
                                            enabled = !isSubmitting
                                        ) {
                                            Text("Remove Service")
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            OutlinedButton(
                                onClick = {
                                    lineItems =
                                        lineItems + InvoiceLineItem(
                                            id = nextItemId,
                                            quantity = "60"
                                        )

                                    nextItemId++
                                    submitError = ""
                                },
                                enabled =
                                    !isSubmitting &&
                                            lineItems.size < 10,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Add Another Service")
                            }
                        }

                        item {
                            HorizontalDivider()

                            Spacer(Modifier.height(12.dp))

                            Text(
                                text =
                                    "Estimated Total: KES " +
                                            formatMoney(invoiceTotal),
                                style =
                                    MaterialTheme.typography.titleLarge
                            )

                            Spacer(Modifier.height(8.dp))

                            Text(
                                text =
                                    "The official total will be " +
                                            "verified by the server.",
                                style =
                                    MaterialTheme.typography.bodySmall
                            )

                            if (submitError.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))

                                Text(
                                    text = submitError,
                                    color =
                                        MaterialTheme.colorScheme.error
                                )
                            }

                            Spacer(Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    submitError = ""
                                    isSubmitting = true

                                    InvoiceApi.submitInvoice(
                                        assignmentId = assignmentId,
                                        serviceDate = serviceDate,
                                        items = validItems,
                                        requestId = requestId,
                                        onSuccess = { invoiceId, total ->
                                            isSubmitting = false
                                            submittedInvoiceId = invoiceId
                                            submittedTotalCents = total
                                        },
                                        onError = { message ->
                                            isSubmitting = false
                                            submitError = message
                                        }
                                    )
                                },
                                enabled =
                                    !isSubmitting &&
                                            dateIsValid &&
                                            allItemsValid &&
                                            invoiceTotal > 0L,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isSubmitting) {
                                    CircularProgressIndicator(
                                        modifier =
                                            Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("Submit Invoice")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
