package com.example.proyectosportpro.features.academia.domain

data class JugadorPlantel(
    val jugadorUid: String,
    val academiaId: String,
    val equipoId: String,
    val nombre: String,
    val activo: Boolean,
    val agregadoPor: String,
    val actualizadoPor: String
)