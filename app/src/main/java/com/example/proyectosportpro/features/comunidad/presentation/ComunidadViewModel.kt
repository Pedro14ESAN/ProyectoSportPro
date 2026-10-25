package com.example.proyectosportpro.features.comunidad.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proyectosportpro.features.auth.domain.RolUsuario
import com.example.proyectosportpro.features.comunidad.data.AvisoRepository
import com.example.proyectosportpro.features.comunidad.domain.Aviso
import com.example.proyectosportpro.features.partidos.data.PartidoRepository
import com.example.proyectosportpro.features.partidos.domain.EquipoPartido
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class ComunidadUiState(
    val equipos: List<EquipoPartido> = emptyList(),
    val avisos: List<Aviso> = emptyList(),
    val cargando: Boolean = true,
    val guardando: Boolean = false,
    val error: String? = null,
    val mensaje: String? = null
) {
    val equiposGestionables: List<EquipoPartido>
        get() = equipos.filter { it.puedeGestionar }

    fun puedeRetirar(aviso: Aviso): Boolean = equipos.any {
        it.puedeGestionar && it.academiaId == aviso.academiaId && it.equipoId == aviso.equipoId
    }
}

class ComunidadViewModel : ViewModel() {
    // Los equipos visibles son los mismos que en Partidos para cada rol.
    private val equiposRepository = PartidoRepository()
    private val repository = AvisoRepository()
    private val usuarioId = FirebaseAuth.getInstance().currentUser?.uid
    private var rol: RolUsuario? = null
    private var consultando = false
    private var avisoPendienteId: String? = null
    private val _uiState = MutableStateFlow(ComunidadUiState())
    val uiState = _uiState.asStateFlow()

    fun iniciar(rolUsuario: RolUsuario) {
        if (rol == rolUsuario) return
        rol = rolUsuario
        actualizar()
    }

    fun actualizar() {
        val rolActual = rol ?: return
        if (_uiState.value.guardando || consultando) return
        consultando = true
        _uiState.value = _uiState.value.copy(cargando = true, error = null, mensaje = null)
        viewModelScope.launch {
            try {
                comprobarSesion()
                val (equipos, avisos) = withTimeout(30_000L) {
                    val equipos = equiposRepository.obtenerEquipos(rolActual)
                    equipos to repository.obtenerAvisos(rolActual, equipos)
                }
                comprobarSesion()
                _uiState.value = _uiState.value.copy(equipos = equipos, avisos = avisos, cargando = false)
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

    /** Devuelve un mensaje de validación, o null si el aviso se puede publicar. */
    fun validar(titulo: String, mensaje: String): String? = when {
        titulo.trim().length !in 2..80 -> "El título debe tener entre 2 y 80 caracteres."
        mensaje.trim().length !in 2..1000 -> "El mensaje debe tener entre 2 y 1000 caracteres."
        else -> null
    }

    fun publicar(equipo: EquipoPartido, titulo: String, mensaje: String) {
        val id = avisoPendienteId ?: repository.nuevoAvisoId(equipo.academiaId)
            .also { avisoPendienteId = it }
        guardar("Aviso publicado.") {
            repository.publicar(id, equipo, titulo.trim(), mensaje.trim())
            avisoPendienteId = null
        }
    }

    fun retirar(aviso: Aviso) = guardar("Aviso retirado.") { repository.retirar(aviso) }

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
                val avisos = withTimeout(30_000L) {
                    repository.obtenerAvisos(rolActual, _uiState.value.equipos)
                }
                comprobarSesion()
                _uiState.value = _uiState.value.copy(avisos = avisos, guardando = false, mensaje = mensajeExito)
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
            equipos = emptyList(), avisos = emptyList(), cargando = false, error = mensaje
        )
    }

    private fun comprobarSesion() {
        check(usuarioId != null && FirebaseAuth.getInstance().currentUser?.uid == usuarioId) {
            "La sesión cambió. Vuelve a abrir Comunidad."
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
