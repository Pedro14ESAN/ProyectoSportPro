package com.example.proyectosportpro.features.comunidad.domain

/** Reglas de formulario que coinciden con las validaciones de Firestore. */
object ValidacionAviso {
    val LONGITUD_TITULO = 2..80
    val LONGITUD_MENSAJE = 2..1000

    /** Devuelve un mensaje de validación, o null si el aviso se puede publicar. */
    fun validar(titulo: String, mensaje: String): String? = when {
        titulo.trim().length !in LONGITUD_TITULO -> "El título debe tener entre 2 y 80 caracteres."
        mensaje.trim().length !in LONGITUD_MENSAJE -> "El mensaje debe tener entre 2 y 1000 caracteres."
        else -> null
    }
}
