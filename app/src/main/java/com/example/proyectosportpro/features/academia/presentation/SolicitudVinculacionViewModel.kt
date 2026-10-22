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

data class SolicitudVinculacionUiState(
    val academiaId: String = "",
    val solicitud: SolicitudVinculacion? = null,
    val cargando: Boolean = false,
    val error: String? = null,
    val mensaje: String? = null
)

class SolicitudVinculacionViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val repository = SolicitudVinculacionRepository()

    private val _uiState = MutableStateFlow(
        SolicitudVinculacionUiState(
            academiaId = savedStateHandle.get<String>("academiaIdSolicitud")
                .orEmpty()
        )
    )

    val uiState = _uiState.asStateFlow()

    fun cambiarAcademiaId(valor: String) {
        if (_uiState.value.cargando) return

        savedStateHandle["academiaIdSolicitud"] = valor

        _uiState.value = SolicitudVinculacionUiState(
            academiaId = valor
        )
    }

    fun enviarSolicitud() {
        ejecutar(enviar = true)
    }

    fun actualizar() {
        ejecutar(enviar = false)
    }

    private fun ejecutar(enviar: Boolean) {
        if (_uiState.value.cargando) return

        val academiaId = _uiState.value.academiaId.trim()

        if (!academiaId.matches(Regex("^[A-Za-z0-9_-]{1,128}$"))) {
            _uiState.value = _uiState.value.copy(
                error = "Ingresa el código de academia que te dio el administrador.",
                mensaje = null
            )
            return
        }

        savedStateHandle["academiaIdSolicitud"] = academiaId

        _uiState.value = _uiState.value.copy(
            academiaId = academiaId,
            cargando = true,
            error = null,
            mensaje = null
        )

        viewModelScope.launch {
            try {
                val solicitud = withTimeout(20_000) {
                    if (enviar) {
                        repository.enviarSolicitud(academiaId)
                    }

                    repository.obtenerMiSolicitud(academiaId)
                }

                val mensaje = when (solicitud?.estado) {
                    EstadoSolicitudVinculacion.PENDIENTE ->
                        "Tu solicitud está pendiente de revisión."

                    EstadoSolicitudVinculacion.APROBADA ->
                        "Tu solicitud fue aprobada."

                    EstadoSolicitudVinculacion.RECHAZADA ->
                        "Tu solicitud fue rechazada. Contacta al administrador."

                    null -> if (enviar) {
                        "No pudimos confirmar el envío. Pulsa Actualizar."
                    } else {
                        "No tienes una solicitud registrada con este código."
                    }
                }

                _uiState.value = _uiState.value.copy(
                    solicitud = solicitud,
                    cargando = false,
                    mensaje = mensaje,
                    error = null
                )
            } catch (e: TimeoutCancellationException) {
                mostrarError(
                    if (enviar) {
                        "No pudimos confirmar el envío a tiempo. " +
                                "Pulsa Actualizar antes de reintentar."
                    } else {
                        "La consulta tardó demasiado. Revisa tu conexión."
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mostrarError(mensajeError(e))
            }
        }
    }

    private fun mostrarError(mensaje: String) {
        _uiState.value = _uiState.value.copy(
            cargando = false,
            error = mensaje,
            mensaje = null
        )
    }

    private fun mensajeError(error: Exception): String {
        // Las transacciones pueden envolver el error original.
        val causas = generateSequence<Throwable>(error) { it.cause }
            .take(10)
            .toList()

        val errorFirestore = causas
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

        return when (errorFirestore?.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "No se permitió la operación. Comprueba el código de academia, " +
                        "el estado de tu cuenta y las reglas de Firestore."

            FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                "Tu sesión no es válida. Vuelve a iniciar sesión."

            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                "No pudimos confirmar la operación. Revisa tu conexión " +
                        "y pulsa Actualizar antes de reintentar."

            else -> {
                val validacion = causas
                    .filterIsInstance<IllegalArgumentException>()
                    .firstOrNull()

                validacion?.message
                    ?: "No pudimos completar la operación. " +
                    "Pulsa Actualizar antes de reintentar."
            }
        }
    }
}