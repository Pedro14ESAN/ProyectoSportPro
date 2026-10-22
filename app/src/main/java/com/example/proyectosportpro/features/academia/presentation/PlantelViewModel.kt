package com.example.proyectosportpro.features.academia.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proyectosportpro.features.academia.data.PlantelRepository
import com.example.proyectosportpro.features.academia.domain.JugadorDisponible
import com.example.proyectosportpro.features.academia.domain.JugadorPlantel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class PlantelUiState(
    val jugadores: List<JugadorPlantel> = emptyList(),
    val disponibles: List<JugadorDisponible> = emptyList(),
    val cargando: Boolean = false,
    val guardando: Boolean = false,
    val cargaCompletada: Boolean = false,
    val necesitaActualizar: Boolean = false,
    val error: String? = null,
    val mensaje: String? = null
)

class PlantelViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {

    private val academiaId = checkNotNull(savedStateHandle.get<String>("academiaId")) {
        "Falta el identificador de la academia."
    }
    private val equipoId = checkNotNull(savedStateHandle.get<String>("equipoId")) {
        "Falta el identificador del equipo."
    }
    private val usuarioId = FirebaseAuth.getInstance().currentUser?.uid
    private val repository = PlantelRepository()
    private var iniciado = false
    private var gestionar = false

    private val _uiState = MutableStateFlow(PlantelUiState())
    val uiState = _uiState.asStateFlow()

    // Este indicador controla la interfaz. La autorización efectiva
    // corresponde al repositorio y a las reglas de Firestore.
    fun iniciar(puedeGestionar: Boolean) {
        if (iniciado) return
        iniciado = true
        gestionar = puedeGestionar
        actualizar()
    }

    fun actualizar() {
        if (!iniciado || _uiState.value.cargando || _uiState.value.guardando) return

        // No se mantiene en pantalla una lista cuya autorización pudo cambiar.
        _uiState.value = PlantelUiState(cargando = true)
        viewModelScope.launch {
            try {
                val datos = withTimeout(30_000L) { consultarDatos() }
                comprobarSesion()
                _uiState.value = datos
            } catch (_: TimeoutCancellationException) {
                _uiState.value = PlantelUiState(
                    necesitaActualizar = true,
                    error = "La consulta tardó demasiado. Pulsa Actualizar."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = PlantelUiState(
                    necesitaActualizar = true,
                    error = mensajeError(e)
                )
            }
        }
    }

    fun agregar(jugadorUid: String) {
        modificar(jugadorUid, agregar = true)
    }

    fun retirar(jugadorUid: String) {
        modificar(jugadorUid, agregar = false)
    }

    private fun modificar(jugadorUid: String, agregar: Boolean) {
        val estado = _uiState.value
        if (estado.cargando || estado.guardando) return

        if (!gestionar) {
            _uiState.value = estado.copy(
                error = "Solo el administrador puede modificar el plantel.",
                mensaje = null
            )
            return
        }
        if (!estado.cargaCompletada || estado.necesitaActualizar) {
            _uiState.value = estado.copy(
                error = "Actualiza el plantel antes de realizar otro cambio.",
                mensaje = null
            )
            return
        }

        val seleccionValida = if (agregar) {
            estado.disponibles.any { it.uid == jugadorUid }
        } else {
            estado.jugadores.any { it.jugadorUid == jugadorUid && it.activo }
        }
        if (!seleccionValida) {
            _uiState.value = estado.copy(
                error = "La selección ya no está disponible. Actualiza el plantel.",
                mensaje = null,
                necesitaActualizar = true
            )
            return
        }

        _uiState.value = estado.copy(guardando = true, error = null, mensaje = null)
        viewModelScope.launch {
            var escrituraConfirmada = false
            try {
                comprobarSesion()
                withTimeout(20_000L) {
                    if (agregar) {
                        repository.agregarJugador(academiaId, equipoId, jugadorUid)
                    } else {
                        repository.retirarJugador(academiaId, equipoId, jugadorUid)
                    }
                }
                escrituraConfirmada = true

                val datos = withTimeout(30_000L) { consultarDatos() }
                comprobarSesion()
                _uiState.value = datos.copy(
                    mensaje = if (agregar) "Jugador incorporado al plantel."
                    else "Jugador retirado del plantel activo. Su registro se conserva."
                )
            } catch (_: TimeoutCancellationException) {
                _uiState.value = PlantelUiState(
                    necesitaActualizar = true,
                    error = if (escrituraConfirmada) {
                        "El cambio se guardó, pero no pudimos cargar el plantel. Pulsa Actualizar."
                    } else {
                        "No pudimos confirmar el resultado del cambio. Pulsa Actualizar antes de reintentar."
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = PlantelUiState(
                    necesitaActualizar = true,
                    error = if (escrituraConfirmada) {
                        "El cambio se guardó, pero falló la consulta posterior. Pulsa Actualizar."
                    } else {
                        mensajeError(e) + " Actualiza antes de reintentar."
                    }
                )
            }
        }
    }

    private suspend fun consultarDatos(): PlantelUiState {
        comprobarSesion()
        val jugadores = repository.obtenerPlantel(academiaId, equipoId)
        val disponibles = if (gestionar) {
            repository.obtenerJugadoresDisponibles(academiaId, equipoId)
        } else {
            emptyList()
        }
        comprobarSesion()
        return PlantelUiState(
            jugadores = jugadores,
            disponibles = disponibles,
            cargaCompletada = true
        )
    }

    private fun comprobarSesion() {
        check(usuarioId != null && FirebaseAuth.getInstance().currentUser?.uid == usuarioId) {
            "La sesión cambió. Vuelve a abrir el plantel."
        }
    }

    private fun mensajeError(error: Exception): String {
        val causas = generateSequence<Throwable>(error) { it.cause }.take(10).toList()
        val firestoreError = causas.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
        return when (firestoreError?.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "No tienes permiso para consultar o modificar este plantel."
            FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                "Tu sesión no es válida. Vuelve a iniciar sesión."
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                "No pudimos confirmar la operación con el servidor."
            else -> causas.firstOrNull {
                it is IllegalArgumentException || it is IllegalStateException
            }?.message ?: "No pudimos completar la operación."
        }
    }
}
