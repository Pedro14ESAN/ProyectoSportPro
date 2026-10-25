package com.example.proyectosportpro.features.partidos.domain

/** Reglas de formulario que coinciden con las validaciones de Firestore. */
object ValidacionEvento {
    val RANGO_MINUTO = 0..130
    const val MAX_DETALLE = 140
    val LONGITUD_MOTIVO = 3..140

    /** Minuto vacío es un dato pendiente; texto inválido devuelve -1. */
    fun minuto(texto: String): Int? {
        val limpio = texto.trim()
        if (limpio.isEmpty()) return null
        return limpio.toIntOrNull()?.takeIf { it in RANGO_MINUTO } ?: -1
    }

    fun validar(datos: DatosEvento): String? = when {
        datos.minuto != null && datos.minuto !in RANGO_MINUTO -> "El minuto debe estar entre 0 y 130."
        datos.jugador != null && datos.lado == LadoEvento.RIVAL -> "Los jugadores del rival no se registran por nombre."
        ValidacionPartido.normalizar(datos.detalle).length > MAX_DETALLE -> "El detalle admite hasta 140 caracteres."
        else -> null
    }

    fun validarMotivo(motivo: String): String? =
        if (ValidacionPartido.normalizar(motivo).length !in LONGITUD_MOTIVO)
            "Explica el motivo en 3 a 140 caracteres." else null
}
