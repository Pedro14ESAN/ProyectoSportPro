package com.example.proyectosportpro.features.academia.domain

data class Academia(
    val id: String,
    val nombre: String,
    val activa: Boolean,
    val creadoPor: String,
    val actualizadoPor: String
)