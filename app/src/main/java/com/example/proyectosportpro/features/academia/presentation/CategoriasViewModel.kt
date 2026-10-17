package com.example.proyectosportpro.features.academia.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proyectosportpro.features.academia.data.CategoriaRepository
import com.example.proyectosportpro.features.academia.domain.Categoria
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class CategoriasUiState(
    val nombre: String = "",
    val categorias: List<Categoria> = emptyList(),
    val cargando: Boolean = false,
    val pendiente: Boolean = false,
    val error: String? = null,
    val mensaje: String? = null
)

class CategoriasViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val repository = CategoriaRepository()

    private val academiaId: String = checkNotNull(
        savedStateHandle["academiaId"]
    ) {
        "Falta el identificador de la academia."
    }

    private val _uiState = MutableStateFlow(
        CategoriasUiState(
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
                    repository.obtenerCategorias(academiaId)
                }

                val idPendiente = savedStateHandle.get<String>("idPendiente")

                if (idPendiente != null &&
                    lista.any { it.id == idPendiente }
                ) {
                    confirmarGuardado(lista)
                } else {
                    _uiState.value = _uiState.value.copy(
                        categorias = lista,
                        cargando = false
                    )
                }
            } catch (e: TimeoutCancellationException) {
                mostrarError(
                    "La consulta tardó demasiado. Revisa tu conexión."
                )
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

        if (nombre.length !in 2..60) {
            mostrarError(
                "El nombre debe tener entre 2 y 60 caracteres."
            )
            return
        }

        val id = savedStateHandle.get<String>("idPendiente")
            ?: repository.generarId(academiaId).also {
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
                    val existentes = repository.obtenerCategorias(academiaId)

                    // Comprueba si un intento anterior ya se guardó.
                    if (existentes.none { it.id == id }) {
                        repository.crearCategoria(
                            academiaId = academiaId,
                            categoriaId = id,
                            nombre = nombre
                        )
                    }

                    repository.obtenerCategorias(academiaId)
                }

                check(lista.any { it.id == id }) {
                    "No se pudo confirmar la categoría."
                }

                confirmarGuardado(lista)
            } catch (e: TimeoutCancellationException) {
                mostrarError(
                    "No pudimos confirmar el guardado a tiempo. " +
                            "Pulsa Actualizar antes de reintentar."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mostrarError(mensajeError(e))
            }
        }
    }

    private fun confirmarGuardado(lista: List<Categoria>) {
        savedStateHandle.remove<String>("idPendiente")
        savedStateHandle["nombre"] = ""

        _uiState.value = CategoriasUiState(
            categorias = lista,
            mensaje = "Categoría guardada correctamente."
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
                "No tienes permiso para esta operación. " +
                        "Comprueba que administras esta academia, " +
                        "que está activa y que las reglas están publicadas."

            error is IllegalArgumentException ->
                error.message ?: "Revisa los datos ingresados."

            else ->
                "No pudimos completar la operación. " +
                        "Revisa tu conexión y pulsa Actualizar."
        }
    }
}