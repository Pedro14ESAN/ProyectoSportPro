package com.example.proyectosportpro.features.academia.data

import com.example.proyectosportpro.features.academia.domain.EquipoAsignado
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import java.util.Locale

class MisEquiposRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun obtenerMisEquipos(): List<EquipoAsignado> {
        val uid = checkNotNull(auth.currentUser?.uid) { "Debes iniciar sesión." }
        // Encuentra los vínculos propios, incluidos los creados antes de esta pantalla.
        val vinculos = firestore.collectionGroup("miembros")
            .whereEqualTo("uid", uid).get(Source.SERVER).await()
        val resultado = mutableListOf<EquipoAsignado>()

        for (vinculo in vinculos.documents) {
            val academiaRef = vinculo.reference.parent.parent ?: continue
            if (academiaRef.parent.path != "academias" || vinculo.id != uid) continue
            val academiaId = academiaRef.id
            if (vinculo.getString("academiaId") != academiaId ||
                vinculo.getString("rol") != "DT" || vinculo.getBoolean("activo") != true) continue

            val academia = academiaRef.get(Source.SERVER).await()
            if (!academia.exists() || academia.getBoolean("activa") != true) continue
            val nombreAcademia = checkNotNull(academia.getString("nombre"))
            val equipos = academiaRef.collection("equipos")
                .whereEqualTo("dtUid", uid).get(Source.SERVER).await()
            val categorias = mutableMapOf<String, String>()

            for (equipo in equipos.documents) {
                check(equipo.getString("academiaId") == academiaId &&
                        equipo.getString("dtUid") == uid) { "La asignación no es válida." }
                val categoriaId = checkNotNull(equipo.getString("categoriaId"))
                require(categoriaId.matches(Regex("^[A-Za-z0-9_-]{1,128}$")))
                if (categoriaId !in categorias) {
                    val categoria = academiaRef.collection("categorias").document(categoriaId)
                        .get(Source.SERVER).await()
                    categorias[categoriaId] = categoria.getString("nombre") ?: "Categoría no disponible"
                }
                resultado += EquipoAsignado(
                    id = equipo.id,
                    academiaId = academiaId,
                    academiaNombre = nombreAcademia,
                    categoriaNombre = categorias.getValue(categoriaId),
                    nombre = checkNotNull(equipo.getString("nombre")),
                    activo = checkNotNull(equipo.getBoolean("activo"))
                )
            }
        }
        check(auth.currentUser?.uid == uid) { "La sesión cambió. Vuelve a entrar." }
        return resultado.sortedWith(compareBy<EquipoAsignado> {
            it.academiaNombre.lowercase(Locale.ROOT)
        }.thenBy { it.nombre.lowercase(Locale.ROOT) })
    }
}
