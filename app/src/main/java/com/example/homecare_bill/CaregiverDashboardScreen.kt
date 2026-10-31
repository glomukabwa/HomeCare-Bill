
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
    onLogout: () -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }

    var firstName by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }

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
                        Text(
                            text = "No pending invitations yet.",
                            modifier = Modifier.padding(16.dp)
                        )
                    }

                    Text(
                        text = "Assigned Care Recipients",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No assigned care recipients yet.",
                            modifier = Modifier.padding(16.dp)
                        )
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
