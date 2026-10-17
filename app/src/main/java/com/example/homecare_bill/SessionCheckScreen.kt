package com.example.homecare_bill

import android.se.omapi.Session
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun SessionCheckScreen(
    onNoSession: () -> Unit,
    //This is a callback function.
    //It allows SessionCheckScreen to tell MainActivity:
    //"There isn't a valid session. Navigate to Login."
    //We aren't directly navigating inside the session-checking screen.

    onValidSession: (String) -> Unit,
    //The (String) means the callback accepts a String argument.
    //We'll use that String to pass the user's role eg onValidSession("CLIENT") or onValidSession("CAREGIVER")

    onMissingProfile: () -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    var db = FirebaseFirestore.getInstance()
    var isChecking by remember { mutableStateOf(true) }
    var sessionError by remember { mutableStateOf("") }
    var retryCount by remember { mutableStateOf(0) }

    LaunchedEffect(retryCount) {
        //LaunchedEffect is a Jetpack Compose function that allows us to perform work associated with a composable entering the screen.
        //Here, we're using it to start our session check.
        //The retryCount key allows us to repeat the session check whenever retryCount changes.
        //This is useful because we don't want Firebase session checks to restart every time Compose updates the UI.

        isChecking = true
        sessionError = ""

        val user = auth.currentUser
        //One thing you should know is that Firebase Authentication keeps users signed in automatically. So this will always not be null unless:
        //User clicks Logout
        //Our code calls auth.signOut()
        //User clears the app's data or reinstalls it
        //Firebase is still initializing or restoring authentication state -> But this one is temporarily null
        //So for the session timeout that u normally see in apps, you have to set that urself. However for this app I'm not gonna do that cz I find them
        //quite annoying. What I'll do instead is to force users to provide additional authentification when it comes to performing sensitive tasks like making payments

        if(user == null){
            onNoSession()
        }else{
            user.reload()
                //The reason we are reloading and checking email verification again despite the fact that we did it in login is cz during session management, we're checking a previously saved session, potentially from days earlier.
                //The user's account information may have changed since then. Calling reload() helps us obtain the latest account information before allowing access.
                .addOnSuccessListener {
                    if (user.isEmailVerified){

                        user.getIdToken(true)
                            .addOnSuccessListener {

                                db.collection("users")
                                    .document(user.uid)
                                    .get()
                                    .addOnSuccessListener { document ->

                                        if (document.exists()) {

                                            val role = document.getString("role")

                                            if (role == "CLIENT" || role == "CAREGIVER") {

                                                onValidSession(role)

                                            } else {

                                                auth.signOut()
                                                onNoSession()
                                            }

                                        } else {

                                            onMissingProfile()//Firebase Authentication confirms the user's identity, but their Firestore profile does not exist. Keep the verified user signed in so they can complete registration.
                                        }
                                    }
                                    .addOnFailureListener {

                                        isChecking = false//A failed Firestore request does not necessarily mean the user's profile is missing. It could be a temporary network problem.

                                        sessionError =
                                            "Unable to retrieve your profile. Please check your internet connection and try again."
                                    }

                            }
                            .addOnFailureListener {
                                isChecking = false

                                sessionError = "Unable to verify your session. Please try again."
                            }
                    }else{
                        auth.signOut()
                        onNoSession()
                    }
                }
                .addOnFailureListener {
                    isChecking = false
                    sessionError = "Unable to verify your session. Please check your internet connection and try again."
                }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isChecking){
            CircularProgressIndicator()

            Text("Checking your session...")
        } else if(sessionError.isNotEmpty()){
            Text(
                text = sessionError,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = {
                    retryCount++
                }
            ) {
                Text("Retry")
            }

            Button(
                onClick = {
                    auth.signOut()
                    onNoSession()
                }
            ) {
                Text("Sign In Again")
            }
        }
    }
}
