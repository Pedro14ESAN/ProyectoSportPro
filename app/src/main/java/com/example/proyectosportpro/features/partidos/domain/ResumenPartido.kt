package com.example.proyectosportpro.features.partidos.domain

enum class EstadoResumen { BORRADOR, APROBADO }

/** Resumen narrativo del partido; solo el aprobado lo ven jugadores y apoderados. */
data class ResumenPartido(
    val texto: String,
    val estado: EstadoResumen,
    val generadoConIA: Boolean,
    val fechaActualizacionMillis: Long?
)

/** Instrucciones para la IA, construidas solo con los eventos registrados. */
object PromptResumen {
    const val MAX_TEXTO = 4000

    fun construir(partido: Partido, eventos: List<EventoPartido>): String {
        val validos = Cronologia.ordenar(eventos.filter { !it.anulado })
        val gf = partido.golesFavor
        val gc = partido.golesContra
        // El resultado guardado manda; sin él, se suman los goles registrados.
        val marcador = if (gf != null && gc != null) Marcador(gf, gc) else Cronologia.marcador(validos)
        val lineas = validos.joinToString("\n") { evento ->
            val d = evento.datos
            val minuto = d.minuto?.let { "min $it" } ?: "minuto sin registrar"
            val equipo = if (d.lado == LadoEvento.PROPIO) partido.nombreEquipo else partido.rival
            val jugador = d.jugador?.nombre?.let { " ($it)" }.orEmpty()
            val detalle = ValidacionPartido.normalizar(d.detalle).takeIf { it.isNotEmpty() }?.let { ": $it" }.orEmpty()
            "- $minuto · ${d.tipo.etiqueta} · $equipo$jugador$detalle"
        }.ifEmpty { "- No se registraron eventos." }
        return """
            Eres el redactor de una academia de fútbol formativo. Escribe en español un resumen narrativo
            del partido en dos o tres párrafos breves, con tono positivo y respetuoso hacia ambos equipos.
            Usa únicamente los datos de abajo: no inventes jugadas, nombres, minutos ni estadísticas.
            Si un dato falta, no lo menciones. No des consejos tácticos ni sugieras alineaciones.

            Partido: ${partido.nombreEquipo} vs ${partido.rival} (${partido.condicion.etiqueta}) en ${partido.lugar}.
            Resultado final: ${partido.nombreEquipo} ${marcador.favor} - ${marcador.contra} ${partido.rival}.
            Eventos registrados:
            $lineas
        """.trimIndent()
    }
}
