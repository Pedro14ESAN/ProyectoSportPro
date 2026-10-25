package com.example.proyectosportpro.features.partidos.domain

enum class FiltroPartidos(val etiqueta: String) {
    PROXIMOS("Próximos"),
    JUGADOS("Jugados"),
    TODOS("Todos");

    /** Próximos en orden cronológico; jugados del más reciente al más antiguo. */
    fun aplicar(partidos: List<Partido>): List<Partido> = when (this) {
        PROXIMOS -> partidos.filter { it.estado == EstadoPartido.PROGRAMADO }
            .sortedBy { it.fechaHoraMillis }
        JUGADOS -> partidos.filter { it.estado == EstadoPartido.FINALIZADO }
            .sortedByDescending { it.fechaHoraMillis }
        TODOS -> partidos.sortedBy { it.fechaHoraMillis }
    }
}
