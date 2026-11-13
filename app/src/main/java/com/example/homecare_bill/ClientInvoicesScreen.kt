
package com.example.homecare_bill

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import java.util.Locale

private data class ClientInvoice(
    val id: String,
    val caregiverId: String,
    val recipientName: String,
    val serviceDate: String,
    val totalCents: Long,
    val status: String,
    val lineItems: List<Map<String, Any>>,
    //• List<...>: This is a standard ordered collection. It means you have multiple items stacked in a row.
    //• Map<String, Any>: This is the blueprint for each individual item inside that list.
    //	    • String: The keys (labels) must be text. For example: "product_name", "price", or "quantity".
    //	    • Any: The values attached to those labels can be absolutely anything (numbers, text, booleans, or even other objects).
    //Example of how it would look like:
    //// A list containing two separate items (Maps)
    //[
    //  // Item 1 (Map)
    //  {
    //    "id" -> 101,          // Int (which counts as 'Any')
    //    "name" -> "Laptop",    // String (which counts as 'Any')
    //    "price" -> 999.99,     // Double (which counts as 'Any')
    //    "isAvailable" -> true  // Boolean (which counts as 'Any')
    //  },
    //  // Item 2 (Map)
    //  {
    //    "id" -> 102,
    //    "name" -> "Mouse",
    //    "price" -> 25.00,
    //    "isAvailable" -> false
    //  }
    //]

    val paymentState: String = "",
    val currentPaymentId: String = "",
    val anomalyStatus: String = "",
    val isAnomaly: Boolean = false,
    val anomalyReasons: List<String> = emptyList(),
    val anomalyDetectionMethod: String = "",
    val mlAnomalyScore: Double? = null,//We're using Double? because older invoices might not have an ML anomaly score.
    val mlModelVersion: String = ""
)

private fun formatInvoiceMoney(cents: Long): String =
    String.format(Locale.US, "%,.2f", cents / 100.0)
