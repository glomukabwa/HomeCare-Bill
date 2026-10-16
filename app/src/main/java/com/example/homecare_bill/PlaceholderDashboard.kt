
package com.example.homecare_bill

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PlaceholderDashboard(
    role: String,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (role == "CLIENT") {
                "Welcome to the Client Dashboard!"
            } else {
                "Welcome to the Caregiver Dashboard!"
            }
        )

        Button(
            onClick = onLogout,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text("Logout")
        }
    }
}
