
package com.example.homecare_bill

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteCaregiverScreen(
    recipientId: String,
    onBack: () -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }

    var caregiverEmail by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var successMessage by remember { mutableStateOf("") }

    val currentUser = auth.currentUser

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Invite Caregiver") }
                //navigationIcon = {
                    //TextButton(onClick = onBack) {
                        //Text("Back")
                    //}
                //}
            )
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            //Text(
                //text = "Invite a caregiver",
                //style = MaterialTheme.typography.headlineSmall
            //)

            Text(
                text = "Enter the email address of the caregiver you want to invite to care for this recipient."
            )

            OutlinedTextField(
                value = caregiverEmail,
                onValueChange = {
                    caregiverEmail = it
                    errorMessage = ""
                    successMessage = ""
                },
                label = { Text("Caregiver Email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            if (errorMessage.isNotEmpty()) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (successMessage.isNotEmpty()) {
                Text(
                    text = successMessage,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Button(
                onClick = {
                    val email = caregiverEmail.trim().lowercase()

                    if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                        errorMessage = "Please enter a valid email address."
                    } else if (currentUser == null) {
                        errorMessage = "You must be logged in."
                    } else if (recipientId.isBlank()) {
                        errorMessage = "Invalid care recipient."
                    } else {
                        isLoading = true
                        errorMessage = ""
                        successMessage = ""

                        db.collection("users")
                            .document(currentUser.uid)
                            .get()
                            .addOnSuccessListener { clientDocument ->

                                if (!clientDocument.exists()) {
                                    isLoading = false
                                    errorMessage = "Client profile not found."
                                    return@addOnSuccessListener
                                }

                                val clientFirstName = clientDocument.getString("firstName") ?: ""

                                val clientLastName = clientDocument.getString("lastName") ?: ""

                                val clientName = "$clientFirstName $clientLastName"

                                db.collection("careRecipients")
                                    .document(recipientId)
                                    .get()
                                    .addOnSuccessListener { recipientDocument ->

                                        if (!recipientDocument.exists()) {
                                            isLoading = false
                                            errorMessage = "Care recipient not found."
                                            return@addOnSuccessListener
                                        }

                                        val recipientFirstName = recipientDocument.getString("firstName") ?: ""

                                        val recipientLastName = recipientDocument.getString("lastName") ?: ""

                                        val recipientName = "$recipientFirstName $recipientLastName"

                                        val relationship = recipientDocument.getString("relationship") ?: ""

                                        val invitation = hashMapOf(
                                            "caregiverEmail" to email,
                                            "clientId" to currentUser.uid,
                                            "recipientId" to recipientId,
                                            "clientName" to clientName,
                                            "recipientName" to recipientName,
                                            "relationship" to relationship,
                                            "status" to "PENDING",
                                            "createdAt" to FieldValue.serverTimestamp()
                                        )

                                        db.collection("caregiverInvites")
                                            .add(invitation)
                                            .addOnSuccessListener {
                                                isLoading = false
                                                successMessage = "Invitation sent successfully!"
                                                caregiverEmail = ""
                                            }
                                            .addOnFailureListener { exception ->
                                                isLoading = false
                                                errorMessage = exception.localizedMessage
                                                    ?: "Unable to send invitation."
                                            }
                                    }
                                    .addOnFailureListener { exception ->
                                        isLoading = false
                                        errorMessage = exception.localizedMessage
                                            ?: "Unable to load care recipient."
                                    }
                            }
                            .addOnFailureListener { exception ->
                                isLoading = false
                                errorMessage = exception.localizedMessage
                                    ?: "Unable to load client profile."
                            }

                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                Text(
                    if (isLoading) "Sending Invitation..."
                    else "Send Invitation"
                )
            }

            OutlinedButton(
                onClick = onBack
            ) {
                Text("Back")
            }
        }
    }
}