//The above function expects cents as an input parameter and it has to be a Long
//The : String part means that the output of the function and then the part after it defines how the output should look like
//• Locale.US: This forces the formatting to use American numbering rules, ensuring that a comma is used for thousands and a
// period is used for decimals (e.g., 1,000.00), regardless of what country the user's phone is set to.
//• "%,.2f": This is a special formatting blueprint rule:
//	• %: Signals the start of a formatting instruction.
//	• ,: Tells the system to add commas as thousands separators (e.g., turning 1000 into 1,000).
//	• .2: Forces exactly two decimal places after the period, rounding if necessary (e.g., turning 5 into 5.00 or 5.678 into 5.68).
//	• f: Stands for "floating-point number," telling the formatter to expect a decimal value.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientInvoicesScreen(
    modifier: Modifier = Modifier,
    initialFilter: String = "ALL",
    targetInvoiceId: String? = null
) {

    val db = remember { FirebaseFirestore.getInstance() }
    val userId = FirebaseAuth.getInstance().currentUser?.uid

    var invoices by remember {
        mutableStateOf<List<ClientInvoice>>(emptyList())
    }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    var selectedFilter by remember(initialFilter) { mutableStateOf(initialFilter) }
    val invoiceListState = rememberLazyListState()

    var highlightedInvoiceId by remember {
        mutableStateOf<String?>(null)
    }

    val filterScrollState = rememberScrollState()

    LaunchedEffect(initialFilter, filterScrollState.maxValue) {
        if (initialFilter == "FLAGGED") {
            filterScrollState.animateScrollTo(filterScrollState.maxValue)
        } else {
            filterScrollState.scrollTo(0)
        }
    }

    var actionInProgress by remember { mutableStateOf<String?>(null) }
    var selectedInvoice by remember(initialFilter) {mutableStateOf<ClientInvoice?>(null) }
    var pendingAction by remember {
        mutableStateOf<String?>(null)
    }

    var paymentInvoiceId by remember {
        mutableStateOf<String?>(null)
    }

    var mpesaPhone by remember {
        mutableStateOf("")
    }

    var paymentLoading by remember {
        mutableStateOf(false)
    }

    var paymentMessage by remember {
        mutableStateOf("")
    }

    var paymentId by remember {
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
                        //This line fetches the data and creates a safety net so the app doesn't crash if the data is missing or corrupted.
                        //• as? List<*>: This is a safe cast. It asks: "Is this data actually a List of something?"
                        //	• If yes, it gives you the list.
                        //	• If no (e.g., the field is a string, a number, or completely missing/null), it returns null instead of crashing.In
                        //      Firestore
                        //• ?: emptyList<Any>(): This is the Elvis Operator. It says: "If the left side resulted in null, use this default
                        // backup value instead." In this case, your backup is just an empty, harmless list ([]).

                        val parsedItems = rawItems.mapNotNull { raw ->
                            @Suppress("UNCHECKED_CAST")
                            raw as? Map<String, Any>
                        }
                        //• rawItems.mapNotNull { ... }: This loops through every item in your raw list. It transforms the items, but automatically throws
                        //          away any item that becomes null. This filters out bad or corrupted items seamlessly.
                        //• @Suppress("UNCHECKED_CAST"): This is a note to the Kotlin compiler. Because of how Kotlin handles data at runtime, the app
                        //         can't completely verify that every single value inside the map matches your types perfectly. This annotation simply says:
                        //         "I know what I'm doing, don't show a yellow warning line under this code."
                        //• raw as? Map<String, Any>: It looks at the individual item (raw) and asks: "Is this a Map with String keys?"
                        //	    • If it is a valid map, it converts it to Map<String, Any>.
                        //	    • If it is not (e.g., someone accidentally saved a random number inside your items list), it returns null. Because you are
                        //	      using mapNotNull, that bad item is instantly dropped from the final results.

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
                            lineItems = parsedItems,
                            paymentState = doc.getString("paymentState") ?: "",
                            currentPaymentId = doc.getString("currentPaymentId") ?: "",
                            anomalyStatus = doc.getString("anomalyStatus") ?: "",
                            isAnomaly = doc.getBoolean("isAnomaly") ?: false,
                            anomalyReasons = (doc.get("anomalyReasons") as? List<*>)
                                ?.filterIsInstance<String>()
                                ?: emptyList(),
                            anomalyDetectionMethod =
                                doc.getString("anomalyDetectionMethod") ?: "",
                            mlAnomalyScore = doc.getDouble("mlAnomalyScore"),
                            mlModelVersion = doc.getString("mlModelVersion") ?: ""
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
        //The above two if statements are returning nothing at all—they are performing an early exit from the function.

        actionInProgress = invoice.id
        errorMessage = ""

        db.collection("invoices")
            .document(invoice.id)
            .update("status", newStatus)
            .addOnSuccessListener {

                actionInProgress = null
                pendingAction = null
                selectedInvoice = null

                if (newStatus == "APPROVED") {
                    paymentInvoiceId = invoice.id
                    paymentId = null
                    paymentMessage = ""
                    mpesaPhone = ""
                    paymentLoading = false
                }
            }
            .addOnFailureListener { exception ->
                actionInProgress = null
                pendingAction = null
                errorMessage = exception.localizedMessage
                    ?: "Unable to update invoice."
            }
    }

    val filteredInvoices = invoices.filter { invoice ->
        when (selectedFilter) {
            "PENDING" -> invoice.status == "PENDING_APPROVAL"
            "APPROVED" -> invoice.status == "APPROVED"
            "PAID" -> invoice.status == "PAID"
            "DISPUTED" -> invoice.status == "DISPUTED"
            "FLAGGED" -> invoice.anomalyStatus == "FLAGGED_FOR_REVIEW"
            else -> true
        }
    }

    LaunchedEffect(
        targetInvoiceId,
        selectedFilter,
        loading,
        filteredInvoices
    ) {
        if (
            targetInvoiceId != null &&
            !loading
        ) {
            val index = filteredInvoices.indexOfFirst {
                it.id == targetInvoiceId
            }

            if (index >= 0) {
                invoiceListState.animateScrollToItem(index)

                highlightedInvoiceId = targetInvoiceId

                delay(3000)

                highlightedInvoiceId = null
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("My Invoices") }
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

            val filters = listOf(
                "ALL" to "All",
                "PENDING" to "Pending",
                "APPROVED" to "Approved",
                "PAID" to "Paid",
                "DISPUTED" to "Disputed",
                "FLAGGED" to "Flagged"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(filterScrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filters.forEach { (value, label) ->
                    FilterChip(
                        selected = selectedFilter == value,
                        onClick = { selectedFilter = value },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when {
                loading -> CircularProgressIndicator()

                invoices.isEmpty() -> {
                    Text("You have no invoices yet.")
                }

                filteredInvoices.isEmpty() -> {
                    Text("No invoices found for this status.")
                }

                else -> LazyColumn(
                    state = invoiceListState,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredInvoices, key = { it.id }) { invoice ->

                        val cardColor by animateColorAsState(
                            targetValue = if (highlightedInvoiceId == invoice.id) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            label = "Invoice highlight"
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = cardColor
                            )
                        ) {
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
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Recipient: ${invoice.recipientName}")
                    Text("Service date: ${invoice.serviceDate}")
                    Text("Status: ${invoice.status}")

                    if (invoice.anomalyStatus == "FLAGGED_FOR_REVIEW") {

                        HorizontalDivider()

                        Text(
                            text = "⚠ Please review this invoice",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.titleSmall
                        )

                        Text(
                            text = "Some charges on this invoice appear unusual. " +
                                    "Please check the services and amounts carefully " +
                                    "before approving."
                        )
                    }

                    if (invoice.paymentState.isNotBlank()) {
                        Text(
                            "Payment: ${invoice.paymentState.replace("_", " ")}"
                        )
                    }
                    if (
                        invoice.currentPaymentId.isNotBlank() &&
                        invoice.paymentState in listOf(
                            "INITIATING", "PENDING", "UNKNOWN"
                        )
                    ) {
                        TextButton(
                            onClick = {
                                paymentInvoiceId = invoice.id
                                paymentId = invoice.currentPaymentId
                                paymentMessage = "Checking the existing payment attempt."
                                selectedInvoice = null
                            }
                        ) {
                            Text("Check Existing Payment")
                        }
                    }

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
                when {
                    invoice.status == "PENDING_APPROVAL" -> {
                        Button(
                            enabled = actionInProgress == null,
                            onClick = {
                                pendingAction = "APPROVED"
                            }
                        ) {
                            Text("Approve")
                        }
                    }

                    invoice.status == "APPROVED" &&
                            (invoice.paymentState.isBlank() ||
                                    invoice.paymentState == "FAILED") -> {
                        Button(
                            onClick = {
                                paymentInvoiceId = invoice.id
                                paymentId = null
                                paymentMessage = ""
                                mpesaPhone = ""
                                selectedInvoice = null
                            }
                        ) {
                            Text("Pay with M-Pesa")
                        }
                    }

                    else -> {
                        TextButton(
                            onClick = { selectedInvoice = null }
                        ) {
                            Text("Close")
                        }
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


    paymentInvoiceId?.let { invoiceId ->

        val invoice = invoices.find { it.id == invoiceId }

        if (invoice != null) {
            AlertDialog(
                onDismissRequest = {
                    if (!paymentLoading) {
                        paymentInvoiceId = null
                    }
                },
                title = {
                    Text("Pay Invoice with M-Pesa")
                },
                text = {
                    Column(
                        verticalArrangement =
                            Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Amount: KES ${
                                formatInvoiceMoney(invoice.totalCents)
                            }"
                        )

                        Text(
                            "Please verify the amount before proceeding with the payment."
                        )

                        if (paymentId == null) {
                            OutlinedTextField(
                                value = mpesaPhone,
                                onValueChange = { mpesaPhone = it },
                                label = { Text("M-Pesa Phone Number") },
                                placeholder = { Text("254XXXXXXXXX") },
                                singleLine = true,
                                enabled = !paymentLoading
                            )
                        }

                        if (paymentLoading) {
                            CircularProgressIndicator()
                        }

                        if (paymentMessage.isNotBlank()) {
                            Text(paymentMessage)
                        }
                    }
                },
                confirmButton = {
                    if (paymentId == null) {
                        Button(
                            enabled = !paymentLoading &&
                                    mpesaPhone.isNotBlank() &&
                                    paymentMessage.isBlank(),
                            onClick = {
                                paymentLoading = true
                                paymentMessage = ""

                                MpesaApi.initiatePayment(
                                    invoiceId = invoice.id,
                                    phoneNumber = mpesaPhone
                                ) { response, error ->
                                    paymentLoading = false

                                    if (error != null) {
                                        paymentMessage =
                                            "$error Do not retry if " +
                                                    "the payment outcome is uncertain."
                                    } else {
                                        paymentId = response
                                            ?.optString("paymentId")
                                            ?.takeIf { it.isNotBlank() }

                                        paymentMessage =
                                            "Payment request submitted. " +
                                                    "Awaiting confirmation."
                                    }
                                }
                            }
                        ) {
                            Text("Send STK Push")
                        }
                    } else {
                        Button(
                            enabled = !paymentLoading,
                            onClick = {
                                val id = paymentId
                                    ?: return@Button

                                paymentLoading = true

                                MpesaApi.checkPaymentStatus(id) {
                                        response, error ->

                                    paymentLoading = false

                                    if (error != null) {
                                        paymentMessage = error
                                    } else {
                                        val status = response
                                            ?.optString("status")
                                            ?: "UNKNOWN"

                                        paymentMessage = when (status) {
                                            "SUCCESS" ->
                                                "Sandbox payment verified successfully."

                                            "FAILED" ->
                                                "Payment was unsuccessful."

                                            "PENDING", "INITIATING" ->
                                                "Payment is still processing."

                                            else ->
                                                "Payment outcome is not yet confirmed."
                                        }
                                    }
                                }
                            }
                        ) {
                            Text("Check Payment Status")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !paymentLoading,
                        onClick = {
                            paymentInvoiceId = null
                        }
                    ) {
                        Text("Close")
                    }
                }
            )
        }
    }

}
