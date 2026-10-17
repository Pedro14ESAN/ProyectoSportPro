package com.example.proyectosportpro.features.academia.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proyectosportpro.features.academia.data.AcademiaRepository
import com.example.proyectosportpro.features.academia.domain.Academia
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class AcademiaUiState(
    val nombre: String = "",
    val academias: List<Academia> = emptyList(),
    val cargando: Boolean = false,
    val pendiente: Boolean = false,
    val error: String? = null,
    val mensaje: String? = null
)

class AcademiaViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val repository = AcademiaRepository()

    private val _uiState = MutableStateFlow(
        AcademiaUiState(
            nombre = savedStateHandle.get<String>("nombre").orEmpty(),
            pendiente = savedStateHandle.get<String>("idPendiente") != null
        )
    )
    val uiState = _uiState.asStateFlow()

    init {
        actualizar()
    }

    fun cambiarNombre(nombre: String) {
        if (_uiState.value.cargando || _uiState.value.pendiente) return

        savedStateHandle["nombre"] = nombre
        _uiState.value = _uiState.value.copy(
            nombre = nombre,
            error = null,
            mensaje = null
        )
    }

    fun actualizar() {
        if (_uiState.value.cargando) return

        _uiState.value = _uiState.value.copy(
            cargando = true,
            error = null,
            mensaje = null
        )

        viewModelScope.launch {
            try {
                val lista = withTimeout(20_000) {
                    repository.obtenerMisAcademias()
                }

                val idPendiente = savedStateHandle.get<String>("idPendiente")
                val confirmado = idPendiente != null &&
                        lista.any { it.id == idPendiente }

                if (confirmado) {
                    confirmarGuardado(lista)
                } else {
                    _uiState.value = _uiState.value.copy(
                        academias = lista,
                        cargando = false
                    )
                }
            } catch (e: TimeoutCancellationException) {
                mostrarError("La consulta tardó demasiado. Revisa tu conexión.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mostrarError(mensajeError(e))
            }
        }
    }

    fun guardar() {
        if (_uiState.value.cargando) return

        val nombre = _uiState.value.nombre.trim()

        if (nombre.length !in 2..100) {
            mostrarError("El nombre debe tener entre 2 y 100 caracteres.")
            return
        }

        // Mantiene el mismo documento al reintentar.
        val id = savedStateHandle.get<String>("idPendiente")
            ?: repository.generarId().also {
                savedStateHandle["idPendiente"] = it
            }

        savedStateHandle["nombre"] = nombre

        _uiState.value = _uiState.value.copy(
            nombre = nombre,
            cargando = true,
            pendiente = true,
            error = null,
            mensaje = null
        )

        viewModelScope.launch {
            try {
                val lista = withTimeout(20_000) {
                    // Primero comprueba si un intento anterior ya se guardó.
                    val existentes = repository.obtenerMisAcademias()

                    if (existentes.none { it.id == id }) {
                        repository.crearAcademia(id, nombre)
                    }

                    repository.obtenerMisAcademias()
                }

                check(lista.any { it.id == id }) {
                    "No se pudo confirmar la academia."
                }

                confirmarGuardado(lista)
            } catch (e: TimeoutCancellationException) {
                mostrarError(
                    "No pudimos confirmar el guardado a tiempo. " +
                            "Pulsa Actualizar o reintenta para comprobarlo."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mostrarError(mensajeError(e))
            }
        }
    }

    private fun confirmarGuardado(lista: List<Academia>) {
        savedStateHandle.remove<String>("idPendiente")
        savedStateHandle["nombre"] = ""

        _uiState.value = AcademiaUiState(
            academias = lista,
            mensaje = "Academia guardada correctamente."
        )
    }

    private fun mostrarError(mensaje: String) {
        _uiState.value = _uiState.value.copy(
            cargando = false,
            error = mensaje,
            mensaje = null
        )
    }

    private fun mensajeError(error: Exception): String {
        return when {
            error is FirebaseFirestoreException &&
                    error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "No tienes permiso para esta operación. Comprueba " +
                        "que la cuenta administradora esté activa y que " +
                        "las reglas estén publicadas."

            error is IllegalArgumentException ->
                error.message ?: "Revisa los datos ingresados."

            else ->
                "No pudimos completar la operación. Revisa tu conexión " +
                        "y pulsa Actualizar antes de volver a intentarlo."
        }
    }
}