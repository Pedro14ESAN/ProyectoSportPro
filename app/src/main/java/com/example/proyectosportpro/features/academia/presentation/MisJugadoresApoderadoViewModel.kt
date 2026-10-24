package com.example.proyectosportpro.features.academia.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proyectosportpro.features.academia.data.MisJugadoresApoderadoRepository
import com.example.proyectosportpro.features.academia.domain.JugadorVinculado
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class MisJugadoresApoderadoUiState(
    val jugadores: List<JugadorVinculado> = emptyList(),
    val cargando: Boolean = true,
    val error: String? = null
)

class MisJugadoresApoderadoViewModel : ViewModel() {
    private val repository = MisJugadoresApoderadoRepository()
    private val usuarioId = FirebaseAuth.getInstance().currentUser?.uid
    private var consultando = false
    private val _uiState = MutableStateFlow(MisJugadoresApoderadoUiState())
    val uiState = _uiState.asStateFlow()

    fun actualizar() {
        if (consultando) return
        consultando = true
        // Una consulta fallida no conserva vínculos antiguos en pantalla.
        _uiState.value = MisJugadoresApoderadoUiState(cargando = true)
        viewModelScope.launch {
            try {
                comprobarSesion()
                val jugadores = withTimeout(30_000L) { repository.obtenerMisJugadores() }
                comprobarSesion()
                _uiState.value = MisJugadoresApoderadoUiState(jugadores = jugadores, cargando = false)
            } catch (_: TimeoutCancellationException) {
                _uiState.value = MisJugadoresApoderadoUiState(
                    cargando = false,
                    error = "La consulta tardó demasiado. Pulsa Actualizar."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = MisJugadoresApoderadoUiState(cargando = false, error = mensajeError(e))
            } finally {
                consultando = false
            }
        }
    }

    private fun comprobarSesion() {
        check(usuarioId != null && FirebaseAuth.getInstance().currentUser?.uid == usuarioId) {
            "La sesión cambió. Vuelve a abrir esta pantalla."
        }
    }

    private fun mensajeError(error: Exception): String {
        val causas = generateSequence<Throwable>(error) { it.cause }.take(10).toList()
        val firestore = causas.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
        return when (firestore?.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "No pudimos confirmar tus vínculos. Comprueba que las reglas estén publicadas y pulsa Actualizar."
            FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                "Tu sesión no es válida. Vuelve a iniciar sesión."
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                "No pudimos conectar con el servidor. Revisa tu conexión y pulsa Actualizar."
            else -> causas.firstOrNull {
                it is IllegalArgumentException || it is IllegalStateException
            }?.message ?: "No pudimos cargar tus jugadores. Pulsa Actualizar."
        }
    }
}
