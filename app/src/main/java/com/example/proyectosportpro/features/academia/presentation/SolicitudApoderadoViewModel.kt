package com.example.proyectosportpro.features.academia.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proyectosportpro.features.academia.data.InvitacionApoderadoRepository
import com.example.proyectosportpro.features.academia.domain.RelacionApoderado
import com.example.proyectosportpro.features.academia.domain.SolicitudApoderado
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.Locale

data class SolicitudApoderadoUiState(
    val codigo: String = "",
    val relacion: RelacionApoderado? = null,
    val solicitud: SolicitudApoderado? = null,
    val cargando: Boolean = false,
    val debeConsultar: Boolean = false,
    val error: String? = null,
    val mensaje: String? = null
)

class SolicitudApoderadoViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val repository = InvitacionApoderadoRepository()
    private val uid = FirebaseAuth.getInstance().currentUser?.uid
    // Las claves por usuario evitan reutilizar un borrador de otra cuenta.
    private val claveCodigo = "invitacion_pad_codigo_$uid"
    private val claveRelacion = "invitacion_pad_relacion_$uid"
    private val clavePendiente = "invitacion_pad_consultar_$uid"
    private val _uiState = MutableStateFlow(
        SolicitudApoderadoUiState(
            codigo = savedStateHandle.get<String>(claveCodigo).orEmpty(),
            relacion = RelacionApoderado.entries.firstOrNull {
                it.name == savedStateHandle.get<String>(claveRelacion)
            },
            debeConsultar = savedStateHandle.get<Boolean>(clavePendiente) ?: false
        )
    )
    val uiState = _uiState.asStateFlow()

    fun cambiarCodigo(valor: String) {
        if (_uiState.value.cargando || _uiState.value.debeConsultar) return
        savedStateHandle[claveCodigo] = valor
        _uiState.value = SolicitudApoderadoUiState(
            codigo = valor,
            relacion = _uiState.value.relacion
        )
    }

    fun seleccionarRelacion(valor: RelacionApoderado) {
        val estado = _uiState.value
        if (estado.cargando || estado.debeConsultar || estado.solicitud != null) return
        savedStateHandle[claveRelacion] = valor.name
        _uiState.value = estado.copy(relacion = valor, error = null, mensaje = null)
    }

    fun enviar() {
        ejecutar(enviar = true)
    }

    fun consultar() {
        ejecutar(enviar = false)
    }

    // La pantalla debe confirmar que cambiar de código no cancela
    // una solicitud que el servidor pudiera haber recibido anteriormente.
    fun usarOtraInvitacion() {
        if (_uiState.value.cargando) return
        savedStateHandle[claveCodigo] = ""
        savedStateHandle.remove<String>(claveRelacion)
        savedStateHandle[clavePendiente] = false
        _uiState.value = SolicitudApoderadoUiState()
    }

    private fun ejecutar(enviar: Boolean) {
        val estado = _uiState.value
        if (estado.cargando) return
        if (enviar && (estado.debeConsultar || estado.solicitud != null)) {
            _uiState.value = estado.copy(
                error = "Consulta el estado de la solicitud antes de continuar.", mensaje = null
            )
            return
        }
        val codigo = estado.codigo.trim().lowercase(Locale.ROOT)
        if (!codigo.matches(Regex("^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$"))) {
            _uiState.value = estado.copy(error = "Pega el código completo de la invitación privada.", mensaje = null)
            return
        }
        val relacion = estado.relacion
        if (enviar && relacion == null) {
            _uiState.value = estado.copy(error = "Selecciona tu relación con el jugador.", mensaje = null)
            return
        }
        savedStateHandle[claveCodigo] = codigo
        if (enviar) savedStateHandle[clavePendiente] = true
        _uiState.value = estado.copy(
            codigo = codigo, cargando = true, error = null, mensaje = null,
            debeConsultar = enviar || estado.debeConsultar
        )

        viewModelScope.launch {
            try {
                comprobarSesion()
                val solicitud = withTimeout(25_000L) {
                    if (enviar) repository.utilizarInvitacion(codigo, checkNotNull(relacion))
                    else repository.obtenerMiSolicitud(codigo)
                }
                comprobarSesion()
                savedStateHandle[clavePendiente] = false
                _uiState.value = _uiState.value.copy(
                    solicitud = solicitud,
                    relacion = solicitud?.relacionDeclarada ?: relacion,
                    cargando = false,
                    debeConsultar = false,
                    mensaje = if (solicitud == null) {
                        "No hay una solicitud registrada con esta invitación."
                    } else {
                        "Estado de tu solicitud: ${solicitud.estado.etiqueta}."
                    }
                )
            } catch (_: TimeoutCancellationException) {
                _uiState.value = _uiState.value.copy(
                    cargando = false,
                    solicitud = null,
                    error = if (enviar) {
                        "No pudimos confirmar el envío. Pulsa Consultar estado antes de reintentar."
                    } else {
                        "La consulta tardó demasiado. Vuelve a consultar el estado."
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Conserva la obligación de consultar si pudo iniciarse una escritura.
                _uiState.value = _uiState.value.copy(
                    cargando = false, solicitud = null, error = mensajeError(e)
                )
            }
        }
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
                "La operación no está autorizada. La invitación puede no corresponder a tu cuenta, haber vencido o haber sido revocada. Consulta al administrador."
            FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                "Tu sesión no es válida. Vuelve a iniciar sesión."
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                "No pudimos confirmar la operación. Revisa tu conexión y consulta el estado."
            else -> causas.firstOrNull {
                it is IllegalArgumentException || it is IllegalStateException
            }?.message ?: "No pudimos completar la operación. Consulta el estado."
        }
    }
}
