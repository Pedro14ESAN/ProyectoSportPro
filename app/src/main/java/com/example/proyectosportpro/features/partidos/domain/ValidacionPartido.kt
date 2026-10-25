package com.example.proyectosportpro.features.partidos.domain

/** Reglas de formulario que coinciden con las validaciones de Firestore. */
object ValidacionPartido {
    val LONGITUD_RIVAL = 2..60
    val LONGITUD_LUGAR = 2..100
    val RANGO_GOLES = 0..99

    fun normalizar(texto: String): String = texto.trim().replace(Regex("\\s+"), " ")

    /** Devuelve un mensaje de validación, o null si los datos se pueden enviar. */
    fun validar(rival: String, lugar: String, fechaHoraMillis: Long?): String? = when {
        normalizar(rival).length !in LONGITUD_RIVAL -> "El rival debe tener entre 2 y 60 caracteres."
        normalizar(lugar).length !in LONGITUD_LUGAR -> "El lugar debe tener entre 2 y 100 caracteres."
        fechaHoraMillis == null -> "Selecciona la fecha y la hora del partido."
        else -> null
    }

    /** Convierte el texto de un marcador; null si no es un número de goles válido. */
    fun goles(texto: String): Int? = texto.trim().toIntOrNull()?.takeIf { it in RANGO_GOLES }
}
