package com.example.homecare_bill

import android.R
import android.util.Patterns
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    onRegisterClick : () -> Unit,
        // onRegisterClick	The name of the parameter
        // ()	            The function takes no arguments
        // ->	            Separates the function's parameters from its return type
        // Unit	            The function doesn't return a meaningful value
        // In simple terms, we're telling LoginScreen: "You will receive a function that should be executed whenever someone clicks Register." This is called a callback function.
        // I'm putting it as a parameter here cz I don't want LoginScreen to handle navigation. I want all the navigation to be done in MainActivity so I'll specify the navigation there

    onLoginSuccess: (String) -> Unit,
    onCompleteProfile: () -> Unit
){
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    //We don't use "by" for the 2 below cz we don't use mutableStateOf(). Instead we are accessing Auth, Firestore and LocalContext objects so we use "="
    var auth = remember { FirebaseAuth.getInstance() }
    var db = remember { FirebaseFirestore.getInstance() }
    var context = LocalContext.current

    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var resetEmail by remember { mutableStateOf("") } //Stores the email entered for password reset
    var resetError by remember { mutableStateOf("") } //Stores validation or Firebase error messages
    var isResetLoading by remember { mutableStateOf(false) } //Tracks whether Firebase is processing the request

    var showVerificationDialog by remember { mutableStateOf(false) }
    var isResendingVerification by remember { mutableStateOf(false) }
    var verificationError by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome Back!",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Sign in to your HomeCare Bill account",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it},
            label = { Text("Email Address")},
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it},
            label = {Text("Password")},
            visualTransformation = if(passwordVisible){
                VisualTransformation.None //displays the password normally
            }else{
                PasswordVisualTransformation() //hides the password characters
            },
            trailingIcon = {
                IconButton(
                    onClick = {
                        passwordVisible = !passwordVisible
                    }
                ) {
                    Icon(
                        imageVector = if(passwordVisible){
                            Icons.Filled.VisibilityOff //the crossed-out eye
                        }else{
                            Icons.Filled.Visibility //the eye icon
                        },
                        contentDescription = if(passwordVisible){ //Helps increase accessibility
                            "Hide Password"
                        } else{
                            "Show Password"
                        }
                    )
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = {
                resetEmail = email.trim()
                resetError = ""
                showForgotPasswordDialog = true
            }
        ) {
            Text("Forgot Password?")
        }

        if(errorMessage.isNotEmpty()){
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                errorMessage = when {
                    email.isBlank() ->
                        "Please enter your email address"

                    !Patterns.EMAIL_ADDRESS
                        .matcher(email.trim()).matches() ->
                            "Please enter a valid email address"

                    password.isBlank() ->
                        "Please enter your password"

                    else -> ""
                }

                if (errorMessage.isEmpty()){
                    isLoading = true

                    auth.signInWithEmailAndPassword(//This sends the email and password to Firebase Authentication for verification.
                        email.trim(),
                        password
                    ).addOnCompleteListener { task -> //This listener executes when an asynchronous task finishes, regardless of whether it succeeds or fails.
                        if (task.isSuccessful) { //Notice that since addOnCompleteListener doesn't check if sth is successful, wu use task.isSuccessful. Instead of writing the two blocks we can just use one addOnSuccessful but I've used this to add ur info about listeners
                            val user = auth.currentUser //auth.CurrentUser contains the uid, email, isEmailVerified(Whether their email address has been verified)

                            if(user != null){//Remember we check this cz user(a reference to auth.currentUser) is a nullable object

                                user.reload() //This asks Firebase Authentication to refresh the user's account information. Cz if the user opens the app, registers and verifies, the info will still be old.
                                    // The app won't automatically know that they have clicked the verification btn cz it happens outside the app. reload() now makes the app aware. Notice that it comes b4 Firestore functionalities
                                    // Notice that the reload happens before profile retrieval. That's cz we want to confirm the user's email that has been verified b4 continuing with the login process. It comes after Authentification cz we use auth to get user info
                                    .addOnSuccessListener {
                                        if (user.isEmailVerified) {

                                            user.getIdToken(true)//Why do we need this when we already have the user.reload() above? In simple terms, reload() tells the app that Mary is verified while getIdToken(true) ensures that her authentification token reflects
                                                //verification when Firestore checks it. Basically, the former updates the frontEnd while the latter updates the backend. Cz rn we've added a rule on allow read that states that the user can't read data unless the email is verified
                                                //and u would think, well the user is already verified that's y we've made it inside the if clause. The app knows u're verified cz of reload() but there's a chance that Authentification (which is in charge of the
                                                //token: uid+email+emailVerificationStatus+password) still isn't. Why? Because an ID token represents the user's authentication information at the time it was issued. It doesn't automatically change when something on the server changes.
                                                //So this line of code forces the token to refresh and so backend is updated
                                                .addOnSuccessListener {
                                                    db.collection("users")
                                                        .document(user.uid)
                                                        .get()//Retrieves the user's Firestore profile
                                                        .addOnSuccessListener { document ->
                                                            isLoading = false

                                                            //Above, addOnSuccessListener checks whether Firestore was able to process our request and return a result.
                                                            // However, this doesn't necessarily mean the requested document exists. Above we are asking, "Please look for a document in the users collection with this particular UID and tell me what you find."
                                                            // Firestore can successfully answer that request in two ways:
                                                            //      - "Yes, I found the document."
                                                            //      - "I successfully checked, but no document exists with that ID." So basically if the user profile doesn't exist it returns an empty string
                                                            //A successful Firestore request doesn't necessarily mean the document exists. That is why below we use document.exists() to check whether Firestore actually returned an existing user profile document.

                                                            if(document.exists()){
                                                                val role = document.getString("role")

                                                                if(role == "CLIENT" || role == "CAREGIVER"){
                                                                    Toast.makeText(
                                                                        context,//This provides access to the Android environment needed for the Toast operation.
                                                                        "Login successful!",
                                                                        Toast.LENGTH_LONG
                                                                    ).show()

                                                                    onLoginSuccess(role)

                                                                    //Navigation based on role will come later
                                                                } else{
                                                                    errorMessage = "Your account has an invalid role. Please contact support."
                                                                }
                                                            } else {
                                                                onCompleteProfile()
                                                            }
                                                        }
                                                        .addOnFailureListener {
                                                            isLoading = false

                                                            errorMessage = "Unable to retrieve your profile. Please try again."
                                                        }
                                                }
                                                .addOnFailureListener { exception ->
                                                    isLoading = false
                                                    errorMessage = "Unable to verify your session. Please try again."
                                                }
                                        } else {
                                            isLoading = false

                                            //auth.signOut()//Why do we need to do this despite the fact that it looks like the user hasn't logged in? One thing you need to know is that as soon as Firebase authenticates a user, it considers
                                            //them as signed in despite the fact that we don't. So without this, later on in another page if you us if(auth.currentUser) for sessions, the unverified user will still have access cz they
                                            //are still authenticated. So our fear that drives this line of code is having an unverified user who is authenticated. When we sign out we unauthenticate them so now auth.currentUser has no user
                                            //limiting the access. It's a VERY important safe measure

                                            verificationError = ""
                                            showVerificationDialog = true
                                        }
                                    }
                            }else {
                                isLoading = false

                                errorMessage = "Unable to retrieve your account. Please try again."
                            }
                        }else{
                            isLoading = false
                            errorMessage = "Unable to sign in. Please check your email and password."
                        }
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }else{
                Text("Login")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = onRegisterClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Don't have an account? Register")
        }
    }

    if(showForgotPasswordDialog){
        AlertDialog(
            onDismissRequest = {
                if(!isResetLoading){
                    showForgotPasswordDialog = false
                }
            },

            title = {
                Text("Reset Password")
            },

            text = {
                Column {
                    Text("Enter your email address and we will send you a password reset link.")

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = {
                            resetEmail = it
                            resetError = ""
                        },
                        label = {
                            Text("Email Address")
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if(resetError.isNotEmpty()){
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            resetError,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },

            confirmButton = {
                TextButton(
                    enabled = !isResetLoading,
                    onClick = {
                        val emailToReset = resetEmail.trim()

                        resetError = when {
                            emailToReset.isBlank() ->
                                "Please enter your email address"

                            !Patterns.EMAIL_ADDRESS.matcher(emailToReset).matches() ->
                                "Please enter a valid email address"

                            else -> ""
                        }

                        if(resetError.isEmpty()){
                            isResetLoading = true

                            auth.sendPasswordResetEmail(emailToReset)//Firebase provides this functionality. After sending the password it'll
                                //handle sending the reset link to the user itself so all we have to do is to check that it is successful
                                .addOnSuccessListener {
                                    isResetLoading = false
                                    showForgotPasswordDialog = false

                                    Toast.makeText(
                                        context,
                                        "A password reset email has been sent to the address you have provided.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                .addOnFailureListener {
                                    isResetLoading = false

                                    resetError = "Unable to process the request. Please try again."
                                }
                        }
                    }
                ) {
                    if (isResetLoading){
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }else{
                        Text("Send link")
                    }
                }
            },

            dismissButton = {
                TextButton(
                    enabled = !isLoading,
                    onClick = {
                        showForgotPasswordDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showVerificationDialog){
        AlertDialog(
            onDismissRequest = {
                if (!isResendingVerification){
                    auth.signOut()
                    verificationError = "" //I set this to empty every time I'm about to remove the dialog(like below) so that when it appears again, the if statement used to check if it is not empty doesn't return true cz then a previous error message will be displayed
                    showVerificationDialog = false
                }
            },

            title = {
                Text(
                    text = "Email Verification Required",
                    textAlign = TextAlign.Center
                )
            },

            text = {
                Column{
                    Text(
                        text = "Your email address has not been verified. Please check your inbox and click the verification link. If you didn't receive the email, you can request another one.",
                        textAlign = TextAlign.Justify
                    )

                    if (verificationError.isNotEmpty()){
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = verificationError,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },

            confirmButton = {
                TextButton(
                    enabled = !isResendingVerification,

                    onClick = {
                        val user = auth.currentUser

                        if(user != null){
                            verificationError = ""
                            isResendingVerification = true

                            user.sendEmailVerification()
                                .addOnSuccessListener {

                                    isResendingVerification = false

                                    auth.signOut()//I'm signing the user out so as ensure I've denied them access until they have verified their email. Remember the success listener is only triggered after firebase has sent the email not after the user has clicked the link

                                    verificationError = ""
                                    showVerificationDialog = false

                                    Toast.makeText(
                                        context,
                                        "Verification email sent! Please check your inbox.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                .addOnFailureListener {
                                    isResendingVerification = false

                                    verificationError = "Failed to resend verification email. Please try again."
                                }
                        } else{
                            isResendingVerification = false

                            verificationError = "Your session has expired. Please log in again."
                        }
                    }
                ) {
                    if (isResendingVerification){
                        CircularProgressIndicator(
                            modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }else{
                        Text("Resend Email")
                    }
                }
            },

            dismissButton = {
                TextButton(
                    enabled = !isResendingVerification,

                    onClick = {
                        auth.signOut()// This is here cz it ensures that the user doesn't remain authenticated while waiting to verify their email. Until they verify their email and login they won't have access to the app features
                        verificationError = ""
                        showVerificationDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

}

@Preview(showBackground = true)
@Composable
fun previewLoginScreen(){
    //LoginScreen()
}