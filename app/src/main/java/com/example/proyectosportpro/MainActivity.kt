package com.example.proyectosportpro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import com.example.proyectosportpro.features.auth.presentation.CompletarFechaScreen
import com.example.proyectosportpro.features.auth.presentation.RegistroScreen
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        val existeSesion =
            FirebaseAuth.getInstance().currentUser != null

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme()
            ) {
                if (existeSesion) {
                    CompletarFechaScreen()
                } else {
                    RegistroScreen()
                }
            }
        }
    }
}