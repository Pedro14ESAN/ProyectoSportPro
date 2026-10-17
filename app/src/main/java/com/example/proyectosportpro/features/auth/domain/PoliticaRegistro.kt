package com.example.proyectosportpro.features.auth.domain

import java.time.LocalDate
import java.time.Period
import java.time.ZoneId

enum class TipoRegistro {
    ADULTO,
    REQUIERE_APODERADO,
    SOLO_PERFIL_GESTIONADO,
    NO_PERMITIDO
}

data class EvaluacionRegistro(
    val edad: Int?,
    val tipo: TipoRegistro,
    val mensaje: String
)

object PoliticaRegistro {

    fun evaluar(
        fechaNacimiento: LocalDate,
        rol: RolUsuario,
        fechaActual: LocalDate = LocalDate.now(
            ZoneId.of("America/Lima")
        )
    ): EvaluacionRegistro {

        if (fechaNacimiento.isAfter(fechaActual)) {
            return EvaluacionRegistro(
                edad = null,
                tipo = TipoRegistro.NO_PERMITIDO,
                mensaje = "La fecha de nacimiento no puede ser futura."
            )
        }
        // Calcular la edad en años.
        val edad = Period.between( // Año, mes, día.
            fechaNacimiento,
            fechaActual
        ).years

        if (rol == RolUsuario.ADM) {
            return EvaluacionRegistro(
                edad = edad,
                tipo = TipoRegistro.NO_PERMITIDO,
                mensaje = "Las cuentas de administrador deben ser " +
                        "habilitadas por la academia."
            )
        }

        if (rol == RolUsuario.PAD || rol == RolUsuario.DT) {
            return if (edad >= 18) {
                EvaluacionRegistro(
                    edad = edad,
                    tipo = TipoRegistro.ADULTO,
                    mensaje = "Puedes continuar con el registro. " +
                            "Los permisos requieren validación de la academia."
                )
            } else {
                EvaluacionRegistro(
                    edad = edad,
                    tipo = TipoRegistro.NO_PERMITIDO,
                    mensaje = "Debes tener al menos 18 años para " +
                            "registrarte como apoderado o director técnico."
                )
            }
        }

        return when {
            edad < 12 -> EvaluacionRegistro(
                edad = edad,
                tipo = TipoRegistro.SOLO_PERFIL_GESTIONADO,
                mensaje = "Tu apoderado debe gestionar tu inscripción " +
                        "con la academia. No se creará una cuenta propia."
            )

            edad < 18 -> EvaluacionRegistro(
                edad = edad,
                tipo = TipoRegistro.REQUIERE_APODERADO,
                mensaje = "Necesitas la autorización de tu apoderado " +
                        "y la aprobación de la academia para activar tu cuenta."
            )

            else -> EvaluacionRegistro(
                edad = edad,
                tipo = TipoRegistro.ADULTO,
                mensaje = "Puedes registrarte sin apoderado. " +
                        "Tu incorporación al equipo requiere validación."
            )
        }
    }
}