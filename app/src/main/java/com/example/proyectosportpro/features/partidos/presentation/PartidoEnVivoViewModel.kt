package com.example.proyectosportpro.features.partidos.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.proyectosportpro.features.partidos.data.EventoPartidoRepository
import com.example.proyectosportpro.features.partidos.data.ResumenPartidoRepository
import com.example.proyectosportpro.features.partidos.domain.Cronologia
import com.example.proyectosportpro.features.partidos.domain.DatosEvento
import com.example.proyectosportpro.features.partidos.domain.EstadoPartido
import com.example.proyectosportpro.features.partidos.domain.EstadoResumen
import com.example.proyectosportpro.features.partidos.domain.EventoPartido
import com.example.proyectosportpro.features.partidos.domain.JugadorEvento
import com.example.proyectosportpro.features.partidos.domain.Marcador
import com.example.proyectosportpro.features.partidos.domain.Partido
import com.example.proyectosportpro.features.partidos.domain.PromptResumen
import com.example.proyectosportpro.features.partidos.domain.ResumenPartido
import com.example.proyectosportpro.features.partidos.domain.ValidacionEvento
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class PartidoEnVivoUiState(
    val partido: Partido? = null,
    val eventos: List<EventoPartido> = emptyList(),
    val cargando: Boolean = true,
    val sinConexion: Boolean = false,
    val jugadores: List<JugadorEvento> = emptyList(),
    val jugadoresNoDisponibles: Boolean = false,
    val puedeGestionar: Boolean = false,
    val esAdministrador: Boolean = false,
    val resumen: ResumenPartido? = null,
    /** Texto del resumen que el cuerpo técnico está revisando; null si no está editando. */
    val borrador: String? = null,
    val borradorConIA: Boolean = false,
    val generando: Boolean = false,
    val guardandoResumen: Boolean = false,
    val error: String? = null,
    val mensaje: String? = null
) {
    /** Terminado, vale el resultado guardado; en juego, el que suman los eventos. */
    val marcador: Marcador
        get() {
            val p = partido
            val gf = p?.golesFavor
            val gc = p?.golesContra
            return if (p?.estado == EstadoPartido.FINALIZADO && gf != null && gc != null) Marcador(gf, gc)
            else Cronologia.marcador(eventos)
        }

    val pendientes: Int get() = eventos.count { it.pendiente }
    val incompletos: Int get() = Cronologia.incompletos(eventos)

    val puedeRegistrar: Boolean
        get() = puedeGestionar && partido?.estado in listOf(EstadoPartido.EN_JUEGO, EstadoPartido.FINALIZADO)

    val puedeIniciar: Boolean
        get() = esAdministrador && puedeGestionar && partido?.estado == EstadoPartido.PROGRAMADO

    val puedeFinalizar: Boolean
        get() = puedeGestionar && partido?.estado == EstadoPartido.EN_JUEGO

    /** Jugadores y apoderados solo ven el resumen aprobado. */
    val resumenVisible: ResumenPartido?
        get() = resumen?.takeIf { puedeGestionar || it.estado == EstadoResumen.APROBADO }
}

class PartidoEnVivoViewModel : ViewModel() {
    private val repository = EventoPartidoRepository()
    private val resumenes = ResumenPartidoRepository()
    private val usuarioId = FirebaseAuth.getInstance().currentUser?.uid
    private var clave: String? = null
    private var escuchas: List<Job> = emptyList()
    private val _uiState = MutableStateFlow(PartidoEnVivoUiState())
    val uiState = _uiState.asStateFlow()

    fun iniciar(partido: Partido, puedeGestionar: Boolean, esAdministrador: Boolean) {
        val nueva = "${partido.academiaId}/${partido.id}"
        if (clave == nueva) return
        clave = nueva
        escuchas.forEach { it.cancel() }
        _uiState.value = PartidoEnVivoUiState(partido = partido, puedeGestionar = puedeGestionar,
            esAdministrador = esAdministrador)

        escuchas = listOf(
            viewModelScope.launch {
                repository.escucharPartido(partido.academiaId, partido.id)
                    .catch { mostrarError(it) }
                    .collect { instantanea ->
                        _uiState.value = _uiState.value.copy(
                            partido = instantanea.valor ?: _uiState.value.partido,
                            sinConexion = instantanea.desdeCache,
                            error = if (instantanea.valor == null && !instantanea.desdeCache)
                                "El partido tiene datos incompletos o ya no existe." else _uiState.value.error
                        )
                    }
            },
            viewModelScope.launch {
                repository.escucharEventos(partido)
                    .catch { mostrarError(it) }
                    .collect { instantanea ->
                        _uiState.value = _uiState.value.copy(
                            eventos = Cronologia.ordenar(instantanea.valor),
                            sinConexion = instantanea.desdeCache,
                            cargando = false
                        )
                    }
            },
            viewModelScope.launch {
                // Sin resumen aprobado, el jugador simplemente no ve nada.
                resumenes.escuchar(partido)
                    .catch { _uiState.value = _uiState.value.copy(resumen = null) }
                    .collect { _uiState.value = _uiState.value.copy(resumen = it) }
            }
        )
        if (puedeGestionar) cargarJugadores()
    }

