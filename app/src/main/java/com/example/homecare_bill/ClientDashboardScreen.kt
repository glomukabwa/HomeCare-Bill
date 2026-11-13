package com.example.homecare_bill

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

data class CareRecipient(//A data class is a Kotlin class primarily designed to hold data.
    val id: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val relationship: String = ""
)

@Composable
fun ClientDashboardScreen(
    onLogout: () -> Unit,
    onAddCareRecipient: () -> Unit,
    onRecipientClick: (String) -> Unit,
    onViewInvoices: () -> Unit
){
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }

    var firstName by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf("home") }
    var billingInitialFilter by remember { mutableStateOf("ALL") }
    var targetInvoiceId by remember { mutableStateOf<String?>(null) }

    var careRecipients by remember { mutableStateOf<List<CareRecipient>>(emptyList()) }
    //Explanation:
    //- List<CareRecipient> means a list containing CareRecipient objects.
    //- emptyList() initializes it as an empty list.
    //- mutableStateOf() makes the list observable by Compose.
    //- remember preserves the state during recomposition.
    //- var allows us to replace the list when Firestore returns the recipients.
    
    var recipientsLoading by remember { mutableStateOf(true) }
    var recipientsError by remember { mutableStateOf("") }

    var flaggedInvoiceCount by remember {
        mutableStateOf(0)
    }

    var pendingFlaggedCount by remember {
        mutableStateOf(0)
    }

    var alertsLoading by remember {
        mutableStateOf(true)
    }

    var alertsError by remember {
        mutableStateOf("")
    }

    LaunchedEffect(Unit) {
        //LaunchedEffect() means: Run this work when the screen appears, and only restart it if the key I gave you changes.
        //In a different page I put LaunchedEffect(retryCount) to mean: Run the code in the brackets if retryCount changes. Here, Unit is a constant key which means the code in
        //the brackets runs only during the first composition. If any recomposition occurs bcz of whatever reason, it doesn't run which is a good thing cz we don't want to be
        // retrieving the first name severally and unnecessarily.
        //Chat used the following statement to define LaunchedEffect: "a Jetpack Compose function that allows us to run a coroutine when a Composable enters the screen or when a specified key changes."
        //I would like to explain what a coroutine is. It is a unit of work that can be suspended and resumed without blocking the thread it is running on. Basically, the screen won't lag just bcz
        //Firestore is still retrieving the first name. The UI will be responsive as the rettrieval is happening at its own pace.

        val user = auth.currentUser

        if(user == null){
            isLoading = false
            errorMessage = "Your session has expired. Please sign in again."
        }else{
            db.collection("users")
                .document(user.uid)
                .get()
                .addOnSuccessListener { document ->
                    isLoading = false

                    if(document.exists()){
                        firstName = document.getString("firstName") ?: ""
                    }else{
                        errorMessage = "Your profile could not be found."
                    }
                }
                .addOnFailureListener {
                    isLoading = false
                    errorMessage = "Unable to load your profile. Please try again."
                }
        }
    }

    DisposableEffect(Unit) {
        //Firestore provides a real-time listener called addSnapshotListener().
        //This listener can automatically update our dashboard whenever a recipient is added or edited.
        //It also needs to be removed when the screen leaves composition, which is what DisposableEffect helps us manage.

        //DisposableEffect(Unit) runs once just like LaunchedEffect(Unit). So how do the changes reflect? When DisposableEffect(Unit) runs at the beginning,
        //it sets up the snapshot listener. The snapshot listener is what now continuously runs depending on changes. If a change occurs the code from .addSnapshotListener... is triggered
        //Another difference btwn Launched and Disposable though is that Launched cancels its coroutine when leaving composition while Disposable executes onDispose when leaving composition

        val uid = auth.currentUser?.uid

        if (uid == null) {
            recipientsLoading = false
            recipientsError = "Please sign in again."
            onDispose { }
        } else {

            val listener = db.collection("careRecipients")
                .whereEqualTo("clientId", uid) //This retrieves only documents in the collection where clientId matches the currently signed-in user's UID. This is important cz we want the client to access only the recipients they manage
                .addSnapshotListener { snapshot, exception ->

                    recipientsLoading = false

                    if (exception != null) {
                        recipientsError =
                            "Unable to load care recipients."
                    } else {
                        recipientsError = ""

                        careRecipients = snapshot?.documents?.map { document ->
                            //snapshot contains the recipients associated with the client so sth like this:
                            //      abc123
                            //          firstName: "Mary"
                            //          lastName: "Wanjiku"
                            //          relationship: "Mother"
                            //      xyz456
                            //          firstName: "John"
                            //          lastName: "Kamau"
                            //          relationship: "Father"
                            //document gets the list of documents so sth like this:
                            //      [
                            //          Document abc123,
                            //          Document xyz456
                            //      ]
                            //map() is a function that transforms every element in a collection into something else. In this case it converts the content of the documents to CareRecipient objects
                            CareRecipient(
                                id = document.id,//document.id already returns a String. We don't need to convert it. The id is the unique value created by Firebase.
                                firstName = document.getString("firstName") ?: "", //?: checks if what is on the left is either missing or null
                                lastName = document.getString("lastName") ?: "",
                                relationship = document.getString("relationship") ?: ""
                            )
                        } ?: emptyList()
                    }
                }

            onDispose {
                listener.remove()
            }
        }
    }

    DisposableEffect(auth.currentUser?.uid) {

        val uid = auth.currentUser?.uid

        if (uid == null) {
            alertsLoading = false
            alertsError = "Please sign in again."

            onDispose { }
        } else {

            val listener = db.collection("invoices")
                .whereEqualTo("clientId", uid)
                .addSnapshotListener { snapshot, exception ->

                    alertsLoading = false

                    if (exception != null) {
                        alertsError = "Unable to load billing alerts."
                        return@addSnapshotListener
                    }

                    alertsError = ""

                    val flaggedInvoices = snapshot?.documents
                        ?.filter { document ->
                            document.getString("anomalyStatus") ==
                                    "FLAGGED_FOR_REVIEW"
                        }
                        ?: emptyList()

                    flaggedInvoiceCount = flaggedInvoices.size

                    pendingFlaggedCount = flaggedInvoices.count { document ->
                        document.getString("status") == "PENDING_APPROVAL"
                    }
                }

            onDispose {
                listener.remove()
            }
        }
    }

    Scaffold(
        bottomBar = {
            ClientBottomNavigation(
                selectedTab = selectedTab,
                onTabSelected = { tab ->
                    if (tab == "billing") {
                        billingInitialFilter = "ALL"
                        targetInvoiceId = null
                    }

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
                    verticalArrangement = Arrangement.spacedBy(20.dp)//This automatically adds 20 dp of spacing between the direct children of the main Column. It saves us from having to insert Spacer after every section.
                ) {

                    //APPLICATION HEADER
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "HomeCare Bill",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Profile",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    HorizontalDivider()

                    //PERSONALIZED WELCOME MESSAGE
                    when{
                        isLoading -> {
                            CircularProgressIndicator()
                        }

                        errorMessage.isNotEmpty() -> {
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        else -> {
                            Column{
                                Text(
                                    text = "Welcome back, $firstName!",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "Here's your care and billing overview.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    //SUMMARY CARDS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ClientSummaryCard(
                            title = "Outstanding Balance",
                            value = "KSh —",
                            description = "Awaiting billing data",
                            modifier = Modifier.weight(1f)
                            //This tells Compose to distribute the available horizontal space equally between the two summary cards.
                            //Because both cards have a weight of 1f, each receives an equal share of the available space after accounting for the gap.
                        )

                        ClientSummaryCard(
                            title = "Upcoming Visits",
                            value = "—",
                            description = "No visits loaded",
                            modifier = Modifier.weight(1f)
                        )
                    }


                    // CARE RECIPIENTS SECTION
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Care Recipients",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )

                        when {
                            recipientsLoading -> {
                                CircularProgressIndicator()
                            }

                            recipientsError.isNotEmpty() -> {
                                Text(
                                    text = recipientsError,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            careRecipients.isEmpty() -> {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text(
                                        text = "No care recipients added yet.",
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }

                            else -> {
                                careRecipients.forEach { recipient ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable{
                                                onRecipientClick(recipient.id)
                                            },
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "${recipient.firstName} ${recipient.lastName}",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )

                                            Text(
                                                text = "Relationship: ${recipient.relationship}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = onAddCareRecipient,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Add Care Recipient")
                        }
                    }

                    //TODAY'S CARE SECTION
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Today's Care",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null
                                )

                                Text(
                                    text = "No care visits scheduled yet.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    //RECENT BILLING SECTION
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Recent Billing",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ReceiptLong,
                                    contentDescription = null
                                )

                                Text(
                                    text = "Your invoices will appear here",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    //ALERTS SECTION
                    // DYNAMIC ALERTS SECTION
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Text(
                                text = "Alerts",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )

                            if (flaggedInvoiceCount > 0) {
                                Text(
                                    text = "View All",
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable {
                                        selectedTab = "alerts"
                                    }
                                )
                            }
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedTab = "alerts"
                                },
                            shape = RoundedCornerShape(16.dp)
                        ) {

                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {

                                Icon(
                                    imageVector = Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    tint = if (pendingFlaggedCount > 0) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )

                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {

                                    when {
                                        alertsLoading -> {
                                            Text("Loading billing alerts...")
                                        }

                                        alertsError.isNotBlank() -> {
                                            Text(
                                                text = alertsError,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }

                                        pendingFlaggedCount > 0 -> {
                                            Text(
                                                text = "$pendingFlaggedCount invoice(s) require review",
                                                fontWeight = FontWeight.SemiBold
                                            )

                                            Text(
                                                text = "Unusual charges were detected. " +
                                                        "Please review these invoices before approving."
                                            )
                                        }

                                        flaggedInvoiceCount > 0 -> {
                                            Text(
                                                text = "$flaggedInvoiceCount previously flagged invoice(s)",
                                                fontWeight = FontWeight.SemiBold
                                            )

                                            Text(
                                                text = "There are no flagged invoices " +
                                                        "awaiting your approval."
                                            )
                                        }

                                        else -> {
                                            Text("No billing alerts to display.")
                                        }
                                    }
                                }
                            }
                        }
                    }


                    OutlinedButton(
                        onClick = onLogout,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Logout")
                    }
                }

            }

            "billing" -> {
                ClientInvoicesScreen(
                    modifier = Modifier.padding(innerPadding),
                    initialFilter = billingInitialFilter,
                    targetInvoiceId = targetInvoiceId
                )
            }

            "alerts" -> {
                ClientAlertsScreen(
                    modifier = Modifier.padding(innerPadding),
                    onViewInvoice = { invoiceId ->
                        targetInvoiceId = invoiceId
                        billingInitialFilter = "FLAGGED"
                        selectedTab = "billing"
                    }
                )
            }
        }
    }
}

@Composable
fun ClientSummaryCard(
    title: String,
    value: String,
    description: String,
    modifier: Modifier = Modifier // This means the caller can optionally customize the card's size or positioning. If no modifier is supplied, the default Modifier is used.
){
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ClientBottomNavigation(
    selectedTab: String,
    onTabSelected: (String) -> Unit
){
    NavigationBar{
        NavigationBarItem(
            selected = selectedTab == "home",// If selected is "home", it becomes true. Otherwise, it is false
            onClick = {onTabSelected("home")},//onTabSelected is a callback that tells the parent Composable when a user selects another tab.
            icon = {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Home"
                )
            },
            label = {Text("Home")}
        )

        NavigationBarItem(
            selected = selectedTab == "billing",
            onClick = {onTabSelected("billing")},
            icon = {
                Icon(
                    imageVector = Icons.Default.Receipt,
                    contentDescription = "Billing"
                )
            },
            label = {Text("Billing")}
        )

        NavigationBarItem(
            selected = selectedTab == "alerts",
            onClick = {onTabSelected("alerts")},
            icon = {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Alerts"
                )
            },
            label = {Text("Alerts")}

        )
    }
}

@Composable
fun ClientBillingScreen(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.ReceiptLong,
            contentDescription = null,
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Billing",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Your invoices and payment history will appear here.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private data class FlaggedInvoiceAlert(
    val id: String,
    val recipientName: String,
    val serviceDate: String,
    val status: String,
    val totalCents: Long
)

@Composable
fun ClientAlertsScreen(
    modifier: Modifier = Modifier,
    onViewInvoice: (String) -> Unit = {}
){
    val db = remember { FirebaseFirestore.getInstance() }
    val userId = FirebaseAuth.getInstance().currentUser?.uid

    var flaggedInvoices by remember {
        mutableStateOf<List<FlaggedInvoiceAlert>>(emptyList())
    }

    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }

    DisposableEffect(userId) {
        if (userId == null) {
            loading = false
            errorMessage = "Please sign in again."
            onDispose { }
        } else {
            val listener = db.collection("invoices")
                .whereEqualTo("clientId", userId)
                .addSnapshotListener { snapshot, exception ->

                    loading = false

                    if (exception != null) {
                        errorMessage =
                            "Unable to load billing alerts."
                        return@addSnapshotListener
                    }

                    errorMessage = ""

                    flaggedInvoices = snapshot?.documents
                        ?.filter { document ->
                            document.getString("anomalyStatus") ==
                                    "FLAGGED_FOR_REVIEW"
                        }
                        ?.map { document ->
                            FlaggedInvoiceAlert(
                                id = document.id,
                                recipientName = document.getString("recipientName")
                                    ?: "Care recipient",
                                serviceDate = document.getString("serviceDate")
                                    ?: "Unknown date",
                                status = document.getString("status")
                                    ?: "UNKNOWN",
                                totalCents = document.getLong("totalCents")
                                    ?: 0L
                            )
                        }
                        ?.sortedByDescending { it.serviceDate }
                        ?: emptyList()
                }

            onDispose {
                listener.remove()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Text(
            text = "Billing Alerts",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Invoices identified as having unusual charges " +
                    "will appear here.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        when {
            loading -> {
                CircularProgressIndicator()
            }

            errorMessage.isNotBlank() -> {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error
                )
            }

            flaggedInvoices.isEmpty() -> {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement =
                            Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector =
                                Icons.Default.Notifications,
                            contentDescription = null
                        )

                        Text("No flagged invoices to display.")
                    }
                }
            }

            else -> {
                flaggedInvoices.forEach { invoice ->

                    val recipientName = invoice.recipientName
                    val serviceDate = invoice.serviceDate
                    val status = invoice.status

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement =
                                Arrangement.spacedBy(8.dp)
                        ) {

                            Row(
                                horizontalArrangement =
                                    Arrangement.spacedBy(8.dp),
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector =
                                        Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    tint =
                                        MaterialTheme.colorScheme.error
                                )

                                Text(
                                    text = if (
                                        status == "PENDING_APPROVAL"
                                    ) {
                                        "Invoice requires review"
                                    } else {
                                        "Previously flagged invoice"
                                    },
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Text(
                                text = "Recipient: $recipientName"
                            )

                            Text(
                                text = "Service date: $serviceDate"
                            )

                            Text(
                                text = "Amount: KES ${
                                    String.format(
                                        java.util.Locale.US,
                                        "%,.2f",
                                        invoice.totalCents / 100.0
                                    )
                                }",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )

                            Text(
                                text = when (status) {
                                    "PENDING_APPROVAL" ->
                                        "Status: Awaiting your approval"
                                    "APPROVED" ->
                                        "Status: Approved"
                                    "PAID" ->
                                        "Status: Paid"
                                    "DISPUTED" ->
                                        "Status: Disputed"
                                    else ->
                                        "Status: $status"
                                }
                            )

                            Text(
                                text = "Unusual billing activity was " +
                                        "detected for this invoice. " +
                                        "Review the charges carefully."
                            )

                            TextButton(
                                onClick = {
                                    onViewInvoice(invoice.id)
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