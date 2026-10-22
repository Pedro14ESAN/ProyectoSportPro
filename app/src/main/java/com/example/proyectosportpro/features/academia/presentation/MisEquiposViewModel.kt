package com.example.proyectosportpro.features.academia.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proyectosportpro.features.academia.data.MisEquiposRepository
import com.example.proyectosportpro.features.academia.domain.EquipoAsignado
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class MisEquiposUiState(
    val equipos: List<EquipoAsignado> = emptyList(),
    val cargando: Boolean = false,
    val error: String? = null
)

class MisEquiposViewModel : ViewModel() {
    private val repository = MisEquiposRepository()
    private val _uiState = MutableStateFlow(MisEquiposUiState())
    val uiState = _uiState.asStateFlow()

    fun actualizar() {
        if (_uiState.value.cargando) return
        // No mantiene datos antiguos si cambió el acceso o falla la nueva consulta.
        _uiState.value = MisEquiposUiState(cargando = true)
        viewModelScope.launch {
            try {
                val equipos = withTimeout(30_000) { repository.obtenerMisEquipos() }
                _uiState.value = MisEquiposUiState(equipos = equipos)
            } catch (e: TimeoutCancellationException) {
                _uiState.value = MisEquiposUiState(error = "La consulta tardó demasiado. Pulsa Actualizar.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val firestore = generateSequence<Throwable>(e) { it.cause }.take(10)
                    .filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                val mensaje = when (firestore?.code) {
                    FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                        "No pudimos consultar tus equipos. Comprueba las reglas publicadas " +
                                "y que tu vínculo con la academia siga activo."
                    FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
                        "La consulta necesita un índice de Firestore. Configura miembros → uid " +
                                "como Ascendente con alcance Grupo de colecciones y espera a que esté listo."
                    else -> "No pudimos cargar tus equipos. Revisa tu conexión y pulsa Actualizar."
                }
                _uiState.value = MisEquiposUiState(error = mensaje)
            }
        }
    }
}
