
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
import java.util.Locale

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
    var errorMessage by remember { mutableStateOf("") }

    var lineItems by remember {
        mutableStateOf(
            listOf(
                InvoiceLineItem(
                    id = 1L,
                    quantity = "60"
                )
            )
            //The above code sets the initial value of lineItems to a list containing exactly one InvoiceLineItem object.
            //However, it does not mean the object only contains id and quantity. Because of how Kotlin handles default parameter values, that object secretly contains a third property (serviceId) behind the scenes.
        )
    }

    var nextItemId by remember { mutableLongStateOf(2L) }

    DisposableEffect(Unit) {
        val listener = db.collection("services")
            .whereEqualTo("isActive", true)
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    errorMessage = error.localizedMessage ?: "Unable to load services."
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

                isLoading = false
                errorMessage = ""
            }

        onDispose {
            listener.remove()
        }
    }

    fun calculateLineTotal(item: InvoiceLineItem): Long {//item is an instance of InvoiceLineItem and Long means that the final answer returned by the function will be a Long number
        val service = services.find { it.id == item.serviceId } ?: return 0L

        val quantity = item.quantity.toLongOrNull() ?: return 0L

        if (quantity <= 0L) return 0L

        return when (service.billingUnit) {

            "HOUR" -> {
                if (quantity % 15L != 0L) return 0L //For this first version, we'll require durations in 15-minute increments.

                (service.rateCents * quantity) / 60L
            }

            "SESSION" -> {
                service.rateCents * quantity
            }

            else -> 0L
        }
    }

    val invoiceTotal = lineItems.sumOf { calculateLineTotal(it) }
    //- lineItems is your list of services added to the invoice.
    //- sumOf goes through every item in the list, calculates a numeric value for it, and adds those values together.
    //- it represents the current InvoiceLineItem being processed.
    //- calculateLineTotal(it) calculates the cost of that particular service.

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Invoice") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
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

                errorMessage.isNotEmpty() -> {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                services.isEmpty() -> {
                    Text("No services are currently available.")
                }

                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {

                        item {
                            Text(
                                text = "Add Services",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }

                        //item { } adds a single item (or block of UI content) to the LazyColumn.
                        //items(...) { } adds multiple items by iterating through a collection, such as a list.

                        items(lineItems, key = { it.id }) { item ->

                            var expanded by remember { mutableStateOf(false) }

                            val selectedService = services.find { it.id == item.serviceId }

                            Card(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {

                                    ExposedDropdownMenuBox(
                                        expanded = expanded,
                                        onExpandedChange = { expanded = !expanded }
                                    ) {

                                        OutlinedTextField(
                                            value = selectedService?.name ?: "Select Service",
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("Service") },
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
                                            onDismissRequest = { expanded = false }
                                        ) {

                                            services.forEach { service ->

                                                DropdownMenuItem(
                                                    text = {
                                                        Text(service.name)
                                                    },
                                                    onClick = {

                                                        val defaultQuantity =
                                                            if (service.billingUnit == "HOUR") {
                                                                "60"
                                                            } else {
                                                                "1"
                                                            }

                                                        lineItems = lineItems.map {
                                                            if (it.id == item.id) {
                                                                it.copy(
                                                                    serviceId = service.id,
                                                                    quantity = defaultQuantity
                                                                )
                                                            } else {
                                                                it
                                                            }
                                                        }

                                                        expanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    if (selectedService != null) {

                                        Text(
                                            text = "Rate: KES ${
                                                String.format(
                                                    Locale.getDefault(),
                                                    "%,.2f",
                                                    selectedService.rateCents / 100.0
                                                )
                                            } per ${selectedService.billingUnit.lowercase()}"
                                        )

                                        OutlinedTextField(
                                            value = item.quantity,
                                            onValueChange = { newValue ->

                                                lineItems = lineItems.map {
                                                    if (it.id == item.id) {
                                                        it.copy(
                                                            quantity = newValue
                                                        )
                                                    } else {
                                                        it
                                                    }
                                                }
                                            },
                                            label = {
                                                Text(
                                                    if (selectedService.billingUnit == "HOUR") {
                                                        "Duration (minutes)"
                                                    } else {
                                                        "Number of sessions"
                                                    }
                                                )
                                            },
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Number
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Text(
                                            text = "Line Total: KES ${
                                                String.format(
                                                    Locale.getDefault(),
                                                    "%,.2f",
                                                    calculateLineTotal(item) / 100.0
                                                )
                                            }"
                                        )
                                    }

                                    if (lineItems.size > 1) {
                                        OutlinedButton(
                                            onClick = {
                                                lineItems = lineItems.filter {
                                                    it.id != item.id
                                                }
                                            }
                                        ) {
                                            Text("Remove Service")
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Button(
                                onClick = {
                                    lineItems = lineItems + InvoiceLineItem(
                                        id = nextItemId,
                                        quantity = "60"
                                    )

                                    nextItemId++
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Add Another Service")
                            }
                        }

                        item {
                            HorizontalDivider()

                            Spacer(
                                modifier = Modifier.height(12.dp)
                            )

                            Text(
                                text = "Invoice Total: KES ${
                                    String.format(
                                        Locale.getDefault(),
                                        "%,.2f",
                                        invoiceTotal / 100.0
                                    )
                                }",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }
                }
            }
        }
    }
}
