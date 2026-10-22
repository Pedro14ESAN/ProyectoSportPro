package com.example.proyectosportpro.features.academia.data

import com.example.proyectosportpro.features.academia.domain.EstadoSolicitudVinculacion
import com.example.proyectosportpro.features.academia.domain.SolicitudVinculacion
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

class SolicitudVinculacionRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private fun obtenerUid(): String {
        return checkNotNull(auth.currentUser?.uid) {
            "Debes iniciar sesión."
        }
    }

    private fun validarAcademiaId(academiaId: String) {
        require(academiaId.matches(Regex("^[A-Za-z0-9_-]{1,128}$"))) {
            "El código de la academia no es válido."
        }
    }

    private fun solicitudes(academiaId: String) =
        firestore.collection("academias")
            .document(academiaId)
            .collection("solicitudesVinculacion")

    suspend fun enviarSolicitud(academiaId: String) {
        validarAcademiaId(academiaId)

        val uid = obtenerUid()
        val perfilRef = firestore.collection("users").document(uid)

        // Un documento por usuario y academia evita solicitudes duplicadas.
        val solicitudRef = solicitudes(academiaId).document(uid)

        firestore.runTransaction { transaction ->
            val perfil = transaction.get(perfilRef)
            val existente = transaction.get(solicitudRef)

            check(perfil.exists()) {
                "No encontramos tu perfil."
            }

            require(perfil.getString("rol") == "DT") {
                "Esta solicitud está disponible para directores técnicos."
            }

            require(
                perfil.getString("estado") in
                        listOf("PENDIENTE_VINCULACION", "ACTIVO")
            ) {
                "Tu cuenta no está habilitada para solicitar vinculación."
            }

            val nombre = checkNotNull(perfil.getString("nombre")) {
                "Tu perfil no tiene un nombre registrado."
            }

            require(nombre.isNotBlank()) {
                "Tu perfil no tiene un nombre válido."
            }

            if (existente.exists()) {
                when (existente.getString("estado")) {
                    "PENDIENTE" -> {
                        // Un reintento de la misma solicitud no la duplica.
                        return@runTransaction
                    }

                    "APROBADA" -> throw IllegalArgumentException(
                        "Ya tienes una solicitud aprobada en esta academia."
                    )

                    "RECHAZADA" -> throw IllegalArgumentException(
                        "Tu solicitud fue rechazada. Contacta al administrador."
                    )

                    else -> error(
                        "La solicitud existente tiene un estado desconocido."
                    )
                }
            }

            transaction.set(
                solicitudRef,
                mapOf(
                    "academiaId" to academiaId,
                    "solicitanteUid" to uid,
                    "nombreSolicitante" to nombre,
                    "estado" to EstadoSolicitudVinculacion.PENDIENTE.name,
                    "fechaCreacion" to FieldValue.serverTimestamp()
                )
            )
        }.await()
    }

    suspend fun obtenerMiSolicitud(
        academiaId: String
    ): SolicitudVinculacion? {
        validarAcademiaId(academiaId)

        val documento = solicitudes(academiaId)
            .document(obtenerUid())
            .get(Source.SERVER)
            .await()

        if (!documento.exists()) return null

        return convertirSolicitud(documento, academiaId)
    }

    suspend fun obtenerSolicitudes(
        academiaId: String
    ): List<SolicitudVinculacion> {
        validarAcademiaId(academiaId)
        obtenerUid()

        // Las reglas limitarán esta consulta al administrador propietario.
        val resultado = solicitudes(academiaId)
            .get(Source.SERVER)
            .await()

        return resultado.documents
            .map { convertirSolicitud(it, academiaId) }
            .sortedWith(
                compareBy<SolicitudVinculacion> {
                    it.estado != EstadoSolicitudVinculacion.PENDIENTE
                }.thenBy {
                    it.nombreSolicitante.lowercase()
                }
            )
    }

    suspend fun resolverSolicitud(
        academiaId: String,
        solicitanteUid: String,
        aprobar: Boolean
    ) {
        validarAcademiaId(academiaId)
        require(solicitanteUid.isNotBlank() && !solicitanteUid.contains("/")) {
            "El usuario de la solicitud no es válido."
        }
        val administradorUid = obtenerUid()
        val solicitudRef = solicitudes(academiaId).document(solicitanteUid)
        val miembroRef = firestore.collection("academias").document(academiaId)
            .collection("miembros").document(solicitanteUid)
        val destino = if (aprobar) "APROBADA" else "RECHAZADA"

        firestore.runTransaction { transaction ->
            val solicitud = transaction.get(solicitudRef)
            val miembro = if (aprobar) transaction.get(miembroRef) else null
            check(solicitud.exists()) { "La solicitud ya no existe." }
            check(solicitud.getString("academiaId") == academiaId &&
                    solicitud.getString("solicitanteUid") == solicitanteUid) {
                "Los datos de la solicitud no son válidos."
            }
            val estado = solicitud.getString("estado")
            if (estado == destino) {
                check(!aprobar || (miembro?.getBoolean("activo") == true &&
                        miembro.getString("rol") == "DT")) {
                    "La solicitud ya fue aprobada, pero el vínculo no está activo."
                }
                return@runTransaction
            }
            require(estado == "PENDIENTE") {
                "La solicitud ya fue resuelta. Actualiza la lista."
            }
            if (aprobar) {
                require(miembro?.exists() == false) {
                    "Ya existe un vínculo para este usuario. Actualiza la lista."
                }
                transaction.set(miembroRef, mapOf(
                    "uid" to solicitanteUid,
                    "academiaId" to academiaId,
                    "rol" to "DT",
                    "activo" to true,
                    "creadoPor" to administradorUid,
                    "fechaCreacion" to FieldValue.serverTimestamp()
                ))
            }
            transaction.update(solicitudRef, mapOf(
                "estado" to destino,
                "revisadoPor" to administradorUid,
                "fechaRevision" to FieldValue.serverTimestamp()
            ))
        }.await()
    }

    private fun convertirSolicitud(
        documento: DocumentSnapshot,
        academiaId: String
    ): SolicitudVinculacion {
        check(documento.getString("academiaId") == academiaId) {
            "La solicitud no pertenece a esta academia."
        }

        val uid = checkNotNull(documento.getString("solicitanteUid")) {
            "La solicitud no tiene un usuario."
        }

        check(documento.id == uid) {
            "El identificador de la solicitud no es válido."
        }

        val codigoEstado = documento.getString("estado")
        val estado = EstadoSolicitudVinculacion.entries.firstOrNull {
            it.name == codigoEstado
        } ?: error("La solicitud tiene un estado desconocido.")

        return SolicitudVinculacion(
            id = documento.id,
            academiaId = academiaId,
            solicitanteUid = uid,
            nombreSolicitante = checkNotNull(
                documento.getString("nombreSolicitante")
            ) {
                "La solicitud no tiene un nombre."
            },
            estado = estado,
            revisadoPor = documento.getString("revisadoPor")
        )
    }
}