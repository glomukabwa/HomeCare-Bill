
package com.example.homecare_bill

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceCatalogueScreen(
    onBack: () -> Unit,
    embedded: Boolean = false
) {
    val db = remember { FirebaseFirestore.getInstance() }

    var services by remember {
        mutableStateOf<List<CareService>>(emptyList())
    }

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }

    DisposableEffect(Unit) {

        val listener = db.collection("services")
            .whereEqualTo("isActive", true)
            .addSnapshotListener { snapshot, error -> //We add a listener cz an admin might change details of a service

                if (error != null) {
                    errorMessage = error.localizedMessage
                        ?: "Unable to load services."
                    isLoading = false
                    return@addSnapshotListener
                }

                services = snapshot?.documents?.map { document ->
                    CareService(
                        id = document.id,
                        name = document.getString("name") ?: "",
                        rateCents = document.getLong("rateCents") ?: 0L, //0L represents the number zero explicitly typed as a Long integer. Long is a data type that stores numbers that are larger than int. Int stores a maximum of 32 bits(2.1 billion) and Long stores up to 62bits (9 quintillion). So the zero here is treated linke a Long not an int
                        billingUnit = document.getString("billingUnit") ?: "",
                        isActive = document.getBoolean("isActive") ?: false
                    )
                } ?: emptyList()

                errorMessage = ""
                isLoading = false
            }

        onDispose {
            listener.remove()
        }
    }

    Scaffold(
        contentWindowInsets = if (embedded) {
            WindowInsets(0, 0, 0, 0)
        } else {
            ScaffoldDefaults.contentWindowInsets
        },
        topBar = {
            if (!embedded) {
                TopAppBar(
                    title = { Text("Service Catalogue") },
                    navigationIcon = {
                        TextButton(onClick = onBack) {
                            Text("Back")
                        }
                    }
                )
            }
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
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

                services.isEmpty() -> {
                    Text("No services are currently available.")
                }

                else -> {
                    LazyColumn(//A lazy column is a scrollable list that only renders the items currently visible on the screen.
                        //Rendering vs loading:
                        // Loading: Fetches text, numbers, or images from a database
                        // Rendering: Takes that loaded data and converts it into pixels, colors, shapes, and text on your physical screen.
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(services, key = { it.id }) { service -> //it.id represents the document ID from Firestore for each individual service in out list so eg home_nursing in services
                            //If you don't provide a key, Compose tracks items purely by their position (Index 0, Index 1, Index 2). This causes severe performance issues or bugs if your data changes in real time.
                            //• Without a key: If you delete the very first item in your Firestore database, Compose gets confused. It thinks the first item is still there but its text changed, forcing it to completely redraw every single item on the screen.
                            //• With a key (it.id): Compose knows exactly which item vanished. It will smoothly animate the deletion of that specific item and leave the rest of the list completely untouched, saving processor power.

                            Card(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {

                                    Text(
                                        text = service.name,
                                        style = MaterialTheme.typography.titleMedium
                                    )

                                    val rate = service.rateCents / 100.0

                                    Text(
                                        text = String.format(//String.format() converts rateCents from a number( a Long) into a string
                                            Locale.getDefault(),//This automatically detects the user's phone settings to format numbers correctly based on their country. Eg in Kenya, a thousand separator is a comma (1,000.00) while in many European countries, it is a dot (1.000,00).
                                            "KES %,.2f",
                                            //The line above is a pattern template (a blueprint) for how the text should look.
                                            //• KES : This is literal text. It will be printed exactly as written.
                                            //• %: This flags the beginning of a formatting command. It tells Kotlin: "Look at the variable I provided at the end (rate) and apply these rules to it."
                                            //• ,: This adds comma separators for large numbers (e.g., changing 150000 to 150,000).
                                            //• .2: This forces the number to display exactly two decimal places (cents), which is standard for currency.
                                            //• f: This tells Kotlin that the input data type is a floating-point decimal number (Float or Double).

                                            rate //The actual variable being injected into the % blueprint.
                                        )
                                    )

                                    Text(
                                        text = "Billing Unit: ${service.billingUnit}"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
