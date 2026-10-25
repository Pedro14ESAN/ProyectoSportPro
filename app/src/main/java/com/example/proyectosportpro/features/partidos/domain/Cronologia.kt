package com.example.proyectosportpro.features.partidos.domain

/** Cálculos sobre los eventos registrados de un partido. */
object Cronologia {
    /** Por minuto; los eventos sin minuto van al final, en el orden en que se registraron. */
    fun ordenar(eventos: List<EventoPartido>): List<EventoPartido> = eventos.sortedWith(
        compareBy<EventoPartido> { it.datos.minuto == null }
            .thenBy { it.datos.minuto ?: 0 }
            .thenBy { it.fechaCreacionMillis ?: Long.MAX_VALUE }
            .thenBy { it.id }
    )

    /** Los eventos anulados no cuentan. */
    fun marcador(eventos: List<EventoPartido>): Marcador {
        val goles = eventos.filter { !it.anulado && it.datos.tipo == TipoEvento.GOL }
        return Marcador(
            favor = goles.count { it.datos.lado == LadoEvento.PROPIO },
            contra = goles.count { it.datos.lado == LadoEvento.RIVAL }
        )
    }

    fun incompletos(eventos: List<EventoPartido>): Int = eventos.count { !it.anulado && it.faltantes.isNotEmpty() }
}
