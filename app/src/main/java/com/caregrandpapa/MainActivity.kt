package com.caregrandpapa

import com.caregrandpapa.app.AppRoot
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.caregrandpapa.core.ui.theme.CareGrandpapaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CareGrandpapaTheme {
                AppRoot()
            }
        }
    }
}
