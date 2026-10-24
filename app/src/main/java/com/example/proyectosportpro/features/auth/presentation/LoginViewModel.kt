package com.example.proyectosportpro.features.auth.presentation

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proyectosportpro.features.auth.data.AuthRepository
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RecuperacionUiState(
    val visible: Boolean = false,
    val correo: String = "",
    val enviando: Boolean = false,
    val solicitudCompletada: Boolean = false,
    val error: String? = null
)

class LoginViewModel : ViewModel() {

    private val repository = AuthRepository()

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState = _uiState.asStateFlow()

    private val _recuperacion = MutableStateFlow(RecuperacionUiState())
    val recuperacion = _recuperacion.asStateFlow()

    private var operacion: Job? = null

    init {
        revisarSesion()
    }

    fun revisarSesion() {
        if (_uiState.value.cargando) return

        if (repository.obtenerUidActual() == null) {
            _uiState.value = LoginUiState(sesionRevisada = true)
            return
        }

        _uiState.value = LoginUiState(cargando = true)

        operacion = viewModelScope.launch {
            try {
                cargarPerfil()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mostrarError(e)
            }
        }
    }

    fun iniciarSesion(correo: String, contrasena: String) {
        if (_uiState.value.cargando || _recuperacion.value.visible) return

        // Resuelve primero cualquier sesión que siga abierta.
        if (repository.obtenerUidActual() != null) {
            revisarSesion()
            return
        }

        val correoLimpio = correo.trim()

        val errorValidacion = when {
            correoLimpio.isBlank() || contrasena.isBlank() ->
                "Ingresa tu correo y contraseña."

            !Patterns.EMAIL_ADDRESS.matcher(correoLimpio).matches() ->
                "Ingresa un correo electrónico válido."

            else -> null
        }

        if (errorValidacion != null) {
            _uiState.value = LoginUiState(
                sesionRevisada = true,
                error = errorValidacion
            )
            return
        }

        _uiState.value = LoginUiState(cargando = true)

        operacion = viewModelScope.launch {
            try {
                repository.iniciarSesion(correoLimpio, contrasena)
                cargarPerfil()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mostrarError(e)
            }
        }
    }

    private suspend fun cargarPerfil() {
        var perfil = repository.obtenerPerfilActual()

        // Activar la cuenta es una mejora: si falla, se mantiene el estado pendiente.
        if (perfil != null && perfil.estado == "PENDIENTE_VINCULACION") {
            try {
                if (repository.activarCuentaSiCorresponde(perfil) != null) {
                    perfil = perfil.copy(estado = "ACTIVO")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }

        if (perfil == null) {
            _uiState.value = LoginUiState(
                sesionRevisada = true,
                requiereCrearPerfil = true
            )
            return
        }

        val estadosPermitidos = setOf(
            "ACTIVO",
            "PENDIENTE_VINCULACION"
        )

        if (perfil.estado !in estadosPermitidos) {
            repository.cerrarSesion()
            _uiState.value = LoginUiState(
                sesionRevisada = true,
                error = "Esta cuenta no está habilitada para acceder. " +
                        "Comunícate con la academia."
            )
            return
        }

        if (!perfil.aceptoTerminos) {
            repository.cerrarSesion()
            _uiState.value = LoginUiState(
                sesionRevisada = true,
                error = "La cuenta requiere revisar la aceptación " +
                        "de términos antes de continuar."
            )
            return
        }

        _uiState.value = LoginUiState(
            sesionRevisada = true,
            perfil = perfil,
            requiereFechaNacimiento = perfil.fechaNacimiento.isBlank()
        )
    }

    fun cerrarSesion() {
        operacion?.cancel()
        operacion = null
        repository.cerrarSesion()
        _uiState.value = LoginUiState(sesionRevisada = true)
    }

    fun abrirRecuperacion(correo: String) {
        if (_uiState.value.cargando || _recuperacion.value.enviando) return

        _recuperacion.value = RecuperacionUiState(
            visible = true,
            correo = correo.trim()
        )
    }

    fun cambiarCorreoRecuperacion(correo: String) {
        val actual = _recuperacion.value
        if (actual.enviando || actual.solicitudCompletada) return

        _recuperacion.value = actual.copy(
            correo = correo,
            error = null
        )
    }

    fun cerrarRecuperacion() {
        if (_recuperacion.value.enviando) return
        _recuperacion.value = RecuperacionUiState()
    }

    fun enviarRecuperacion() {
        val actual = _recuperacion.value

        if (!actual.visible || actual.enviando ||
            actual.solicitudCompletada || _uiState.value.cargando
        ) {
            return
        }

        val correo = actual.correo.trim()

        if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            _recuperacion.value = actual.copy(
                error = "Ingresa un correo electrónico válido."
            )
            return
        }

        _recuperacion.value = actual.copy(
            correo = correo,
            enviando = true,
            error = null
        )

        viewModelScope.launch {
            try {
                repository.enviarRecuperacionContrasena(correo) // Envía el enlace
                completarSolicitudRecuperacion()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Mantiene la misma respuesta si el correo no existe.
                if (e is FirebaseAuthException &&
                    e.errorCode == "ERROR_USER_NOT_FOUND"
                ) {
                    completarSolicitudRecuperacion()
                } else {
                    val mensaje = when (e) {
                        is FirebaseTooManyRequestsException ->
                            "Se realizaron demasiados intentos. " +
                                    "Espera un momento y vuelve a intentarlo."

                        is FirebaseNetworkException ->
                            "No pudimos conectarnos. " +
                                    "Revisa tu conexión a Internet."

                        else ->
                            "No pudimos procesar la solicitud. " +
                                    "Inténtalo nuevamente."
                    }

                    _recuperacion.value = _recuperacion.value.copy(
                        enviando = false,
                        error = mensaje
                    )
                }
            }
        }
    }

    private fun completarSolicitudRecuperacion() {
        _recuperacion.value = _recuperacion.value.copy(
            enviando = false,
            solicitudCompletada = true,
            error = null
        )
    }

    private fun mostrarError(error: Exception) {
        val mensaje = when (error) {
            is FirebaseTooManyRequestsException ->
                "Se realizaron demasiados intentos. Espera un momento " +
                        "antes de volver a intentarlo."

            is FirebaseNetworkException ->
                "No pudimos conectarnos. Revisa tu conexión a Internet."

            is FirebaseAuthException ->
                "No se pudo iniciar sesión. Revisa tus datos " +
                        "o recupera el acceso a tu cuenta."

            is FirebaseFirestoreException ->
                "No se pudo consultar tu perfil. Comprueba tu conexión " +
                        "y vuelve a intentarlo."

            else ->
                "No pudimos completar el acceso a tu perfil. " +
                        "Inténtalo nuevamente."
        }

        _uiState.value = LoginUiState(
            sesionRevisada = true,
            error = mensaje
        )
    }
}