package com.example.proyectosportpro.features.academia.data

import com.example.proyectosportpro.features.academia.domain.EstadoSolicitudVinculacion
import com.example.proyectosportpro.features.academia.domain.SolicitudVinculacion
import com.example.proyectosportpro.features.auth.domain.RolUsuario
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

            comprobarSesion(uid)
            check(perfil.getString("uid") == uid) {
                "El perfil no corresponde a tu cuenta."
            }
            val rol = perfil.getString("rol")
            require(rol in listOf("DT", "JUG")) {
                "Esta solicitud está disponible para jugadores y directores técnicos."
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
                check(existente.getString("academiaId") == academiaId &&
                        existente.getString("solicitanteUid") == uid &&
                        leerRolSolicitud(existente).name == rol) {
                    "La solicitud existente no corresponde a tu cuenta o rol."
                }
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
                    "rolSolicitante" to rol,
                    "estado" to EstadoSolicitudVinculacion.PENDIENTE.name,
                    "fechaCreacion" to FieldValue.serverTimestamp()
                )
            )
        }.await()
        comprobarSesion(uid)
    }

    suspend fun obtenerMiSolicitud(
        academiaId: String
    ): SolicitudVinculacion? {
        validarAcademiaId(academiaId)

        val uid = obtenerUid()
        val documento = solicitudes(academiaId)
            .document(uid)
            .get(Source.SERVER)
            .await()

        comprobarSesion(uid)
        if (!documento.exists()) return null

        return convertirSolicitud(documento, academiaId)
    }

    suspend fun obtenerSolicitudes(
        academiaId: String
    ): List<SolicitudVinculacion> {
        validarAcademiaId(academiaId)
        val uid = obtenerUid()

        // Las reglas limitarán esta consulta al administrador propietario.
        val resultado = solicitudes(academiaId)
            .get(Source.SERVER)
            .await()

        comprobarSesion(uid)
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
            comprobarSesion(administradorUid)
            val solicitud = transaction.get(solicitudRef)
            val miembro = if (aprobar) transaction.get(miembroRef) else null
            check(solicitud.exists()) { "La solicitud ya no existe." }
            check(solicitud.getString("academiaId") == academiaId &&
                    solicitud.getString("solicitanteUid") == solicitanteUid) {
                "Los datos de la solicitud no son válidos."
            }
            val rol = leerRolSolicitud(solicitud)
            val estado = solicitud.getString("estado")
            if (estado == destino) {
                check(!aprobar || (miembro?.getBoolean("activo") == true &&
                        miembro.getString("rol") == rol.name &&
                        miembro.getString("uid") == solicitanteUid &&
                        miembro.getString("academiaId") == academiaId)) {
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
                    "rol" to rol.name,
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
        comprobarSesion(administradorUid)
    }

    private fun comprobarSesion(uid: String) {
        check(auth.currentUser?.uid == uid) {
            "La sesión cambió. Vuelve a abrir esta pantalla."
        }
    }

    private fun leerRolSolicitud(documento: DocumentSnapshot): RolUsuario {
        // Las solicitudes creadas antes de incorporar jugadores eran de DT.
        // Solo la ausencia del campo admite ese valor heredado.
        val codigo = if (documento.contains("rolSolicitante")) {
            documento.getString("rolSolicitante")
        } else {
            "DT"
        }
        return when (codigo) {
            "DT" -> RolUsuario.DT
            "JUG" -> RolUsuario.JUG
            else -> error("La solicitud tiene un rol no permitido.")
        }
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
            revisadoPor = documento.getString("revisadoPor"),
            rolSolicitante = leerRolSolicitud(documento)
        )
    }
}