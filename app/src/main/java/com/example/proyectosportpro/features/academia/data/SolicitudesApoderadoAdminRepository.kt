package com.example.proyectosportpro.features.academia.data

import com.example.proyectosportpro.features.academia.domain.EstadoSolicitudApoderado
import com.example.proyectosportpro.features.academia.domain.RelacionApoderado
import com.example.proyectosportpro.features.academia.domain.SolicitudApoderado
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

/** Las operaciones requieren las reglas del panel de revisión antes de usarse. */
class SolicitudesApoderadoAdminRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private fun uidActual(): String = checkNotNull(auth.currentUser?.uid) {
        "Debes iniciar sesión."
    }

    private fun comprobarSesion(uid: String) {
        check(auth.currentUser?.uid == uid) { "La sesión cambió. Abre nuevamente la pantalla." }
    }

    private fun validarId(id: String) {
        require(id.isNotBlank() && id.length <= 256 && '/' !in id && id != "." && id != "..") {
            "Identificador no válido."
        }
    }

    suspend fun obtenerSolicitudes(academiaId: String): List<SolicitudApoderado> {
        validarId(academiaId)
        val uid = uidActual()
        val resultado = db.collection("solicitudesApoderado")
            .whereEqualTo("academiaId", academiaId)
            .get(Source.SERVER).await()
        comprobarSesion(uid)
        // Orden local: evita necesitar un índice compuesto para este listado.
        return resultado.documents.map { convertir(it) }
            .sortedWith(compareBy<SolicitudApoderado> {
                if (it.estado == EstadoSolicitudApoderado.PENDIENTE) 0 else 1
            }.thenBy { it.nombreApoderado.lowercase() }.thenBy { it.id })
    }

    suspend fun aprobar(academiaId: String, solicitudId: String) =
        decidir(academiaId, solicitudId, EstadoSolicitudApoderado.APROBADA)

    suspend fun rechazar(academiaId: String, solicitudId: String) =
        decidir(academiaId, solicitudId, EstadoSolicitudApoderado.RECHAZADA)

    suspend fun revocar(academiaId: String, solicitudId: String) =
        decidir(academiaId, solicitudId, EstadoSolicitudApoderado.REVOCADA)

    private suspend fun decidir(
        academiaId: String,
        solicitudId: String,
        destino: EstadoSolicitudApoderado
    ) {
        validarId(academiaId)
        validarId(solicitudId)
        val uid = uidActual()
        val academiaRef = db.collection("academias").document(academiaId)
        val solicitudRef = db.collection("solicitudesApoderado").document(solicitudId)

        db.runTransaction { tx ->
            comprobarSesion(uid)
            val administrador = tx.get(db.collection("users").document(uid))
            val academia = tx.get(academiaRef)
            val documento = tx.get(solicitudRef)
            require(administrador.getString("rol") == "ADM" &&
                    administrador.getString("estado") == "ACTIVO" &&
                    academia.getString("creadoPor") == uid) {
                "Solo el administrador propietario puede revisar estas solicitudes."
            }
            val solicitud = convertir(documento)
            require(solicitud.academiaId == academiaId) { "La solicitud pertenece a otra academia." }

            // Un reintento de una decisión ya confirmada no vuelve a escribir ni cambia su fecha.
            if (solicitud.estado != destino) {
                val esRevocacion = destino == EstadoSolicitudApoderado.REVOCADA
                require(solicitud.estado == if (esRevocacion)
                    EstadoSolicitudApoderado.APROBADA else EstadoSolicitudApoderado.PENDIENTE) {
                    "El estado cambió. Actualiza las solicitudes antes de continuar."
                }

                val vinculoRef = academiaRef.collection("vinculosApoderados")
                    .document(solicitud.jugadorUid).collection("apoderados")
                    .document(solicitud.apoderadoUid)

                if (destino == EstadoSolicitudApoderado.APROBADA) {
                    val vinculo = tx.get(vinculoRef)
                    val miembro = tx.get(academiaRef.collection("miembros").document(solicitud.jugadorUid))
                    // Las reglas verifican las cuentas JUG y PAD sin exponer sus perfiles al administrador.
                    require(academia.getBoolean("activa") == true &&
                            miembro.getString("uid") == solicitud.jugadorUid &&
                            miembro.getString("academiaId") == academiaId &&
                            miembro.getString("rol") == "JUG" && miembro.getBoolean("activo") == true) {
                        "La academia, el jugador y el apoderado deben estar habilitados."
                    }
                    require(vinculo.getBoolean("activo") != true) {
                        "Este apoderado ya tiene un vínculo activo con el jugador."
                    }
                    tx.set(vinculoRef, mapOf(
                        "academiaId" to academiaId,
                        "jugadorUid" to solicitud.jugadorUid,
                        "apoderadoUid" to solicitud.apoderadoUid,
                        "solicitudId" to solicitudId,
                        "relacionDeclarada" to solicitud.relacionDeclarada.name,
                        "activo" to true,
                        "aprobadoPor" to uid,
                        "fechaAprobacion" to FieldValue.serverTimestamp()
                    ))
                } else if (esRevocacion) {
                    val vinculo = tx.get(vinculoRef)
                    require(vinculo.getString("solicitudId") == solicitudId &&
                            vinculo.getBoolean("activo") == true) {
                        "Esta solicitud ya no corresponde al vínculo activo. Actualiza la pantalla."
                    }
                    tx.update(vinculoRef, mapOf(
                        "activo" to false,
                        "revocadoPor" to uid,
                        "fechaRevocacion" to FieldValue.serverTimestamp()
                    ))
                }

                val cambios = mutableMapOf<String, Any>("estado" to destino.name)
                if (esRevocacion) {
                    cambios["revocadoPor"] = uid
                    cambios["fechaRevocacion"] = FieldValue.serverTimestamp()
                } else {
                    cambios["revisadoPor"] = uid
                    cambios["fechaRevision"] = FieldValue.serverTimestamp()
                }
                tx.update(solicitudRef, cambios)
            }
        }.await()
        comprobarSesion(uid)
    }

    private fun convertir(documento: DocumentSnapshot): SolicitudApoderado {
        check(documento.exists()) { "La solicitud ya no existe." }
        fun texto(campo: String): String = checkNotNull(documento.getString(campo)) {
            "La solicitud está incompleta: $campo."
        }
        val academiaId = texto("academiaId")
        val jugadorUid = texto("jugadorUid")
        val apoderadoUid = texto("apoderadoUid")
        listOf(academiaId, jugadorUid, apoderadoUid).forEach { validarId(it) }
        return SolicitudApoderado(
            id = documento.id,
            academiaId = academiaId,
            jugadorUid = jugadorUid,
            apoderadoUid = apoderadoUid,
            nombreApoderado = texto("nombreApoderado"),
            relacionDeclarada = RelacionApoderado.valueOf(texto("relacionDeclarada")),
            estado = EstadoSolicitudApoderado.valueOf(texto("estado")),
            revisadoPor = documento.getString("revisadoPor"),
            revocadoPor = documento.getString("revocadoPor")
        )
    }
}
