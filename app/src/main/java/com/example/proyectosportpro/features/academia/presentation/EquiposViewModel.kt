package com.example.proyectosportpro.features.academia.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proyectosportpro.features.academia.data.CategoriaRepository
import com.example.proyectosportpro.features.academia.data.EquipoRepository
import com.example.proyectosportpro.features.academia.domain.Categoria
import com.example.proyectosportpro.features.academia.domain.Equipo
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

enum class FiltroEquipos(val etiqueta: String) {
    ACTIVOS("Activos"),
    TODOS("Todos"),
    INACTIVOS("Inactivos")
}

data class EquiposUiState(
    val equipos: List<Equipo> = emptyList(),
    val categorias: List<Categoria> = emptyList(),
    val filtroEstado: FiltroEquipos = FiltroEquipos.ACTIVOS,
    val filtroCategoriaId: String? = null,
    val cargando: Boolean = false,
    val guardando: Boolean = false,
    val formularioAbierto: Boolean = false,
    val nombre: String = "",
    val categoriaSeleccionadaId: String? = null,
    val solicitudPendiente: Boolean = false,
    val error: String? = null,
    val mensaje: String? = null
) {
    val equiposVisibles: List<Equipo>
        get() = equipos.filter { equipo ->
            val coincideEstado = when (filtroEstado) {
                FiltroEquipos.ACTIVOS -> equipo.activo
                FiltroEquipos.TODOS -> true
                FiltroEquipos.INACTIVOS -> !equipo.activo
            }

            val coincideCategoria =
                filtroCategoriaId == null ||
                        equipo.categoriaId == filtroCategoriaId

            coincideEstado && coincideCategoria
        }
}

class EquiposViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val equipoRepository = EquipoRepository()
    private val categoriaRepository = CategoriaRepository()

    private val academiaId: String = checkNotNull(
        savedStateHandle.get<String>("academiaId")
    ) {
        "Falta el identificador de la academia."
    }

    private val _uiState = MutableStateFlow(
        EquiposUiState(
            nombre = savedStateHandle.get<String>("nombre").orEmpty(),
            categoriaSeleccionadaId =
                savedStateHandle.get<String>("categoriaSeleccionadaId"),
            solicitudPendiente =
                savedStateHandle.get<String>("solicitudId") != null,
            formularioAbierto =
                savedStateHandle.get<Boolean>("formularioAbierto") == true ||
                        savedStateHandle.get<String>("solicitudId") != null
        )
    )

    val uiState = _uiState.asStateFlow()

    init {
        actualizar()
    }

    fun actualizar() {
        if (_uiState.value.cargando || _uiState.value.guardando) return

        _uiState.value = _uiState.value.copy(
            cargando = true,
            error = null,
            mensaje = null
        )

        viewModelScope.launch {
            try {
                val resultado = withTimeout(20_000) {
                    val categorias =
                        categoriaRepository.obtenerCategorias(academiaId)
                    val equipos =
                        equipoRepository.obtenerEquipos(academiaId)

                    categorias to equipos
                }

                _uiState.value = _uiState.value.copy(
                    categorias = resultado.first,
                    equipos = resultado.second,
                    cargando = false
                )
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

    fun cambiarFiltroEstado(filtro: FiltroEquipos) {
        _uiState.value = _uiState.value.copy(filtroEstado = filtro)
    }

    fun cambiarFiltroCategoria(categoriaId: String?) {
        _uiState.value = _uiState.value.copy(
            filtroCategoriaId = categoriaId
        )
    }

    fun abrirFormulario() {
        if (_uiState.value.cargando || _uiState.value.guardando) return

        savedStateHandle["formularioAbierto"] = true

        _uiState.value = _uiState.value.copy(
            formularioAbierto = true,
            error = null,
            mensaje = null
        )
    }

    fun cerrarFormulario() {
        if (_uiState.value.guardando ||
            _uiState.value.solicitudPendiente
        ) {
            return
        }

        savedStateHandle["formularioAbierto"] = false
        _uiState.value = _uiState.value.copy(
            formularioAbierto = false,
            error = null
        )
    }

    fun cambiarNombre(nombre: String) {
        if (_uiState.value.guardando ||
            _uiState.value.solicitudPendiente
        ) {
            return
        }

        savedStateHandle["nombre"] = nombre

        _uiState.value = _uiState.value.copy(
            nombre = nombre,
            error = null
        )
    }

    fun seleccionarCategoria(categoriaId: String) {
        if (_uiState.value.guardando ||
            _uiState.value.solicitudPendiente
        ) {
            return
        }

        if (_uiState.value.categorias.none {
                it.id == categoriaId && it.activa
            }
        ) {
            return
        }

        savedStateHandle["categoriaSeleccionadaId"] = categoriaId

        _uiState.value = _uiState.value.copy(
            categoriaSeleccionadaId = categoriaId,
            error = null
        )
    }

    fun guardar() {
        val actual = _uiState.value

        if (actual.guardando || actual.cargando) return

        val nombre = actual.nombre.trim().replace(Regex(" +"), " ")
        val categoriaId = actual.categoriaSeleccionadaId

        val error = when {
            nombre.length !in 2..60 ->
                "El nombre debe tener entre 2 y 60 caracteres."

            !nombre.matches(
                Regex("^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ0-9 .'-]+$")
            ) ->
                "Usa letras, números, espacios, puntos, apóstrofos o guiones."

            categoriaId == null ->
                "Selecciona una categoría."

            !actual.solicitudPendiente &&
                    actual.categorias.none {
                        it.id == categoriaId && it.activa
                    } ->
                "Selecciona una categoría activa."

            else -> null
        }

        if (error != null) {
            mostrarError(error)
            return
        }

        val categoriaValidada = categoriaId ?: return

        val solicitudId = savedStateHandle.get<String>("solicitudId")
            ?: equipoRepository.nuevaSolicitudId().also {
                savedStateHandle["solicitudId"] = it
            }

        savedStateHandle["nombre"] = nombre
        savedStateHandle["formularioAbierto"] = true

        _uiState.value = actual.copy(
            nombre = nombre,
            guardando = true,
            formularioAbierto = true,
            solicitudPendiente = true,
            error = null,
            mensaje = null
        )

        viewModelScope.launch {
            try {
                withTimeout(20_000) {
                    equipoRepository.crearEquipo(
                        academiaId = academiaId,
                        categoriaId = categoriaValidada,
                        nombre = nombre,
                        solicitudId = solicitudId
                    )
                }

                limpiarSolicitud()

                _uiState.value = _uiState.value.copy(
                    guardando = false,
                    solicitudPendiente = false,
                    formularioAbierto = false,
                    nombre = "",
                    categoriaSeleccionadaId = null,
                    filtroEstado = FiltroEquipos.ACTIVOS,
                    filtroCategoriaId = null,
                    mensaje = "Equipo guardado correctamente."
                )

                // El guardado ya está confirmado. Si falla esta consulta,
                // se informa por separado sin repetir la creación.
                try {
                    val equipos = withTimeout(20_000) {
                        equipoRepository.obtenerEquipos(academiaId)
                    }

                    _uiState.value = _uiState.value.copy(
                        equipos = equipos
                    )
                } catch (e: TimeoutCancellationException) {
                    mostrarError(
                        "El equipo se guardó, pero falta actualizar la lista. " +
                                "Pulsa Actualizar."
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    mostrarError(
                        "El equipo se guardó, pero no pudimos cargar la lista. " +
                                "Pulsa Actualizar."
                    )
                }
            } catch (e: TimeoutCancellationException) {
                mostrarError(
                    "No pudimos confirmar el resultado. Reintenta " +
                            "con los mismos datos para comprobar el guardado."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val causas = generateSequence<Throwable>(e) { it.cause }
                    .take(10)
                    .toList()

                val validacion = causas
                    .filterIsInstance<IllegalArgumentException>()
                    .firstOrNull()

                val sinPermiso = causas
                    .filterIsInstance<FirebaseFirestoreException>()
                    .any {
                        it.code ==
                                FirebaseFirestoreException.Code.PERMISSION_DENIED
                    }

                // Una validación rechazada o un permiso denegado
                // permite corregir los datos de la solicitud.
                if (validacion != null || sinPermiso) {
                    savedStateHandle.remove<String>("solicitudId")
                    _uiState.value = _uiState.value.copy(
                        solicitudPendiente = false
                    )
                }

                mostrarError(
                    validacion?.message ?: mensajeError(e)
                )
            }
        }
    }

    fun cambiarEstadoEquipo(equipoId: String, activo: Boolean) {
        val actual = _uiState.value

        if (
            actual.guardando ||
            actual.cargando ||
            actual.solicitudPendiente ||
            actual.formularioAbierto
        ) return

        if (actual.equipos.none { it.id == equipoId }) return

        _uiState.value = actual.copy(
            guardando = true,
            error = null,
            mensaje = null
        )

        viewModelScope.launch {
            try {
                withTimeout(20_000) {
                    equipoRepository.cambiarEstadoEquipo(
                        academiaId = academiaId,
                        equipoId = equipoId,
                        activo = activo
                    )
                }

                _uiState.value = _uiState.value.copy(
                    guardando = false,
                    equipos = _uiState.value.equipos.map {
                        if (it.id == equipoId) it.copy(activo = activo)
                        else it
                    },
                    mensaje = if (activo) {
                        "Equipo reactivado correctamente."
                    } else {
                        "Equipo desactivado. Se conserva su historial."
                    }
                )
            } catch (e: TimeoutCancellationException) {
                mostrarError(
                    "No pudimos confirmar el cambio. " +
                            "Pulsa Actualizar para comprobar el estado."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mostrarError(mensajeError(e))
            }
        }
    }

    private fun limpiarSolicitud() {
        savedStateHandle.remove<String>("solicitudId")
        savedStateHandle.remove<String>("categoriaSeleccionadaId")
        savedStateHandle["nombre"] = ""
        savedStateHandle["formularioAbierto"] = false
    }

    private fun mostrarError(mensaje: String) {
        _uiState.value = _uiState.value.copy(
            cargando = false,
            guardando = false,
            error = mensaje
        )
    }

    private fun mensajeError(error: Exception): String {
        val sinPermiso = generateSequence<Throwable>(error) { it.cause }
            .take(10)
            .filterIsInstance<FirebaseFirestoreException>()
            .any {
                it.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
            }

        return if (sinPermiso) {
            "No tienes permiso para esta operación. Comprueba las reglas, " +
                    "tu acceso y el estado de la academia y la categoría."
        } else {
            "No pudimos completar la operación. Revisa tu conexión " +
                    "y vuelve a intentarlo."
        }
    }
}