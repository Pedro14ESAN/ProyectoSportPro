package com.example.proyectosportpro.features.comunidad.domain

data class Aviso(
    val id: String,
    val academiaId: String,
    val equipoId: String,
    val nombreEquipo: String,
    val titulo: String,
    val mensaje: String,
    val autorUid: String,
    val autorNombre: String,
    val fechaMillis: Long
)
