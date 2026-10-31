
package com.example.homecare_bill

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaregiverDashboardScreen(
    onLogout: () -> Unit,
    onViewInvitations: () -> Unit,
    onViewAssignments: () -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }

    var firstName by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }

    var pendingInvitationCount by remember { mutableIntStateOf(0) }

    var assignedRecipientCount by remember { mutableIntStateOf(0) }

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


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HomeCare Bill") },
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
                        text = "Manage your care assignments and billing.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    HorizontalDivider()

                    Text(
                        text = "Pending Invitations",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {

                            when {
                                pendingInvitationCount == 0 -> {
                                    Text("No pending invitations yet.")
                                }

                                pendingInvitationCount == 1 -> {
                                    Text("You have 1 pending invitation.")
                                }

                                else -> {
                                    Text(
                                        "You have $pendingInvitationCount pending invitations."
                                    )
                                }
                            }

                            Button(
                                onClick = onViewInvitations,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("View Invitations")
                            }
                        }
                    }

                    Text(
                        text = "Assigned Care Recipients",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {

                            when (assignedRecipientCount) {
                                0 -> Text("No assigned care recipients yet.")
                                1 -> Text("You have 1 assigned care recipient.")
                                else -> Text(
                                    "You have $assignedRecipientCount assigned care recipients."
                                )
                            }

                            Button(
                                onClick = onViewAssignments,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("View Assigned Recipients")
                            }
                        }
                    }


                    Text(
                        text = "Invoices",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No invoices created yet.",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}
