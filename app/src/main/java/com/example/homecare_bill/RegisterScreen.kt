package com.example.homecare_bill

import android.R
import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.modifier.modifierLocalOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.core.ComponentProvider

@Composable
fun RegisterScreen(modifier: Modifier = Modifier){
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("CLIENT") }//Client is selected by default.

    Column(
        modifier = modifier//Notice that this is in lowercase because we're now using the parameter passed into the RegisterScreen() function above. I'm gonna pass the padding values that will be calculated by Scaffold in MainActivity.kt
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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

        Button(
            onClick = {
                //Firebase registration
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Register")
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
}

@Preview(showBackground = true)
@Composable
fun RegisterScreenPreview(){
    RegisterScreen()
}