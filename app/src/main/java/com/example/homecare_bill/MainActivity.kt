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
                        startDestination = "login",//startDestination tells NavHost which screen should be displayed first
                        modifier = Modifier.padding(innerPadding)
                    ){
                        composable("login") {
                            LoginScreen(
                                onRegisterClick = {
                                    navController.navigate("register")
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
                    }
                }
            }
        }
    }
}