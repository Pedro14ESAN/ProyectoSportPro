package com.example.proyectosportpro.features.auth.presentation

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proyectosportpro.features.auth.data.AuthRepository
import com.example.proyectosportpro.features.auth.domain.PerfilUsuario
import com.example.proyectosportpro.features.auth.domain.RolUsuario
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.proyectosportpro.features.auth.domain.PoliticaRegistro
import com.example.proyectosportpro.features.auth.domain.TipoRegistro
import java.time.LocalDate


class RegistroViewModel : ViewModel() {

    private val repository = AuthRepository()

    private val _uiState = MutableStateFlow(RegistroUiState())
    val uiState = _uiState.asStateFlow()

    // Conservamos el perfil, nunca la contraseña, para reintentar.
    private var perfilPorGuardar: PerfilUsuario? = null

    fun registrar(
        nombre: String,
        correo: String,
        contrasena: String,
        confirmacion: String,
        rol: RolUsuario?,
        aceptoTerminos: Boolean,
        fechaNacimiento: LocalDate?

    ) {
        if (_uiState.value.cargando || _uiState.value.registroExitoso) {
            return
        }

        if (perfilPorGuardar != null) {
            _uiState.value = _uiState.value.copy(
                error = "Tu cuenta ya fue creada. Pulsa Reintentar perfil."
            )
            return
        }

        val nombreLimpio = nombre.trim()
        val correoLimpio = correo.trim()

        val errorValidacion = when {
            nombreLimpio.length !in 2..100 ->
                "Escribe tu nombre completo, entre 2 y 100 caracteres."

            !Patterns.EMAIL_ADDRESS.matcher(correoLimpio).matches() ->
                "Ingresa un correo electrónico válido."

            contrasena.length < 8 ->
                "La contraseña debe tener al menos 8 caracteres."

            contrasena != confirmacion ->
                "Las contraseñas no coinciden."

            rol == null || rol !in RolUsuario.rolesDeRegistro ->
                "Selecciona un rol permitido."

            !aceptoTerminos ->
                "Debes aceptar los términos y la política de privacidad."

            else -> null
        }

        if (errorValidacion != null) {
            _uiState.value = RegistroUiState(error = errorValidacion)
            return
        }

        if (fechaNacimiento == null) {
            _uiState.value = RegistroUiState(
                error = "Selecciona tu fecha de nacimiento."
            )
            return
        }

        val evaluacion = PoliticaRegistro.evaluar(
            fechaNacimiento = fechaNacimiento,
            rol = requireNotNull(rol)
        )

        if (evaluacion.tipo != TipoRegistro.ADULTO) {
            _uiState.value = RegistroUiState(
                error = evaluacion.mensaje
            )
            return
        }

        if (repository.obtenerUidActual() != null) {
            _uiState.value = RegistroUiState(
                error = "Existe una sesión abierta. Debes continuar con " +
                        "esa cuenta antes de registrar otra."
            )
            return
        }

        val rolValidado = requireNotNull(rol)

        _uiState.value = RegistroUiState(cargando = true)

        viewModelScope.launch {
            try {
                val uid = repository.crearCuenta(
                    correo = correoLimpio,
                    contrasena = contrasena
                )

                perfilPorGuardar = PerfilUsuario(
                    uid = uid,
                    nombre = nombreLimpio,
                    correo = correoLimpio,
                    rol = rolValidado,
                    fechaNacimiento = fechaNacimiento.toString(),
                    aceptoTerminos = aceptoTerminos
                )

                guardarPerfilPendiente()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mostrarError(e)
            }
        }
    }

    fun reintentarPerfil() {
        if (_uiState.value.cargando || perfilPorGuardar == null) {
            return
        }

        _uiState.value = RegistroUiState(
            cargando = true,
            perfilPendiente = true
        )

        viewModelScope.launch {
            try {
                guardarPerfilPendiente()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mostrarError(e)
            }
        }
    }

    private suspend fun guardarPerfilPendiente() {
        val perfil = checkNotNull(perfilPorGuardar)

        repository.guardarPerfil(perfil)

        perfilPorGuardar = null
        _uiState.value = RegistroUiState(
            registroExitoso = true
        )
    }

    private fun mostrarError(error: Exception) {
        val pendiente = perfilPorGuardar != null

        val mensaje = when {
            pendiente ->
                "La cuenta fue creada, pero falta confirmar el perfil. " +
                        "Comprueba tu conexión y pulsa Reintentar perfil."

            error is FirebaseNetworkException ->
                "No pudimos conectarnos. Revisa tu conexión a Internet."

            error is FirebaseAuthException -> when (error.errorCode) {
                "ERROR_EMAIL_ALREADY_IN_USE" ->
                    "No se pudo registrar ese correo. Prueba iniciar sesión " +
                            "o recuperar el acceso."

                "ERROR_WEAK_PASSWORD" ->
                    "La contraseña no cumple la política de seguridad."

                "ERROR_INVALID_EMAIL" ->
                    "El correo electrónico no tiene un formato válido."

                else ->
                    "No se pudo crear la cuenta. Inténtalo nuevamente."
            }

            else ->
                "No se pudo completar el registro. Inténtalo nuevamente."
        }

        _uiState.value = RegistroUiState(
            error = mensaje,
            perfilPendiente = pendiente
        )
    }
}