package com.example.proyectosportpro.features.auth.presentation

import com.example.proyectosportpro.features.auth.domain.PerfilUsuario

data class LoginUiState(
    val cargando: Boolean = false,
    val sesionRevisada: Boolean = false,
    val perfil: PerfilUsuario? = null,
    val requiereCrearPerfil: Boolean = false,
    val requiereFechaNacimiento: Boolean = false,
    val error: String? = null
)