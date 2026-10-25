package com.example.proyectosportpro.features.partidos

import com.example.proyectosportpro.features.partidos.domain.Cronologia
import com.example.proyectosportpro.features.partidos.domain.DatosEvento
import com.example.proyectosportpro.features.partidos.domain.EventoPartido
import com.example.proyectosportpro.features.partidos.domain.JugadorEvento
import com.example.proyectosportpro.features.partidos.domain.LadoEvento
import com.example.proyectosportpro.features.partidos.domain.Marcador
import com.example.proyectosportpro.features.partidos.domain.TipoEvento
import org.junit.Assert.assertEquals
import org.junit.Test

class CronologiaTest {
    private val ana = JugadorEvento("jug1", "Ana Pérez")

    private fun evento(
        id: String,
        minuto: Int?,
        tipo: TipoEvento = TipoEvento.GOL,
        lado: LadoEvento = LadoEvento.PROPIO,
        jugador: JugadorEvento? = ana,
        anulado: Boolean = false,
        creado: Long? = 0
    ) = EventoPartido(id, DatosEvento(tipo, lado, minuto, jugador, ""), anulado,
        if (anulado) "Fuera de juego" else null, 1, creado, pendiente = false)

    @Test
    fun ordenaPorMinutoYDejaAlFinalLosEventosSinMinuto() {
        val eventos = listOf(evento("c", null, creado = 5), evento("b", 30), evento("a", 5), evento("d", null, creado = 1))
        assertEquals(listOf("a", "b", "d", "c"), Cronologia.ordenar(eventos).map { it.id })
    }

    @Test
    fun elMarcadorCuentaGolesDeCadaLadoSinLosAnulados() {
        val eventos = listOf(
            evento("g1", 10),
            evento("g2", 20, lado = LadoEvento.RIVAL, jugador = null),
            evento("g3", 30, anulado = true),
            evento("t1", 40, tipo = TipoEvento.TARJETA_AMARILLA)
        )
        assertEquals(Marcador(1, 1), Cronologia.marcador(eventos))
    }

    @Test
    fun unEventoPropioSinMinutoNiJugadorQuedaIncompleto() {
        val incompleto = evento("x", null, jugador = null)
        assertEquals(listOf("minuto", "jugador"), incompleto.faltantes)
        assertEquals(emptyList<String>(), evento("r", 3, lado = LadoEvento.RIVAL, jugador = null).faltantes)
    }

    @Test
    fun losEventosAnuladosNoCuentanComoIncompletos() {
        val eventos = listOf(evento("x", null, jugador = null), evento("y", null, jugador = null, anulado = true))
        assertEquals(1, Cronologia.incompletos(eventos))
    }
}
