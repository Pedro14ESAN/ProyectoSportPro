package com.example.proyectosportpro.features.academia.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proyectosportpro.features.academia.data.SolicitudVinculacionRepository
import com.example.proyectosportpro.features.academia.domain.EstadoSolicitudVinculacion
import com.example.proyectosportpro.features.academia.domain.SolicitudVinculacion
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class SolicitudesAdminUiState(
    val solicitudes: List<SolicitudVinculacion> = emptyList(),
    val cargando: Boolean = false,
    val error: String? = null,
    val mensaje: String? = null
)

class SolicitudesAdminViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val academiaId = checkNotNull(savedStateHandle.get<String>("academiaId"))
    private val repository = SolicitudVinculacionRepository()
    private val _uiState = MutableStateFlow(SolicitudesAdminUiState())
    val uiState = _uiState.asStateFlow()

    init { actualizar() }

    fun actualizar() {
        if (_uiState.value.cargando) return
        _uiState.value = _uiState.value.copy(cargando = true, error = null, mensaje = null)
        viewModelScope.launch {
            try {
                val lista = withTimeout(20_000) { repository.obtenerSolicitudes(academiaId) }
                _uiState.value = SolicitudesAdminUiState(solicitudes = lista)
            } catch (e: TimeoutCancellationException) {
                error("La consulta tardó demasiado. Pulsa Actualizar.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error(mensajeError(e))
            }
        }
    }

    fun resolver(solicitud: SolicitudVinculacion, aprobar: Boolean) {
        if (_uiState.value.cargando) return
        if (solicitud.academiaId != academiaId ||
            solicitud.estado != EstadoSolicitudVinculacion.PENDIENTE) return
        _uiState.value = _uiState.value.copy(cargando = true, error = null, mensaje = null)
        viewModelScope.launch {
            try {
                withTimeout(20_000) {
                    repository.resolverSolicitud(academiaId, solicitud.solicitanteUid, aprobar)
                }
                // Se confirma la escritura antes de actualizar la lista.
                // Si la consulta falla, no se presenta como fallo del guardado.
                val estadoNuevo = if (aprobar) EstadoSolicitudVinculacion.APROBADA
                else EstadoSolicitudVinculacion.RECHAZADA
                _uiState.value = _uiState.value.copy(
                    solicitudes = _uiState.value.solicitudes.map {
                        if (it.id == solicitud.id) it.copy(estado = estadoNuevo) else it
                    },
                    cargando = false,
                    mensaje = if (aprobar) "Solicitud aprobada. El DT quedó vinculado a esta academia."
                    else "Solicitud rechazada."
                )
            } catch (e: TimeoutCancellationException) {
                error("No pudimos confirmar la decisión a tiempo. Pulsa Actualizar antes de reintentar.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error(mensajeError(e))
            }
        }
    }

    private fun error(texto: String) {
        _uiState.value = _uiState.value.copy(cargando = false, error = texto, mensaje = null)
    }

    private fun mensajeError(e: Exception): String {
        val causas = generateSequence<Throwable>(e) { it.cause }.take(10).toList()
        val firestore = causas.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
        if (firestore?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            return "No se permitió la operación. Comprueba que eres el administrador de esta " +
                    "academia, que está activa y que publicaste las reglas actualizadas."
        }
        return causas.filterIsInstance<IllegalArgumentException>().firstOrNull()?.message
            ?: "No pudimos completar la operación. Revisa la conexión y pulsa Actualizar."
    }
}
