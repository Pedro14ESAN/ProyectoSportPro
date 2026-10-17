package com.example.proyectosportpro.features.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proyectosportpro.features.auth.data.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class CompletarFechaUiState(
    val cargando: Boolean = true,
    val consultado: Boolean = false,
    val fechaGuardada: String? = null,
    val error: String? = null
)

class CompletarFechaViewModel : ViewModel() {

    private val repository = AuthRepository()

    private val _uiState = MutableStateFlow(CompletarFechaUiState())
    val uiState = _uiState.asStateFlow()

    init {
        consultar()
    }

    fun consultar() {
        _uiState.value = CompletarFechaUiState()

        viewModelScope.launch {
            try {
                val fecha = repository.consultarFechaNacimiento()

                _uiState.value = CompletarFechaUiState(
                    cargando = false,
                    consultado = true,
                    fechaGuardada = fecha
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = CompletarFechaUiState(
                    cargando = false,
                    error = "No se pudo consultar el perfil. Comprueba " +
                            "la conexión y que tu documento exista en users."
                )
            }
        }
    }

    fun guardar(fecha: LocalDate?) {
        val estado = _uiState.value

        if (
            estado.cargando ||
            !estado.consultado ||
            estado.fechaGuardada != null
        ) {
            return
        }

        if (fecha == null) {
            _uiState.value = estado.copy(
                error = "Selecciona tu fecha de nacimiento."
            )
            return
        }

        val hoy = LocalDate.now(ZoneId.of("America/Lima"))

        if (fecha.isAfter(hoy)) {
            _uiState.value = estado.copy(
                error = "La fecha no puede ser futura."
            )
            return
        }

        _uiState.value = estado.copy(
            cargando = true,
            error = null
        )

        viewModelScope.launch {
            try {
                repository.completarFechaNacimiento(fecha)

                _uiState.value = CompletarFechaUiState(
                    cargando = false,
                    consultado = true,
                    fechaGuardada = fecha.toString()
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = estado.copy(
                    cargando = false,
                    error = "No se pudo confirmar el guardado. Revisa tu " +
                            "conexión y las reglas de Firestore. Puedes reintentar."
                )
            }
        }
    }
}