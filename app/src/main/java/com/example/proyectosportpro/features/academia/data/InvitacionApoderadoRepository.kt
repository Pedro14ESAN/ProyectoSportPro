package com.example.proyectosportpro.features.academia.data

import com.example.proyectosportpro.features.academia.domain.EstadoInvitacionApoderado
import com.example.proyectosportpro.features.academia.domain.EstadoSolicitudApoderado
import com.example.proyectosportpro.features.academia.domain.InvitacionApoderado
import com.example.proyectosportpro.features.academia.domain.RelacionApoderado
import com.example.proyectosportpro.features.academia.domain.SolicitudApoderado
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

class InvitacionApoderadoRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // El ViewModel debe conservar este código para reintentar la misma creación.
    fun generarCodigo(): String = UUID.randomUUID().toString()

    private fun uidActual() = checkNotNull(auth.currentUser?.uid) { "Debes iniciar sesión." }

    private fun comprobarSesion(uid: String) {
        check(auth.currentUser?.uid == uid) { "La sesión cambió. Vuelve a abrir esta pantalla." }
    }

    private fun validarId(id: String) {
        require(id.isNotBlank() && id.length <= 256 && '/' !in id && id != "." && id != "..") {
            "El identificador no es válido."
        }
    }

    private fun validarCodigo(codigo: String) {
        require(codigo.matches(Regex("^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$"))) {
            "El código de invitación no es válido."
        }
    }

    suspend fun crearInvitacion(
        codigo: String,
        academiaId: String,
        jugadorUid: String,
        apoderadoUid: String
    ): InvitacionApoderado {
        validarCodigo(codigo)
        listOf(academiaId, jugadorUid, apoderadoUid).forEach { validarId(it) }
        require(jugadorUid != apoderadoUid) { "El jugador y el apoderado deben tener cuentas diferentes." }
        val uid = uidActual()
        val academiaRef = db.collection("academias").document(academiaId)
        val invitacionRef = db.collection("invitacionesApoderado").document(codigo)
        // La fecha propuesta se validará contra request.time en las reglas.
        val vencimiento = Timestamp(Date(System.currentTimeMillis() + 48L * 60 * 60 * 1000))

        db.runTransaction { tx ->
            comprobarSesion(uid)
            val cuenta = tx.get(db.collection("users").document(uid))
            val academia = tx.get(academiaRef)
            val miembro = tx.get(academiaRef.collection("miembros").document(jugadorUid))
            val existente = tx.get(invitacionRef)
            require(cuenta.getString("uid") == uid && cuenta.getString("rol") == "ADM" &&
                    cuenta.getString("estado") == "ACTIVO" && academia.getString("creadoPor") == uid &&
                    academia.getBoolean("activa") == true) {
                "Solo el administrador propietario de una academia activa puede crear invitaciones."
            }
            require(miembro.getString("uid") == jugadorUid &&
                    miembro.getString("academiaId") == academiaId &&
                    miembro.getString("rol") == "JUG" && miembro.getBoolean("activo") == true) {
                "El jugador debe tener un vínculo activo con esta academia."
            }
            if (existente.exists()) {
                check(existente.getString("academiaId") == academiaId &&
                        existente.getString("jugadorUid") == jugadorUid &&
                        existente.getString("apoderadoUid") == apoderadoUid &&
                        existente.getString("creadoPor") == uid) {
                    "El código ya corresponde a otra invitación."
                }
                return@runTransaction
            }
            // Las reglas comprobarán el rol y estado de la cuenta destinataria.
            // No se permite al administrador listar perfiles privados de usuarios.
            tx.set(invitacionRef, mapOf(
                "academiaId" to academiaId,
                "jugadorUid" to jugadorUid,
                "apoderadoUid" to apoderadoUid,
                "creadoPor" to uid,
                "estado" to "DISPONIBLE",
                "fechaCreacion" to FieldValue.serverTimestamp(),
                "fechaVencimiento" to vencimiento
            ))
        }.await()
        comprobarSesion(uid)
        val documento = invitacionRef.get(Source.SERVER).await()
        comprobarSesion(uid)
        return convertirInvitacion(documento)
    }

    /** Anula una invitación sin utilizar; si ya se anuló, el reintento no vuelve a escribir. */
    suspend fun anularInvitacion(codigo: String): InvitacionApoderado {
        validarCodigo(codigo)
        val uid = uidActual()
        val invitacionRef = db.collection("invitacionesApoderado").document(codigo)

        db.runTransaction { tx ->
            comprobarSesion(uid)
            val invitacion = convertirInvitacion(tx.get(invitacionRef))
            check(invitacion.creadoPor == uid) { "Solo quien creó la invitación puede anularla." }
            when (invitacion.estado) {
                EstadoInvitacionApoderado.REVOCADA -> return@runTransaction
                EstadoInvitacionApoderado.UTILIZADA -> error(
                    "La invitación ya se utilizó. Revisa la solicitud en Apoderados."
                )
                EstadoInvitacionApoderado.DISPONIBLE -> tx.update(invitacionRef, mapOf(
                    "estado" to "REVOCADA",
                    "revocadoPor" to uid,
                    "fechaRevocacion" to FieldValue.serverTimestamp()
                ))
            }
        }.await()
        comprobarSesion(uid)
        val documento = invitacionRef.get(Source.SERVER).await()
        comprobarSesion(uid)
        return convertirInvitacion(documento)
    }

    suspend fun utilizarInvitacion(
        codigoIngresado: String,
        relacion: RelacionApoderado
    ): SolicitudApoderado {
        val codigo = codigoIngresado.trim().lowercase(java.util.Locale.ROOT)
        validarCodigo(codigo)
        val uid = uidActual()
        val invitacionRef = db.collection("invitacionesApoderado").document(codigo)
        val solicitudRef = db.collection("solicitudesApoderado").document(codigo)

        db.runTransaction { tx ->
            comprobarSesion(uid)
            val cuenta = tx.get(db.collection("users").document(uid))
            val invitacion = tx.get(invitacionRef)
            val solicitud = tx.get(solicitudRef)
            require(cuenta.getString("uid") == uid && cuenta.getString("rol") == "PAD" &&
                    cuenta.getString("estado") in listOf("ACTIVO", "PENDIENTE_VINCULACION")) {
                "Debes usar una cuenta de apoderado habilitada."
            }
            require(invitacion.exists() && invitacion.getString("apoderadoUid") == uid) {
                "La invitación no está disponible para tu cuenta."
            }
            val datos = convertirInvitacion(invitacion)
            if (solicitud.exists()) {
                val anterior = convertirSolicitud(solicitud)
                check(anterior.apoderadoUid == uid && anterior.jugadorUid == datos.jugadorUid &&
                        anterior.academiaId == datos.academiaId &&
                        datos.estado == EstadoInvitacionApoderado.UTILIZADA && datos.solicitudId == codigo) {
                    "La invitación y la solicitud no coinciden."
                }
                // Reintentar consulta la solicitud original, sin crear otra ni cambiar la relación.
                return@runTransaction
            }
            require(datos.estado == EstadoInvitacionApoderado.DISPONIBLE) {
                "La invitación ya no está disponible."
            }
            // El servidor será la autoridad para comprobar el vencimiento.
            val nombre = checkNotNull(cuenta.getString("nombre")) { "Tu cuenta no tiene nombre." }
            require(nombre.length in 2..100) { "El nombre de tu cuenta no es válido." }
            tx.set(solicitudRef, mapOf(
                "academiaId" to datos.academiaId,
                "jugadorUid" to datos.jugadorUid,
                "apoderadoUid" to uid,
                "nombreApoderado" to nombre,
                "relacionDeclarada" to relacion.name,
                "estado" to "PENDIENTE",
                "fechaCreacion" to FieldValue.serverTimestamp()
            ))
            tx.update(invitacionRef, mapOf(
                "estado" to "UTILIZADA",
                "solicitudId" to codigo,
                "utilizadaPor" to uid,
                "fechaUso" to FieldValue.serverTimestamp()
            ))
        }.await()
        comprobarSesion(uid)
        return obtenerMiSolicitud(codigo)
            ?: error("No pudimos confirmar la solicitud. Consulta su estado antes de reintentar.")
    }

    suspend fun obtenerMiSolicitud(codigoIngresado: String): SolicitudApoderado? {
        val codigo = codigoIngresado.trim().lowercase(java.util.Locale.ROOT)
        validarCodigo(codigo)
        val uid = uidActual()
        val documento = db.collection("solicitudesApoderado").document(codigo)
            .get(Source.SERVER).await()
        comprobarSesion(uid)
        if (!documento.exists()) return null
        val solicitud = convertirSolicitud(documento)
        check(solicitud.apoderadoUid == uid) { "La solicitud no corresponde a tu cuenta." }
        return solicitud
    }

    private fun convertirInvitacion(d: DocumentSnapshot): InvitacionApoderado {
        check(d.exists()) { "No se encontró la invitación." }
        return InvitacionApoderado(
            id = d.id,
            academiaId = checkNotNull(d.getString("academiaId")),
            jugadorUid = checkNotNull(d.getString("jugadorUid")),
            apoderadoUid = checkNotNull(d.getString("apoderadoUid")),
            creadoPor = checkNotNull(d.getString("creadoPor")),
            fechaVencimientoMillis = checkNotNull(d.getTimestamp("fechaVencimiento")).toDate().time,
            estado = checkNotNull(EstadoInvitacionApoderado.entries.firstOrNull {
                it.name == d.getString("estado")
            }),
            solicitudId = d.getString("solicitudId")
        )
    }

    private fun convertirSolicitud(d: DocumentSnapshot): SolicitudApoderado {
        return SolicitudApoderado(
            id = d.id,
            academiaId = checkNotNull(d.getString("academiaId")),
            jugadorUid = checkNotNull(d.getString("jugadorUid")),
            apoderadoUid = checkNotNull(d.getString("apoderadoUid")),
            nombreApoderado = checkNotNull(d.getString("nombreApoderado")),
            relacionDeclarada = checkNotNull(RelacionApoderado.entries.firstOrNull {
                it.name == d.getString("relacionDeclarada")
            }),
            estado = checkNotNull(EstadoSolicitudApoderado.entries.firstOrNull {
                it.name == d.getString("estado")
            }),
            revisadoPor = d.getString("revisadoPor"),
            revocadoPor = d.getString("revocadoPor")
        )
    }
}
