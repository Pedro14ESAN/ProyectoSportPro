package com.example.proyectosportpro.features.partidos

import com.example.proyectosportpro.features.partidos.domain.CondicionPartido
import com.example.proyectosportpro.features.partidos.domain.DatosEvento
import com.example.proyectosportpro.features.partidos.domain.EstadoPartido
import com.example.proyectosportpro.features.partidos.domain.EventoPartido
import com.example.proyectosportpro.features.partidos.domain.JugadorEvento
import com.example.proyectosportpro.features.partidos.domain.LadoEvento
import com.example.proyectosportpro.features.partidos.domain.Partido
import com.example.proyectosportpro.features.partidos.domain.PromptResumen
import com.example.proyectosportpro.features.partidos.domain.TipoEvento
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptResumenTest {
    private val partido = Partido("p1", "a1", "e1", "Sub 12", "Leones", "Estadio", 0L,
        CondicionPartido.LOCAL, EstadoPartido.FINALIZADO, 1, 0)

    private fun evento(id: String, minuto: Int?, jugador: String?, anulado: Boolean = false) = EventoPartido(id,
        DatosEvento(TipoEvento.GOL, LadoEvento.PROPIO, minuto, jugador?.let { JugadorEvento("j$id", it) }, ""),
        anulado, if (anulado) "Fuera de juego" else null, 1, 0, pendiente = false)

    @Test
    fun usaSoloLosEventosValidosYElResultadoQueSuman() {
        val prompt = PromptResumen.construir(partido, listOf(
            evento("1", 12, "Ana Pérez"),
            evento("2", 30, "Luis Gol Anulado", anulado = true)
        ))
        assertTrue(prompt.contains("min 12 · Gol · Sub 12 (Ana Pérez)"))
        assertTrue(prompt.contains("Sub 12 1 - 0 Leones"))
        assertFalse(prompt.contains("Luis Gol Anulado"))
        assertTrue(prompt.contains("no inventes"))
    }

    @Test
    fun indicaLosDatosFaltantesSinInventarlos() {
        val prompt = PromptResumen.construir(partido, listOf(evento("1", null, null)))
        assertTrue(prompt.contains("minuto sin registrar · Gol · Sub 12"))
        assertTrue(PromptResumen.construir(partido, emptyList()).contains("No se registraron eventos."))
    }
}
