package com.example.proyectosportpro.features.partidos.data

import com.example.proyectosportpro.features.partidos.domain.EstadoResumen
import com.example.proyectosportpro.features.partidos.domain.Partido
import com.example.proyectosportpro.features.partidos.domain.PromptResumen
import com.example.proyectosportpro.features.partidos.domain.ResumenPartido
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Resumen narrativo generado con Gemini mediante Firebase AI Logic y aprobado por el cuerpo técnico. */
class ResumenPartidoRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    // La clave de Gemini queda en Firebase; la app no guarda ninguna clave propia.
    private val modelo by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(MODELO)
    }

    private fun resumenRef(partido: Partido) = firestore.collection("academias").document(partido.academiaId)
        .collection("partidos").document(partido.id).collection("resumen").document("actual")

    fun escuchar(partido: Partido): Flow<ResumenPartido?> = callbackFlow {
        val registro = resumenRef(partido).addSnapshotListener { snapshot, error ->
            if (error != null) close(error)
            else if (snapshot != null) trySend(
                if (!snapshot.exists()) null else ResumenPartido(
                    texto = snapshot.getString("texto").orEmpty(),
                    estado = EstadoResumen.entries.firstOrNull { it.name == snapshot.getString("estado") }
                        ?: EstadoResumen.BORRADOR,
                    generadoConIA = snapshot.getBoolean("generadoConIA") == true,
                    fechaActualizacionMillis = snapshot.getTimestamp("fechaActualizacion")?.toDate()?.time
                )
            )
        }
        awaitClose { registro.remove() }
    }

    /** Devuelve el borrador; no se guarda hasta que el DT lo revise. */
    suspend fun generar(prompt: String): String {
        val texto = modelo.generateContent(prompt).text?.trim()
        if (texto.isNullOrEmpty()) error("La IA no devolvió un resumen. Inténtalo de nuevo.")
        return texto.take(PromptResumen.MAX_TEXTO)
    }

    suspend fun guardar(partido: Partido, texto: String, estado: EstadoResumen, generadoConIA: Boolean) {
        val uid = checkNotNull(auth.currentUser?.uid) { "Debes iniciar sesión." }
        resumenRef(partido).set(mapOf(
            "academiaId" to partido.academiaId,
            "partidoId" to partido.id,
            "texto" to texto.trim(),
            "estado" to estado.name,
            "generadoConIA" to generadoConIA,
            "modelo" to MODELO,
            "actualizadoPor" to uid,
            "fechaActualizacion" to FieldValue.serverTimestamp()
        )).await()
    }

    companion object {
        // Si Google retira este modelo, basta con cambiar el nombre aquí.
        const val MODELO = "gemini-3.5-flash"
    }
}
