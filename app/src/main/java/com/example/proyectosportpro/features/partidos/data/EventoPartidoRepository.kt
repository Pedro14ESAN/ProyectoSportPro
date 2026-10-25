package com.example.proyectosportpro.features.partidos.data

import com.example.proyectosportpro.features.academia.data.PlantelRepository
import com.example.proyectosportpro.features.partidos.domain.DatosEvento
import com.example.proyectosportpro.features.partidos.domain.EstadoPartido
import com.example.proyectosportpro.features.partidos.domain.EventoPartido
import com.example.proyectosportpro.features.partidos.domain.JugadorEvento
import com.example.proyectosportpro.features.partidos.domain.LadoEvento
import com.example.proyectosportpro.features.partidos.domain.Marcador
import com.example.proyectosportpro.features.partidos.domain.Partido
import com.example.proyectosportpro.features.partidos.domain.TipoEvento
import com.example.proyectosportpro.features.partidos.domain.ValidacionPartido
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Valor recibido en tiempo real y si viene del caché local porque no hay conexión. */
data class Instantanea<T>(val valor: T, val desdeCache: Boolean)

/**
 * Eventos en vivo de un partido. Las escrituras se aplican primero en el caché local de
 * Firestore y se sincronizan al volver la conexión, por eso devuelven la tarea sin esperarla.
 */
class EventoPartidoRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private fun uidActual(): String = checkNotNull(auth.currentUser?.uid) { "Debes iniciar sesión." }

    private fun partidoRef(academiaId: String, partidoId: String) =
        firestore.collection("academias").document(academiaId).collection("partidos").document(partidoId)

    private fun eventos(partido: Partido) =
        partidoRef(partido.academiaId, partido.id).collection("eventos")

    fun nuevoEventoId(partido: Partido): String = eventos(partido).document().id

    fun escucharPartido(academiaId: String, partidoId: String): Flow<Instantanea<Partido?>> = callbackFlow {
        val registro = partidoRef(academiaId, partidoId)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) close(error)
                else if (snapshot != null) trySend(Instantanea(
                    if (snapshot.exists()) PartidoRepository.convertir(snapshot) else null,
                    snapshot.metadata.isFromCache
                ))
            }
        awaitClose { registro.remove() }
    }

    fun escucharEventos(partido: Partido): Flow<Instantanea<List<EventoPartido>>> = callbackFlow {
        val registro = eventos(partido).addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) close(error)
            else if (snapshot != null) trySend(Instantanea(
                snapshot.documents.mapNotNull { convertir(it) },
                snapshot.metadata.isFromCache
            ))
        }
        awaitClose { registro.remove() }
    }

    /** Jugadores activos del plantel; requiere conexión porque el plantel se lee del servidor. */
    suspend fun obtenerJugadores(partido: Partido): List<JugadorEvento> =
        PlantelRepository().obtenerPlantel(partido.academiaId, partido.equipoId)
            .filter { it.activo }
            .map { JugadorEvento(it.jugadorUid, it.nombre) }

    /** Solo el administrador inicia el partido. */
    fun iniciar(partido: Partido): Task<Void> = actualizarPartido(partido, mapOf(
        "estado" to EstadoPartido.EN_JUEGO.name
    ))

    fun finalizar(partido: Partido, marcador: Marcador): Task<Void> = actualizarPartido(partido, mapOf(
        "estado" to EstadoPartido.FINALIZADO.name,
        "golesFavor" to marcador.favor,
        "golesContra" to marcador.contra
    ))

    fun registrar(partido: Partido, eventoId: String, datos: DatosEvento, marcadorFinal: Marcador?): Task<Void> {
        val uid = uidActual()
        val batch = firestore.batch()
        batch.set(eventos(partido).document(eventoId), campos(datos) + mapOf(
            "partidoId" to partido.id,
            "anulado" to false,
            "motivoAnulacion" to null,
            "version" to 1,
            "creadoPor" to uid,
            "actualizadoPor" to uid,
            "fechaCreacion" to FieldValue.serverTimestamp(),
            "fechaActualizacion" to FieldValue.serverTimestamp()
        ))
        recalcularResultado(batch, partido, marcadorFinal)
        return batch.commit()
    }

    fun corregir(partido: Partido, evento: EventoPartido, datos: DatosEvento, marcadorFinal: Marcador?): Task<Void> =
        cambiar(partido, evento, marcadorFinal, campos(datos) + mapOf("anulado" to false, "motivoAnulacion" to null))

    fun anular(partido: Partido, evento: EventoPartido, motivo: String, marcadorFinal: Marcador?): Task<Void> =
        cambiar(partido, evento, marcadorFinal, mapOf(
            "anulado" to true,
            "motivoAnulacion" to ValidacionPartido.normalizar(motivo)
        ))

    /** Guarda la versión anterior en el historial y aplica el cambio en la misma escritura. */
    private fun cambiar(
        partido: Partido,
        evento: EventoPartido,
        marcadorFinal: Marcador?,
        cambios: Map<String, Any?>
    ): Task<Void> {
        val uid = uidActual()
        val ref = eventos(partido).document(evento.id)
        val batch = firestore.batch()
        batch.set(ref.collection("historial").document(evento.version.toString()),
            campos(evento.datos) + mapOf(
                "version" to evento.version,
                "anulado" to evento.anulado,
                "motivoAnulacion" to evento.motivoAnulacion,
                "reemplazadoPor" to uid,
                "fechaCambio" to FieldValue.serverTimestamp()
            ))
        batch.update(ref, cambios + mapOf(
            "version" to evento.version + 1,
            "actualizadoPor" to uid,
            "fechaActualizacion" to FieldValue.serverTimestamp()
        ))
        recalcularResultado(batch, partido, marcadorFinal)
        return batch.commit()
    }

    /** Si el partido ya terminó, el resultado guardado se corrige junto con el evento. */
    private fun recalcularResultado(batch: WriteBatch, partido: Partido, marcadorFinal: Marcador?) {
        if (partido.estado != EstadoPartido.FINALIZADO || marcadorFinal == null) return
        batch.update(partidoRef(partido.academiaId, partido.id), mapOf(
            "golesFavor" to marcadorFinal.favor,
            "golesContra" to marcadorFinal.contra,
            "actualizadoPor" to uidActual(),
            "fechaActualizacion" to FieldValue.serverTimestamp()
        ))
    }

    private fun actualizarPartido(partido: Partido, cambios: Map<String, Any?>): Task<Void> =
        partidoRef(partido.academiaId, partido.id).update(cambios + mapOf(
            "actualizadoPor" to uidActual(),
            "fechaActualizacion" to FieldValue.serverTimestamp()
        ))

    private fun campos(datos: DatosEvento): Map<String, Any?> = mapOf(
        "tipo" to datos.tipo.name,
        "lado" to datos.lado.name,
        "minuto" to datos.minuto,
        "jugadorUid" to datos.jugador?.uid,
        "jugadorNombre" to datos.jugador?.nombre,
        "detalle" to ValidacionPartido.normalizar(datos.detalle)
    )

    /** Los eventos con datos dañados se ignoran para no bloquear la cronología. */
    private fun convertir(d: DocumentSnapshot): EventoPartido? {
        val tipo = TipoEvento.entries.firstOrNull { it.name == d.getString("tipo") } ?: return null
        val lado = LadoEvento.entries.firstOrNull { it.name == d.getString("lado") } ?: return null
        val jugadorUid = d.getString("jugadorUid")
        val jugadorNombre = d.getString("jugadorNombre")
        return EventoPartido(
            id = d.id,
            datos = DatosEvento(
                tipo = tipo,
                lado = lado,
                minuto = d.getLong("minuto")?.toInt(),
                jugador = if (jugadorUid != null && jugadorNombre != null) JugadorEvento(jugadorUid, jugadorNombre) else null,
                detalle = d.getString("detalle").orEmpty()
            ),
            anulado = d.getBoolean("anulado") == true,
            motivoAnulacion = d.getString("motivoAnulacion"),
            version = d.getLong("version")?.toInt() ?: 1,
            fechaCreacionMillis = d.getTimestamp("fechaCreacion")?.toDate()?.time,
            pendiente = d.metadata.hasPendingWrites()
        )
    }
}
