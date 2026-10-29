package com.example.homecare_bill

import android.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
//This means that this function uses an experimental Material 3 API.
// We need it because of the Material 3 TopAppBar API.
// ::class refers to the Kotlin class representation of ExperimentalMaterial3Api therefore we don't need to create an object of that class.
@Composable
fun AddCareRecipientScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit
){
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("") }

    var isSaving by remember { mutableStateOf(false)}
    var errorMessage by remember { mutableStateOf<String?>(null) }
    //<String?> makes errorMessage nullable. In Kotlin, a String normally holds text, but it cannot hold null.
    //Plz note that an empty string is different from the null value. Now errorMessage can either hold a string or null
    //Later we'll use ?. to check whether the errorMessage is null or not to determine whether we need to display it

    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Care Recipient") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)//This is the external padding. It tells the column how much space to leave so that it doesn't overlap with the top app bar
                .verticalScroll(rememberScrollState())
                .padding(16.dp),//This is the internal padding. It is so that the contents of the column don't touch its edged
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Enter the details of the person receiving care.",
                style = MaterialTheme.typography.bodyMedium
            )

            OutlinedTextField(
                value = firstName,
                onValueChange = {firstName = it},
                label = {Text("First Name")},
                singleLine = true,
                enabled = !isSaving,//Meaning you can only type when the content is not being saved
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = lastName,
                onValueChange = { lastName = it },
                label = { Text("Last Name") },
                singleLine = true,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = relationship,
                onValueChange = { relationship = it },
                label = { Text("Relationship (e.g. Self, Parent)") },
                singleLine = true,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth()
            )

            errorMessage?.let { message -> //let will only be executed if the errorMessage is null -> ?.
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = {
                    val first = firstName.trim()//The rule is usually to use val by default and to use var when a variable needs to be reassigned
                    //For the variables above, I've used var cz they keep changing as the user types but these ones don't. Once the user clicks "Save", they are sure that the values they've entered are the ones they want to be saved. I'll an update btn later for mistakes
                    val last = lastName.trim()
                    val relation = relationship.trim()
                    val uid = auth.currentUser?.uid //?. Safely accesses the uid if the user isn't null. Cz the auth.currentUser can be null

                    when {
                        uid == null -> {
                            errorMessage = "Please sign in again."
                        }

                        first.isEmpty() || last.isEmpty() || relation.isEmpty() -> {
                            errorMessage = "Please fill in all fields."
                        }

                        first.length > 100 || last.length > 100 || relation.length > 100 -> {
                            errorMessage = "Fields cannot exceed 100 characters."
                        }

                        else -> {
                            isSaving = true
                            errorMessage = null

                            val recipient = hashMapOf(
                                "firstName" to first,
                                "lastName" to last,
                                "relationship" to relation,
                                "clientId" to uid,
                                "createdAt" to FieldValue.serverTimestamp()
                            )

                            db.collection("careRecipients")
                                .add(recipient)
                                .addOnSuccessListener {
                                    isSaving = false
                                    onSaved()
                                }
                                .addOnFailureListener {  exception ->
                                    isSaving = false
                                    errorMessage = exception.localizedMessage ?: "Failed to save care recipient."
                                }
                        }
                    }
                },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if(isSaving){
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }else{
                    Text("Save Care Recipient")
                }
            }

            OutlinedButton(
                onClick = onBack,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }
        }
    }
}