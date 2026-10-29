
package com.example.homecare_bill

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CareRecipientDetailsScreen(
    recipientId: String,
    onBack: () -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }

    var recipient by remember(recipientId) { mutableStateOf<CareRecipient?>(null) }
    //remember(recipientId) means that the block of code inside the curly braces will only re-execute (re-calculate) if the value of recipientId changes.
    //It means that Compose will keep the existing state unless recipientId changes. If recipientId changes, it clears out the old CareRecipient and resets it back to null.
    //If recipientId stays the same: Every time your composable recomposes (reruns), Compose completely skips the mutableStateOf(null) block. It "remembers" whatever the current value of recipient is.
    //• If recipientId changes: Compose detects that the "key" (recipientId) is different. It destroys the old state and completely restarts the block, resetting recipient back to null.
    //<CareRecipient?> defines the strict rules for that holder. It tells Kotlin: This variable can only ever be a CareRecipient object or null.
    var isLoading by remember(recipientId) { mutableStateOf(true) }
    var errorMessage by remember(recipientId) { mutableStateOf("") }

    LaunchedEffect(recipientId) {
        val uid = auth.currentUser?.uid

        if (uid == null) {
            isLoading = false
            errorMessage = "Please sign in again."
        } else {
            db.collection("careRecipients")
                .document(recipientId)
                .get()
                .addOnSuccessListener { document ->
                    isLoading = false

                    if (!document.exists()) {
                        errorMessage = "Care recipient not found."
                    } else if (document.getString("clientId") != uid) {
                        errorMessage = "You cannot access this care recipient."
                    } else {
                        recipient = CareRecipient(
                            id = document.id,
                            firstName = document.getString("firstName") ?: "",
                            lastName = document.getString("lastName") ?: "",
                            relationship = document.getString("relationship") ?: ""
                        )
                    }
                }
                .addOnFailureListener {
                    isLoading = false
                    errorMessage = "Unable to load care recipient details."
                }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Care Recipient Details") }
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

            OutlinedButton(
                onClick = onBack
            ) {
                Text("Back")
            }

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

                recipient != null -> {
                    val currentRecipient = recipient!!

                    Text(
                        text = "${currentRecipient.firstName} ${currentRecipient.lastName}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )

                    HorizontalDivider()

                    Text(
                        text = "First Name: ${currentRecipient.firstName}"
                    )

                    Text(
                        text = "Last Name: ${currentRecipient.lastName}"
                    )

                    Text(
                        text = "Relationship: ${currentRecipient.relationship}"
                    )
                }
            }
        }
    }
}
