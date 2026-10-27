package com.example.homecare_bill

import android.R
import android.view.RoundedCorner
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.modifier.modifierLocalOf
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.Navigation
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.CountDownLatch

@Composable
fun ClientDashboardScreen(
    onLogout: () -> Unit
){
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }

    var firstName by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf("home") }

    LaunchedEffect(Unit) {
        //LaunchedEffect() means: Run this work when the screen appears, and only restart it if the key I gave you changes.
        //In a different page I put LaunchedEffect(retryCount) to mean: Run the code in the brackets if retryCount changes. Here, Unit is a constant key which means the code in
        //the brackets runs only during the first composition. If any recomposition occurs bcz of whatever reason, it doesn't run which is a good thing cz we don't want to be
        // retrieving the first name severally and unnecessarily.
        //Chat used the following statement to define LaunchedEffect: "a Jetpack Compose function that allows us to run a coroutine when a Composable enters the screen or when a specified key changes."
        //I would like to explain what a coroutine is. It is a unit of work that can be suspended and resumed without blocking the thread it is running on. Basically, the screen won't lag just bcz
        //Firestore is still retrieving the first name. The UI will be responsive as the rettrieval is happening at its own pace.

        val user = auth.currentUser

        if(user == null){
            isLoading = false
            errorMessage = "Your session has expired. Please sign in again."
        }else{
            db.collection("users")
                .document(user.uid)
                .get()
                .addOnSuccessListener { document ->
                    isLoading = false

                    if(document.exists()){
                        firstName = document.getString("firstName") ?: ""
                    }else{
                        errorMessage = "Your profile could not be found."
                    }
                }
                .addOnFailureListener {
                    isLoading = false
                    errorMessage = "Unable to load your profile. Please try again."
                }
        }
    }

    Scaffold(
        bottomBar = {
            ClientBottomNavigation (
                selectedTab = selectedTab,
                onTabSelected = {tab -> selectedTab = tab}
            )
        }
    ) { innerPadding ->

        when (selectedTab) {
            "home" -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)//This automatically adds 20 dp of spacing between the direct children of the main Column. It saves us from having to insert Spacer after every section.
                ) {

                    //APPLICATION HEADER
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "HomeCare Bill",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Profile",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    HorizontalDivider()

                    //PERSONALIZED WELCOME MESSAGE
                    when{
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
                            Column{
                                Text(
                                    text = "Welcome back, $firstName!",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "Here's your care and billing overview.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    //SUMMARY CARDS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ClientSummaryCard(
                            title = "Outstanding Balance",
                            value = "KSh —",
                            description = "Awaiting billing data",
                            modifier = Modifier.weight(1f)
                            //This tells Compose to distribute the available horizontal space equally between the two summary cards.
                            //Because both cards have a weight of 1f, each receives an equal share of the available space after accounting for the gap.
                        )

                        ClientSummaryCard(
                            title = "Upcoming Visits",
                            value = "—",
                            description = "No visits loaded",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    //TODAY'S CARE SECTION
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Today's Care",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null
                                )

                                Text(
                                    text = "No care visits scheduled yet.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    //RECENT BILLING SECTION
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Recent Billing",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ReceiptLong,
                                    contentDescription = null
                                )

                                Text(
                                    text = "Your invoices will appear here",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    //ALERTS SECTION
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Alerts",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WarningAmber,
                                    contentDescription = null
                                )

                                Text(
                                    text = "No alerts to display",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onLogout,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Logout")
                    }
                }

            }

            "billing" -> {
                ClientBillingScreen(
                    modifier = Modifier.padding(innerPadding)
                )
            }

            "alerts" -> {
                ClientAlertsScreen(
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

@Composable
fun ClientSummaryCard(
    title: String,
    value: String,
    description: String,
    modifier: Modifier = Modifier // This means the caller can optionally customize the card's size or positioning. If no modifier is supplied, the default Modifier is used.
){
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ClientBottomNavigation(
    selectedTab: String,
    onTabSelected: (String) -> Unit
){
    NavigationBar{
        NavigationBarItem(
            selected = selectedTab == "home",// If selected is "home", it becomes true. Otherwise, it is false
            onClick = {onTabSelected("home")},//onTabSelected is a callback that tells the parent Composable when a user selects another tab.
            icon = {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Home"
                )
            },
            label = {Text("Home")}
        )

        NavigationBarItem(
            selected = selectedTab == "billing",
            onClick = {onTabSelected("billing")},
            icon = {
                Icon(
                    imageVector = Icons.Default.Receipt,
                    contentDescription = "Billing"
                )
            },
            label = {Text("Billing")}
        )

        NavigationBarItem(
            selected = selectedTab == "alerts",
            onClick = {onTabSelected("alerts")},
            icon = {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Alerts"
                )
            },
            label = {Text("Alerts")}

        )
    }
}

@Composable
fun ClientBillingScreen(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.ReceiptLong,
            contentDescription = null,
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Billing",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Your invoices and payment history will appear here.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun ClientAlertsScreen(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Notifications,
            contentDescription = null,
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Alerts",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Your billing alerts and notifications will appear here.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}