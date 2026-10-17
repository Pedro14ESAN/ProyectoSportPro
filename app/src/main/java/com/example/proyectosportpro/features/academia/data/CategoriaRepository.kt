package com.example.proyectosportpro.features.academia.data

import com.example.proyectosportpro.features.academia.domain.Categoria
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await

class CategoriaRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private fun validarId(id: String) {
        require(id.isNotBlank() && !id.contains("/")) {
            "El identificador no es válido."
        }
    }

    private fun categorias(academiaId: String) =
        firestore.collection("academias")
            .document(academiaId)
            .collection("categorias")

    fun generarId(academiaId: String): String {
        validarId(academiaId)
        return categorias(academiaId).document().id
    }

    suspend fun crearCategoria(
        academiaId: String,
        categoriaId: String,
        nombre: String
    ) {
        validarId(academiaId)
        validarId(categoriaId)

        val uid = checkNotNull(auth.currentUser?.uid) {
            "Debes iniciar sesión."
        }

        val nombreLimpio = nombre.trim()

        require(nombreLimpio.length in 2..60) {
            "El nombre debe tener entre 2 y 60 caracteres."
        }

        val datos = mapOf(
            "academiaId" to academiaId,
            "nombre" to nombreLimpio,
            "activa" to true,
            "creadoPor" to uid,
            "actualizadoPor" to uid,
            "fechaCreacion" to FieldValue.serverTimestamp(),
            "fechaActualizacion" to FieldValue.serverTimestamp()
        )

        categorias(academiaId)
            .document(categoriaId)
            .set(datos)
            .await()
    }

    suspend fun obtenerCategorias(
        academiaId: String
    ): List<Categoria> {
        validarId(academiaId)

        checkNotNull(auth.currentUser) {
            "Debes iniciar sesión."
        }

        val resultado = categorias(academiaId)
            .get(Source.SERVER)
            .await()

        return resultado.documents.map { documento ->
            check(documento.getString("academiaId") == academiaId) {
                "La categoría no corresponde a la academia."
            }

            Categoria(
                id = documento.id,
                academiaId = academiaId,
                nombre = checkNotNull(
                    documento.getString("nombre")
                ) {
                    "La categoría no tiene nombre."
                },
                activa = checkNotNull(
                    documento.getBoolean("activa")
                ) {
                    "La categoría no tiene un estado válido."
                },
                creadoPor = checkNotNull(
                    documento.getString("creadoPor")
                ) {
                    "La categoría no tiene un creador registrado."
                },
                actualizadoPor = checkNotNull(
                    documento.getString("actualizadoPor")
                ) {
                    "La categoría no tiene un responsable de actualización."
                }
            )
        }.sortedBy { it.nombre.lowercase() }
    }
}