    fun cargarJugadores() {
        val partido = _uiState.value.partido ?: return
        viewModelScope.launch {
            try {
                val jugadores = withTimeout(20_000L) { repository.obtenerJugadores(partido) }
                _uiState.value = _uiState.value.copy(jugadores = jugadores, jugadoresNoDisponibles = false)
            } catch (e: CancellationException) {
                if (e !is TimeoutCancellationException) throw e
                _uiState.value = _uiState.value.copy(jugadoresNoDisponibles = true)
            } catch (_: Exception) {
                // Sin conexión el evento se puede registrar sin jugador y completarse después.
                _uiState.value = _uiState.value.copy(jugadoresNoDisponibles = true)
            }
        }
    }

    fun limpiarAvisos() {
        _uiState.value = _uiState.value.copy(error = null, mensaje = null)
    }

    fun validar(datos: DatosEvento): String? = ValidacionEvento.validar(datos)

    fun validarMotivo(motivo: String): String? = ValidacionEvento.validarMotivo(motivo)

    fun iniciarPartido() {
        val partido = _uiState.value.partido ?: return
        if (!_uiState.value.puedeIniciar) return
        enviar("El partido está en juego.") { repository.iniciar(partido) }
    }

    fun finalizarPartido() {
        val estado = _uiState.value
        val partido = estado.partido ?: return
        if (!estado.puedeFinalizar) return
        enviar("Partido finalizado ${Cronologia.marcador(estado.eventos)}.") {
            repository.finalizar(partido, Cronologia.marcador(estado.eventos))
        }
    }

    fun registrar(datos: DatosEvento) {
        val estado = _uiState.value
        val partido = estado.partido ?: return
        if (!estado.puedeRegistrar || ValidacionEvento.validar(datos) != null) return
        // El identificador se crea en el teléfono, así el evento existe aunque no haya conexión.
        val id = repository.nuevoEventoId(partido)
        val provisional = EventoPartido(id, datos, anulado = false, motivoAnulacion = null, version = 1,
            fechaCreacionMillis = null, pendiente = true)
        enviar("Evento registrado.") {
            repository.registrar(partido, id, datos, Cronologia.marcador(estado.eventos + provisional))
        }
    }

    fun corregir(evento: EventoPartido, datos: DatosEvento) {
        val estado = _uiState.value
        val partido = estado.partido ?: return
        if (!estado.puedeRegistrar || ValidacionEvento.validar(datos) != null) return
        val corregido = evento.copy(datos = datos, anulado = false, motivoAnulacion = null)
        enviar("Evento corregido. La versión anterior queda en el historial.") {
            repository.corregir(partido, evento, datos, marcadorCon(estado.eventos, corregido))
        }
    }

    fun anular(evento: EventoPartido, motivo: String) {
        val estado = _uiState.value
        val partido = estado.partido ?: return
        if (!estado.puedeRegistrar || ValidacionEvento.validarMotivo(motivo) != null) return
        val anulado = evento.copy(anulado = true, motivoAnulacion = motivo)
        enviar("Evento anulado. Sigue visible en la cronología con su motivo.") {
            repository.anular(partido, evento, motivo, marcadorCon(estado.eventos, anulado))
        }
    }

    private fun marcadorCon(eventos: List<EventoPartido>, cambiado: EventoPartido): Marcador =
        Cronologia.marcador(eventos.map { if (it.id == cambiado.id) cambiado else it })

