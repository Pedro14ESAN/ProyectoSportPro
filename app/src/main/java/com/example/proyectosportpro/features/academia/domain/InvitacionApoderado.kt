package com.example.proyectosportpro.features.academia.domain

enum class EstadoInvitacionApoderado {
    DISPONIBLE,
    UTILIZADA,
    REVOCADA
}

data class InvitacionApoderado(
    val id: String,
    val academiaId: String,
    val jugadorUid: String,
    val apoderadoUid: String,
    val creadoPor: String,
    val fechaVencimientoMillis: Long,
    val estado: EstadoInvitacionApoderado,
    val solicitudId: String? = null
)