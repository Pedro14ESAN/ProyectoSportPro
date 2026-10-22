package com.example.proyectosportpro.features.jugador.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proyectosportpro.features.jugador.data.PerfilJugadorRepository
import com.example.proyectosportpro.features.jugador.domain.PerfilJugador
import com.example.proyectosportpro.features.jugador.domain.PosicionJugador
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class PerfilJugadorUiState(
    val perfil: PerfilJugador? = null,
    val cargando: Boolean = false,
    val guardando: Boolean = false,
    val cargaCompletada: Boolean = false,
    val necesitaRecargar: Boolean = false,
    val error: String? = null,
    val mensaje: String? = null
)

class PerfilJugadorViewModel : ViewModel() {

    private val repository = PerfilJugadorRepository()

    private val usuarioId =
        FirebaseAuth.getInstance().currentUser?.uid

    private val _uiState = MutableStateFlow(PerfilJugadorUiState())
    val uiState = _uiState.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        if (_uiState.value.cargando || _uiState.value.guardando) return

        _uiState.value = _uiState.value.copy(
            cargando = true,
            cargaCompletada = false,
            error = null,
            mensaje = null
        )

        viewModelScope.launch {
            try {
                comprobarSesion()

                val perfil = withTimeout(20_000L) {
                    repository.obtenerMiPerfil()
                }

                comprobarSesion()

                _uiState.value = PerfilJugadorUiState(
                    perfil = perfil,
                    cargaCompletada = true
                )
            } catch (_: TimeoutCancellationException) {
                _uiState.value = _uiState.value.copy(
                    cargando = false,
                    error = "La consulta tardó demasiado. Vuelve a intentarlo."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    cargando = false,
                    error = mensajeError(e)
                )
            }
        }
    }

    fun guardar(
        posicion: PosicionJugador?,
        alturaTexto: String,
        pesoTexto: String,
        telefonoContacto: String,
        nombreContactoEmergencia: String,
        telefonoContactoEmergencia: String
    ) {
        val estado = _uiState.value

        if (estado.cargando || estado.guardando) return

        if (!estado.cargaCompletada || estado.necesitaRecargar) {
            _uiState.value = estado.copy(
                error = "Primero actualiza el perfil para consultar sus datos."
            )
            return
        }

        val perfil = try {
            val uid = comprobarSesion()

            val alturaLimpia = alturaTexto.trim()
            val pesoLimpio = pesoTexto.trim().replace(',', '.')

            val altura = if (alturaLimpia.isEmpty()) {
                null
            } else {
                requireNotNull(alturaLimpia.toIntOrNull()) {
                    "La altura debe ser un número entero en centímetros."
                }
            }

            val peso = if (pesoLimpio.isEmpty()) {
                null
            } else {
                requireNotNull(pesoLimpio.toDoubleOrNull()) {
                    "El peso debe ser un número en kilogramos."
                }
            }

            PerfilJugador(
                uid = uid,
                posicion = posicion,
                alturaCm = altura,
                pesoKg = peso,
                fotoPath = estado.perfil?.fotoPath,
                telefonoContacto = telefonoContacto,
                nombreContactoEmergencia = nombreContactoEmergencia,
                telefonoContactoEmergencia = telefonoContactoEmergencia
            )
        } catch (e: Exception) {
            _uiState.value = estado.copy(
                error = mensajeError(e),
                mensaje = null
            )
            return
        }

        _uiState.value = estado.copy(
            guardando = true,
            error = null,
            mensaje = null
        )

        viewModelScope.launch {
            var escrituraConfirmada = false

            try {
                withTimeout(20_000L) {
                    repository.guardarMiPerfil(perfil)
                }
                escrituraConfirmada = true

                val guardado = withTimeout(20_000L) {
                    repository.obtenerMiPerfil()
                }

                comprobarSesion()

                checkNotNull(guardado) {
                    "No se pudo recuperar el perfil guardado."
                }

                _uiState.value = PerfilJugadorUiState(
                    perfil = guardado,
                    cargaCompletada = true,
                    mensaje = "Perfil deportivo guardado."
                )
            } catch (_: TimeoutCancellationException) {
                _uiState.value = _uiState.value.copy(
                    guardando = false,
                    necesitaRecargar = true,
                    error = if (escrituraConfirmada) {
                        "El perfil se guardó, pero no pudimos volver a cargarlo. " +
                                "Pulsa Actualizar."
                    } else {
                        "No pudimos confirmar el resultado del guardado. " +
                                "Pulsa Actualizar antes de volver a guardar."
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    guardando = false,
                    necesitaRecargar = escrituraConfirmada ||
                            e !is IllegalArgumentException,
                    error = if (escrituraConfirmada) {
                        "El perfil se guardó, pero falló la consulta posterior. " +
                                "Pulsa Actualizar."
                    } else {
                        mensajeError(e)
                    }
                )
            }
        }
    }

    private fun comprobarSesion(): String {
        val uid = checkNotNull(usuarioId) {
            "Debes iniciar sesión."
        }

        check(FirebaseAuth.getInstance().currentUser?.uid == uid) {
            "La sesión cambió. Vuelve a abrir tu perfil."
        }

        return uid
    }

    private fun mensajeError(error: Exception): String {
        return when (error) {
            is FirebaseFirestoreException -> when (error.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    "No tienes permiso para realizar esta operación."

                FirebaseFirestoreException.Code.UNAVAILABLE ->
                    "No pudimos conectar con el servidor. Revisa tu conexión."

                FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                    "Tu sesión no está disponible. Vuelve a iniciar sesión."

                else ->
                    "No pudimos completar la operación. Actualiza el perfil."
            }

            is IllegalArgumentException,
            is IllegalStateException ->
                error.message ?: "Revisa los datos e inténtalo nuevamente."

            else ->
                "Ocurrió un error. Actualiza el perfil e inténtalo nuevamente."
        }
    }
}