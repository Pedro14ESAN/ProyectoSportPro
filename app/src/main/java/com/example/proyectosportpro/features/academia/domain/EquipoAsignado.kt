package com.example.proyectosportpro.features.academia.domain

data class EquipoAsignado(
    val id: String,
    val academiaId: String,
    val academiaNombre: String,
    val categoriaNombre: String,
    val nombre: String,
    val activo: Boolean
)
