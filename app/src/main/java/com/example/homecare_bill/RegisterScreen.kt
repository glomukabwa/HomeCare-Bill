package com.example.homecare_bill

import android.R
import android.content.res.Configuration
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.modifier.modifierLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.core.ComponentProvider

@Composable
fun RegisterScreen(modifier: Modifier = Modifier){
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    //PASSWORD CONDITIONS

    val hasMinLength = password.length >= 8
    val hasUppercase = password.any{it.isUpperCase()}//.any { } checks whether at least one character in the password satisfies the condition.
    val hasLowercase = password.any{it.isLowerCase()}
    val hasNumber = password.any{it.isDigit()}
    val hasSpecialCharacter = password.any{!it.isLetterOrDigit()}

    val passwordValid = hasMinLength && hasUppercase && hasLowercase && hasNumber && hasSpecialCharacter

    val strengthScore = listOf(
        hasMinLength,
        hasUppercase,
        hasLowercase,
        hasNumber,
        hasSpecialCharacter
    ).count {it} //counts how many values are true eg if three of the five requirements are satisfied, strengthScore will be 3.

    //END OF PASSWORD CONDITIONS

    var confirmPassword by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("CLIENT") }//Client is selected by default.
    var errorMessage by remember { mutableStateOf("") }

    val auth = remember { FirebaseAuth.getInstance() }
    val context = LocalContext.current
    /*FirebaseAuth.getInstance() gets the Firebase Authentication instance configured for our app.
      We use remember so we don't repeatedly retrieve it during recomposition.
      LocalContext.current gives us the Android context, which we'll use to display Toast messages.*/
    val db = remember { FirebaseFirestore.getInstance() } //This gives us access to our Firestore database.

    //"by remember" VS "= remember"
    //val state = remember { ... } returns the actual MutableState wrapper object. To read or write the actual value inside it, you must explicitly use .value.
    //var state by remember { ... } uses delegation to automatically unwrap the object. It allows you to read and write to the variable directly, as if it were a normal primitive variable.

    var isLoading by remember { mutableStateOf(false) }
    var profileSaveFailed by remember { mutableStateOf(false) }// Meaning: false ->No profile-saving failure has been recorded, true -> Authentication succeeded, but saving the Firestore profile failed

    Column(
        modifier = modifier//Notice that this is in lowercase because we're now using the parameter passed into the RegisterScreen() function above. I'm gonna pass the padding values that will be calculated by Scaffold in MainActivity.kt
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            /*verticalScroll() enables vertical scrolling for the Column.
              rememberScrollState() creates and remembers the scroll position, allowing Compose to track how far the user has scrolled.*/
            .padding(24.dp),//I've explained why we still need to put our own padding in MainActivity.kt
                                //Sth I should add though is that the reason we are still inheriting the modifier we'll pass in MainActivity.kt is cz column needs to know
                                //the space to leave first before it adds its own space. With Composable functions, order is very important. That's why u'll notice that the
                                //inherited modifier(which will specify innerPadding in MainActivity.kt) comes fast then, fillMaxWidth() then this additional padding. Chat says
                                //they have to follow each other like that
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Create Account",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = firstName,//value determines what appears in the field, while onValueChange updates our variable whenever the user types.
            onValueChange = { firstName = it},
            label = {Text("First Name")},
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier =  Modifier.height(12.dp))

        OutlinedTextField(
            value = lastName,
            onValueChange = { lastName = it},
            label = {Text("Last Name")},
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = email,
            onValueChange = {email = it},
            label = {Text("Email")},
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = {password = it},
            label = {Text("New Password")},
            visualTransformation = PasswordVisualTransformation(),//hides the password characters on screen.
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        if(password.isNotBlank()){
            Text(
                text = "Password requirements:",
                style = MaterialTheme.typography.labelMedium
            )

            PasswordRequirement("At least 8 characters", hasMinLength)
            PasswordRequirement("One uppercase letter", hasUppercase)
            PasswordRequirement("One lowercase letter", hasLowercase)
            PasswordRequirement("One number", hasNumber)
            PasswordRequirement("One special character", hasSpecialCharacter)

            Spacer(modifier = Modifier.height(8.dp))

            val strengthLabel = when(strengthScore) {
                0, 1, 2 -> "Weak"
                3,4 -> "Moderate"
                else -> "Strong"
            }

            Text(
                text = "Password strength: $strengthLabel",
                style = MaterialTheme.typography.bodySmall
            )

            LinearProgressIndicator(
                progress = { strengthScore / 5f},
                modifier = Modifier.fillMaxWidth()
            )

        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = {confirmPassword = it},
            label = {Text("Confirm Password")},
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(20.dp))

        //ROLE SELECTION SECTION
        Text(
            text = "Register as:",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        val roles = listOf(
            "CLIENT" to "Client",
            "CAREGIVER" to "Caregiver / Service Provider"
        )

        roles.forEach { (roleValue, roleLabel) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        /*selectable() is a Jetpack Compose modifier that makes a component behave like a selectable item.
                          It lets us specify which option is selected, what should happen when the user taps it, and what kind of selection control it represents.*/
                        selected =  selectedRole == roleValue,
                        //This is kinda like an if statement. If selectedRole and roleValue hold the same value then its value becomes true
                        //There are two roleValues in the list, "CLIENT" and "CAREGIVER" but there's a single selectedRole variable
                        //The value of roleValue doesn't change. It remains the same according to its row. What I mean is that row 1 has the roleValue "CLIENT" and row 2
                        //has the rowValue "CAREGIVER". selectedRole is what changes depending on what the user has clicked
                        //onClick below is what is used to change selectedRole. When you choose option "Client", that means that you have picked the row with the rowValue "CLIENT"
                        //So selectedRole is assigned the roleValue "CLIENT" then selected compares selectedRole with the two available roleValues to find which one is true so
                        //that it can display the correct RadioButton as selected. Below u'll notice that inside the RadioButton() I added another selected. The first selected here
                        //allows someone to select the radio button by clicking anywhere inside the row, may it be the text, the actual radio button etc while the selected inside the
                        //RadioButton controls the actual appearance. So it is what makes the selected RadioButton be shaded
                        onClick = { selectedRole = roleValue},
                        role = Role.RadioButton
                        //role is also a parameter of selectable() and it describes the kind of UI control the selectable item represents
                        //So basically we use it here to say that the selectable Row is a RadioButton. Other uses: Role.Checkbox, Role.Switch
                    ),
                verticalAlignment = Alignment.CenterVertically
                /*Alignment.CenterVertically vs Alignment.Center :
                *       Alignment.CenterVertically -> 1D (Vertical axis only)	        -> Row	-> Aligns items to the middle of the Row's height.
                *       Alignment.Center	       -> 2D (Both Horizontal & Vertical)	-> Box, Column, Row	-> Centers items perfectly in the exact middle of the Box (both width and height).*/
            ) {
                RadioButton(
                    selected = selectedRole == roleValue,
                    onClick = null
                    //I've made onClick null here cz we've already handled the clicking in the row. This avoids creating a separate click handler for the RadioButton.
                    //It is a common Jetpack Compose pattern when a radio button and its label should function as one selectable item.
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(text = roleLabel)
            }
        }

        //END OF ROLE SELECTION SECTION

        Spacer(modifier = Modifier.height(24.dp))

        if(errorMessage.isNotEmpty()){
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        Button(
            onClick = {
                errorMessage = when {//In Kotlin,when is similar to if...else...
                    firstName.isBlank() ->
                        "Please enter your first name"

                    lastName.isBlank() ->
                        "Please enter your last name"

                    email.isBlank() ->
                        "Please enter your email address"

                    !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() ->
                        "Please enter a valid email address"
                    /*email.trim() removes spaces at the beginning and end of the email.
                      Patterns.EMAIL_ADDRESS provides Android's email-format pattern.
                      .matcher(...).matches() checks whether the email matches that pattern.
                      ! means NOT. Meaning that if the email doesn't match the format then assign the error message*/

                    !passwordValid ->
                        "Password does not meet all the security requirements"

                    password != confirmPassword ->
                        "Passwords entered don't match"

                    selectedRole !in listOf("CLIENT", "CAREGIVER") ->
                        "Please select a valid role"

                    else -> ""
                    /*If u're wondering, what if all the conditions are fulfilled? How will it display all the error messages when errorMessage is not
                    * an array? Kotlin evaluates the conditions from top to bottom so let's say both the first and last name are blank, the error message that
                    * will be displayed is the one associated with the first name cz we won't have reached the condition of the second name.*/
                }

                if (errorMessage.isEmpty()) {//Sth to note is that isBlank() is better than isEmpty() cz isBlank() considers whitespaces as emptiness while isEmpty() doesn't
                    isLoading = true

                    profileSaveFailed = false

                    auth.createUserWithEmailAndPassword(
                        email.trim(),
                        password
                        //This tells Firebase to create a new user using the supplied email address and password.
                        //Firebase handles the authentication credentials securely.
                    ).addOnCompleteListener { task ->
                        //Firebase communicates over the network, so registration isn't necessarily completed immediately.
                        //Instead of freezing the app while waiting, we attach a listener.
                        //Once Firebase finishes processing the request, the listener executes.
                        //The task object contains information about whether registration succeeded or failed.
                        if (task.isSuccessful){

                            val user = auth.currentUser
                            //auth.currentUser retrieves the currently authenticated Firebase user(the user that Firebase has successfully created).
                            //Since account creation normally signs the user in automatically, we expect this to be the newly registered user.

                            if(user != null){//We check user != null because currentUser is nullable(it is a nullable property).
                                val userProfile = hashMapOf(
                                    "firstName" to firstName.trim(),
                                    "lastName" to lastName.trim(),
                                    "email" to (user.email ?: email.trim()),//This ensures that the email entered into Authentification is the one
                                    //recorded into Firestore. I'd gotten an error before when I tried Registering the 1st time that's why I put this here.
                                    //I'm not sure if it helped solve the error or not but I'm leaving it here.
                                    "role" to selectedRole
                                )

                                db.collection("users")//selects the users collection.
                                    .document(user.uid)//identifies the document using the authenticated user's UID.
                                    .set(userProfile)//writes the profile fields into that document.
                                    .addOnSuccessListener {

                                        isLoading = false
                                        profileSaveFailed = false

                                        Toast.makeText(
                                            context,
                                            "Account created successfully!",
                                            Toast.LENGTH_LONG
                                            //Toast.LENGTH_LONG lasts for exactly 3.5 seconds.
                                            //Toast.LENGTH_SHORT lasts for exactly 2 seconds.
                                        ).show()
                                    }
                                    .addOnFailureListener { exception ->

                                        isLoading = false //Cz if there's an error, loading needs to stop so that the error message is displayed
                                        profileSaveFailed = true

                                        errorMessage = "Your account was created, but we couldn't save your profile." +
                                                "Please retry saving your profile. " +
                                                (exception.localizedMessage ?: "")
                                    }
                            }else{
                                isLoading = false //Cz if there's an error, loading needs to stop so that the error message is displayed
                                errorMessage = "Unable to retrieve the new account. Please try again."
                            }

                        }else{

                            isLoading = false //Cz if there's an error, loading needs to stop so that the error message is displayed

                            errorMessage = task.exception?.localizedMessage
                                ?: "Registration failed. Please try again."
                            //?. — Safely access a property when the object might be null.
                                //task.exception can be null. Why, you wonder, if there's an error?
                                //First cz the Firebase's Task API defines exception as a nullable property.
                                //Remember, the else clause is triggered as long as task.isSuccessful is false
                                //If the authentication operation is cancelled for an unknown reason, task.isSuccessful
                                //will be false but task.execption will be null. So we need to use ?. to check. It's like the
                                //normal "?" we use instead of "if" but then "?." is used when the thing we're checking is nullable
                                //One more useful distinction: null doesn't necessarily mean something went wrong. In Kotlin, it simply means there is no value available for that property.
                            //?: — Provide a fallback value when the result is null.
                                //So now just in case it IS null, the statement after this is used
                            //localizedMessage — Retrieves a description of an exception. Cz the Firebase exceptions are not always user-friendly
                        }
                    }
                }
            },
            enabled = !isLoading,//enabes determines whether the button can be clicked. If value of !isLoading is false, then the btn will be disabled and vice versa. It initial value is therefore tru cz isLoading is false. It'll become false when isLoading becomes true
            modifier = Modifier.fillMaxWidth()
        ) {

            if(isLoading){
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp), //determines the overall size of the loading indicator
                    strokeWidth = 2.dp,//determines how thick the circular line of the loading indicator is
                    color = MaterialTheme.colorScheme.onPrimary //onPrimary represents the color used for content displayed on top of a primary-colored background
                )
            }else{
                Text("Register")
            }
        }

        TextButton(
            onClick = {
                // Login functionality
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Already have an account? Login")
        }
    }

    if(profileSaveFailed){
        AlertDialog(

            title = {
                Text("Profile Not Saved")
            },

            text = {
                Text(
                    "Your account was created successfully, but we couldn't save your profile. Please try again."
                )
            },

            confirmButton = {
                TextButton(
                    onClick = {
                        val user = auth.currentUser //Cz the currentUser is the most recent user -> The user whose details have just been authenticated but whose profile has not been added

                        if(user != null) {
                            isLoading = true
                            errorMessage = ""

                            val userProfile = hashMapOf(
                                "firstName" to firstName.trim(),
                                "lastName" to lastName.trim(),
                                "email" to (user.email ?: email.trim()),
                                "role" to selectedRole
                            )

                            db.collection("users")
                                .document(user.uid)//The ID that was created bu Authentification
                                .set(userProfile)
                                .addOnSuccessListener {
                                    isLoading = false
                                    profileSaveFailed = false

                                    Toast.makeText(
                                        context,
                                        "Registration completed successfully!",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                .addOnFailureListener { exception ->
                                    isLoading = false
                                    errorMessage =
                                        "Profile save failed: " +
                                                (exception.localizedMessage ?: "Please try again.")
                                }
                        }else{
                            profileSaveFailed = false
                            errorMessage =
                                "Your session has expired. Please sign in again."
                        }
                    },
                    enabled = !isLoading
                ) {
                    if(isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }else{
                        Text("Retry")
                    }
                }
            },

            dismissButton = {
                TextButton(
                    onClick = {
                        profileSaveFailed = false
                    },
                    enabled = !isLoading
                ) {
                    Text("Close")
                }
            },

            onDismissRequest = {//This determines what happens if the user tries to dismiss the popup, for example by tapping outside it or pressing the Android Back button.
                if(!isLoading) {//This means only allow the popup to close if Firebase is NOT currently processing a request ie it is not retying the registration.
                    // In the code above in the Firestore failure section, we've set isLoading to false and profileSaveFailed to true. The profileSaveFailed is
                    // what triggers the alert dialog. When someone clicks the retry registration button, the isLoading is set to true and then to false if the
                    // retry has either succeeded or failed. However, there's a chance that the user may try to dismiss the alert while the app is retying registration
                    // so this section is here to ensure that isLoading is false (meaning there's no process going on), before profileSaveFailed is set to false making
                    // the dialog box disappear.
                    profileSaveFailed = false
                }
            }

        )
    }

}


@Composable
fun PasswordRequirement(
    requirement: String,
    isMet: Boolean
){
    val reqColour = if(isMet){
        MaterialTheme.colorScheme.primary
    } else{
        MaterialTheme.colorScheme.error
    }

    Row(
        verticalAlignment = Alignment.CenterVertically
    ){
        Text(
            text = if (isMet) "✓" else "○",
            color = reqColour
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = requirement,
            color = reqColour,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RegisterScreenPreview(){
    RegisterScreen()
}