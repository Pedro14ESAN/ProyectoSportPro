package com.example.proyectosportpro.features.academia.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proyectosportpro.features.academia.data.MisEquiposJugadorRepository
import com.example.proyectosportpro.features.academia.domain.EquipoAsignado
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class MisEquiposJugadorUiState(
    val equipos: List<EquipoAsignado> = emptyList(),
    val cargando: Boolean = true,
    val error: String? = null
)

class MisEquiposJugadorViewModel : ViewModel() {
    private val repository = MisEquiposJugadorRepository()
    private val usuarioId = FirebaseAuth.getInstance().currentUser?.uid
    private var consultando = false
    private val _uiState = MutableStateFlow(MisEquiposJugadorUiState())
    val uiState = _uiState.asStateFlow()

    fun actualizar() {
        if (consultando) return
        consultando = true
        // Una consulta fallida no conserva asignaciones antiguas en pantalla.
        _uiState.value = MisEquiposJugadorUiState(cargando = true)
        viewModelScope.launch {
            try {
                comprobarSesion()
                val equipos = withTimeout(30_000L) { repository.obtenerMisEquipos() }
                comprobarSesion()
                _uiState.value = MisEquiposJugadorUiState(equipos = equipos, cargando = false)
            } catch (_: TimeoutCancellationException) {
                _uiState.value = MisEquiposJugadorUiState(
                    cargando = false,
                    error = "La consulta tardó demasiado. Pulsa Actualizar."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = MisEquiposJugadorUiState(
                    cargando = false,
                    error = mensajeError(e)
                )
            } finally {
                consultando = false
            }
        }
    }

    private fun comprobarSesion() {
        check(usuarioId != null && FirebaseAuth.getInstance().currentUser?.uid == usuarioId) {
            "La sesión cambió. Vuelve a abrir tus equipos."
        }
    }

    private fun mensajeError(error: Exception): String {
        val causas = generateSequence<Throwable>(error) { it.cause }.take(10).toList()
        val firestore = causas.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
        return when (firestore?.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "No pudimos confirmar el acceso a tus equipos. Actualiza; si el problema continúa, consulta al administrador."
            FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                "Tu sesión no es válida. Vuelve a iniciar sesión."
            FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
                "La consulta de equipos aún no está disponible. Intenta nuevamente más tarde."
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                "No pudimos conectar con el servidor. Revisa tu conexión y pulsa Actualizar."
            else -> causas.firstOrNull {
                it is IllegalArgumentException || it is IllegalStateException
            }?.message ?: "No pudimos cargar tus equipos. Pulsa Actualizar."
        }
    }
}
