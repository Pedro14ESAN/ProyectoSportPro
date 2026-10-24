package com.example.proyectosportpro.features.academia.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proyectosportpro.features.academia.data.SolicitudesApoderadoAdminRepository
import com.example.proyectosportpro.features.academia.domain.EstadoSolicitudApoderado
import com.example.proyectosportpro.features.academia.domain.SolicitudApoderado
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

data class SolicitudesApoderadoAdminUiState(
    val solicitudes: List<SolicitudApoderado> = emptyList(),
    val filtro: EstadoSolicitudApoderado? = EstadoSolicitudApoderado.PENDIENTE,
    val cargando: Boolean = false,
    val procesandoId: String? = null,
    val requiereActualizar: Boolean = true,
    val error: String? = null,
    val mensaje: String? = null
) {
    val solicitudesVisibles: List<SolicitudApoderado>
        get() = solicitudes.filter { filtro == null || it.estado == filtro }
}

class SolicitudesApoderadoAdminViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {
    private val academiaId = checkNotNull(savedStateHandle.get<String>("academiaId"))
    private val uidSesion = FirebaseAuth.getInstance().currentUser?.uid
    private val repository = SolicitudesApoderadoAdminRepository()
    private val _uiState = MutableStateFlow(SolicitudesApoderadoAdminUiState())
    val uiState = _uiState.asStateFlow()

    init { actualizar() }

    private fun comprobarSesion() {
        check(uidSesion != null && FirebaseAuth.getInstance().currentUser?.uid == uidSesion) {
            "La sesión cambió. Sal de esta pantalla y vuelve a entrar."
        }
    }

    fun seleccionarFiltro(filtro: EstadoSolicitudApoderado?) {
        _uiState.value = _uiState.value.copy(filtro = filtro)
    }

    fun actualizar() {
        if (_uiState.value.cargando) return
        _uiState.value = _uiState.value.copy(cargando = true, error = null, mensaje = null)
        viewModelScope.launch {
            try {
                comprobarSesion()
                val solicitudes = withTimeout(30.seconds) { repository.obtenerSolicitudes(academiaId) }
                comprobarSesion()
                _uiState.value = _uiState.value.copy(
                    solicitudes = solicitudes, cargando = false,
                    procesandoId = null, requiereActualizar = false
                )
            } catch (_: TimeoutCancellationException) {
                mostrarError("La consulta tardó demasiado. Pulsa Actualizar para intentarlo de nuevo.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mostrarError(mensajeError(e))
            }
        }
    }

    // La pantalla debe pedir confirmación y verificación de la autorización antes de llamar a aprobar.
    fun aprobar(solicitud: SolicitudApoderado) = decidir(solicitud, EstadoSolicitudApoderado.APROBADA)
    fun rechazar(solicitud: SolicitudApoderado) = decidir(solicitud, EstadoSolicitudApoderado.RECHAZADA)
    fun revocar(solicitud: SolicitudApoderado) = decidir(solicitud, EstadoSolicitudApoderado.REVOCADA)

    private fun decidir(solicitud: SolicitudApoderado, destino: EstadoSolicitudApoderado) {
        val estado = _uiState.value
        if (estado.cargando || estado.requiereActualizar) return
        val actual = estado.solicitudes.firstOrNull { it.id == solicitud.id } ?: return
        val origen = if (destino == EstadoSolicitudApoderado.REVOCADA)
            EstadoSolicitudApoderado.APROBADA else EstadoSolicitudApoderado.PENDIENTE
        if (actual.academiaId != academiaId || actual.estado != origen || actual != solicitud) return

        _uiState.value = estado.copy(
            cargando = true, procesandoId = actual.id, error = null, mensaje = null
        )
        viewModelScope.launch {
            var escrituraConfirmada = false
            try {
                comprobarSesion()
                withTimeout(25.seconds) {
                    when (destino) {
                        EstadoSolicitudApoderado.APROBADA -> repository.aprobar(academiaId, actual.id)
                        EstadoSolicitudApoderado.RECHAZADA -> repository.rechazar(academiaId, actual.id)
                        EstadoSolicitudApoderado.REVOCADA -> repository.revocar(academiaId, actual.id)
                        EstadoSolicitudApoderado.PENDIENTE -> error("Decisión no válida.")
                    }
                }
                escrituraConfirmada = true
                comprobarSesion()
                val lista = withTimeout(30.seconds) { repository.obtenerSolicitudes(academiaId) }
                comprobarSesion()
                _uiState.value = _uiState.value.copy(
                    solicitudes = lista, cargando = false, procesandoId = null,
                    requiereActualizar = false,
                    mensaje = when (destino) {
                        EstadoSolicitudApoderado.APROBADA -> "Vínculo aprobado."
                        EstadoSolicitudApoderado.RECHAZADA -> "Solicitud rechazada."
                        EstadoSolicitudApoderado.REVOCADA -> "Vínculo revocado."
                        else -> null
                    }
                )
            } catch (_: TimeoutCancellationException) {
                mostrarError(if (escrituraConfirmada)
                    "La decisión se guardó, pero no pudimos recargar la lista. Pulsa Actualizar."
                else "No pudimos confirmar el resultado. Pulsa Actualizar antes de tomar otra decisión.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mostrarError(if (escrituraConfirmada)
                    "La decisión se guardó, pero falta recargar la lista. ${mensajeError(e)}"
                else "${mensajeError(e)} Actualiza para comprobar el estado antes de volver a decidir.")
            }
        }
    }

    private fun mostrarError(mensaje: String) {
        // No dejar botones activos sobre estados que podrían haber cambiado en el servidor.
        _uiState.value = _uiState.value.copy(
            solicitudes = emptyList(), cargando = false, procesandoId = null,
            requiereActualizar = true, error = mensaje, mensaje = null
        )
    }

    private fun mensajeError(error: Exception): String {
        val firestore = generateSequence<Throwable>(error) { it.cause }
            .take(10).filterIsInstance<FirebaseFirestoreException>().firstOrNull()
        return when (firestore?.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "No tienes permiso para esta operación. Comprueba la cuenta administradora y las reglas publicadas."
            FirebaseFirestoreException.Code.UNAVAILABLE ->
                "No se pudo conectar con el servidor. Comprueba tu conexión."
            FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
                "No se pudo completar la operación. Revisa la configuración de Firestore y vuelve a consultar."
            FirebaseFirestoreException.Code.ABORTED ->
                "Los datos cambiaron durante la operación."
            else -> if (error is IllegalArgumentException || error is IllegalStateException)
                error.message ?: "No se pudo completar la operación."
            else "No se pudo completar la operación. Inténtalo nuevamente."
        }
    }
}
