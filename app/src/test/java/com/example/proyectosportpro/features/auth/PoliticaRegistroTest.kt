package com.example.proyectosportpro.features.auth

import com.example.proyectosportpro.features.auth.domain.PoliticaRegistro
import com.example.proyectosportpro.features.auth.domain.RolUsuario
import com.example.proyectosportpro.features.auth.domain.TipoRegistro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class PoliticaRegistroTest {
    private val hoy = LocalDate.of(2026, 11, 7)

    private fun tipo(nacimiento: LocalDate, rol: RolUsuario) =
        PoliticaRegistro.evaluar(nacimiento, rol, hoy).tipo

    @Test
    fun rechazaFechasFuturas() {
        val evaluacion = PoliticaRegistro.evaluar(hoy.plusDays(1), RolUsuario.JUG, hoy)
        assertEquals(TipoRegistro.NO_PERMITIDO, evaluacion.tipo)
        assertNull(evaluacion.edad)
    }

    @Test
    fun noPermiteRegistrarAdministradores() {
        assertEquals(TipoRegistro.NO_PERMITIDO, tipo(hoy.minusYears(30), RolUsuario.ADM))
    }

    @Test
    fun apoderadosYDirectoresDebenSerMayoresDeEdad() {
        assertEquals(TipoRegistro.ADULTO, tipo(hoy.minusYears(18), RolUsuario.PAD))
        assertEquals(TipoRegistro.ADULTO, tipo(hoy.minusYears(40), RolUsuario.DT))
        assertEquals(TipoRegistro.NO_PERMITIDO, tipo(hoy.minusYears(18).plusDays(1), RolUsuario.PAD))
        assertEquals(TipoRegistro.NO_PERMITIDO, tipo(hoy.minusYears(17), RolUsuario.DT))
    }

    @Test
    fun clasificaJugadoresPorEdad() {
        assertEquals(TipoRegistro.SOLO_PERFIL_GESTIONADO, tipo(hoy.minusYears(11), RolUsuario.JUG))
        assertEquals(TipoRegistro.REQUIERE_APODERADO, tipo(hoy.minusYears(12), RolUsuario.JUG))
        assertEquals(TipoRegistro.REQUIERE_APODERADO, tipo(hoy.minusYears(17), RolUsuario.JUG))
        assertEquals(TipoRegistro.ADULTO, tipo(hoy.minusYears(18), RolUsuario.JUG))
    }

    @Test
    fun calculaLaEdadSinContarElCumpleanosPendiente() {
        val evaluacion = PoliticaRegistro.evaluar(LocalDate.of(2010, 11, 8), RolUsuario.JUG, hoy)
        assertEquals(15, evaluacion.edad)
    }
}
