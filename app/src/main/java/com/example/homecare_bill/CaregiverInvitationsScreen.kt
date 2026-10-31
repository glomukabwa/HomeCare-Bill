
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaregiverInvitationsScreen(
    onBack: () -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }

    var invitations by remember {
        mutableStateOf<List<CaregiverInvitation>>(emptyList())
    }

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }

    val caregiverEmail = auth.currentUser?.email

    DisposableEffect(caregiverEmail) {

        if (caregiverEmail.isNullOrBlank()) {
            isLoading = false
            errorMessage = "Unable to identify caregiver."
            onDispose { }
        } else {
            val listener = db.collection("caregiverInvites")
                .whereEqualTo("caregiverEmail", caregiverEmail)
                .whereEqualTo("status", "PENDING")
                .addSnapshotListener { snapshot, error ->

                    if (error != null) {
                        errorMessage = error.localizedMessage
                            ?: "Unable to load invitations."
                        isLoading = false
                        return@addSnapshotListener
                    }

                    invitations = snapshot?.documents?.map { document ->
                        CaregiverInvitation(
                            id = document.id,
                            clientId = document.getString("clientId") ?: "",
                            recipientId = document.getString("recipientId") ?: "",
                            caregiverEmail = document.getString("caregiverEmail") ?: "",
                            clientName = document.getString("clientName") ?: "",
                            recipientName = document.getString("recipientName") ?: "",
                            relationship = document.getString("relationship") ?: "",
                            status = document.getString("status") ?: "",
                            createdAt = document.getTimestamp("createdAt")
                        )

                    } ?: emptyList()

                    isLoading = false
                    errorMessage = ""
                }

            onDispose {
                listener.remove()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pending Invitations") },
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

                invitations.isEmpty() -> {
                    Text("You have no pending invitations.")
                }

                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(invitations, key = { it.id }) { invitation ->

                            Card(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Caregiver Invitation",
                                        style = MaterialTheme.typography.titleMedium
                                    )

                                    Text(
                                        text = "Invited by: ${invitation.clientName}",
                                        style = MaterialTheme.typography.bodyLarge
                                    )

                                    Text(
                                        text = "Care Recipient: ${invitation.recipientName}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )

                                    Text(
                                        text = "Relationship: ${invitation.relationship}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )

                                    Text(
                                        text = "Status: ${invitation.status}",
                                        color = MaterialTheme.colorScheme.primary
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
