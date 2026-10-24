package com.example.proyectosportpro.features.academia.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proyectosportpro.features.academia.data.InvitacionApoderadoRepository
import com.example.proyectosportpro.features.academia.domain.InvitacionApoderado
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class InvitacionApoderadoUiState(
    val apoderadoUid: String = "",
    val cargando: Boolean = false,
    val operacionIniciada: Boolean = false,
    val invitacion: InvitacionApoderado? = null,
    val error: String? = null
)

class InvitacionApoderadoViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val academiaId = checkNotNull(savedStateHandle.get<String>("academiaId")) {
        "Falta el identificador de la academia."
    }
    private val jugadorUid = checkNotNull(savedStateHandle.get<String>("jugadorUid")) {
        "Falta el identificador del jugador."
    }
    private val uid = FirebaseAuth.getInstance().currentUser?.uid
    private val repository = InvitacionApoderadoRepository()
    private val claveDestino = "invitacion_destinatario_$uid"
    private val claveCodigo = "invitacion_operacion_$uid"

    private val _uiState = MutableStateFlow(
        InvitacionApoderadoUiState(
            apoderadoUid = savedStateHandle.get<String>(claveDestino).orEmpty(),
            operacionIniciada = savedStateHandle.get<String>(claveCodigo) != null
        )
    )
    val uiState = _uiState.asStateFlow()

    fun cambiarApoderadoUid(valor: String) {
        val estado = _uiState.value
        if (estado.cargando || estado.operacionIniciada) return
        savedStateHandle[claveDestino] = valor
        _uiState.value = estado.copy(apoderadoUid = valor, error = null)
    }

    fun generarORecuperar() {
        val estado = _uiState.value
        if (estado.cargando) return
        val destino = estado.apoderadoUid.trim()
        if (destino.isBlank() || destino.length > 256 || '/' in destino ||
            destino == "." || destino == ".." || destino == jugadorUid) {
            _uiState.value = estado.copy(error = "Pega el identificador de una cuenta de apoderado diferente del jugador.")
            return
        }

        val codigo = savedStateHandle.get<String>(claveCodigo)
            ?: repository.generarCodigo().also { savedStateHandle[claveCodigo] = it }
        savedStateHandle[claveDestino] = destino
        _uiState.value = estado.copy(
            apoderadoUid = destino, cargando = true,
            operacionIniciada = true, error = null
        )

        viewModelScope.launch {
            try {
                comprobarSesion()
                val invitacion = withTimeout(25_000L) {
                    repository.crearInvitacion(codigo, academiaId, jugadorUid, destino)
                }
                comprobarSesion()
                _uiState.value = _uiState.value.copy(cargando = false, invitacion = invitacion)
            } catch (_: TimeoutCancellationException) {
                _uiState.value = _uiState.value.copy(
                    cargando = false,
                    error = "No pudimos confirmar el resultado. Pulsa Recuperar invitación para consultar o completar la misma operación."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(cargando = false, error = mensajeError(e))
            }
        }
    }

    // Requiere confirmación en la pantalla: no cancela ni revoca
    // una invitación que el servidor pudiera haber creado previamente.
    fun prepararOtraInvitacion() {
        if (_uiState.value.cargando) return
        savedStateHandle.remove<String>(claveCodigo)
        savedStateHandle.remove<String>(claveDestino)
        _uiState.value = InvitacionApoderadoUiState()
    }

    private fun comprobarSesion() {
        check(uid != null && FirebaseAuth.getInstance().currentUser?.uid == uid) {
            "La sesión cambió. Vuelve a abrir esta pantalla."
        }
    }

    private fun mensajeError(error: Exception): String {
        val causas = generateSequence<Throwable>(error) { it.cause }.take(10).toList()
        val firestore = causas.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
        return when (firestore?.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "No se autorizó la invitación. Comprueba tu acceso a la academia y que el destinatario sea una cuenta PAD habilitada."
            FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                "Tu sesión no es válida. Vuelve a iniciar sesión."
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                "No pudimos confirmar la operación. Revisa tu conexión y pulsa Recuperar invitación."
            else -> causas.firstOrNull {
                it is IllegalArgumentException || it is IllegalStateException
            }?.message ?: "No pudimos confirmar la invitación. Pulsa Recuperar invitación."
        }
    }
}
