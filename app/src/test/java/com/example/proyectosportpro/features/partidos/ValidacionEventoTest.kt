package com.example.proyectosportpro.features.partidos

import com.example.proyectosportpro.features.partidos.domain.DatosEvento
import com.example.proyectosportpro.features.partidos.domain.JugadorEvento
import com.example.proyectosportpro.features.partidos.domain.LadoEvento
import com.example.proyectosportpro.features.partidos.domain.TipoEvento
import com.example.proyectosportpro.features.partidos.domain.ValidacionEvento
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ValidacionEventoTest {
    private fun datos(minuto: Int? = 10, lado: LadoEvento = LadoEvento.PROPIO, detalle: String = "") =
        DatosEvento(TipoEvento.GOL, lado, minuto, JugadorEvento("jug1", "Ana"), detalle)

    @Test
    fun elMinutoVacioQuedaPendienteYElInvalidoSeMarca() {
        assertNull(ValidacionEvento.minuto("  "))
        assertEquals(45, ValidacionEvento.minuto("45"))
        assertEquals(-1, ValidacionEvento.minuto("200"))
    }

    @Test
    fun aceptaEventosSinMinutoPeroNoFueraDeRango() {
        assertNull(ValidacionEvento.validar(datos(minuto = null)))
        assertNotNull(ValidacionEvento.validar(datos(minuto = -1)))
        assertNotNull(ValidacionEvento.validar(datos(minuto = 131)))
    }

    @Test
    fun noSeAsignaJugadorPropioAlRivalNiDetallesLargos() {
        assertNotNull(ValidacionEvento.validar(datos(lado = LadoEvento.RIVAL)))
        assertNotNull(ValidacionEvento.validar(datos(detalle = "x".repeat(141))))
    }

    @Test
    fun anularExigeUnMotivo() {
        assertNotNull(ValidacionEvento.validarMotivo(" no"))
        assertNull(ValidacionEvento.validarMotivo("Fuera de juego"))
    }
}
