package com.example.proyectosportpro.features.jugador.domain

enum class PosicionJugador(val etiqueta: String) {
    PORTERO("Portero"),
    DEFENSA("Defensa"),
    MEDIOCAMPISTA("Mediocampista"),
    DELANTERO("Delantero")
}

data class PerfilJugador(
    val uid: String,
    val posicion: PosicionJugador? = null,
    val alturaCm: Int? = null,
    val pesoKg: Double? = null,
    val fotoPath: String? = null,
    val telefonoContacto: String = "",
    val nombreContactoEmergencia: String = "",
    val telefonoContactoEmergencia: String = ""
)