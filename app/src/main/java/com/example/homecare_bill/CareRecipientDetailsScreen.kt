
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
    onBack: () -> Unit,
    onInviteCaregiver: () -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }

    var recipient by remember(recipientId) { mutableStateOf<CareRecipient?>(null) }
    //remember(recipientId) means that the block of code inside the curly braces will only re-execute (re-calculate) if the value of recipientId changes.
    //It means that Compose will keep the existing state unless recipientId changes. If recipientId changes, it clears out the old CareRecipient and resets it back to null.
    //If recipientId stays the same: Every time your composable recomposes (reruns), Compose completely skips the mutableStateOf(null) block. It "remembers" whatever the current value of recipient is.
    //If recipientId changes: Compose detects that the "key" (recipientId) is different. It destroys the old state and completely restarts the block, resetting recipient back to null.
    //<CareRecipient?> defines the strict rules for that holder. It tells Kotlin: This variable can only ever be a CareRecipient object or null.
    var isLoading by remember(recipientId) { mutableStateOf(true) }
    var errorMessage by remember(recipientId) { mutableStateOf("") }
    var isEditing by remember(recipientId) { mutableStateOf(false) }
    var isSaving by remember(recipientId) { mutableStateOf(false) }
    var editedFirstName by remember(recipientId) { mutableStateOf("") }
    var editedLastName by remember(recipientId) { mutableStateOf("") }
    var editedRelationship by remember(recipientId) { mutableStateOf("") }
    var saveMessage by remember(recipientId) { mutableStateOf("") }//Displays success or validation messages

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

                        editedFirstName = recipient!!.firstName
                        //This line means: "I am absolutely certain that recipient is not null right now. Forcefully treat it as a valid object and grab its firstName."
                        //Because we defined recipient as a nullable type (CareRecipient?), Kotlin normally protects us by refusing to let us access its properties directly (to prevent the app from crashing).
                        //By using !!, we are telling the compiler to turn off that protection.

                        editedLastName = recipient!!.lastName
                        editedRelationship = recipient!!.relationship
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

                    if (!isEditing) {

                        // VIEW MODE
                        Text(
                            text = "${currentRecipient.firstName} ${currentRecipient.lastName}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )

                        HorizontalDivider()

                        Text("First Name: ${currentRecipient.firstName}")
                        Text("Last Name: ${currentRecipient.lastName}")
                        Text("Relationship: ${currentRecipient.relationship}")

                        Button(
                            onClick = {
                                editedFirstName = currentRecipient.firstName
                                editedLastName = currentRecipient.lastName
                                editedRelationship = currentRecipient.relationship

                                saveMessage = ""
                                isEditing = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Edit Details")
                        }

                        if (saveMessage.isNotEmpty()) {
                            Text(
                                text = saveMessage,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                    } else {

                        // EDIT MODE
                        Text(
                            text = "Edit Care Recipient",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )

                        OutlinedTextField(
                            value = editedFirstName,
                            onValueChange = { editedFirstName = it },
                            label = { Text("First Name") },
                            singleLine = true,
                            enabled = !isSaving,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = editedLastName,
                            onValueChange = { editedLastName = it },
                            label = { Text("Last Name") },
                            singleLine = true,
                            enabled = !isSaving,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = editedRelationship,
                            onValueChange = { editedRelationship = it },
                            label = { Text("Relationship") },
                            singleLine = true,
                            enabled = !isSaving,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (saveMessage.isNotEmpty()) {
                            Text(
                                text = saveMessage,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Button(
                            onClick = {
                                val first = editedFirstName.trim()
                                val last = editedLastName.trim()
                                val relationship = editedRelationship.trim()

                                when {
                                    first.isEmpty() || last.isEmpty() || relationship.isEmpty() -> {
                                        saveMessage = "Please fill in all fields."
                                    }

                                    first.length > 100 || last.length > 100 || relationship.length > 100 -> {
                                        saveMessage = "Each field must be 100 characters or fewer."
                                    }

                                    auth.currentUser?.uid == null -> {
                                        saveMessage = "Please sign in again."
                                    }

                                    else -> {
                                        isSaving = true
                                        saveMessage = ""

                                        db.collection("careRecipients")
                                            .document(recipientId)
                                            .update(
                                                mapOf(
                                                    "firstName" to first,
                                                    "lastName" to last,
                                                    "relationship" to relationship
                                                )
                                            )
                                            .addOnSuccessListener {
                                                recipient = currentRecipient.copy(
                                                    firstName = first,
                                                    lastName = last,
                                                    relationship = relationship
                                                )

                                                isSaving = false
                                                isEditing = false
                                                saveMessage = "Details updated successfully."
                                            }
                                            .addOnFailureListener { exception ->
                                                isSaving = false
                                                saveMessage = exception.localizedMessage ?: "Unable to update details."
                                            }
                                    }
                                }
                            },
                            enabled = !isSaving,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Save Changes")
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                isEditing = false
                                saveMessage = ""
                            },
                            enabled = !isSaving,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            }

            Button(
                onClick = onInviteCaregiver,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Invite Caregiver")
            }
        }
    }
}
