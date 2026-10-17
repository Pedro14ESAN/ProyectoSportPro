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

class LoginViewModel : ViewModel() {

    private val repository = AuthRepository()

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState = _uiState.asStateFlow()

    private var operacion: Job? = null

    init {
        revisarSesion()
    }

    fun revisarSesion() {
        if (_uiState.value.cargando) {
            return
        }

        if (repository.obtenerUidActual() == null) {
            _uiState.value = LoginUiState(
                sesionRevisada = true
            )
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

    fun iniciarSesion(
        correo: String,
        contrasena: String
    ) {
        if (_uiState.value.cargando) {
            return
        }

        // Una sesión existente debe resolverse o cerrarse
        // antes de iniciar sesión con otra cuenta.
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
                repository.iniciarSesion(
                    correo = correoLimpio,
                    contrasena = contrasena
                )

                cargarPerfil()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mostrarError(e)
            }
        }
    }

    private suspend fun cargarPerfil() {
        val perfil = repository.obtenerPerfilActual()

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

        // Elimina del estado el perfil y los datos de la sesión.
        _uiState.value = LoginUiState(
            sesionRevisada = true
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