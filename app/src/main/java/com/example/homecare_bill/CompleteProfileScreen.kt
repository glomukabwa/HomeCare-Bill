
package com.example.homecare_bill

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun CompleteProfileScreen(
    onProfileCompleted: (String) -> Unit,
    onCancel: () -> Unit
) {

    val context = LocalContext.current

    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }

    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("CLIENT") }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "Complete Your Profile",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Your account exists, but your profile wasn't saved. Please fill in your details to continue."
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = firstName,
            onValueChange = { firstName = it },
            label = { Text("First Name") },
            singleLine = true,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = lastName,
            onValueChange = { lastName = it },
            label = { Text("Last Name") },
            singleLine = true,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text("Register as:")

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {

            RadioButton(
                selected = selectedRole == "CLIENT",
                onClick = { selectedRole = "CLIENT" },
                enabled = !isLoading
            )

            Text("Client")

            Spacer(modifier = Modifier.width(16.dp))

            RadioButton(
                selected = selectedRole == "CAREGIVER",
                onClick = { selectedRole = "CAREGIVER" },
                enabled = !isLoading
            )

            Text("Caregiver")
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (errorMessage.isNotEmpty()) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(12.dp))
        }

        Button(
            onClick = {

                val user = auth.currentUser

                if (firstName.isBlank() || lastName.isBlank()) {

                    errorMessage = "Please enter your first and last name."

                } else if (user == null) {

                    errorMessage = "Your session has expired. Please sign in again."

                } else if (!user.isEmailVerified) {

                    errorMessage = "Please verify your email before completing your profile."

                } else {

                    isLoading = true
                    errorMessage = ""

                    //Use the authenticated user's UID and email.
                    //Do not let the user type or change these values.
                    val userProfile = hashMapOf(
                        "firstName" to firstName.trim(),
                        "lastName" to lastName.trim(),
                        "email" to (user.email ?: ""),//We're retrieving the email from Firebase Authentication instead of asking the user to enter it
                        "role" to selectedRole
                    )

                    val documentRef = db.collection("users")
                        .document(user.uid)

                    //A transaction checks whether the profile already exists.
                    //If it exists, we do not overwrite it.
                    db.runTransaction { transaction ->

                        val snapshot = transaction.get(documentRef)

                        if (snapshot.exists()) {
                            throw IllegalStateException(
                                "A profile already exists for this account."
                            )
                        }

                        transaction.set(documentRef, userProfile)

                        null

                        //The abobe code means:
                        //1. Check whether the user's profile already exists.
                        //2. Stop if it already exists.
                        //3. Otherwise, create the profile.

                    }.addOnSuccessListener {

                        isLoading = false

                        Toast.makeText(
                            context,
                            "Profile completed successfully!",
                            Toast.LENGTH_LONG
                        ).show()

                        //Navigate to the dashboard for the selected role.
                        onProfileCompleted(selectedRole)

                    }.addOnFailureListener {

                        isLoading = false

                        errorMessage =
                            "Unable to complete your profile. Please try again."
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text("Complete Registration")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(
            onClick = {
                auth.signOut()
                onCancel()
            },
            enabled = !isLoading
        ) {
            Text("Cancel and Sign Out")
        }
    }
}
