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
import com.example.homecare_bill.ui.theme.HomeCare_BillTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HomeCare_BillTheme {
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
                    RegisterScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}