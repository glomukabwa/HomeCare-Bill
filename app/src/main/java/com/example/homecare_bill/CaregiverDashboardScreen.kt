
package com.example.homecare_bill

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

private data class RecentCaregiverInvoice(
    val id: String,
    val recipientName: String,
    val serviceDate: String,
    val totalCents: Long,
    val status: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaregiverDashboardScreen(
    onLogout: () -> Unit,
    onViewInvitations: () -> Unit,
    onViewAssignments: () -> Unit,
    onViewServices: () -> Unit,
    onViewInvoices: () -> Unit,
    onCreateInvoice: (String) -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }

    var firstName by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }

    var selectedTab by rememberSaveable { mutableStateOf("home") }

    var focusedInvoiceId by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    var pendingInvitationCount by remember { mutableIntStateOf(0) }

    var assignedRecipientCount by remember { mutableIntStateOf(0) }

    var recentInvoices by remember {
        mutableStateOf<List<RecentCaregiverInvoice>>(emptyList())
    }

    var invoicesLoading by remember {
        mutableStateOf(true)
    }

    var invoicesError by remember {
        mutableStateOf("")
    }

    LaunchedEffect(Unit) {
        val uid = auth.currentUser?.uid

        if (uid == null) {
            isLoading = false
            errorMessage = "Please sign in again."
        } else {
            db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener { document ->
                    isLoading = false


                    if (document.exists() &&
                        document.getString("role") == "CAREGIVER"
                    ) {
                        firstName = document.getString("firstName") ?: ""

                        val caregiverData = hashMapOf(
                            "email" to (document.getString("email") ?: ""),
                            "firstName" to (document.getString("firstName") ?: ""),
                            "lastName" to (document.getString("lastName") ?: "")
                        )

                        val directoryRef = db.collection("caregiverDirectory")
                            .document(uid)//We're using the same uid as the one in users. It'll tie the data in this collection with the data in the users collection

                        directoryRef.get()
                            .addOnSuccessListener { directoryDocument ->

                                if (!directoryDocument.exists()) {
                                    directoryRef.set(caregiverData)//Unlike .add(), which generates an automatic document ID, .set() writes to the document reference we've specified.
                                        .addOnFailureListener {
                                            errorMessage =
                                                "Unable to create caregiver directory entry."
                                        }
                                }
                            }
                            .addOnFailureListener {
                                errorMessage =
                                    "Unable to check caregiver directory."
                            }

                    } else {
                        errorMessage = "Unable to load caregiver profile."
                    }
                }
                .addOnFailureListener {
                    isLoading = false
                    errorMessage =
                        "Unable to load your profile."
                }
        }
    }


    val caregiverEmail = auth.currentUser?.email

    DisposableEffect(caregiverEmail) {

        if (caregiverEmail.isNullOrBlank()) {
            pendingInvitationCount = 0
            onDispose { }
        } else {

            val listener = db.collection("caregiverInvites")
                .whereEqualTo("caregiverEmail", caregiverEmail)
                .whereEqualTo("status", "PENDING")
                .addSnapshotListener { snapshot, error ->

                    if (error != null) {
                        pendingInvitationCount = 0
                        return@addSnapshotListener
                    }

                    pendingInvitationCount = snapshot?.size() ?: 0
                }

            onDispose {
                listener.remove()
            }
        }
    }


    val caregiverUid = auth.currentUser?.uid

    DisposableEffect(caregiverUid, caregiverEmail) {

        if (caregiverUid.isNullOrBlank() ||
            caregiverEmail.isNullOrBlank()
        ) {
            assignedRecipientCount = 0
            onDispose { }
        } else {

            val listener = db.collection("caregiverInvites")
                .whereEqualTo("caregiverEmail", caregiverEmail)
                .whereEqualTo("caregiverId", caregiverUid)
                .whereEqualTo("status", "ACTIVE")
                .addSnapshotListener { snapshot, error ->

                    if (error != null) {
                        assignedRecipientCount = 0
                        return@addSnapshotListener //This halts further execution of this specific callback instance. The app skips any code that comes after the if block inside those curly braces. However, plz not that the listener is not disposed. It still remains alive.
                    }

                    assignedRecipientCount = snapshot?.documents
                        ?.mapNotNull { document ->
                            document.getString("recipientId")
                        }
                        ?.distinct()
                        ?.size ?: 0
                    // Explanation:
                    //      mapNotNull() extracts the recipientId from each invitation, excluding missing values.
                    //      distinct() removes duplicate recipient IDs.
                    //      size counts the remaining unique recipients.
                }

            onDispose {
                listener.remove()
            }
        }
    }


    DisposableEffect(caregiverUid) {

        if (caregiverUid.isNullOrBlank()) {
            invoicesLoading = false
            invoicesError = "Please sign in again."

            onDispose { }
        } else {

            val listener = db.collection("invoices")
                .whereEqualTo("caregiverId", caregiverUid)
                .addSnapshotListener { snapshot, exception ->

                    invoicesLoading = false

                    if (exception != null) {
                        invoicesError = "Unable to load recent invoices."
                        return@addSnapshotListener
                    }

                    recentInvoices = snapshot?.documents
                        ?.map { doc ->
                            RecentCaregiverInvoice(
                                id = doc.id,
                                recipientName = doc.getString("recipientName")
                                    ?: "Care recipient",
                                serviceDate = doc.getString("serviceDate") ?: "",
                                totalCents = doc.getLong("totalCents") ?: 0L,
                                status = doc.getString("status") ?: "UNKNOWN"
                            )
                        }
                        ?.sortedByDescending { it.serviceDate }
                        ?.take(3)
                        ?: emptyList()

                    invoicesError = ""
                }

            onDispose {
                listener.remove()
            }
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (selectedTab) {
                            "assignments" -> "Assignments"
                            "services" -> "Service Catalogue"
                            "invoices" -> "My Invoices"
                            else -> "HomeCare Bill"
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
            CaregiverBottomNavigation(
                selectedTab = selectedTab,
                onTabSelected = { tab ->

                    when (tab) {
                        "home" -> {
                            selectedTab = "home"
                        }

                        "assignments" -> {
                            selectedTab = "assignments"
                        }

                        "services" -> {
                            selectedTab = "services"
                        }

                        "invoices" -> {
                            selectedTab = "invoices"
                        }
                    }
                }
            )
        }
    ) { innerPadding ->

        when (selectedTab) {

            "assignments" -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {

                    // Pending Invitations section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 8.dp
                            ),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Pending Invitations",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            text = when (pendingInvitationCount) {
                                0 -> "No pending invitations."
                                1 -> "You have 1 pending invitation."
                                else -> "You have $pendingInvitationCount pending invitations."
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Button(
                            onClick = onViewInvitations,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("View Invitations")
                        }
                    }

                    HorizontalDivider()

                    // Assigned Care Recipients section
                    Text(
                        text = "Assigned Care Recipients",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp,
                            bottom = 4.dp
                        )
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        AssignedCareRecipientsScreen(
                            onBack = {
                                selectedTab = "home"
                            },
                            onCreateInvoice = onCreateInvoice,
                            embedded = true
                        )
                    }
                }
            }

            "services" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    ServiceCatalogueScreen(
                        onBack = {
                            selectedTab = "home"
                        },
                        embedded = true
                    )
                }
            }

            "invoices" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    CaregiverInvoicesScreen(
                        embedded = true,
                        focusedInvoiceId = focusedInvoiceId,
                        onFocusHandled = {
                            focusedInvoiceId = null
                        }
                    )
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
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

                        else -> {
                            Text(
                                text = "Welcome, $firstName!",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "Here's an overview of your caregiving activity.",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            HorizontalDivider()

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {

                                Card(
                                    onClick = onViewInvitations,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Pending Invitations",
                                            style = MaterialTheme.typography.titleSmall
                                        )

                                        Text(
                                            text = pendingInvitationCount.toString(),
                                            style = MaterialTheme.typography.headlineLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Card(
                                    onClick = {
                                        selectedTab = "assignments"
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Assigned Recipients",
                                            style = MaterialTheme.typography.titleSmall
                                        )

                                        Text(
                                            text = assignedRecipientCount.toString(),
                                            style = MaterialTheme.typography.headlineLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "Recent Invoices",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )

                            when {
                                invoicesLoading -> {
                                    CircularProgressIndicator()
                                }

                                invoicesError.isNotBlank() -> {
                                    Text(
                                        text = invoicesError,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }

                                recentInvoices.isEmpty() -> {
                                    Text(
                                        text = "You haven't submitted any invoices yet.",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }

                                else -> {
                                    recentInvoices.forEach { invoice ->

                                        Card(
                                            onClick = {
                                                focusedInvoiceId = invoice.id
                                                selectedTab = "invoices"
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(16.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {

                                                Text(
                                                    text = invoice.recipientName,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )

                                                Text(
                                                    text = "Service date: ${invoice.serviceDate}",
                                                    style = MaterialTheme.typography.bodySmall
                                                )

                                                Text(
                                                    text = "Status: ${
                                                        invoice.status.replace("_", " ")
                                                    }",
                                                    style = MaterialTheme.typography.bodyMedium
                                                )

                                                Text(
                                                    text = "KES ${
                                                        String.format(
                                                            Locale.US,
                                                            "%,.2f",
                                                            invoice.totalCents / 100.0
                                                        )
                                                    }",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            TextButton(
                                onClick = {
                                    selectedTab = "invoices"
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("View All Invoices")
                            }
                        }
                    }
                }
            }
        }
    }
}

        @Composable
        fun CaregiverBottomNavigation(
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
                    selected = selectedTab == "assignments",
                    onClick = { onTabSelected("assignments") },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = "Assignments"
                        )
                    },
                    label = { Text("Assignments") }
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

                NavigationBarItem(
                    selected = selectedTab == "invoices",
                    onClick = { onTabSelected("invoices") },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Receipt,
                            contentDescription = "Invoices"
                        )
                    },
                    label = { Text("Invoices") }
                )
            }
        }
