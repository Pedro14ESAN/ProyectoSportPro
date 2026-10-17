package com.example.proyectosportpro.features.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proyectosportpro.features.auth.data.AuthRepository
import com.example.proyectosportpro.features.auth.domain.RolUsuario
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class CompletarPerfilUiState(
    val guardando: Boolean = false,
    val completado: Boolean = false,
    val error: String? = null
)

class CompletarPerfilViewModel : ViewModel() {

    private val repository = AuthRepository()

    private val _uiState = MutableStateFlow(CompletarPerfilUiState())
    val uiState = _uiState.asStateFlow()

    fun guardar(
        nombre: String,
        fechaNacimiento: LocalDate?,
        rol: RolUsuario?,
        aceptoTerminos: Boolean
    ) {
        if (_uiState.value.guardando || _uiState.value.completado) return

        val error = when {
            nombre.trim().length !in 2..100 ->
                "El nombre debe tener entre 2 y 100 caracteres."

            fechaNacimiento == null ->
                "Selecciona tu fecha de nacimiento."

            rol == null ->
                "Selecciona tu rol."

            !aceptoTerminos ->
                "Debes aceptar los términos y la política de privacidad."

            else -> null
        }

        if (error != null) {
            _uiState.value = CompletarPerfilUiState(error = error)
            return
        }

        val fechaValidada = fechaNacimiento ?: return
        val rolValidado = rol ?: return

        _uiState.value = CompletarPerfilUiState(guardando = true)

        viewModelScope.launch {
            try {
                repository.completarPerfilPendiente(
                    nombre = nombre,
                    fechaNacimiento = fechaValidada,
                    rol = rolValidado,
                    aceptoTerminos = aceptoTerminos
                )

                _uiState.value = CompletarPerfilUiState(
                    completado = true
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val mensaje = when (e) {
                    is IllegalArgumentException ->
                        e.message ?: "Revisa los datos ingresados."

                    is FirebaseNetworkException ->
                        "No pudimos conectarnos. Revisa Internet " +
                                "y vuelve a guardar."

                    is FirebaseFirestoreException ->
                        "No pudimos confirmar el guardado del perfil. " +
                                "Vuelve a intentarlo. Si continúa, " +
                                "comunícate con el administrador."

                    else ->
                        "No pudimos completar el perfil. " +
                                "Inténtalo nuevamente o cierra sesión " +
                                "y vuelve a entrar."
                }

                _uiState.value = CompletarPerfilUiState(
                    error = mensaje
                )
            }
        }
    }
}