package com.example.proyectosportpro.features.auth.presentation
import com.google.firebase.firestore.Source
import java.time.LocalDate
import java.time.ZoneId

data class RegistroUiState(
    val cargando: Boolean = false,
    val error: String? = null,
    val registroExitoso: Boolean = false,
    val perfilPendiente: Boolean = false
)