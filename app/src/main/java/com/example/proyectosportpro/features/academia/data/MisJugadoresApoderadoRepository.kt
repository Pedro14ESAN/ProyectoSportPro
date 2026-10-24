package com.example.proyectosportpro.features.academia.data

import com.example.proyectosportpro.features.academia.domain.EstadoSolicitudApoderado
import com.example.proyectosportpro.features.academia.domain.JugadorVinculado
import com.example.proyectosportpro.features.academia.domain.RelacionApoderado
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import java.util.Locale

class MisJugadoresApoderadoRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private fun comprobarSesion(uid: String) {
        check(auth.currentUser?.uid == uid) { "La sesión cambió. Vuelve a abrir esta pantalla." }
    }

    suspend fun obtenerMisJugadores(): List<JugadorVinculado> {
        val uid = checkNotNull(auth.currentUser?.uid) { "Debes iniciar sesión." }
        // Las reglas solo permiten esta consulta filtrada por la propia cuenta.
        val solicitudes = firestore.collection("solicitudesApoderado")
            .whereEqualTo("apoderadoUid", uid)
            .get(Source.SERVER).await()
        comprobarSesion(uid)

        val resultado = mutableListOf<JugadorVinculado>()
        for (documento in solicitudes.documents) {
            val academiaId = documento.getString("academiaId") ?: continue
            val jugadorUid = documento.getString("jugadorUid") ?: continue
            if (documento.getString("apoderadoUid") != uid) continue
            val estado = EstadoSolicitudApoderado.entries
                .firstOrNull { it.name == documento.getString("estado") } ?: continue
            val relacion = RelacionApoderado.entries
                .firstOrNull { it.name == documento.getString("relacionDeclarada") } ?: continue
            // Las rechazadas no aportan información útil en esta vista.
            if (estado == EstadoSolicitudApoderado.RECHAZADA) continue

            var nombreJugador = "Jugador en revisión"
            var nombreAcademia = "Academia"
            var fechaAprobacion: Long? = null
            var estadoVisible = estado
            if (estado == EstadoSolicitudApoderado.APROBADA) {
                val vinculo = firestore.collection("academias").document(academiaId)
                    .collection("vinculosApoderados").document(jugadorUid)
                    .collection("apoderados").document(uid)
                    .get(Source.SERVER).await()
                comprobarSesion(uid)
                if (vinculo.getBoolean("activo") != true ||
                    vinculo.getString("solicitudId") != documento.id) {
                    estadoVisible = EstadoSolicitudApoderado.REVOCADA
                } else {
                    // Los vínculos aprobados antes de esta versión no guardan los nombres.
                    nombreJugador = vinculo.getString("nombreJugador") ?: "Jugador vinculado"
                    nombreAcademia = vinculo.getString("nombreAcademia") ?: nombreAcademia
                    fechaAprobacion = vinculo.getTimestamp("fechaAprobacion")?.toDate()?.time
                }
            }
            resultado += JugadorVinculado(
                solicitudId = documento.id,
                academiaId = academiaId,
                jugadorUid = jugadorUid,
                nombreJugador = nombreJugador,
                nombreAcademia = nombreAcademia,
                relacion = relacion,
                estado = estadoVisible,
                fechaAprobacionMillis = fechaAprobacion
            )
        }
        comprobarSesion(uid)
        return resultado.sortedWith(compareBy<JugadorVinculado> {
            when (it.estado) {
                EstadoSolicitudApoderado.APROBADA -> 0
                EstadoSolicitudApoderado.PENDIENTE -> 1
                else -> 2
            }
        }.thenBy { it.nombreJugador.lowercase(Locale.ROOT) }.thenBy { it.solicitudId })
    }
}
