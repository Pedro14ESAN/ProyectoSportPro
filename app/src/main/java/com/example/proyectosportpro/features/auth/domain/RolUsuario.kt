package com.example.proyectosportpro.features.auth.domain

enum class RolUsuario(val etiqueta: String) {
    JUG("Jugador"),
    PAD("Padre o apoderado"),
    DT("Director técnico"),
    ADM("Administrador");

    companion object {
        val rolesDeRegistro: List<RolUsuario> = listOf(
            JUG,
            PAD,
            DT
        )
    }
}