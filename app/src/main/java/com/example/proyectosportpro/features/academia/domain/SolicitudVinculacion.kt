package com.example.proyectosportpro.features.academia.domain

import com.example.proyectosportpro.features.auth.domain.RolUsuario

enum class EstadoSolicitudVinculacion {
    PENDIENTE,
    APROBADA,
    RECHAZADA
}

data class SolicitudVinculacion(
    val id: String,
    val academiaId: String,
    val solicitanteUid: String,
    val nombreSolicitante: String,
    val estado: EstadoSolicitudVinculacion,
    val revisadoPor: String? = null,
    val rolSolicitante: RolUsuario = RolUsuario.DT
)