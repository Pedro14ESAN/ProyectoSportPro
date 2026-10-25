package com.example.proyectosportpro.features.partidos.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proyectosportpro.features.auth.domain.RolUsuario
import com.example.proyectosportpro.features.partidos.data.PartidoRepository
import com.example.proyectosportpro.features.partidos.domain.DatosPartido
import com.example.proyectosportpro.features.partidos.domain.EquipoPartido
import com.example.proyectosportpro.features.partidos.domain.EstadoPartido
import com.example.proyectosportpro.features.partidos.domain.Partido
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

enum class FiltroPartidos(val etiqueta: String) {
    PROXIMOS("Próximos"),
    JUGADOS("Jugados"),
    TODOS("Todos")
}

data class PartidosUiState(
    val equipos: List<EquipoPartido> = emptyList(),
    val partidos: List<Partido> = emptyList(),
    val filtro: FiltroPartidos = FiltroPartidos.PROXIMOS,
    val cargando: Boolean = true,
    val guardando: Boolean = false,
    val error: String? = null,
    val mensaje: String? = null
) {
    val equiposGestionables: List<EquipoPartido>
        get() = equipos.filter { it.puedeGestionar }

    val partidosVisibles: List<Partido>
        get() = when (filtro) {
            FiltroPartidos.PROXIMOS -> partidos.filter { it.estado == EstadoPartido.PROGRAMADO }
            FiltroPartidos.JUGADOS -> partidos.filter { it.estado == EstadoPartido.FINALIZADO }
                .sortedByDescending { it.fechaHoraMillis }
            FiltroPartidos.TODOS -> partidos
        }

    fun puedeGestionar(partido: Partido): Boolean = equipos.any {
        it.puedeGestionar && it.academiaId == partido.academiaId && it.equipoId == partido.equipoId
    }
}

class PartidosViewModel : ViewModel() {
    private val repository = PartidoRepository()
    private val usuarioId = FirebaseAuth.getInstance().currentUser?.uid
    private var rol: RolUsuario? = null
    private var consultando = false
    private val _uiState = MutableStateFlow(PartidosUiState())
    val uiState = _uiState.asStateFlow()

    // Identificador reservado para que reintentar una creación no la duplique.
    private var partidoPendienteId: String? = null

    fun iniciar(rolUsuario: RolUsuario) {
        if (rol == rolUsuario) return
        rol = rolUsuario
        actualizar()
    }

    fun seleccionarFiltro(filtro: FiltroPartidos) {
        _uiState.value = _uiState.value.copy(filtro = filtro)
    }

    fun actualizar() {
        val rolActual = rol ?: return
        val estado = _uiState.value
        if (estado.guardando || consultando) return
        consultando = true
        _uiState.value = estado.copy(cargando = true, error = null, mensaje = null)
        viewModelScope.launch {
            try {
                comprobarSesion()
                val (equipos, partidos) = withTimeout(30_000L) {
                    val equipos = repository.obtenerEquipos(rolActual)
                    equipos to repository.obtenerPartidos(rolActual, equipos)
                }
                comprobarSesion()
                _uiState.value = _uiState.value.copy(
                    equipos = equipos, partidos = partidos, cargando = false
                )
            } catch (_: TimeoutCancellationException) {
                mostrarError("La consulta tardó demasiado. Pulsa Actualizar.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mostrarError(mensajeError(e))
            } finally {
                consultando = false
            }
        }
    }

    /** Devuelve un mensaje de validación, o null si los datos se pueden enviar. */
    fun validar(rival: String, lugar: String, fechaHoraMillis: Long?): String? = when {
        rival.trim().length !in 2..60 -> "El rival debe tener entre 2 y 60 caracteres."
        lugar.trim().length !in 2..100 -> "El lugar debe tener entre 2 y 100 caracteres."
        fechaHoraMillis == null -> "Selecciona la fecha y la hora del partido."
        else -> null
    }

    fun programar(equipo: EquipoPartido, datos: DatosPartido) {
        val id = partidoPendienteId ?: repository.nuevoPartidoId(equipo.academiaId)
            .also { partidoPendienteId = it }
        guardar("Partido programado.") {
            repository.programar(id, equipo, limpiar(datos))
            partidoPendienteId = null
        }
    }

    fun editar(partido: Partido, datos: DatosPartido) =
        guardar("Partido actualizado.") { repository.editar(partido, limpiar(datos)) }

    fun registrarResultado(partido: Partido, golesFavor: Int, golesContra: Int) =
        guardar("Resultado registrado.") { repository.registrarResultado(partido, golesFavor, golesContra) }

    fun cancelar(partido: Partido) =
        guardar("Partido cancelado.") { repository.cancelar(partido) }

    private fun limpiar(datos: DatosPartido) = datos.copy(
        rival = datos.rival.trim().replace(Regex("\\s+"), " "),
        lugar = datos.lugar.trim().replace(Regex("\\s+"), " ")
    )

    private fun guardar(mensajeExito: String, operacion: suspend () -> Unit) {
        val rolActual = rol ?: return
        val estado = _uiState.value
        if (estado.cargando || estado.guardando) return
        _uiState.value = estado.copy(guardando = true, error = null, mensaje = null)
        viewModelScope.launch {
            var guardado = false
            try {
                comprobarSesion()
                withTimeout(25_000L) { operacion() }
                guardado = true
                val partidos = withTimeout(30_000L) {
                    repository.obtenerPartidos(rolActual, _uiState.value.equipos)
                }
                comprobarSesion()
                _uiState.value = _uiState.value.copy(
                    partidos = partidos, guardando = false, mensaje = mensajeExito
                )
            } catch (_: TimeoutCancellationException) {
                _uiState.value = _uiState.value.copy(guardando = false, error = if (guardado)
                    "Se guardó, pero no pudimos recargar la lista. Pulsa Actualizar."
                else "No pudimos confirmar el cambio. Pulsa Actualizar antes de reintentar.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(guardando = false, error = mensajeError(e))
            }
        }
    }

    private fun mostrarError(mensaje: String) {
        _uiState.value = _uiState.value.copy(
            equipos = emptyList(), partidos = emptyList(), cargando = false, error = mensaje
        )
    }

    private fun comprobarSesion() {
        check(usuarioId != null && FirebaseAuth.getInstance().currentUser?.uid == usuarioId) {
            "La sesión cambió. Vuelve a abrir Partidos."
        }
    }

    private fun mensajeError(error: Exception): String {
        val causas = generateSequence<Throwable>(error) { it.cause }.take(10).toList()
        val firestore = causas.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
        return when (firestore?.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "No tienes permiso para esta operación. Comprueba tu equipo y que las reglas estén publicadas."
            FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                "Tu sesión no es válida. Vuelve a iniciar sesión."
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                "No pudimos conectar con el servidor. Revisa tu conexión y pulsa Actualizar."
            else -> causas.firstOrNull {
                it is IllegalArgumentException || it is IllegalStateException
            }?.message ?: "No pudimos completar la operación. Pulsa Actualizar."
        }
    }
}
