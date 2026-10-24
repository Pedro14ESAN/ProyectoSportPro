package com.example.proyectosportpro.features.academia.domain

/** Resumen visible para el apoderado; no incluye el perfil deportivo privado. */
data class JugadorVinculado(
    val solicitudId: String,
    val academiaId: String,
    val jugadorUid: String,
    val nombreJugador: String,
    val nombreAcademia: String,
    val relacion: RelacionApoderado,
    val estado: EstadoSolicitudApoderado,
    val fechaAprobacionMillis: Long? = null
)
