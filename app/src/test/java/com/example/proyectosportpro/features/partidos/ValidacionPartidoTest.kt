package com.example.proyectosportpro.features.partidos

import com.example.proyectosportpro.features.partidos.domain.ValidacionPartido
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ValidacionPartidoTest {
    private val fecha = 1_800_000_000_000L

    @Test
    fun aceptaDatosCompletos() {
        assertNull(ValidacionPartido.validar("Leones FC", "Estadio Municipal", fecha))
    }

    @Test
    fun exigeRivalLugarYFecha() {
        assertNotNull(ValidacionPartido.validar("L", "Estadio", fecha))
        assertNotNull(ValidacionPartido.validar("Leones", " ", fecha))
        assertNotNull(ValidacionPartido.validar("Leones", "Estadio", null))
        assertNotNull(ValidacionPartido.validar("x".repeat(61), "Estadio", fecha))
    }

    @Test
    fun ignoraEspaciosSobrantesAlValidar() {
        assertNotNull(ValidacionPartido.validar("  A  ", "Estadio", fecha))
        assertEquals("Leones FC", ValidacionPartido.normalizar("  Leones    FC "))
    }

    @Test
    fun aceptaSoloGolesEntreCeroYNoventaYNueve() {
        assertEquals(0, ValidacionPartido.goles("0"))
        assertEquals(12, ValidacionPartido.goles(" 12 "))
        assertNull(ValidacionPartido.goles(""))
        assertNull(ValidacionPartido.goles("-1"))
        assertNull(ValidacionPartido.goles("100"))
        assertNull(ValidacionPartido.goles("dos"))
    }
}
