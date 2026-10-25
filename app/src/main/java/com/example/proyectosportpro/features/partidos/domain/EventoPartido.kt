package com.example.proyectosportpro.features.partidos.domain

enum class TipoEvento(val etiqueta: String, val icono: String) {
    GOL("Gol", "⚽"),
    TARJETA_AMARILLA("Tarjeta amarilla", "🟨"),
    TARJETA_ROJA("Tarjeta roja", "🟥"),
    CAMBIO("Cambio", "🔁"),
    OTRO("Otro", "📝")
}

enum class LadoEvento(val etiqueta: String) {
    PROPIO("Nuestro equipo"),
    RIVAL("Rival")
}

/** Jugador del plantel que puede figurar en un evento. */
data class JugadorEvento(val uid: String, val nombre: String)

/** Lo que el DT anota; minuto y jugador pueden quedar pendientes y completarse después. */
data class DatosEvento(
    val tipo: TipoEvento,
    val lado: LadoEvento,
    val minuto: Int?,
    val jugador: JugadorEvento?,
    val detalle: String
)

data class EventoPartido(
    val id: String,
    val datos: DatosEvento,
    val anulado: Boolean,
    val motivoAnulacion: String?,
    val version: Int,
    val fechaCreacionMillis: Long?,
    /** El cambio está guardado en el teléfono, pero aún no llegó al servidor. */
    val pendiente: Boolean
) {
    /** Datos que faltan para que el evento quede completo. */
    val faltantes: List<String>
        get() = buildList {
            if (datos.minuto == null) add("minuto")
            if (datos.lado == LadoEvento.PROPIO && datos.tipo != TipoEvento.OTRO && datos.jugador == null) add("jugador")
        }
}

/** Marcador desde el punto de vista del equipo de la academia. */
data class Marcador(val favor: Int, val contra: Int) {
    override fun toString() = "$favor - $contra"
}
