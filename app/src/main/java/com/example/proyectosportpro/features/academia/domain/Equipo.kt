package com.example.proyectosportpro.features.academia.domain

data class Equipo(
    val id: String,
    val academiaId: String,
    val categoriaId: String,
    val nombre: String,
    val activo: Boolean,
    val creadoPor: String,
    val actualizadoPor: String,
    //  Identificador del DT asignado al equipo.
    val dtUid: String? = null
)