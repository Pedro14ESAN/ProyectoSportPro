package com.example.proyectosportpro.features.partidos.domain

enum class EstadoPartido(val etiqueta: String) {
    PROGRAMADO("Programado"),
    FINALIZADO("Finalizado"),
    CANCELADO("Cancelado")
}

enum class CondicionPartido(val etiqueta: String) {
    LOCAL("Local"),
    VISITA("Visita")
}

/** Equipo cuyos partidos puede ver la cuenta; puedeGestionar indica si además puede programarlos. */
data class EquipoPartido(
    val academiaId: String,
    val equipoId: String,
    val nombre: String,
    val academiaNombre: String,
    val puedeGestionar: Boolean
)

data class DatosPartido(
    val rival: String,
    val lugar: String,
    val fechaHoraMillis: Long,
    val condicion: CondicionPartido
)

data class Partido(
    val id: String,
    val academiaId: String,
    val equipoId: String,
    val nombreEquipo: String,
    val rival: String,
    val lugar: String,
    val fechaHoraMillis: Long,
    val condicion: CondicionPartido,
    val estado: EstadoPartido,
    val golesFavor: Int? = null,
    val golesContra: Int? = null
) {
    val resultado: String?
        get() = if (golesFavor != null && golesContra != null) "$golesFavor - $golesContra" else null
}
