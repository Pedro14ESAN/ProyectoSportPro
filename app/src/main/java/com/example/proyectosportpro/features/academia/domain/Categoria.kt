package com.example.proyectosportpro.features.academia.domain

data class Categoria(
    val id: String,
    val academiaId: String,
    val nombre: String,
    val activa: Boolean,
    val creadoPor: String,
    val actualizadoPor: String
)