package com.example.proyectosportpro.features.academia.data

// Este repositorio prepara la consulta de los equipos donde el jugador
// tiene una incorporación activa y mantiene un vínculo activo con la
// academia. Si lo retiran del plantel, el equipo dejará de aparecer al
// actualizar.
import com.example.proyectosportpro.features.academia.domain.EquipoAsignado
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import java.util.Locale

class MisEquiposJugadorRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private fun comprobarSesion(uid: String) {
        check(auth.currentUser?.uid == uid) {
            "La sesión cambió. Vuelve a abrir tus equipos."
        }
    }

    suspend fun obtenerMisEquipos(): List<EquipoAsignado> {
        val uid = checkNotNull(auth.currentUser?.uid) { "Debes iniciar sesión." }
        val cuenta = firestore.collection("users").document(uid)
            .get(Source.SERVER).await()
        comprobarSesion(uid)
        require(cuenta.getString("uid") == uid && cuenta.getString("rol") == "JUG" &&
                cuenta.getString("estado") in listOf("ACTIVO", "PENDIENTE_VINCULACION")) {
            "Esta consulta requiere una cuenta de jugador habilitada."
        }

        // La consulta identifica únicamente documentos pertenecientes a este jugador.
        // Las reglas de Firestore deben imponer también ese límite.
        val asignaciones = firestore.collectionGroup("plantel")
            .whereEqualTo("jugadorUid", uid)
            .get(Source.SERVER).await()
        comprobarSesion(uid)

        val resultado = mutableListOf<EquipoAsignado>()
        val membresias = mutableMapOf<String, Boolean>()
        val academias = mutableMapOf<String, DocumentSnapshot>()
        val categorias = mutableMapOf<String, String>()

        for (asignacion in asignaciones.documents) {
            comprobarSesion(uid)
            val partes = asignacion.reference.path.split('/')
            if (partes.size != 6 || partes[0] != "academias" ||
                partes[2] != "equipos" || partes[4] != "plantel" || partes[5] != uid) continue

            val academiaId = partes[1]
            val equipoId = partes[3]
            check(asignacion.getString("jugadorUid") == uid &&
                    asignacion.getString("academiaId") == academiaId &&
                    asignacion.getString("equipoId") == equipoId) {
                "Una asignación tiene datos inconsistentes."
            }
            if (asignacion.getBoolean("activo") != true) continue

            val academiaRef = firestore.collection("academias").document(academiaId)
            if (academiaId !in membresias) {
                val miembro = academiaRef.collection("miembros").document(uid)
                    .get(Source.SERVER).await()
                comprobarSesion(uid)
                membresias[academiaId] = miembro.getString("uid") == uid &&
                        miembro.getString("academiaId") == academiaId &&
                        miembro.getString("rol") == "JUG" && miembro.getBoolean("activo") == true
            }
            if (membresias[academiaId] != true) continue

            if (academiaId !in academias) {
                academias[academiaId] = academiaRef.get(Source.SERVER).await()
                comprobarSesion(uid)
            }
            val academia = academias.getValue(academiaId)
            if (!academia.exists() || academia.getBoolean("activa") != true) continue

            val equipo = academiaRef.collection("equipos").document(equipoId)
                .get(Source.SERVER).await()
            comprobarSesion(uid)
            if (!equipo.exists()) continue
            check(equipo.getString("academiaId") == academiaId) {
                "El equipo no corresponde a la academia."
            }
            val categoriaId = checkNotNull(equipo.getString("categoriaId")) {
                "El equipo no tiene categoría."
            }
            require(categoriaId.matches(Regex("^[A-Za-z0-9_-]{1,128}$"))) {
                "La categoría del equipo no es válida."
            }
            val claveCategoria = "$academiaId/$categoriaId"
            if (claveCategoria !in categorias) {
                val categoria = academiaRef.collection("categorias").document(categoriaId)
                    .get(Source.SERVER).await()
                comprobarSesion(uid)
                check(!categoria.exists() || categoria.getString("academiaId") == academiaId) {
                    "La categoría no corresponde a la academia."
                }
                categorias[claveCategoria] = categoria.getString("nombre") ?: "Categoría no disponible"
            }
            resultado += EquipoAsignado(
                id = equipoId,
                academiaId = academiaId,
                academiaNombre = checkNotNull(academia.getString("nombre")),
                categoriaNombre = categorias.getValue(claveCategoria),
                nombre = checkNotNull(equipo.getString("nombre")),
                activo = checkNotNull(equipo.getBoolean("activo"))
            )
        }
        comprobarSesion(uid)
        return resultado.sortedWith(compareBy<EquipoAsignado> {
            it.academiaNombre.lowercase(Locale.ROOT)
        }.thenBy { it.nombre.lowercase(Locale.ROOT) }.thenBy { "${it.academiaId}/${it.id}" })
    }
}
