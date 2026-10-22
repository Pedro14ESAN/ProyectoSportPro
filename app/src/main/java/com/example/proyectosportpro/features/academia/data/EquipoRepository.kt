package com.example.proyectosportpro.features.academia.data

import com.example.proyectosportpro.features.academia.domain.Equipo
import com.example.proyectosportpro.features.academia.domain.DirectorTecnico
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import java.util.Locale
import java.util.UUID

class EquipoRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private fun obtenerUid(): String {
        return checkNotNull(auth.currentUser?.uid) {
            "Debes iniciar sesión."
        }
    }

    private fun validarId(id: String) {
        require(id.matches(Regex("^[A-Za-z0-9_-]{1,128}$"))) {
            "El identificador no es válido."
        }
    }

    private fun equipos(academiaId: String) =
        firestore.collection("academias")
            .document(academiaId)
            .collection("equipos")

    fun nuevaSolicitudId(): String {
        return UUID.randomUUID().toString()
    }

    private fun normalizarNombre(nombre: String): String {
        val limpio = nombre.trim().replace(Regex(" +"), " ")

        require(limpio.length in 2..60) {
            "El nombre debe tener entre 2 y 60 caracteres."
        }

        require(
            limpio.matches(
                Regex("^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ0-9 .'-]+$")
            )
        ) {
            "Usa letras, números, espacios, puntos, apóstrofos o guiones."
        }

        return limpio
    }

    suspend fun crearEquipo(
        academiaId: String,
        categoriaId: String,
        nombre: String,
        solicitudId: String
    ): String {
        validarId(academiaId)
        validarId(categoriaId)
        require(solicitudId.matches(Regex(
            "^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$"
        ))) { "La solicitud no es válida." }

        val uid = obtenerUid()
        val nombreLimpio = normalizarNombre(nombre)
        val claveNombre = categoriaId + "_" + nombreLimpio.lowercase(Locale.ROOT)
        val equipoId = solicitudId
        val equipoRef = equipos(academiaId).document(equipoId)
        val reservaRef = firestore.collection("academias").document(academiaId)
            .collection("nombresEquipos").document(claveNombre)
        val anteriorRef = equipos(academiaId).document(claveNombre)
        val datos = mapOf(
            "academiaId" to academiaId,
            "categoriaId" to categoriaId,
            "nombre" to nombreLimpio,
            "activo" to true,
            "creadoPor" to uid,
            "actualizadoPor" to uid,
            "fechaCreacion" to FieldValue.serverTimestamp(),
            "fechaActualizacion" to FieldValue.serverTimestamp(),
            "solicitudId" to solicitudId
        )
        return firestore.runTransaction { tx ->
            val existente = tx.get(equipoRef)
            val anterior = tx.get(anteriorRef)
            val reserva = tx.get(reservaRef)
            when {
                existente.exists() -> {
                    require(existente.getString("solicitudId") == solicitudId &&
                            existente.getString("creadoPor") == uid &&
                            existente.getString("academiaId") == academiaId &&
                            existente.getString("categoriaId") == categoriaId
                    ) { "La solicitud no corresponde a este equipo." }
                    equipoId
                }
                anterior.exists() &&
                        anterior.getString("solicitudId") == solicitudId &&
                        anterior.getString("creadoPor") == uid -> anterior.id
                else -> {
                    val ocupado = anterior.exists() &&
                            anterior.getString("nombre")?.lowercase(Locale.ROOT) ==
                            nombreLimpio.lowercase(Locale.ROOT)
                    require(!reserva.exists() && !ocupado) {
                        "Ya existe un equipo con ese nombre en esta categoría. " +
                                "Revisa también los equipos inactivos."
                    }
                    tx.set(equipoRef, datos)
                    tx.set(reservaRef, mapOf("equipoId" to equipoId))
                    equipoId
                }
            }
        }.await()
    }

    suspend fun editarNombreEquipo(
        academiaId: String,
        equipoId: String,
        nombreAnterior: String,
        nuevoNombre: String
    ) {
        validarId(academiaId)
        require(equipoId.isNotBlank() && !equipoId.contains("/")) {
            "El identificador del equipo no es válido."
        }
        val uid = obtenerUid()
        val nombreLimpio = normalizarNombre(nuevoNombre)
        val equipoRef = equipos(academiaId).document(equipoId)
        val nombresRef = firestore.collection("academias").document(academiaId)
            .collection("nombresEquipos")

        firestore.runTransaction { tx ->
            val documento = tx.get(equipoRef)
            require(documento.exists()) { "El equipo ya no está disponible." }
            require(documento.getString("academiaId") == academiaId) {
                "El equipo no corresponde a esta academia."
            }
            val categoriaId = checkNotNull(documento.getString("categoriaId"))
            val nombreActual = checkNotNull(documento.getString("nombre"))
            if (nombreActual != nombreLimpio) {
                require(nombreActual == nombreAnterior) {
                    "El nombre cambió desde que abriste el formulario. " +
                            "Actualiza la lista y vuelve a intentarlo."
                }
                val claveAnterior = categoriaId + "_" + nombreActual.lowercase(Locale.ROOT)
                val claveNueva = categoriaId + "_" + nombreLimpio.lowercase(Locale.ROOT)
                val nuevaRef = nombresRef.document(claveNueva)
                val nueva = tx.get(nuevaRef)
                require(!nueva.exists() || nueva.getString("equipoId") == equipoId) {
                    "Ya existe un equipo con ese nombre en esta categoría. " +
                            "Revisa también los equipos inactivos."
                }
                val legado = tx.get(equipos(academiaId).document(claveNueva))
                require(!(legado.exists() && legado.id != equipoId &&
                        legado.getString("nombre")?.lowercase(Locale.ROOT) ==
                        nombreLimpio.lowercase(Locale.ROOT))) {
                    "Ya existe un equipo con ese nombre en esta categoría. " +
                            "Revisa también los equipos inactivos."
                }
                val anteriorRef = nombresRef.document(claveAnterior)
                val anterior = if (claveAnterior != claveNueva) tx.get(anteriorRef) else null

                // Todas las lecturas preceden a las escrituras.
                if (!nueva.exists()) tx.set(nuevaRef, mapOf("equipoId" to equipoId))
                tx.update(equipoRef, mapOf(
                    "nombre" to nombreLimpio,
                    "actualizadoPor" to uid,
                    "fechaActualizacion" to FieldValue.serverTimestamp()
                ))
                if (anterior != null && anterior.exists() &&
                    anterior.getString("equipoId") == equipoId) {
                    tx.delete(anteriorRef)
                }
            }
            true
        }.await()
    }

    suspend fun cambiarEstadoEquipo(
        academiaId: String,
        equipoId: String,
        activo: Boolean
    ) {
        validarId(academiaId)
        require(equipoId.isNotBlank() && !equipoId.contains("/")) {
            "El identificador del equipo no es válido."
        }

        val uid = obtenerUid()

        equipos(academiaId).document(equipoId)
            .update(
                mapOf(
                    "activo" to activo,
                    "actualizadoPor" to uid,
                    "fechaActualizacion" to FieldValue.serverTimestamp()
                )
            )
            .await()
    }

    suspend fun obtenerDirectoresTecnicos(
        academiaId: String
    ): List<DirectorTecnico> {
        validarId(academiaId)
        obtenerUid()
        val academiaRef = firestore.collection("academias").document(academiaId)
        val miembros = academiaRef.collection("miembros")
            .get(Source.SERVER).await()
        val aprobadas = academiaRef.collection("solicitudesVinculacion")
            .whereEqualTo("estado", "APROBADA")
            .get(Source.SERVER).await()

        val idsHabilitados = miembros.documents.filter {
            it.getString("uid") == it.id &&
                    it.getString("academiaId") == academiaId &&
                    it.getString("rol") == "DT" &&
                    it.getBoolean("activo") == true
        }.map { it.id }.toSet()

        return aprobadas.documents.filter {
            it.id in idsHabilitados &&
                    it.getString("solicitanteUid") == it.id &&
                    it.getString("academiaId") == academiaId
        }.map { documento ->
            DirectorTecnico(
                uid = documento.id,
                nombre = checkNotNull(documento.getString("nombreSolicitante")) {
                    "Una solicitud aprobada no tiene nombre."
                }
            )
        }.sortedWith(compareBy<DirectorTecnico> {
            it.nombre.lowercase(Locale.ROOT)
        }.thenBy { it.uid })
    }

    suspend fun asignarDirectorTecnico(
        academiaId: String,
        equipoId: String,
        dtAnteriorUid: String?,
        nuevoDtUid: String?
    ) {
        validarId(academiaId)
        require(equipoId.isNotBlank() && !equipoId.contains("/")) {
            "El identificador del equipo no es válido."
        }
        require(nuevoDtUid == null ||
                (nuevoDtUid.isNotBlank() && !nuevoDtUid.contains("/"))) {
            "El identificador del entrenador no es válido."
        }
        val administradorUid = obtenerUid()
        val academiaRef = firestore.collection("academias").document(academiaId)
        val equipoRef = equipos(academiaId).document(equipoId)

        firestore.runTransaction { tx ->
            val equipo = tx.get(equipoRef)
            require(equipo.exists() && equipo.getString("academiaId") == academiaId) {
                "El equipo ya no está disponible en esta academia."
            }
            val actualUid = equipo.getString("dtUid")
            // Un reintento después de un guardado confirmado no repite la escritura.
            if (actualUid == nuevoDtUid) return@runTransaction
            require(actualUid == dtAnteriorUid) {
                "La asignación cambió desde que abriste el equipo. Actualiza antes de continuar."
            }

            if (nuevoDtUid != null) {
                require(equipo.getBoolean("activo") == true) {
                    "Reactiva el equipo antes de asignarle un entrenador."
                }
                val miembro = tx.get(academiaRef.collection("miembros").document(nuevoDtUid))
                val solicitud = tx.get(
                    academiaRef.collection("solicitudesVinculacion").document(nuevoDtUid)
                )
                require(miembro.exists() &&
                        miembro.getString("uid") == nuevoDtUid &&
                        miembro.getString("academiaId") == academiaId &&
                        miembro.getString("rol") == "DT" &&
                        miembro.getBoolean("activo") == true &&
                        solicitud.getString("estado") == "APROBADA" &&
                        solicitud.getString("solicitanteUid") == nuevoDtUid &&
                        solicitud.getString("academiaId") == academiaId) {
                    "El DT debe tener un vínculo activo y una solicitud aprobada en esta academia."
                }
            }

            // Las reglas de Firestore también deben autorizar y validar esta asignación.
            tx.update(equipoRef, mapOf(
                "dtUid" to nuevoDtUid,
                "actualizadoPor" to administradorUid,
                "fechaActualizacion" to FieldValue.serverTimestamp()
            ))
        }.await()
    }

    suspend fun obtenerEquipos(
        academiaId: String
    ): List<Equipo> {
        validarId(academiaId)
        obtenerUid()

        val resultado = equipos(academiaId)
            .get(Source.SERVER)
            .await()

        return resultado.documents.map { documento ->
            check(documento.getString("academiaId") == academiaId) {
                "El equipo no corresponde a esta academia."
            }

            Equipo(
                id = documento.id,
                academiaId = academiaId,
                categoriaId = checkNotNull(
                    documento.getString("categoriaId")
                ) {
                    "El equipo no tiene una categoría."
                },
                nombre = checkNotNull(
                    documento.getString("nombre")
                ) {
                    "El equipo no tiene nombre."
                },
                activo = checkNotNull(
                    documento.getBoolean("activo")
                ) {
                    "El equipo no tiene un estado válido."
                },
                creadoPor = checkNotNull(
                    documento.getString("creadoPor")
                ) {
                    "El equipo no tiene un creador registrado."
                },
                actualizadoPor = checkNotNull(
                    documento.getString("actualizadoPor")
                ) {
                    "El equipo no tiene un responsable de actualización."
                },
                dtUid = documento.getString("dtUid")
            )
        }.sortedBy { it.nombre.lowercase(Locale.ROOT) }
    }
}