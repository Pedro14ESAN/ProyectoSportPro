package com.example.proyectosportpro.features.partidos

import com.example.proyectosportpro.features.partidos.domain.CondicionPartido
import com.example.proyectosportpro.features.partidos.domain.EstadoPartido
import com.example.proyectosportpro.features.partidos.domain.FiltroPartidos
import com.example.proyectosportpro.features.partidos.domain.Partido
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FiltroPartidosTest {
    private fun partido(id: String, dia: Long, estado: EstadoPartido, gf: Int? = null, gc: Int? = null) =
        Partido(id, "a1", "e1", "Sub 12", "Rival $id", "Cancha", dia * 86_400_000L,
            CondicionPartido.LOCAL, estado, gf, gc)

    private val partidos = listOf(
        partido("p3", 30, EstadoPartido.PROGRAMADO),
        partido("p1", 10, EstadoPartido.FINALIZADO, 2, 1),
        partido("p4", 20, EstadoPartido.PROGRAMADO),
        partido("p2", 15, EstadoPartido.FINALIZADO, 0, 0),
        partido("p5", 25, EstadoPartido.CANCELADO)
    )

    @Test
    fun proximosMuestraProgramadosEnOrdenCronologico() {
        assertEquals(listOf("p4", "p3"), FiltroPartidos.PROXIMOS.aplicar(partidos).map { it.id })
    }

    @Test
    fun jugadosMuestraFinalizadosDelMasReciente() {
        assertEquals(listOf("p2", "p1"), FiltroPartidos.JUGADOS.aplicar(partidos).map { it.id })
    }

    @Test
    fun todosIncluyeCanceladosEnOrdenCronologico() {
        assertEquals(listOf("p1", "p2", "p4", "p5", "p3"), FiltroPartidos.TODOS.aplicar(partidos).map { it.id })
    }

    @Test
    fun elResultadoSoloExisteConAmbosMarcadores() {
        assertEquals("2 - 1", partidos.first { it.id == "p1" }.resultado)
        assertNull(partidos.first { it.id == "p3" }.resultado)
    }
}
