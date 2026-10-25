package com.example.proyectosportpro.features.comunidad.data

import com.example.proyectosportpro.features.auth.domain.RolUsuario
import com.example.proyectosportpro.features.comunidad.domain.Aviso
import com.example.proyectosportpro.features.partidos.domain.EquipoPartido
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

class AvisoRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private fun uidActual(): String = checkNotNull(auth.currentUser?.uid) { "Debes iniciar sesión." }

    private fun comprobarSesion(uid: String) {
        check(auth.currentUser?.uid == uid) { "La sesión cambió. Vuelve a abrir Comunidad." }
    }

    private fun avisos(academiaId: String) =
        firestore.collection("academias").document(academiaId).collection("avisos")

    fun nuevoAvisoId(academiaId: String): String = avisos(academiaId).document().id

    suspend fun obtenerAvisos(rol: RolUsuario, equipos: List<EquipoPartido>): List<Aviso> {
        val uid = uidActual()
        val documentos = if (rol == RolUsuario.ADM) {
            equipos.map { it.academiaId }.distinct().flatMap { academiaId ->
                avisos(academiaId).get(Source.SERVER).await().documents
            }
        } else {
            // Las reglas exigen filtrar por equipo para el DT, el jugador y el apoderado.
            equipos.flatMap { equipo ->
                avisos(equipo.academiaId).whereEqualTo("equipoId", equipo.equipoId)
                    .get(Source.SERVER).await().documents
            }
        }
        comprobarSesion(uid)
        return documentos.filter { it.getBoolean("activo") == true }
            .map { convertir(it) }
            .distinctBy { "${it.academiaId}/${it.id}" }
            .sortedByDescending { it.fechaMillis }
    }

    suspend fun publicar(avisoId: String, equipo: EquipoPartido, titulo: String, mensaje: String) {
        val uid = uidActual()
        val ref = avisos(equipo.academiaId).document(avisoId)
        val usuarioRef = firestore.collection("users").document(uid)
        firestore.runTransaction { tx ->
            comprobarSesion(uid)
            val usuario = tx.get(usuarioRef)
            // Un reintento con el mismo identificador no duplica el aviso.
            if (tx.get(ref).exists()) return@runTransaction
            val nombre = checkNotNull(usuario.getString("nombre")) { "Tu cuenta no tiene nombre." }
            tx.set(ref, mapOf(
                "academiaId" to equipo.academiaId,
                "equipoId" to equipo.equipoId,
                "nombreEquipo" to equipo.nombre,
                "titulo" to titulo,
                "mensaje" to mensaje,
                "autorUid" to uid,
                "autorNombre" to nombre,
                "activo" to true,
                "fechaCreacion" to FieldValue.serverTimestamp()
            ))
        }.await()
        comprobarSesion(uid)
    }

    suspend fun retirar(aviso: Aviso) {
        val uid = uidActual()
        avisos(aviso.academiaId).document(aviso.id).update("activo", false).await()
        comprobarSesion(uid)
    }

    private fun convertir(d: DocumentSnapshot): Aviso {
        fun texto(campo: String) = checkNotNull(d.getString(campo)) { "El aviso está incompleto: $campo." }
        return Aviso(
            id = d.id,
            academiaId = texto("academiaId"),
            equipoId = texto("equipoId"),
            nombreEquipo = texto("nombreEquipo"),
            titulo = texto("titulo"),
            mensaje = texto("mensaje"),
            autorUid = texto("autorUid"),
            autorNombre = texto("autorNombre"),
            // Recién creado puede llegar sin la fecha del servidor resuelta.
            fechaMillis = d.getTimestamp("fechaCreacion")?.toDate()?.time ?: System.currentTimeMillis()
        )
    }
}
