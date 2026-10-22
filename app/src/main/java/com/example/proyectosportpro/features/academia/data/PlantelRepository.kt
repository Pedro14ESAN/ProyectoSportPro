package com.example.proyectosportpro.features.academia.data

import com.example.proyectosportpro.features.academia.domain.JugadorDisponible
import com.example.proyectosportpro.features.academia.domain.JugadorPlantel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import java.util.Locale

class PlantelRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private fun uidActual() = checkNotNull(auth.currentUser?.uid) {
        "Debes iniciar sesión."
    }

    private fun comprobarSesion(uid: String) {
        check(auth.currentUser?.uid == uid) {
            "La sesión cambió. Vuelve a abrir el plantel."
        }
    }

    private fun validarId(id: String) {
        require(id.isNotBlank() && id.length <= 256 &&
                '/' !in id && id != "." && id != "..") {
            "El identificador no es válido."
        }
    }

    private fun academia(academiaId: String) =
        firestore.collection("academias").document(academiaId)

    private fun plantel(academiaId: String, equipoId: String) =
        academia(academiaId).collection("equipos")
            .document(equipoId).collection("plantel")

    suspend fun obtenerPlantel(
        academiaId: String,
        equipoId: String
    ): List<JugadorPlantel> {
        validarId(academiaId)
        validarId(equipoId)
        val uid = uidActual()
        val resultado = plantel(academiaId, equipoId)
            .get(Source.SERVER).await()
        comprobarSesion(uid)

        return resultado.documents.map {
            convertir(it, academiaId, equipoId)
        }.sortedWith(compareBy<JugadorPlantel> {
            !it.activo
        }.thenBy {
            it.nombre.lowercase(Locale.ROOT)
        }.thenBy { it.jugadorUid })
    }

    // Solo el administrador podrá consultar los candidatos de su academia.
    // No se consultan los perfiles deportivos privados.
    suspend fun obtenerJugadoresDisponibles(
        academiaId: String,
        equipoId: String
    ): List<JugadorDisponible> {
        validarId(academiaId)
        validarId(equipoId)
        val uid = uidActual()
        val academiaRef = academia(academiaId)
        val miembros = academiaRef.collection("miembros")
            .get(Source.SERVER).await()
        comprobarSesion(uid)
        val solicitudes = academiaRef.collection("solicitudesVinculacion")
            .whereEqualTo("estado", "APROBADA")
            .get(Source.SERVER).await()
        comprobarSesion(uid)
        val actuales = obtenerPlantel(academiaId, equipoId)
            .filter { it.activo }.map { it.jugadorUid }.toSet()
        comprobarSesion(uid)

        val aprobados = solicitudes.documents.filter {
            it.getString("academiaId") == academiaId &&
                    it.getString("solicitanteUid") == it.id &&
                    it.getString("rolSolicitante") == "JUG"
        }.associateBy { it.id }

        return miembros.documents.mapNotNull { miembro ->
            if (miembro.getString("uid") != miembro.id ||
                miembro.getString("academiaId") != academiaId ||
                miembro.getString("rol") != "JUG" ||
                miembro.getBoolean("activo") != true ||
                miembro.id in actuales) return@mapNotNull null
            val solicitud = aprobados[miembro.id] ?: return@mapNotNull null
            val nombre = checkNotNull(solicitud.getString("nombreSolicitante")) {
                "Una solicitud aprobada no tiene nombre."
            }
            check(nombre.isNotBlank()) { "El nombre del jugador no es válido." }
            JugadorDisponible(uid = miembro.id, nombre = nombre)
        }.sortedWith(compareBy<JugadorDisponible> {
            it.nombre.lowercase(Locale.ROOT)
        }.thenBy { it.uid })
    }

    suspend fun agregarJugador(
        academiaId: String,
        equipoId: String,
        jugadorUid: String
    ) = cambiarEstado(academiaId, equipoId, jugadorUid, activo = true)

    suspend fun retirarJugador(
        academiaId: String,
        equipoId: String,
        jugadorUid: String
    ) = cambiarEstado(academiaId, equipoId, jugadorUid, activo = false)

    private suspend fun cambiarEstado(
        academiaId: String,
        equipoId: String,
        jugadorUid: String,
        activo: Boolean
    ) {
        validarId(academiaId)
        validarId(equipoId)
        validarId(jugadorUid)
        val uid = uidActual()
        val academiaRef = academia(academiaId)
        val equipoRef = academiaRef.collection("equipos").document(equipoId)
        val jugadorRef = plantel(academiaId, equipoId).document(jugadorUid)

        firestore.runTransaction { tx ->
            comprobarSesion(uid)
            val cuenta = tx.get(firestore.collection("users").document(uid))
            val academiaDoc = tx.get(academiaRef)
            val equipo = tx.get(equipoRef)
            val existente = tx.get(jugadorRef)

            require(cuenta.getString("uid") == uid &&
                    cuenta.getString("rol") == "ADM" &&
                    cuenta.getString("estado") == "ACTIVO" &&
                    academiaDoc.getString("creadoPor") == uid) {
                "Solo el administrador propietario puede modificar el plantel."
            }
            require(academiaDoc.getBoolean("activa") == true) {
                "La academia debe estar activa."
            }
            require(equipo.exists() && equipo.getString("academiaId") == academiaId) {
                "El equipo no está disponible en esta academia."
            }
            if (existente.exists()) convertir(existente, academiaId, equipoId)

            if (activo) {
                require(equipo.getBoolean("activo") == true) {
                    "Reactiva el equipo antes de incorporar jugadores."
                }
                val miembro = tx.get(academiaRef.collection("miembros").document(jugadorUid))
                val solicitud = tx.get(
                    academiaRef.collection("solicitudesVinculacion").document(jugadorUid)
                )
                require(miembro.getString("uid") == jugadorUid &&
                        miembro.getString("academiaId") == academiaId &&
                        miembro.getString("rol") == "JUG" &&
                        miembro.getBoolean("activo") == true &&
                        solicitud.getString("solicitanteUid") == jugadorUid &&
                        solicitud.getString("academiaId") == academiaId &&
                        solicitud.getString("rolSolicitante") == "JUG" &&
                        solicitud.getString("estado") == "APROBADA") {
                    "El jugador debe tener un vínculo activo y una solicitud aprobada."
                }
                val nombre = checkNotNull(solicitud.getString("nombreSolicitante")) {
                    "La solicitud no tiene el nombre del jugador."
                }
                require(nombre.length in 2..100) { "El nombre del jugador no es válido." }

                // Reintentar una incorporación ya completada no duplica el registro.
                if (existente.getBoolean("activo") == true) return@runTransaction
                if (existente.exists()) {
                    tx.update(jugadorRef, mapOf(
                        "activo" to true,
                        "actualizadoPor" to uid,
                        "fechaActualizacion" to FieldValue.serverTimestamp()
                    ))
                } else {
                    tx.set(jugadorRef, mapOf(
                        "jugadorUid" to jugadorUid,
                        "academiaId" to academiaId,
                        "equipoId" to equipoId,
                        "nombre" to nombre,
                        "activo" to true,
                        "agregadoPor" to uid,
                        "actualizadoPor" to uid,
                        "fechaCreacion" to FieldValue.serverTimestamp(),
                        "fechaActualizacion" to FieldValue.serverTimestamp()
                    ))
                }
            } else {
                require(existente.exists()) { "El jugador no pertenece a este plantel." }
                if (existente.getBoolean("activo") == false) return@runTransaction
                tx.update(jugadorRef, mapOf(
                    "activo" to false,
                    "actualizadoPor" to uid,
                    "fechaActualizacion" to FieldValue.serverTimestamp()
                ))
            }
        }.await()
        comprobarSesion(uid)
    }

    private fun convertir(
        documento: DocumentSnapshot,
        academiaId: String,
        equipoId: String
    ): JugadorPlantel {
        check(documento.getString("jugadorUid") == documento.id &&
                documento.getString("academiaId") == academiaId &&
                documento.getString("equipoId") == equipoId) {
            "El registro no corresponde al plantel de este equipo."
        }
        return JugadorPlantel(
            jugadorUid = documento.id,
            academiaId = academiaId,
            equipoId = equipoId,
            nombre = checkNotNull(documento.getString("nombre")) {
                "El registro no tiene nombre."
            },
            activo = checkNotNull(documento.getBoolean("activo")) {
                "El registro no tiene un estado válido."
            },
            agregadoPor = checkNotNull(documento.getString("agregadoPor")) {
                "El registro no tiene un responsable de incorporación."
            },
            actualizadoPor = checkNotNull(documento.getString("actualizadoPor")) {
                "El registro no tiene un responsable de actualización."
            }
        )
    }
}
