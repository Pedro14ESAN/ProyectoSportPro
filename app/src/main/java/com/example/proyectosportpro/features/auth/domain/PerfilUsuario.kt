package com.example.proyectosportpro.features.auth.domain

data class PerfilUsuario(
    val uid: String,
    val nombre: String,
    val correo: String,
    val rol: RolUsuario,
    val fechaNacimiento: String,
    val estado: String = "PENDIENTE_VINCULACION",
    val aceptoTerminos: Boolean
)