package com.example.proyectosportpro.features.partidos.data

import com.example.proyectosportpro.features.academia.data.AcademiaRepository
import com.example.proyectosportpro.features.academia.data.EquipoRepository
import com.example.proyectosportpro.features.academia.data.MisEquiposJugadorRepository
import com.example.proyectosportpro.features.academia.data.MisEquiposRepository
import com.example.proyectosportpro.features.auth.domain.RolUsuario
import com.example.proyectosportpro.features.partidos.domain.CondicionPartido
import com.example.proyectosportpro.features.partidos.domain.DatosPartido
import com.example.proyectosportpro.features.partidos.domain.EquipoPartido
import com.example.proyectosportpro.features.partidos.domain.EstadoPartido
import com.example.proyectosportpro.features.partidos.domain.Partido
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import java.util.Date

class PartidoRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private fun uidActual(): String = checkNotNull(auth.currentUser?.uid) { "Debes iniciar sesión." }

    private fun comprobarSesion(uid: String) {
        check(auth.currentUser?.uid == uid) { "La sesión cambió. Vuelve a abrir Partidos." }
    }

    private fun partidos(academiaId: String) =
        firestore.collection("academias").document(academiaId).collection("partidos")

    fun nuevoPartidoId(academiaId: String): String = partidos(academiaId).document().id

    /** Equipos visibles según el rol, reutilizando las consultas ya autorizadas de cada uno. */
    suspend fun obtenerEquipos(rol: RolUsuario): List<EquipoPartido> {
        val uid = uidActual()
        val equipos = when (rol) {
            RolUsuario.ADM -> AcademiaRepository().obtenerMisAcademias()
                .filter { it.activa }
                .flatMap { academia ->
                    EquipoRepository().obtenerEquipos(academia.id).map { equipo ->
                        EquipoPartido(academia.id, equipo.id, equipo.nombre, academia.nombre,
                            puedeGestionar = equipo.activo)
                    }
                }
            RolUsuario.DT -> MisEquiposRepository().obtenerMisEquipos().map {
                EquipoPartido(it.academiaId, it.id, it.nombre, it.academiaNombre, puedeGestionar = it.activo)
            }
            RolUsuario.JUG -> MisEquiposJugadorRepository().obtenerMisEquipos().map {
                EquipoPartido(it.academiaId, it.id, it.nombre, it.academiaNombre, puedeGestionar = false)
            }
            RolUsuario.PAD -> emptyList()
        }
        comprobarSesion(uid)
        return equipos
    }

    suspend fun obtenerPartidos(rol: RolUsuario, equipos: List<EquipoPartido>): List<Partido> {
        val uid = uidActual()
        val documentos = if (rol == RolUsuario.ADM) {
            // El administrador consulta cada academia completa.
            equipos.map { it.academiaId }.distinct().flatMap { academiaId ->
                partidos(academiaId).get(Source.SERVER).await().documents
            }
        } else {
            // Las reglas exigen filtrar por equipo para el DT y el jugador.
            equipos.flatMap { equipo ->
                partidos(equipo.academiaId).whereEqualTo("equipoId", equipo.equipoId)
                    .get(Source.SERVER).await().documents
            }
        }
        comprobarSesion(uid)
        return documentos.map { convertir(it) }.distinctBy { "${it.academiaId}/${it.id}" }
            .sortedBy { it.fechaHoraMillis }
    }

    suspend fun programar(partidoId: String, equipo: EquipoPartido, datos: DatosPartido) {
        val uid = uidActual()
        val ref = partidos(equipo.academiaId).document(partidoId)
        firestore.runTransaction { tx ->
            comprobarSesion(uid)
            // Un reintento con el mismo identificador no duplica el partido.
            if (tx.get(ref).exists()) return@runTransaction
            tx.set(ref, mapOf(
                "academiaId" to equipo.academiaId,
                "equipoId" to equipo.equipoId,
                "nombreEquipo" to equipo.nombre,
                "rival" to datos.rival,
                "lugar" to datos.lugar,
                "fechaHora" to Timestamp(Date(datos.fechaHoraMillis)),
                "condicion" to datos.condicion.name,
                "estado" to EstadoPartido.PROGRAMADO.name,
                "golesFavor" to null,
                "golesContra" to null,
                "creadoPor" to uid,
                "actualizadoPor" to uid,
                "fechaCreacion" to FieldValue.serverTimestamp(),
                "fechaActualizacion" to FieldValue.serverTimestamp()
            ))
        }.await()
        comprobarSesion(uid)
    }

    suspend fun editar(partido: Partido, datos: DatosPartido) = actualizar(partido, mapOf(
        "rival" to datos.rival,
        "lugar" to datos.lugar,
        "fechaHora" to Timestamp(Date(datos.fechaHoraMillis)),
        "condicion" to datos.condicion.name
    ))

    suspend fun registrarResultado(partido: Partido, golesFavor: Int, golesContra: Int) {
        require(golesFavor in 0..99 && golesContra in 0..99) { "Los goles deben estar entre 0 y 99." }
        actualizar(partido, mapOf(
            "estado" to EstadoPartido.FINALIZADO.name,
            "golesFavor" to golesFavor,
            "golesContra" to golesContra
        ))
    }

    suspend fun cancelar(partido: Partido) = actualizar(partido, mapOf(
        "estado" to EstadoPartido.CANCELADO.name,
        "golesFavor" to null,
        "golesContra" to null
    ))

    private suspend fun actualizar(partido: Partido, cambios: Map<String, Any?>) {
        val uid = uidActual()
        partidos(partido.academiaId).document(partido.id).update(cambios + mapOf(
            "actualizadoPor" to uid,
            "fechaActualizacion" to FieldValue.serverTimestamp()
        )).await()
        comprobarSesion(uid)
    }

    private fun convertir(d: DocumentSnapshot): Partido {
        fun texto(campo: String) = checkNotNull(d.getString(campo)) { "El partido está incompleto: $campo." }
        return Partido(
            id = d.id,
            academiaId = texto("academiaId"),
            equipoId = texto("equipoId"),
            nombreEquipo = texto("nombreEquipo"),
            rival = texto("rival"),
            lugar = texto("lugar"),
            fechaHoraMillis = checkNotNull(d.getTimestamp("fechaHora")).toDate().time,
            condicion = CondicionPartido.valueOf(texto("condicion")),
            estado = EstadoPartido.valueOf(texto("estado")),
            golesFavor = d.getLong("golesFavor")?.toInt(),
            golesContra = d.getLong("golesContra")?.toInt()
        )
    }
}
