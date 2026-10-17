package com.example.proyectosportpro.features.academia.data

import com.example.proyectosportpro.features.academia.domain.Academia
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

class AcademiaRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private fun obtenerUid(): String {
        return checkNotNull(auth.currentUser?.uid) {
            "Debes iniciar sesión."
        }
    }

    fun generarId(): String {
        return firestore.collection("academias").document().id
    }

    suspend fun crearAcademia(
        academiaId: String,
        nombre: String
    ) {
        val uid = obtenerUid()
        val nombreLimpio = nombre.trim()

        require(academiaId.isNotBlank() && !academiaId.contains("/")) {
            "El identificador de la academia no es válido."
        }

        require(nombreLimpio.length in 2..100) {
            "El nombre debe tener entre 2 y 100 caracteres."
        }

        val datos = mapOf(
            "nombre" to nombreLimpio,
            "activa" to true,
            "creadoPor" to uid,
            "actualizadoPor" to uid,
            "fechaCreacion" to FieldValue.serverTimestamp(),
            "fechaActualizacion" to FieldValue.serverTimestamp()
        )

        firestore.collection("academias")
            .document(academiaId)
            .set(datos)
            .await()
    }

    suspend fun obtenerMisAcademias(): List<Academia> {
        val uid = obtenerUid()

        val resultado = firestore.collection("academias")
            // cada administrador consulta sus propias academias. Solo mis academias.
            .whereEqualTo("creadoPor", uid)
            .get(Source.SERVER)
            .await()

        return resultado.documents.map { documento ->
            Academia(
                id = documento.id,
                nombre = checkNotNull(
                    documento.getString("nombre")
                ) {
                    "La academia no tiene nombre."
                },
                activa = checkNotNull(
                    documento.getBoolean("activa")
                ) {
                    "La academia no tiene un estado válido."
                },
                creadoPor = checkNotNull(
                    documento.getString("creadoPor")
                ) {
                    "La academia no tiene un creador registrado."
                },
                actualizadoPor = checkNotNull(
                    documento.getString("actualizadoPor")
                ) {
                    "La academia no tiene un responsable de actualización."
                }
            )
        }.sortedBy { it.nombre.lowercase() }
    }
}