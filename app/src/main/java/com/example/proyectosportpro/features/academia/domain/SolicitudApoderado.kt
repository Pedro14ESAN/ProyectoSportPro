package com.example.proyectosportpro.features.academia.domain

enum class RelacionApoderado(val etiqueta: String) {
    MADRE("Madre"),
    PADRE("Padre"),
    APODERADO_AUTORIZADO("Apoderado autorizado")
}

enum class EstadoSolicitudApoderado(val etiqueta: String) {
    PENDIENTE("Pendiente de revisión"),
    APROBADA("Aprobada"),
    RECHAZADA("Rechazada"),
    REVOCADA("Vínculo revocado")
}

data class SolicitudApoderado(
    val id: String,
    val academiaId: String,
    val jugadorUid: String,
    val apoderadoUid: String,
    val nombreApoderado: String,
    val relacionDeclarada: RelacionApoderado,
    val estado: EstadoSolicitudApoderado,
    val revisadoPor: String? = null,
    val revocadoPor: String? = null
)