    /**
     * La escritura queda aplicada en el teléfono al instante; si no hay conexión, Firestore la
     * envía al volver. Un rechazo del servidor llega después, como error.
     */
    private fun enviar(mensajeExito: String, escritura: () -> Task<Void>) {
        try {
            comprobarSesion()
            escritura().addOnFailureListener { e ->
                _uiState.value = _uiState.value.copy(mensaje = null,
                    error = "El servidor rechazó un cambio: ${mensajeError(e)}")
            }
            _uiState.value = _uiState.value.copy(error = null, mensaje = if (_uiState.value.sinConexion)
                "$mensajeExito Se guardó en el teléfono y se enviará al recuperar la conexión."
            else mensajeExito)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(mensaje = null, error = mensajeError(e))
        }
    }

    fun generarResumen() {
        val estado = _uiState.value
        val partido = estado.partido ?: return
        if (!estado.puedeGestionar || estado.generando || partido.estado != EstadoPartido.FINALIZADO) return
        _uiState.value = estado.copy(generando = true, error = null, mensaje = null)
        viewModelScope.launch {
            try {
                comprobarSesion()
                val texto = withTimeout(60_000L) {
                    resumenes.generar(PromptResumen.construir(partido, _uiState.value.eventos))
                }
                _uiState.value = _uiState.value.copy(generando = false, borrador = texto, borradorConIA = true,
                    mensaje = "Revisa el borrador: corrígelo si hace falta antes de aprobarlo.")
            } catch (e: CancellationException) {
                if (e !is TimeoutCancellationException) throw e
                _uiState.value = _uiState.value.copy(generando = false,
                    error = "La IA tardó demasiado. Inténtalo de nuevo.")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(generando = false, error = if (e is IllegalStateException)
                    e.message else "No pudimos generar el resumen. Revisa tu conexión y que Firebase AI Logic esté activado.")
            }
        }
    }

    fun editarResumen() {
        val resumen = _uiState.value.resumen ?: return
        _uiState.value = _uiState.value.copy(borrador = resumen.texto, borradorConIA = resumen.generadoConIA)
    }

    fun cambiarBorrador(texto: String) {
        _uiState.value = _uiState.value.copy(borrador = texto.take(PromptResumen.MAX_TEXTO))
    }

    fun descartarBorrador() {
        _uiState.value = _uiState.value.copy(borrador = null)
    }

    fun guardarResumen(aprobar: Boolean) {
        val estado = _uiState.value
        val partido = estado.partido ?: return
        val texto = estado.borrador?.trim() ?: return
        if (texto.length < 20) {
            _uiState.value = estado.copy(error = "El resumen debe tener al menos 20 caracteres.")
            return
        }
        _uiState.value = estado.copy(guardandoResumen = true, error = null, mensaje = null)
        viewModelScope.launch {
            try {
                comprobarSesion()
                withTimeout(25_000L) {
                    resumenes.guardar(partido, texto,
                        if (aprobar) EstadoResumen.APROBADO else EstadoResumen.BORRADOR, estado.borradorConIA)
                }
                _uiState.value = _uiState.value.copy(guardandoResumen = false, borrador = null,
                    mensaje = if (aprobar) "Resumen aprobado: ya lo ven jugadores y apoderados."
                    else "Borrador guardado. Solo el cuerpo técnico lo ve.")
            } catch (e: CancellationException) {
                if (e !is TimeoutCancellationException) throw e
                _uiState.value = _uiState.value.copy(guardandoResumen = false,
                    error = "No pudimos confirmar el guardado. Revisa tu conexión.")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(guardandoResumen = false, error = mensajeError(e))
            }
        }
    }

    private fun mostrarError(error: Throwable) {
        _uiState.value = _uiState.value.copy(cargando = false, error = mensajeError(error))
    }

    private fun comprobarSesion() {
        check(usuarioId != null && FirebaseAuth.getInstance().currentUser?.uid == usuarioId) {
            "La sesión cambió. Vuelve a abrir Partidos."
        }
    }

    private fun mensajeError(error: Throwable): String {
        val causas = generateSequence(error) { it.cause }.take(10).toList()
        val firestore = causas.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
        return when (firestore?.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "No tienes permiso para esta operación. Comprueba tu equipo y que las reglas estén publicadas."
            FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                "Tu sesión no es válida. Vuelve a iniciar sesión."
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                "No pudimos conectar con el servidor. Revisa tu conexión."
            else -> causas.firstOrNull {
                it is IllegalArgumentException || it is IllegalStateException
            }?.message ?: "No pudimos completar la operación."
        }
    }
}
