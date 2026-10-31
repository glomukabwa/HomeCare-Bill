
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
fun AssignedCareRecipientsScreen(
    onBack: () -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }

    var assignments by remember {
        mutableStateOf<List<CaregiverInvitation>>(emptyList())
    }

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }

    val caregiverUid = auth.currentUser?.uid
    val caregiverEmail = auth.currentUser?.email

    DisposableEffect(caregiverUid, caregiverEmail) {

        if (caregiverUid.isNullOrBlank() ||
            caregiverEmail.isNullOrBlank()
        ) {
            isLoading = false
            errorMessage = "Please sign in again."
            onDispose { }
        } else {

            val listener = db.collection("caregiverInvites")
                .whereEqualTo("caregiverEmail", caregiverEmail)
                .whereEqualTo("caregiverId", caregiverUid)
                .whereEqualTo("status", "ACTIVE")
                .addSnapshotListener { snapshot, error ->

                    if (error != null) {
                        errorMessage = error.localizedMessage
                            ?: "Unable to load assigned recipients."
                        isLoading = false
                        return@addSnapshotListener
                    }

                    assignments = snapshot?.documents?.map { document ->
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
                title = { Text("Assigned Care Recipients") },
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

                assignments.isEmpty() -> {
                    Text("You have no assigned care recipients yet.")
                }

                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(assignments, key = { it.id }) { assignment ->

                            Card(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {

                                    Text(
                                        text = assignment.recipientName,
                                        style = MaterialTheme.typography.titleMedium
                                    )

                                    Text(
                                        text = "Client: ${assignment.clientName}"
                                    )

                                    Text(
                                        text = "Relationship: ${assignment.relationship}"
                                    )

                                    Text(
                                        text = "Status: Active",
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
