package com.example.homecare_bill

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScrollModifierNode
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.homecare_bill.ui.theme.HomeCare_BillTheme
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HomeCare_BillTheme {

                val navController = rememberNavController()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    //innerPadding above is the space calculated by the Scaffold. It calculates the space the containers
                    //it holds need. Rn we have a RegisterScreen() function that creates a column container. It'll calculate the
                    //space to be left on the edges so that the Column doesn't start directly on the edges of the devices
                    //If we had a TopBar and a bottom bar, it would also include calculations of the space to be left between
                    //them so that they are not squeezed together. However, it does not replace the need of adding padding inside
                    //these containers. For example u'll notice that in the column in RegisterScreen(), I've still added padding despite
                    //inheriting the modifier. That's bcz innerPadding is for outside the major containers. I still need to state
                    //the spacing inside the containers I create.
                    //Also, innerPadding is not the rigid name. I could call it containerPadding and it would still work
                    //LoginScreen(modifier = Modifier.padding(innerPadding))

                    //NavController	    ->  Controls movement between screens
                    //NavHost	        ->  Defines which screens exist and displays the current screen
                    //composable()	    ->  Registers a particular screen and its route

                    NavHost(
                        navController = navController,
                        startDestination = "session_check",//startDestination tells NavHost which screen should be displayed first
                        modifier = Modifier.padding(innerPadding)
                    ){
                        composable("session_check"){
                            SessionCheckScreen(
                                onNoSession = {
                                    navController.navigate("login") {
                                        popUpTo("session_check"){
                                            inclusive = true
                                        }//The above lines of code mean: remove all the other pages in the backstack that precede login including session_check
                                        //Why are we doing this? Cz we don't want sb to go back to the loading page of the session check after going to login.
                                        // It has already been confirmed that u need to login again. The check doesn't need to be done twice
                                        launchSingleTop = true
                                        //This prevents Navigation Compose from creating another copy of the destination if it's already at the top of the back stack. Eg we don't want a backstack that looks like this: [LoginScreen, LoginScreen]
                                        //Why would it create another copy? In case another set of code calls login, and another and another, they'll keep being added in the Backstack. We don't want this cz then the user will keep seeing a login screen
                                        // when they hit back till they've finished all the login screens. So this line of code basically says, "If Login is already the destination at the top of the back stack, don't add another copy."
                                    }
                                },

                                onValidSession = { role ->
                                    val destination = when (role) {
                                        "CLIENT" -> "client_dashboard"
                                        "CAREGIVER" -> "caregiver_dashboard"
                                        else -> "login"
                                    }

                                    navController.navigate(destination){
                                        popUpTo("session_check"){
                                            inclusive = true
                                        }
                                        launchSingleTop = true
                                    }
                                },

                                onMissingProfile = {

                                    navController.navigate("complete_profile") {

                                        popUpTo("session_check") {
                                            inclusive = true
                                        }

                                        launchSingleTop = true
                                    }
                                }
                            )
                        }

                        composable("login") {
                            LoginScreen(
                                onRegisterClick = {
                                    navController.navigate("register")
                                },

                                onLoginSuccess = {role ->
                                    val destination = when (role){
                                        "CLIENT" -> "client_dashboard"
                                        "CAREGIVER" -> "caregiver_dashboard"
                                        else -> "login"
                                    }

                                    navController.navigate(destination){
                                        popUpTo("login"){
                                            inclusive = true
                                        }
                                        launchSingleTop = true
                                    }
                                },

                                onCompleteProfile = {
                                    navController.navigate("complete_profile"){
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }


                        composable("complete_profile") {

                            CompleteProfileScreen(

                                onProfileCompleted = { role ->

                                    val destination = when (role) {
                                        "CLIENT" -> "client_dashboard"
                                        "CAREGIVER" -> "caregiver_dashboard"
                                        else -> "login"
                                    }

                                    navController.navigate(destination) {

                                        popUpTo(navController.graph.id) {//Remove the recovery screen and any previous authentication screens from the back stack.
                                            inclusive = false
                                        }

                                        launchSingleTop = true
                                    }
                                },

                                onCancel = {

                                    navController.navigate("login") {

                                        popUpTo(navController.graph.id) {//The user has cancelled recovery and signed out. Return to Login with a clean navigation history.
                                            inclusive = false
                                        }

                                        launchSingleTop = true
                                    }
                                }
                            )
                        }


                        composable("register") {
                            RegisterScreen(
                                onLoginClick = {
                                    navController.popBackStack()
                                    //popBackStack() removes the current screen and returns to the previous one
                                    //Why am I using it? Eg if the app opens and we're in login and the user presses registration, registration will be displayed
                                    //and login will be in the backstack. If they click login again, registration will be added to the back stack so the order will be:
                                            // Login (Current page)
                                            //   ↓
                                            // Registration
                                            //   ↓
                                            // Login
                                    //If they keep going back and forth, they'll have a really long backstack. popBackStack() prevents that cz assume that we're on the
                                    //Registration in the middle of the backstack above, to move to login, it removes that screen and is left with just the Login as the
                                    //current page and this will keep happening making sure the backstack never grows that long
                                    //Also sth u need to know is that when someone clicks back on their phone(the btn at the bottom), it goes to the previous page on the backstack so if
                                    //a user is in Login and they want to exit the app, if u had not used popBackstack() they'll keep going back until they finish the backstack and that'll
                                    //inconvenient cz it'll be really long
                                }
                            )
                        }

                        composable("client_dashboard") {

                            ClientDashboardScreen(
                                onLogout = {
                                    FirebaseAuth.getInstance().signOut()

                                    navController.navigate("login"){
                                        popUpTo("client_dashboard"){
                                            inclusive = true
                                        }
                                        launchSingleTop = true
                                    }
                                },
                                onAddCareRecipient = {
                                    navController.navigate("add_care_recipient")
                                }
                            )
                        }


                        composable("add_care_recipient") {
                            AddCareRecipientScreen(
                                onBack = {
                                    navController.popBackStack()
                                },
                                onSaved = {
                                    navController.popBackStack()
                                }
                            )
                        }


                        composable("caregiver_dashboard") {

                            PlaceholderDashboard(
                                role = "CAREGIVER",
                                onLogout = {
                                    FirebaseAuth.getInstance().signOut()

                                    navController.navigate("login") {
                                        popUpTo("caregiver_dashboard") {
                                            inclusive = true
                                        }
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}