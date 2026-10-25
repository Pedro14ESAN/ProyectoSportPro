package com.example.proyectosportpro.features.comunidad

import com.example.proyectosportpro.features.comunidad.domain.ValidacionAviso
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ValidacionAvisoTest {
    @Test
    fun aceptaUnAvisoCompleto() {
        assertNull(ValidacionAviso.validar("Entrenamiento", "Mañana a las 4 pm en la cancha 2."))
    }

    @Test
    fun rechazaTitulosYMensajesFueraDeRango() {
        assertNotNull(ValidacionAviso.validar(" ", "Mensaje válido"))
        assertNotNull(ValidacionAviso.validar("x".repeat(81), "Mensaje válido"))
        assertNotNull(ValidacionAviso.validar("Título", "a"))
        assertNotNull(ValidacionAviso.validar("Título", "x".repeat(1001)))
    }
}
